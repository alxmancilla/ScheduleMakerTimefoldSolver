package com.example;

import ai.timefold.solver.core.api.solver.Solver;
import ai.timefold.solver.core.api.solver.SolverFactory;
import com.example.data.DemoDataGenerator;
import com.example.domain.SchoolSchedule;
import com.example.solver.SchoolSolverConfig;
import java.util.*;
import com.example.analysis.ScheduleAnalyzer;
import com.example.util.PdfReporter;
import com.example.util.SchedulePrinter;
import com.example.util.ScheduleReport;
import java.io.IOException;

public class MainApp {

    public static void main(String[] args) {
        // Generate demo data
        SchoolSchedule initialSchedule = DemoDataGenerator.generateDemoData();

        System.out.println("=== School Schedule Solver ===");
        System.out.println("Initial problem:");
        System.out.println("  Teachers: " + initialSchedule.getTeachers().size());
        System.out.println("  Courses: " + initialSchedule.getCourses().size());
        System.out.println("  Rooms: " + initialSchedule.getRooms().size());
        System.out.println("  Timeslots: " + initialSchedule.getTimeslots().size());
        System.out.println("  Groups: " + initialSchedule.getGroups().size());
        System.out.println("  Course Assignments: " + initialSchedule.getCourseAssignments().size());
        System.out.println();

        // Build solver
        SolverFactory<SchoolSchedule> solverFactory = SchoolSolverConfig.buildSolverFactory();

        // SolverFactory<SchoolSchedule> solverFactory = SolverFactory
        // .createFromXmlResource("solverConfig.xml");

        Solver<SchoolSchedule> solver = solverFactory.buildSolver();

        // Solve
        System.out.println("Solving...");
        SchoolSchedule solvedSchedule = solver.solve(initialSchedule);

        // Analyze solution and build report object
        Map<String, Integer> hardViolations = ScheduleAnalyzer.analyzeHardConstraintViolations(solvedSchedule);
        Map<String, List<String>> hardViolationsDetailed = ScheduleAnalyzer
                .analyzeHardConstraintViolationsDetailed(solvedSchedule);
        Map<String, Integer> softViolations = ScheduleAnalyzer.analyzeSoftConstraintViolations(solvedSchedule);
        Map<String, List<String>> softViolationsDetailed = ScheduleAnalyzer
                .analyzeSoftConstraintViolationsDetailed(solvedSchedule);
        ScheduleReport report = new ScheduleReport(solvedSchedule, hardViolations, hardViolationsDetailed,
                softViolations, softViolationsDetailed);

        // Print console reports
        SchedulePrinter.printFullReport(report);

        // Write PDF report
        try {
            String base = "schedule-report";
            PdfReporter.generateReports(solvedSchedule, report.getHardViolations(), report.getSoftViolations(), base);
            System.out.println("PDF reports written to: " + base + "-violations.pdf, " + base + "-by-teacher.pdf, "
                    + base + "-by-group.pdf");
        } catch (IOException e) {
            System.err.println("Failed to write PDF report: " + e.getMessage());
        }
    }
}
