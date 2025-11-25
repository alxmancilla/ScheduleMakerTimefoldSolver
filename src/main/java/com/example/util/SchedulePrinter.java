package com.example.util;

import com.example.domain.CourseAssignment;
import com.example.domain.SchoolSchedule;
import java.time.DayOfWeek;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public class SchedulePrinter {

    public static void printFullReport(ScheduleReport report) {
        System.out.println();
        System.out.println("=== Solved Schedule ===");
        System.out.println("Score: " + report.getSchedule().getScore());
        System.out.println();

        printViolations(report);

        System.out.println("=== Schedule by Day ===");
        printScheduleByDay(report.getSchedule());

        System.out.println();
        System.out.println("=== Schedule by Teacher ===");
        printScheduleByTeacher(report.getSchedule());

        System.out.println();
        System.out.println("=== Schedule by Group ===");
        printScheduleByGroup(report.getSchedule());
    }

    public static void printViolations(ScheduleReport report) {
        System.out.println("=== Hard Constraint Violations (by rule) ===");
        report.getHardViolations().forEach((k, v) -> System.out.println("- " + k + ": " + v));
        System.out.println();

        System.out.println("=== Hard Constraint Violations (details) ===");
        report.getHardViolationsDetailed().forEach((rule, offenders) -> {
            System.out.println("- " + rule + ": " + offenders.size());
            for (String desc : offenders) {
                System.out.println("    " + desc);
            }
        });
        System.out.println();

        System.out.println("=== Soft Constraint Violations (by rule) ===");
        report.getSoftViolations().forEach((k, v) -> System.out.println("- " + k + ": " + v));
        System.out.println();

        System.out.println("=== Soft Constraint Violations (details) ===");
        report.getSoftViolationsDetailed().forEach((rule, offenders) -> {
            System.out.println("- " + rule + ": " + offenders.size());
            for (String desc : offenders) {
                System.out.println("    " + desc);
            }
        });
        System.out.println();
    }

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

            long hoursPerWeek = teacherAssignments.stream()
                    .mapToInt(a -> a.getCourse().getRequiredHoursPerWeek()).sum() /
                    teacherAssignments.stream().map(a -> a.getCourse().getName()).distinct().count();
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