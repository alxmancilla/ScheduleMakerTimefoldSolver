package com.example.reporter;

import ai.timefold.solver.core.api.score.buildin.hardsoft.HardSoftScore;
import com.example.analysis.BlockScheduleAnalyzer;
import com.example.data.DataLoader;
import com.example.domain.SchoolSchedule;
import com.example.util.PdfReporter;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Standalone worker that generates the block-based PDF reports.
 *
 * It is fully decoupled from the solver: the solved schedule is reconstructed
 * from PostgreSQL (the same rows the engine persists and the web API serves),
 * violations are recomputed with {@link BlockScheduleAnalyzer}, and the reports
 * are written with {@link PdfReporter}. Run it on demand or right after the
 * engine, sharing only the database.
 */
public class PdfReportApp {

    public static void main(String[] args) throws Exception {
        System.out.println("=== Block-Based Schedule PDF Reporter ===");
        System.out.println();

        // Database connection (overridable via DB_URL / DB_USER / DB_PASSWORD).
        String jdbcUrl = System.getenv().getOrDefault("DB_URL", "jdbc:postgresql://localhost:5432/school_schedule");
        String username = System.getenv().getOrDefault("DB_USER", "mancilla");
        String password = System.getenv().getOrDefault("DB_PASSWORD", "");

        // Optional: generate from a specific past schedule_run instead of the
        // current schedule (unset means "current", same as before this existed).
        String reportRunIdEnv = System.getenv("REPORT_RUN_ID");
        Integer reportRunId = (reportRunIdEnv != null && !reportRunIdEnv.isBlank())
                ? Integer.parseInt(reportRunIdEnv.trim())
                : null;
        if (reportRunId != null) {
            System.out.println("Generating from schedule run #" + reportRunId + " (not the current schedule)");
        }

        DataLoader dataLoader = new DataLoader(jdbcUrl, username, password);
        SchoolSchedule schedule = dataLoader.loadDataForBlockScheduling(reportRunId);

        // Term is a display-only label (not tied to any particular schedule_run -
        // see school_term's own docs), so it's always the current value regardless
        // of which run the schedule content above came from.
        String termLabel = loadCurrentTermLabel(jdbcUrl, username, password);

        // When solved (schedule_run.created_at) the run backing this report's content
        // actually is - reportRunId if one was explicitly requested, otherwise the
        // same latest run DataLoader just resolved above. Also carries that run's
        // persisted score: DataLoader only loads placements, never solves, so
        // schedule.getScore() (Timefold's @PlanningScore field) is always null on a
        // schedule reconstructed this way - schedule_run.hard_score/soft_score is
        // the only place the actual score still exists once the solver process that
        // produced it has exited.
        ScheduleRunMetadata runMetadata = loadScheduleRunMetadata(jdbcUrl, username, password, reportRunId);
        LocalDateTime scheduleRunTimestamp = runMetadata != null ? runMetadata.createdAt() : null;
        if (runMetadata != null) {
            schedule.setScore(runMetadata.score());
        }

        // Report chrome language ("es" or anything else -> "en"); unset defaults to
        // English, same as before this existed. This only covers PdfReporter's own
        // fixed text (titles, labels, day names) - the violation detail sentences
        // from BlockScheduleAnalyzer below are not covered by this yet.
        String locale = System.getenv().getOrDefault("REPORT_LOCALE", "en");

        System.out.println();
        System.out.println("Schedule loaded from database:");
        System.out.println("  Course Block Assignments: " + schedule.getCourseBlockAssignments().size());
        System.out.println();

        // Recompute violations from the persisted schedule.
        System.out.println("=== Hard Constraint Violations (by rule) ===");
        Map<String, Integer> violations = BlockScheduleAnalyzer.analyzeHardConstraintViolations(schedule);
        violations.forEach((k, v) -> System.out.println("- " + k + ": " + v));
        System.out.println();

        System.out.println("=== Hard Constraint Violations (details) ===");
        Map<String, List<String>> details = BlockScheduleAnalyzer.analyzeHardConstraintViolationsDetailed(schedule);
        details.forEach((rule, offenders) -> {
            System.out.println("- " + rule + ": " + offenders.size());
            for (String desc : offenders) {
                System.out.println("    " + desc);
            }
        });
        System.out.println();

        System.out.println("=== Soft Constraint Violations (by rule) ===");
        Map<String, Integer> softViolations = BlockScheduleAnalyzer.analyzeSoftConstraintViolations(schedule);
        softViolations.forEach((k, v) -> System.out.println("- " + k + ": " + v));
        System.out.println();

        // Write PDF report(s) into the working directory, per REPORT_TARGET:
        //   violations (EngineRunnerService's automatic post-solve admin snapshot)
        //     -> only calendario-incumplimientos.pdf
        //   schedules (ReportRunnerService's WRITER-triggered "Generate PDFs" button)
        //     -> only the by-teacher/by-group reports, never the violations report -
        //        that one only ever comes from a specific solve, not from whatever
        //        the schedule happens to look like when someone clicks the button
        //   all (default; direct/manual runs outside the web app)
        //     -> all three
        String base = "calendario";
        String reportTarget = System.getenv().getOrDefault("REPORT_TARGET", "all");
        if ("violations".equalsIgnoreCase(reportTarget)) {
            PdfReporter.generateBlockViolationsPdf(schedule, violations, softViolations,
                    base + "-incumplimientos.pdf", scheduleRunTimestamp, locale);
            System.out.println("PDF report written to:");
            System.out.println("  - " + base + "-incumplimientos.pdf");
        } else if ("schedules".equalsIgnoreCase(reportTarget)) {
            PdfReporter.generateBlockSchedulePdfs(schedule, base, termLabel, scheduleRunTimestamp, locale);
            System.out.println("PDF reports written to:");
            System.out.println("  - " + base + "-por-maestro.pdf");
            System.out.println("  - " + base + "-por-grupo.pdf");
        } else {
            PdfReporter.generateBlockReports(schedule, violations, softViolations, base, termLabel,
                    scheduleRunTimestamp, locale);
            System.out.println("PDF reports written to:");
            System.out.println("  - " + base + "-incumplimientos.pdf");
            System.out.println("  - " + base + "-por-maestro.pdf");
            System.out.println("  - " + base + "-por-grupo.pdf");
        }
        System.out.println();
        System.out.println("=== PDF Reporting Complete! ===");
    }

    /** The current term label (school_term is a singleton, display-only, id=1), or null if unset/unreachable. */
    private static String loadCurrentTermLabel(String jdbcUrl, String username, String password) {
        try (Connection conn = DriverManager.getConnection(jdbcUrl, username, password);
                Statement stmt = conn.createStatement();
                ResultSet rs = stmt.executeQuery("SELECT label FROM school_term WHERE id = 1")) {
            if (rs.next()) {
                return rs.getString("label");
            }
        } catch (Exception e) {
            System.out.println("Note: could not load the current term label for the report cover page: " + e.getMessage());
        }
        return null;
    }

    /** The timestamp and persisted score of the schedule_run backing this report's content. */
    private record ScheduleRunMetadata(LocalDateTime createdAt, HardSoftScore score) {
    }

    /**
     * When the schedule_run backing this report's content was actually solved -
     * that specific run if scheduleRunId is non-null, otherwise the latest run
     * (same COALESCE-to-MAX(id) resolution DataLoader's default path uses) -
     * plus that run's persisted hard_score/soft_score, since a schedule
     * reconstructed by DataLoader never goes through the solver and so never
     * gets its @PlanningScore field populated any other way. Null if no
     * schedule_run exists yet or the lookup fails.
     */
    private static ScheduleRunMetadata loadScheduleRunMetadata(String jdbcUrl, String username, String password,
            Integer scheduleRunId) {
        String sql = "SELECT created_at, hard_score, soft_score FROM schedule_run "
                + "WHERE id = COALESCE(?, (SELECT MAX(id) FROM schedule_run))";
        try (Connection conn = DriverManager.getConnection(jdbcUrl, username, password);
                PreparedStatement stmt = conn.prepareStatement(sql)) {
            if (scheduleRunId != null) {
                stmt.setInt(1, scheduleRunId);
            } else {
                stmt.setNull(1, Types.INTEGER);
            }
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    Timestamp ts = rs.getTimestamp("created_at");
                    LocalDateTime createdAt = ts != null ? ts.toLocalDateTime() : null;
                    HardSoftScore score = HardSoftScore.of(rs.getInt("hard_score"), rs.getInt("soft_score"));
                    return new ScheduleRunMetadata(createdAt, score);
                }
            }
        } catch (Exception e) {
            System.out.println(
                    "Note: could not load the schedule run metadata for the report cover page: " + e.getMessage());
        }
        return null;
    }
}
