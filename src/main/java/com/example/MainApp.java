package com.example;

import ai.timefold.solver.core.api.solver.Solver;
import ai.timefold.solver.core.api.solver.SolverFactory;

import com.example.data.DataLoader;
import com.example.data.DataSaver;
import com.example.data.DemoDataGenerator;
import com.example.domain.CourseAssignment;
import com.example.domain.SchoolSchedule;
import com.example.solver.SchoolSolverConfig;
import java.time.DayOfWeek;
import java.util.*;
import com.example.analysis.ScheduleAnalyzer;
import com.example.util.PdfReporter;
import java.io.IOException;
import java.sql.SQLException;

public class MainApp {

    public static void main(String[] args) throws Exception {
        // Generate demo data
        // SchoolSchedule initialSchedule = DemoDataGenerator.generateDemoData();
        String jdbcUrl = "jdbc:postgresql://localhost:5432/school_schedule";
        String username = "mancilla";
        String password = "";
        DataLoader dataLoader = new DataLoader(jdbcUrl, username, password);
        SchoolSchedule initialSchedule = dataLoader.loadData();

        System.out.println("=== School Schedule Solver ===");
        System.out.println("Initial problem:");
        System.out.println("  Teachers: " + initialSchedule.getTeachers().size());
        System.out.println("  Courses: " + initialSchedule.getCourses().size());
        System.out.println("  Rooms: " + initialSchedule.getRooms().size());
        System.out.println("  Timeslots: " + initialSchedule.getTimeslots().size());
        System.out.println("  Groups: " + initialSchedule.getGroups().size());
        System.out.println("  Course Assignments: " + initialSchedule.getCourseAssignments().size());
        System.out.println();

        // System.exit(0);

        // Build solver
        SolverFactory<SchoolSchedule> solverFactory = SchoolSolverConfig.buildSolverFactory();
        Solver<SchoolSchedule> solver = solverFactory.buildSolver();

        // Solve
        System.out.println("Solving...");
        SchoolSchedule solvedSchedule = solver.solve(initialSchedule);

        // Print results
        System.out.println();
        System.out.println("=== Solved Schedule ===");
        System.out.println("Score: " + solvedSchedule.getScore());
        System.out.println();

        // Analyze hard constraint violations by checking the solution against each hard
        // rule
        System.out.println("=== Hard Constraint Violations (by rule) ===");
        Map<String, Integer> violations = ScheduleAnalyzer.analyzeHardConstraintViolations(solvedSchedule);
        violations.forEach((k, v) -> System.out.println("- " + k + ": " + v));
        System.out.println();

        // Also print detailed offending assignments for each hard rule
        System.out.println("=== Hard Constraint Violations (details) ===");
        Map<String, List<String>> details = ScheduleAnalyzer.analyzeHardConstraintViolationsDetailed(solvedSchedule);
        details.forEach((rule, offenders) -> {
            System.out.println("- " + rule + ": " + offenders.size());
            for (String desc : offenders) {
                System.out.println("    " + desc);
            }
        });
        System.out.println();

        /** */
        // Analyze soft constraint violations (counts)
        System.out.println("=== Soft Constraint Violations (by rule) ===");
        Map<String, Integer> softViolations = ScheduleAnalyzer.analyzeSoftConstraintViolations(solvedSchedule);
        softViolations.forEach((k, v) -> System.out.println("- " + k + ": " + v));
        System.out.println();

        // Also print detailed offending assignments for each soft rule
        System.out.println("=== Soft Constraint Violations (details) ===");
        Map<String, List<String>> softDetails = ScheduleAnalyzer
                .analyzeSoftConstraintViolationsDetailed(solvedSchedule);

        /**
         * softDetails.forEach((rule, offenders) -> {
         * System.out.println("- " + rule + ": " + offenders.size());
         * for (String desc : offenders) {
         * System.out.println(" " + desc);
         * }
         * });
         */
        System.out.println();

        // Save results back to database
        System.out.println();
        System.out.println("=== Saving to Database ===");
        DataSaver dataSaver = new DataSaver(jdbcUrl, username, password);
        try {
            dataSaver.saveSchedule(solvedSchedule);

            // Print statistics
            System.out.println();
            System.out.println("=== Database Statistics ===");
            Map<String, Integer> stats = dataSaver.getScheduleStatistics();
            stats.forEach((k, v) -> System.out.println("- " + k + ": " + v));
        } catch (SQLException e) {
            System.err.println("Failed to save schedule to database: " + e.getMessage());
            e.printStackTrace();
        }

        // Print schedule by day
        // System.out.println("=== Schedule by Day ===");
        // printScheduleByDay(solvedSchedule);

        // Print schedule by teacher
        System.out.println();
        // System.out.println("=== Schedule by Teacher ===");
        // printScheduleByTeacher(solvedSchedule);

        // Print schedule by group
        System.out.println();
        // System.out.println("=== Schedule by Group ===");
        // printScheduleByGroup(solvedSchedule);

        // Write PDF report
        try {
            String base = "calendario";
            PdfReporter.generateReports(solvedSchedule, violations, softViolations, base);
            System.out
                    .println("PDF reports written to: " + base + "-incumplimientos.pdf, " + base + "-por-maestro.pdf, "
                            + base + "-por-grupo.pdf");
        } catch (IOException e) {
            System.err.println("Failed to write PDF report: " + e.getMessage());
        }

    }

    // Soft constraint analysis

    private static void printScheduleByDay(SchoolSchedule schedule) {
        Map<DayOfWeek, List<CourseAssignment>> byDay = new TreeMap<>();

        for (CourseAssignment assignment : schedule.getCourseAssignments()) {
            if (assignment.getTimeslot() != null) {
                byDay.computeIfAbsent(assignment.getTimeslot().getDayOfWeek(), k -> new ArrayList<>())
                        .add(assignment);
            }
        }

        for (DayOfWeek day : byDay.keySet()) {
            System.out.println(day + ":");
            List<CourseAssignment> dayAssignments = byDay.get(day);
            dayAssignments.sort(Comparator.comparing(a -> a.getTimeslot().getHour()));

            for (CourseAssignment assignment : dayAssignments) {
                System.out.printf("  %s: %s (Group: %s, Teacher: %s, Room: %s)%n",
                        assignment.getTimeslot(),
                        assignment.getCourse().getName(),
                        assignment.getGroup().getName(),
                        assignment.getTeacher() != null ? assignment.getTeacher().getName() : "UNASSIGNED",
                        assignment.getRoom() != null ? assignment.getRoom().getName() : "UNASSIGNED");
            }
        }
    }

    private static void printScheduleByTeacher(SchoolSchedule schedule) {
        Map<String, List<CourseAssignment>> byTeacher = new TreeMap<>();

        for (CourseAssignment assignment : schedule.getCourseAssignments()) {
            if (assignment.getTeacher() != null) {
                byTeacher.computeIfAbsent(assignment.getTeacher().getName(), k -> new ArrayList<>())
                        .add(assignment);
            }
        }

        for (String teacherName : byTeacher.keySet()) {
            System.out.println(teacherName + ":");
            List<CourseAssignment> teacherAssignments = byTeacher.get(teacherName);
            teacherAssignments.sort(Comparator.comparing(a -> {
                if (a.getTimeslot() == null)
                    return Integer.MAX_VALUE;
                return a.getTimeslot().getDayOfWeek().getValue() * 100 + a.getTimeslot().getHour();
            }));

            for (CourseAssignment assignment : teacherAssignments) {
                System.out.printf("  %s: %s (Group: %s, Room: %s)%n",
                        assignment.getTimeslot() != null ? assignment.getTimeslot() : "UNASSIGNED",
                        assignment.getCourse().getName(),
                        assignment.getGroup().getName(),
                        assignment.getRoom() != null ? assignment.getRoom().getName() : "UNASSIGNED");
            }

            int hoursPerWeek = (int) schedule.getCourseAssignments().stream()
                    .filter(a -> a.getTeacher() != null && a.getTeacher().getName().equals(teacherName))
                    .count();
            System.out.println("  Total hours: " + hoursPerWeek);
            System.out.println();
        }
    }

    private static void printScheduleByGroup(SchoolSchedule schedule) {
        Map<String, List<CourseAssignment>> byGroup = new TreeMap<>();

        for (CourseAssignment assignment : schedule.getCourseAssignments()) {
            byGroup.computeIfAbsent(assignment.getGroup().getName(), k -> new ArrayList<>())
                    .add(assignment);
        }

        for (String groupName : byGroup.keySet()) {
            System.out.println(groupName + ":");
            List<CourseAssignment> groupAssignments = byGroup.get(groupName);
            groupAssignments.sort(Comparator.comparing(a -> {
                if (a.getTimeslot() == null)
                    return Integer.MAX_VALUE;
                return a.getTimeslot().getDayOfWeek().getValue() * 100 + a.getTimeslot().getHour();
            }));

            for (CourseAssignment assignment : groupAssignments) {
                System.out.printf("  %s: %s (Teacher: %s, Room: %s)%n",
                        assignment.getTimeslot() != null ? assignment.getTimeslot() : "UNASSIGNED",
                        assignment.getCourse().getName(),
                        assignment.getTeacher() != null ? assignment.getTeacher().getName() : "UNASSIGNED",
                        assignment.getRoom() != null ? assignment.getRoom().getName() : "UNASSIGNED");
            }
            System.out.println();
        }
    }
}
