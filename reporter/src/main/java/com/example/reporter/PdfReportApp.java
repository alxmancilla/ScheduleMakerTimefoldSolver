package com.example.reporter;

import com.example.analysis.BlockScheduleAnalyzer;
import com.example.data.DataLoader;
import com.example.domain.SchoolSchedule;
import com.example.util.PdfReporter;

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

        DataLoader dataLoader = new DataLoader(jdbcUrl, username, password);
        SchoolSchedule schedule = dataLoader.loadDataForBlockScheduling();

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
            PdfReporter.generateBlockViolationsPdf(schedule, violations, softViolations, base + "-incumplimientos.pdf");
            System.out.println("PDF report written to:");
            System.out.println("  - " + base + "-incumplimientos.pdf");
        } else if ("schedules".equalsIgnoreCase(reportTarget)) {
            PdfReporter.generateBlockSchedulePdfs(schedule, base);
            System.out.println("PDF reports written to:");
            System.out.println("  - " + base + "-por-maestro.pdf");
            System.out.println("  - " + base + "-por-grupo.pdf");
        } else {
            PdfReporter.generateBlockReports(schedule, violations, softViolations, base);
            System.out.println("PDF reports written to:");
            System.out.println("  - " + base + "-incumplimientos.pdf");
            System.out.println("  - " + base + "-por-maestro.pdf");
            System.out.println("  - " + base + "-por-grupo.pdf");
        }
        System.out.println();
        System.out.println("=== PDF Reporting Complete! ===");
    }
}
