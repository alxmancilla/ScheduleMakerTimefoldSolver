package com.example;

import ai.timefold.solver.core.api.solver.Solver;
import ai.timefold.solver.core.api.solver.SolverFactory;
import com.example.data.DemoDataGenerator;
import com.example.domain.CourseAssignment;
import com.example.domain.SchoolSchedule;
import com.example.solver.SchoolSolverConfig;
import java.time.DayOfWeek;
import java.util.*;

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
        Map<String, Integer> violations = analyzeHardConstraintViolations(solvedSchedule);
        violations.forEach((k, v) -> System.out.println("- " + k + ": " + v));
        System.out.println();

        // Also print detailed offending assignments for each hard rule
        System.out.println("=== Hard Constraint Violations (details) ===");
        Map<String, List<String>> details = analyzeHardConstraintViolationsDetailed(solvedSchedule);
        details.forEach((rule, offenders) -> {
            System.out.println("- " + rule + ": " + offenders.size());
            for (String desc : offenders) {
                System.out.println("    " + desc);
            }
        });
        System.out.println();
        // Print schedule by day
        System.out.println("=== Schedule by Day ===");
        printScheduleByDay(solvedSchedule);

        // Print schedule by teacher
        System.out.println();
        System.out.println("=== Schedule by Teacher ===");
        printScheduleByTeacher(solvedSchedule);

        // Print schedule by group
        System.out.println();
        System.out.println("=== Schedule by Group ===");
        printScheduleByGroup(solvedSchedule);
    }

    private static Map<String, Integer> analyzeHardConstraintViolations(SchoolSchedule schedule) {
        Map<String, Integer> result = new LinkedHashMap<>();

        // Teacher must be qualified
        int unqualified = 0;
        for (CourseAssignment a : schedule.getCourseAssignments()) {
            if (a.getTeacher() != null && !a.getTeacher().isQualifiedFor(a.getCourse().getName()))
                unqualified++;
        }
        result.put("Teacher must be qualified", unqualified);

        // Teacher must be available at timeslot
        int unavailable = 0;
        for (CourseAssignment a : schedule.getCourseAssignments()) {
            if (a.getTeacher() != null && a.getTimeslot() != null
                    && !a.getTeacher().isAvailableAt(a.getTimeslot()))
                unavailable++;
        }
        result.put("Teacher must be available at timeslot", unavailable);

        // No teacher double-booking (count unique conflicting pairs)
        int teacherDouble = 0;
        List<CourseAssignment> list = schedule.getCourseAssignments();
        for (int i = 0; i < list.size(); i++) {
            for (int j = i + 1; j < list.size(); j++) {
                CourseAssignment a1 = list.get(i);
                CourseAssignment a2 = list.get(j);
                if (a1.getTeacher() != null && a1.getTeacher().equals(a2.getTeacher())
                        && a1.getTimeslot() != null && a1.getTimeslot().equals(a2.getTimeslot())) {
                    teacherDouble++;
                }
            }
        }
        result.put("No teacher double-booking", teacherDouble);

        // No room double-booking
        int roomDouble = 0;
        for (int i = 0; i < list.size(); i++) {
            for (int j = i + 1; j < list.size(); j++) {
                CourseAssignment a1 = list.get(i);
                CourseAssignment a2 = list.get(j);
                if (a1.getRoom() != null && a1.getRoom().equals(a2.getRoom())
                        && a1.getTimeslot() != null && a1.getTimeslot().equals(a2.getTimeslot())) {
                    roomDouble++;
                }
            }
        }
        result.put("No room double-booking", roomDouble);

        // Room type must satisfy course requirement
        int roomTypeMismatch = 0;
        for (CourseAssignment a : schedule.getCourseAssignments()) {
            if (a.getRoom() != null && !a.getRoom().satisfiesRequirement(a.getCourse().getRoomRequirement()))
                roomTypeMismatch++;
        }
        result.put("Room type must satisfy course requirement", roomTypeMismatch);

        // Group cannot have two courses at same time
        int groupConflict = 0;
        for (int i = 0; i < list.size(); i++) {
            for (int j = i + 1; j < list.size(); j++) {
                CourseAssignment a1 = list.get(i);
                CourseAssignment a2 = list.get(j);
                if (a1.getGroup().equals(a2.getGroup())
                        && a1.getTimeslot() != null && a1.getTimeslot().equals(a2.getTimeslot())) {
                    groupConflict++;
                }
            }
        }
        result.put("Group cannot have two courses at same time", groupConflict);

        // Same teacher for all course hours
        int sameTeacherViolation = 0;
        for (int i = 0; i < list.size(); i++) {
            for (int j = i + 1; j < list.size(); j++) {
                CourseAssignment a1 = list.get(i);
                CourseAssignment a2 = list.get(j);
                if (a1.getGroup().equals(a2.getGroup()) && a1.getCourse().equals(a2.getCourse())
                        && a1.getTeacher() != null && a2.getTeacher() != null
                        && !a1.getTeacher().equals(a2.getTeacher())) {
                    sameTeacherViolation++;
                }
            }
        }
        result.put("Same teacher for all course hours", sameTeacherViolation);

        // Group non-lab courses must use same room
        int groupRoomMismatch = 0;
        for (int i = 0; i < list.size(); i++) {
            for (int j = i + 1; j < list.size(); j++) {
                CourseAssignment a1 = list.get(i);
                CourseAssignment a2 = list.get(j);
                if (!a1.getGroup().equals(a2.getGroup()))
                    continue;
                if (a1.getRoom() == null || a2.getRoom() == null)
                    continue;
                boolean a1IsLab = "lab".equals(a1.getCourse().getRoomRequirement());
                boolean a2IsLab = "lab".equals(a2.getCourse().getRoomRequirement());
                if (!a1IsLab && !a2IsLab && !a1.getRoom().equals(a2.getRoom())) {
                    groupRoomMismatch++;
                }
            }
        }
        result.put("Group non-lab courses must use same room", groupRoomMismatch);

        return result;
    }

    private static Map<String, List<String>> analyzeHardConstraintViolationsDetailed(SchoolSchedule schedule) {
        Map<String, List<String>> details = new LinkedHashMap<>();
        List<CourseAssignment> list = schedule.getCourseAssignments();

        // Teacher must be qualified
        List<String> unqualified = new ArrayList<>();
        for (CourseAssignment a : list) {
            if (a.getTeacher() != null && !a.getTeacher().isQualifiedFor(a.getCourse().getName())) {
                unqualified.add(assignmentToString(a));
            }
        }
        details.put("Teacher must be qualified", unqualified);

        // Teacher must be available at timeslot
        List<String> unavailable = new ArrayList<>();
        for (CourseAssignment a : list) {
            if (a.getTeacher() != null && a.getTimeslot() != null
                    && !a.getTeacher().isAvailableAt(a.getTimeslot())) {
                unavailable.add(assignmentToString(a));
            }
        }
        details.put("Teacher must be available at timeslot", unavailable);

        // No teacher double-booking
        List<String> teacherDouble = new ArrayList<>();
        for (int i = 0; i < list.size(); i++) {
            for (int j = i + 1; j < list.size(); j++) {
                CourseAssignment a1 = list.get(i);
                CourseAssignment a2 = list.get(j);
                if (a1.getTeacher() != null && a1.getTeacher().equals(a2.getTeacher())
                        && a1.getTimeslot() != null && a1.getTimeslot().equals(a2.getTimeslot())) {
                    teacherDouble.add(assignmentToString(a1) + "  <->  " + assignmentToString(a2));
                }
            }
        }
        details.put("No teacher double-booking", teacherDouble);

        // No room double-booking
        List<String> roomDouble = new ArrayList<>();
        for (int i = 0; i < list.size(); i++) {
            for (int j = i + 1; j < list.size(); j++) {
                CourseAssignment a1 = list.get(i);
                CourseAssignment a2 = list.get(j);
                if (a1.getRoom() != null && a1.getRoom().equals(a2.getRoom())
                        && a1.getTimeslot() != null && a1.getTimeslot().equals(a2.getTimeslot())) {
                    roomDouble.add(assignmentToString(a1) + "  <->  " + assignmentToString(a2));
                }
            }
        }
        details.put("No room double-booking", roomDouble);

        // Room type must satisfy course requirement
        List<String> roomTypeMismatch = new ArrayList<>();
        for (CourseAssignment a : list) {
            if (a.getRoom() != null && !a.getRoom().satisfiesRequirement(a.getCourse().getRoomRequirement())) {
                roomTypeMismatch
                        .add(assignmentToString(a) + " (roomRequirement=" + a.getCourse().getRoomRequirement() + ")");
            }
        }
        details.put("Room type must satisfy course requirement", roomTypeMismatch);

        // Group cannot have two courses at same time
        List<String> groupConflict = new ArrayList<>();
        for (int i = 0; i < list.size(); i++) {
            for (int j = i + 1; j < list.size(); j++) {
                CourseAssignment a1 = list.get(i);
                CourseAssignment a2 = list.get(j);
                if (a1.getGroup().equals(a2.getGroup())
                        && a1.getTimeslot() != null && a1.getTimeslot().equals(a2.getTimeslot())) {
                    groupConflict.add(assignmentToString(a1) + "  <->  " + assignmentToString(a2));
                }
            }
        }
        details.put("Group cannot have two courses at same time", groupConflict);

        // Same teacher for all course hours
        List<String> sameTeacherViolation = new ArrayList<>();
        for (int i = 0; i < list.size(); i++) {
            for (int j = i + 1; j < list.size(); j++) {
                CourseAssignment a1 = list.get(i);
                CourseAssignment a2 = list.get(j);
                if (a1.getGroup().equals(a2.getGroup()) && a1.getCourse().equals(a2.getCourse())
                        && a1.getTeacher() != null && a2.getTeacher() != null
                        && !a1.getTeacher().equals(a2.getTeacher())) {
                    sameTeacherViolation.add(assignmentToString(a1) + "  <->  " + assignmentToString(a2));
                }
            }
        }
        details.put("Same teacher for all course hours", sameTeacherViolation);

        // Group non-lab courses must use same room
        List<String> groupRoomMismatch = new ArrayList<>();
        for (int i = 0; i < list.size(); i++) {
            for (int j = i + 1; j < list.size(); j++) {
                CourseAssignment a1 = list.get(i);
                CourseAssignment a2 = list.get(j);
                if (!a1.getGroup().equals(a2.getGroup()))
                    continue;
                if (a1.getRoom() == null || a2.getRoom() == null)
                    continue;
                boolean a1IsLab = "lab".equals(a1.getCourse().getRoomRequirement());
                boolean a2IsLab = "lab".equals(a2.getCourse().getRoomRequirement());
                if (!a1IsLab && !a2IsLab && !a1.getRoom().equals(a2.getRoom())) {
                    groupRoomMismatch.add(assignmentToString(a1) + "  <->  " + assignmentToString(a2));
                }
            }
        }
        details.put("Group non-lab courses must use same room", groupRoomMismatch);

        return details;
    }

    private static String assignmentToString(CourseAssignment a) {
        String timeslot = a.getTimeslot() != null ? a.getTimeslot().toString() : "UNASSIGNED";
        String teacher = a.getTeacher() != null ? a.getTeacher().getName() : "UNASSIGNED";
        String room = a.getRoom() != null ? a.getRoom().getName() : "UNASSIGNED";
        return String.format("%s [%s] slot=%s teacher=%s room=%s",
                Integer.valueOf(System.identityHashCode(a)), a.getCourse().getName(), timeslot, teacher, room);
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
