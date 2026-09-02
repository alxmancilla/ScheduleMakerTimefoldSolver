package com.example;

import ai.timefold.solver.core.api.solver.Solver;
import ai.timefold.solver.core.api.solver.SolverFactory;
import ai.timefold.solver.core.api.solver.event.BestSolutionChangedEvent;
import ai.timefold.solver.core.api.solver.event.SolverEventListener;

import com.example.data.DataLoader;
import com.example.data.DataSaver;
import com.example.data.DemoDataGenerator;
import com.example.domain.SchoolSchedule;
import com.example.solver.SchoolSolverConfig;
import com.example.analysis.BlockScheduleAnalyzer;
import com.example.validation.PreSolveValidator;
import com.example.validation.ValidationResult;

import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Main application for block-based scheduling.
 * This demonstrates the complete workflow for block-based course scheduling:
 * 1. Load or generate block-based demo data
 * 2. Solve the scheduling problem
 * 3. Analyze constraint violations
 * 4. Save results to database
 *
 * PDF reports are generated separately by the reporter module.
 */
public class MainBlockSchedulingApp {

    public static void main(String[] args) throws Exception {
        System.out.println("=== Block-Based School Schedule Solver ===");
        System.out.println();

        // Option 1: Generate demo data (no database required)
        // SchoolSchedule initialSchedule = DemoDataGenerator.generateBlockDemoData();

        // Option 2: Load from database (overridable via DB_URL / DB_USER / DB_PASSWORD)
        String jdbcUrl = System.getenv().getOrDefault("DB_URL", "jdbc:postgresql://localhost:5432/school_schedule");
        String username = System.getenv().getOrDefault("DB_USER", "mancilla");
        String password = System.getenv().getOrDefault("DB_PASSWORD", "");
        DataLoader dataLoader = new DataLoader(jdbcUrl, username, password);
        SchoolSchedule initialSchedule = dataLoader.loadDataForBlockScheduling();

        System.out.println("Initial problem:");
        System.out.println("  Teachers: " + initialSchedule.getTeachers().size());
        System.out.println("  Courses: " + initialSchedule.getCourses().size());
        System.out.println("  Rooms: " + initialSchedule.getRooms().size());
        System.out.println("  Block Timeslots: " + initialSchedule.getBlockTimeslots().size());
        System.out.println("  Groups: " + initialSchedule.getGroups().size());
        System.out.println("  Course Block Assignments: " + initialSchedule.getCourseBlockAssignments().size());
        System.out.println();

        // Validate before solving: invalid pinned data (excluded from the
        // solver's hard constraints, so it would otherwise be silently
        // accepted) and whole-schedule capacity facts (e.g. a teacher
        // assigned more hours than they have availability for) that make at
        // least one hard violation mathematically certain regardless of how
        // well the solve goes. Fail fast with a clear report instead of
        // burning the full solve budget to confirm what's already provable.
        //
        // SKIP_PRESOLVE_VALIDATION=true proceeds to solve anyway - validation
        // still runs and prints everything it finds either way, nothing is
        // hidden; the flag only changes whether a non-empty result aborts the
        // run. Every check here is a proven mathematical fact, not a
        // heuristic, so there's no legitimate reason to disable one
        // selectively - this is a blanket "I know something's broken, let me
        // see the best-effort result anyway" escape hatch for testing/debugging,
        // not a way to silence a check you disagree with.
        ValidationResult validation = PreSolveValidator.validate(initialSchedule);
        System.out.println(validation.describe());
        System.out.println();
        boolean skipValidation = parseBooleanEnv("SKIP_PRESOLVE_VALIDATION");
        if (!validation.isValid()) {
            if (skipValidation) {
                System.out.println("SKIP_PRESOLVE_VALIDATION is set - proceeding despite the problem(s) above.");
                System.out.println();
            } else {
                System.err.println("Aborting solve: fix the problems above and retry "
                        + "(or set SKIP_PRESOLVE_VALIDATION=true to proceed anyway).");
                System.exit(1);
            }
        }

        // Build solver, optionally overriding the local search time budget via env
        // vars (SOLVER_MINUTES_LIMIT / SOLVER_UNIMPROVED_MINUTES_LIMIT) - unset means
        // "use solverConfig.xml's own values" (5 / 2 minutes), same as before this
        // override point existed.
        Long minutesSpentLimit = parseLongEnv("SOLVER_MINUTES_LIMIT");
        Long unimprovedMinutesSpentLimit = parseLongEnv("SOLVER_UNIMPROVED_MINUTES_LIMIT");
        SchoolSolverConfig.Built built = SchoolSolverConfig.build(minutesSpentLimit, unimprovedMinutesSpentLimit);
        SolverFactory<SchoolSchedule> solverFactory = built.factory();
        Solver<SchoolSchedule> solver = solverFactory.buildSolver();

        // Add event listener to track progress. Best-solution events can fire very
        // frequently, so throttle logging to at most one line every 5 seconds (plus
        // the first improvement) to keep the output readable.
        final AtomicInteger improvementCounter = new AtomicInteger(0);
        final AtomicLong lastLogMillis = new AtomicLong(0);
        final long startTime = System.currentTimeMillis();

        solver.addEventListener(new SolverEventListener<SchoolSchedule>() {
            @Override
            public void bestSolutionChanged(BestSolutionChangedEvent<SchoolSchedule> event) {
                int improvement = improvementCounter.incrementAndGet();
                long now = System.currentTimeMillis();
                long previous = lastLogMillis.get();
                // Log the first improvement, then at most one line every 5 seconds.
                boolean shouldLog = previous == 0 || now - previous >= 5000;
                if (shouldLog && lastLogMillis.compareAndSet(previous, now)) {
                    System.out.println(String.format(
                            "[+%ds] new best score: %s (improvement #%d)",
                            (now - startTime) / 1000,
                            event.getNewBestSolution().getScore(),
                            improvement));
                }
            }
        });

        // Solve
        System.out.println("Solving...");
        System.out.println("NOTE: If no progress is shown after 30 seconds, the solver may be stuck.");
        System.out.println();
        SchoolSchedule solvedSchedule = solver.solve(initialSchedule);

        // Print results
        System.out.println();
        System.out.println("=== Solved Schedule ===");
        System.out.println("Score: " + solvedSchedule.getScore());
        System.out.println();

        // Analyze hard constraint violations
        System.out.println("=== Hard Constraint Violations (by rule) ===");
        Map<String, Integer> violations = BlockScheduleAnalyzer.analyzeHardConstraintViolations(solvedSchedule);
        violations.forEach((k, v) -> System.out.println("- " + k + ": " + v));
        System.out.println();

        // Print detailed violations
        System.out.println("=== Hard Constraint Violations (details) ===");
        Map<String, List<String>> details = BlockScheduleAnalyzer
                .analyzeHardConstraintViolationsDetailed(solvedSchedule);
        details.forEach((rule, offenders) -> {
            System.out.println("- " + rule + ": " + offenders.size());
            for (String desc : offenders) {
                System.out.println("    " + desc);
            }
        });
        System.out.println();

        // Analyze soft constraint violations
        System.out.println("=== Soft Constraint Violations (by rule) ===");
        Map<String, Integer> softViolations = BlockScheduleAnalyzer.analyzeSoftConstraintViolations(solvedSchedule);
        softViolations.forEach((k, v) -> System.out.println("- " + k + ": " + v));
        System.out.println();

        // Save to database
        System.out.println("=== Saving to Database ===");
        DataSaver dataSaver = new DataSaver(jdbcUrl, username, password);
        try {
            dataSaver.saveSchedule(solvedSchedule, built.minutesSpentLimit(), built.unimprovedMinutesSpentLimit(),
                    violations.keySet(), softViolations.keySet());

            // Print statistics
            System.out.println();
            System.out.println("=== Database Statistics ===");
            Map<String, Integer> stats = dataSaver.getBlockScheduleStatistics();
            stats.forEach((k, v) -> System.out.println("- " + k + ": " + v));
        } catch (SQLException e) {
            System.err.println("Failed to save schedule to database: " + e.getMessage());
            e.printStackTrace();
        }
        System.out.println();

        System.out.println("=== Block-Based Scheduling Complete! ===");
        System.out.println("Run the reporter module to generate PDF reports from the persisted schedule.");
    }

    /** Null if the env var is unset/blank; throws on a set-but-non-numeric value rather than silently ignoring it. */
    private static Long parseLongEnv(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            return null;
        }
        return Long.parseLong(value.trim());
    }

    private static boolean parseBooleanEnv(String name) {
        String value = System.getenv(name);
        return value != null && Boolean.parseBoolean(value.trim());
    }
}
