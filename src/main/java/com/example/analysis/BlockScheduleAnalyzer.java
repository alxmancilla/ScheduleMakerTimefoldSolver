package com.example.analysis;

import com.example.domain.BlockTimeslot;
import com.example.domain.CourseBlockAssignment;
import com.example.domain.SchoolSchedule;
import com.example.domain.Teacher;
import java.time.DayOfWeek;
import java.util.*;

/**
 * Analyzer for block-based scheduling constraints.
 * Analyzes hard and soft constraint violations for CourseBlockAssignment
 * entities.
 */
public final class BlockScheduleAnalyzer {

    private BlockScheduleAnalyzer() {
    }

    /**
     * Analyze hard constraint violations for block-based schedule.
     * Returns a map of constraint name to violation count.
     */
    public static Map<String, Integer> analyzeHardConstraintViolations(SchoolSchedule schedule) {
        Map<String, Integer> result = new LinkedHashMap<>();

        if (schedule.getCourseBlockAssignments() == null) {
            return result; // No block assignments to analyze
        }

        List<CourseBlockAssignment> list = schedule.getCourseBlockAssignments();

        // Teacher must be qualified
        int unqualified = 0;
        for (CourseBlockAssignment a : list) {
            if (a.getTeacher() != null && !a.getTeacher().isQualifiedFor(a.getCourse().getName()))
                unqualified++;
        }
        result.put("Teacher must be qualified", unqualified);

        // Teacher must be available for entire block
        int unavailable = 0;
        for (CourseBlockAssignment a : list) {
            if (a.getTeacher() != null && a.getTimeslot() != null
                    && !a.getTeacher().isAvailableForBlock(a.getTimeslot()))
                unavailable++;
        }
        result.put("Teacher must be available for entire block", unavailable);

        // No teacher double-booking (blocks overlap)
        int teacherDouble = 0;
        for (int i = 0; i < list.size(); i++) {
            for (int j = i + 1; j < list.size(); j++) {
                CourseBlockAssignment a1 = list.get(i);
                CourseBlockAssignment a2 = list.get(j);
                if (a1.getTeacher() != null && a1.getTeacher().equals(a2.getTeacher())
                        && a1.getTimeslot() != null && a2.getTimeslot() != null
                        && blocksOverlap(a1.getTimeslot(), a2.getTimeslot())) {
                    teacherDouble++;
                }
            }
        }
        result.put("No teacher double-booking", teacherDouble);

        // No room double-booking (blocks overlap)
        int roomDouble = 0;
        for (int i = 0; i < list.size(); i++) {
            for (int j = i + 1; j < list.size(); j++) {
                CourseBlockAssignment a1 = list.get(i);
                CourseBlockAssignment a2 = list.get(j);
                if (a1.getRoom() != null && a1.getRoom().equals(a2.getRoom())
                        && a1.getTimeslot() != null && a2.getTimeslot() != null
                        && blocksOverlap(a1.getTimeslot(), a2.getTimeslot())) {
                    roomDouble++;
                }
            }
        }
        result.put("No room double-booking", roomDouble);

        // Room type must satisfy course requirement
        int roomTypeMismatch = 0;
        for (CourseBlockAssignment a : list) {
            if (a.getRoom() != null && !a.getRoom().satisfiesRequirement(a.getCourse().getRoomRequirement()))
                roomTypeMismatch++;
        }
        result.put("Room type must satisfy course requirement", roomTypeMismatch);

        // Group cannot have two courses at same time (blocks overlap)
        int groupConflict = 0;
        for (int i = 0; i < list.size(); i++) {
            for (int j = i + 1; j < list.size(); j++) {
                CourseBlockAssignment a1 = list.get(i);
                CourseBlockAssignment a2 = list.get(j);
                if (a1.getGroup().equals(a2.getGroup())
                        && a1.getTimeslot() != null && a2.getTimeslot() != null
                        && blocksOverlap(a1.getTimeslot(), a2.getTimeslot())) {
                    groupConflict++;
                }
            }
        }
        result.put("Group cannot have two courses at same time", groupConflict);

        // Teacher max hours per week (hard constraint for block scheduling)
        int teacherMaxViolations = 0;
        Map<String, Integer> teacherHours = new HashMap<>();
        for (CourseBlockAssignment a : list) {
            if (a.getTeacher() != null && a.getTimeslot() != null) {
                String teacherId = a.getTeacher().getId();
                teacherHours.put(teacherId, teacherHours.getOrDefault(teacherId, 0) + a.getBlockLength());
            }
        }
        for (CourseBlockAssignment a : list) {
            if (a.getTeacher() != null) {
                String teacherId = a.getTeacher().getId();
                int totalHours = teacherHours.getOrDefault(teacherId, 0);
                if (totalHours > a.getTeacher().getMaxHoursPerWeek()) {
                    teacherMaxViolations++;
                    break; // Count once per teacher
                }
            }
        }
        result.put("Teacher exceeds max hours per week", teacherMaxViolations);

        // Non-BASICAS courses must finish by 2pm
        int nonBasicasAfter2pm = 0;
        for (CourseBlockAssignment a : list) {
            if (a.getCourse() != null && a.getTimeslot() != null) {
                String component = a.getCourse().getComponent();
                if (component != null && !component.equalsIgnoreCase("BASICAS")) {
                    int endHour = a.getTimeslot().getStartHour() + a.getTimeslot().getLengthHours();
                    if (endHour > 14) {
                        nonBasicasAfter2pm++;
                    }
                }
            }
        }
        result.put("Non-BASICAS courses must finish by 2pm", nonBasicasAfter2pm);

        // Maximum 1 block per non-BASICAS course per group per day
        int maxOneBlockPerCoursePerDay = 0;
        Map<String, Map<String, Map<DayOfWeek, Integer>>> groupCourseDayCount = new HashMap<>();
        for (CourseBlockAssignment a : list) {
            if (a.getGroup() != null && a.getCourse() != null && a.getTimeslot() != null) {
                String component = a.getCourse().getComponent();
                if (component != null && !component.equalsIgnoreCase("BASICAS")) {
                    String groupId = a.getGroup().getId();
                    String courseId = a.getCourse().getId();
                    DayOfWeek day = a.getTimeslot().getDayOfWeek();
                    groupCourseDayCount
                            .computeIfAbsent(groupId, k -> new HashMap<>())
                            .computeIfAbsent(courseId, k -> new HashMap<>())
                            .merge(day, 1, Integer::sum);
                }
            }
        }
        for (Map<String, Map<DayOfWeek, Integer>> courseDayCounts : groupCourseDayCount.values()) {
            for (Map<DayOfWeek, Integer> dayCounts : courseDayCounts.values()) {
                for (int count : dayCounts.values()) {
                    if (count > 1) {
                        maxOneBlockPerCoursePerDay += (count - 1);
                    }
                }
            }
        }
        result.put("Maximum 1 block per non-BASICAS course per group per day", maxOneBlockPerCoursePerDay);

        return result;
    }

    /**
     * Analyze hard constraint violations with detailed descriptions.
     * Returns a map of constraint name to list of violation descriptions.
     */
    public static Map<String, List<String>> analyzeHardConstraintViolationsDetailed(SchoolSchedule schedule) {
        Map<String, List<String>> details = new LinkedHashMap<>();

        if (schedule.getCourseBlockAssignments() == null) {
            return details;
        }

        List<CourseBlockAssignment> list = schedule.getCourseBlockAssignments();

        // Teacher must be qualified
        List<String> unqualified = new ArrayList<>();
        for (CourseBlockAssignment a : list) {
            if (a.getTeacher() != null && !a.getTeacher().isQualifiedFor(a.getCourse().getName())) {
                unqualified.add(blockAssignmentToString(a));
            }
        }
        details.put("Teacher must be qualified", unqualified);

        // Teacher must be available for entire block
        List<String> unavailable = new ArrayList<>();
        for (CourseBlockAssignment a : list) {
            if (a.getTeacher() != null && a.getTimeslot() != null
                    && !a.getTeacher().isAvailableForBlock(a.getTimeslot())) {
                unavailable.add(blockAssignmentToString(a));
            }
        }
        details.put("Teacher must be available for entire block", unavailable);

        // No teacher double-booking
        List<String> teacherDouble = new ArrayList<>();
        for (int i = 0; i < list.size(); i++) {
            for (int j = i + 1; j < list.size(); j++) {
                CourseBlockAssignment a1 = list.get(i);
                CourseBlockAssignment a2 = list.get(j);
                if (a1.getTeacher() != null && a1.getTeacher().equals(a2.getTeacher())
                        && a1.getTimeslot() != null && a2.getTimeslot() != null
                        && blocksOverlap(a1.getTimeslot(), a2.getTimeslot())) {
                    teacherDouble.add(blockAssignmentToString(a1) + "  <->  " + blockAssignmentToString(a2));
                }
            }
        }
        details.put("No teacher double-booking", teacherDouble);

        // No room double-booking
        List<String> roomDouble = new ArrayList<>();
        for (int i = 0; i < list.size(); i++) {
            for (int j = i + 1; j < list.size(); j++) {
                CourseBlockAssignment a1 = list.get(i);
                CourseBlockAssignment a2 = list.get(j);
                if (a1.getRoom() != null && a1.getRoom().equals(a2.getRoom())
                        && a1.getTimeslot() != null && a2.getTimeslot() != null
                        && blocksOverlap(a1.getTimeslot(), a2.getTimeslot())) {
                    roomDouble.add(blockAssignmentToString(a1) + "  <->  " + blockAssignmentToString(a2));
                }
            }
        }
        details.put("No room double-booking", roomDouble);

        // Room type must satisfy course requirement
        List<String> roomTypeMismatch = new ArrayList<>();
        for (CourseBlockAssignment a : list) {
            if (a.getRoom() != null && !a.getRoom().satisfiesRequirement(a.getCourse().getRoomRequirement())) {
                roomTypeMismatch.add(
                        blockAssignmentToString(a) + " (roomRequirement=" + a.getCourse().getRoomRequirement() + ")");
            }
        }
        details.put("Room type must satisfy course requirement", roomTypeMismatch);

        // Group cannot have two courses at same time
        List<String> groupConflict = new ArrayList<>();
        for (int i = 0; i < list.size(); i++) {
            for (int j = i + 1; j < list.size(); j++) {
                CourseBlockAssignment a1 = list.get(i);
                CourseBlockAssignment a2 = list.get(j);
                if (a1.getGroup().equals(a2.getGroup())
                        && a1.getTimeslot() != null && a2.getTimeslot() != null
                        && blocksOverlap(a1.getTimeslot(), a2.getTimeslot())) {
                    groupConflict.add(blockAssignmentToString(a1) + "  <->  " + blockAssignmentToString(a2));
                }
            }
        }
        details.put("Group cannot have two courses at same time", groupConflict);

        // Teacher max hours per week
        List<String> teacherMaxExcess = new ArrayList<>();
        Map<String, Integer> teacherHours = new HashMap<>();
        Map<String, Teacher> teacherMap = new HashMap<>();
        for (CourseBlockAssignment a : list) {
            if (a.getTeacher() != null && a.getTimeslot() != null) {
                String teacherId = a.getTeacher().getId();
                teacherHours.put(teacherId, teacherHours.getOrDefault(teacherId, 0) + a.getBlockLength());
                teacherMap.put(teacherId, a.getTeacher());
            }
        }
        for (Map.Entry<String, Integer> entry : teacherHours.entrySet()) {
            String teacherId = entry.getKey();
            int totalHours = entry.getValue();
            Teacher teacher = teacherMap.get(teacherId);
            if (teacher != null && totalHours > teacher.getMaxHoursPerWeek()) {
                int excess = totalHours - teacher.getMaxHoursPerWeek();
                teacherMaxExcess.add(String.format("%s: assigned=%d hours, max=%d hours, excess=%d hours",
                        teacher.getName(), totalHours, teacher.getMaxHoursPerWeek(), excess));
            }
        }
        details.put("Teacher exceeds max hours per week", teacherMaxExcess);

        // Non-BASICAS courses must finish by 2pm
        List<String> nonBasicasAfter2pm = new ArrayList<>();
        for (CourseBlockAssignment a : list) {
            if (a.getCourse() != null && a.getTimeslot() != null) {
                String component = a.getCourse().getComponent();
                if (component != null && !component.equalsIgnoreCase("BASICAS")) {
                    int endHour = a.getTimeslot().getStartHour() + a.getTimeslot().getLengthHours();
                    if (endHour > 14) {
                        nonBasicasAfter2pm.add(String.format("%s (component=%s, ends at %d:00)",
                                blockAssignmentToString(a), component, endHour));
                    }
                }
            }
        }
        details.put("Non-BASICAS courses must finish by 2pm", nonBasicasAfter2pm);

        // Maximum 1 block per non-BASICAS course per group per day
        List<String> maxOneBlockPerCoursePerDay = new ArrayList<>();
        Map<String, Map<String, Map<DayOfWeek, List<CourseBlockAssignment>>>> groupCourseDayAssignments = new HashMap<>();
        for (CourseBlockAssignment a : list) {
            if (a.getGroup() != null && a.getCourse() != null && a.getTimeslot() != null) {
                String component = a.getCourse().getComponent();
                if (component != null && !component.equalsIgnoreCase("BASICAS")) {
                    String groupId = a.getGroup().getId();
                    String courseId = a.getCourse().getId();
                    DayOfWeek day = a.getTimeslot().getDayOfWeek();
                    groupCourseDayAssignments
                            .computeIfAbsent(groupId, k -> new HashMap<>())
                            .computeIfAbsent(courseId, k -> new HashMap<>())
                            .computeIfAbsent(day, k -> new ArrayList<>())
                            .add(a);
                }
            }
        }
        for (Map.Entry<String, Map<String, Map<DayOfWeek, List<CourseBlockAssignment>>>> groupEntry : groupCourseDayAssignments
                .entrySet()) {
            String groupId = groupEntry.getKey();
            for (Map.Entry<String, Map<DayOfWeek, List<CourseBlockAssignment>>> courseEntry : groupEntry.getValue()
                    .entrySet()) {
                String courseId = courseEntry.getKey();
                for (Map.Entry<DayOfWeek, List<CourseBlockAssignment>> dayEntry : courseEntry.getValue().entrySet()) {
                    DayOfWeek day = dayEntry.getKey();
                    List<CourseBlockAssignment> assignments = dayEntry.getValue();
                    if (assignments.size() > 1) {
                        StringBuilder sb = new StringBuilder();
                        String courseName = assignments.get(0).getCourse().getName();
                        sb.append(String.format("Group %s, Course [%s] on %s has %d blocks: ",
                                groupId, courseName, day, assignments.size()));
                        for (int i = 0; i < assignments.size(); i++) {
                            if (i > 0)
                                sb.append(", ");
                            CourseBlockAssignment a = assignments.get(i);
                            sb.append(formatBlockTimeslot(a.getTimeslot()));
                        }
                        maxOneBlockPerCoursePerDay.add(sb.toString());
                    }
                }
            }
        }
        details.put("Maximum 1 block per non-BASICAS course per group per day", maxOneBlockPerCoursePerDay);

        return details;
    }

    /**
     * Analyze soft constraint violations for block-based schedule.
     * Returns a map of constraint name to violation count.
     */
    public static Map<String, Integer> analyzeSoftConstraintViolations(SchoolSchedule schedule) {
        Map<String, Integer> result = new LinkedHashMap<>();

        if (schedule.getCourseBlockAssignments() == null) {
            return result;
        }

        List<CourseBlockAssignment> list = schedule.getCourseBlockAssignments();

        // Minimize teacher idle gaps (soft, weight 1, availability-aware)
        int idleGaps = 0;
        Map<String, Map<DayOfWeek, List<CourseBlockAssignment>>> teacherDayAssignments = new HashMap<>();
        for (CourseBlockAssignment a : list) {
            if (a.getTeacher() == null || a.getTimeslot() == null)
                continue;
            String teacherKey = a.getTeacher().getId();
            DayOfWeek day = a.getTimeslot().getDayOfWeek();
            teacherDayAssignments.computeIfAbsent(teacherKey, k -> new HashMap<>())
                    .computeIfAbsent(day, k -> new ArrayList<>())
                    .add(a);
        }

        for (Map.Entry<String, Map<DayOfWeek, List<CourseBlockAssignment>>> teacherEntry : teacherDayAssignments
                .entrySet()) {
            for (Map.Entry<DayOfWeek, List<CourseBlockAssignment>> dayEntry : teacherEntry.getValue().entrySet()) {
                List<CourseBlockAssignment> assigns = dayEntry.getValue();
                assigns.sort(Comparator.comparing(a -> a.getTimeslot().getStartHour()));

                // Calculate gaps between blocks
                for (int i = 1; i < assigns.size(); i++) {
                    CourseBlockAssignment prev = assigns.get(i - 1);
                    CourseBlockAssignment curr = assigns.get(i);

                    int prevEnd = prev.getTimeslot().getStartHour() + prev.getTimeslot().getLengthHours();
                    int currStart = curr.getTimeslot().getStartHour();
                    int gap = currStart - prevEnd;

                    if (gap > 0) {
                        // Check if teacher is available during gap hours
                        DayOfWeek day = dayEntry.getKey();
                        boolean availableDuringGap = true;

                        for (int gapHour = prevEnd; gapHour < currStart; gapHour++) {
                            if (!prev.getTeacher().isAvailableAt(day, gapHour)) {
                                availableDuringGap = false;
                                break;
                            }
                        }

                        // Only count as violation if teacher IS available during gap
                        if (availableDuringGap) {
                            idleGaps += gap;
                        }
                    }
                }
            }
        }
        result.put("Minimize teacher idle gaps (availability-aware)", idleGaps);

        return result;
    }

    /**
     * Helper method to check if two block timeslots overlap.
     */
    private static boolean blocksOverlap(BlockTimeslot block1, BlockTimeslot block2) {
        if (block1 == null || block2 == null) {
            return false;
        }

        if (!block1.getDayOfWeek().equals(block2.getDayOfWeek())) {
            return false;
        }

        int start1 = block1.getStartHour();
        int end1 = block1.getStartHour() + block1.getLengthHours();
        int start2 = block2.getStartHour();
        int end2 = block2.getStartHour() + block2.getLengthHours();

        return start1 < end2 && start2 < end1;
    }

    /**
     * Helper method to format a block assignment as a string.
     */
    private static String blockAssignmentToString(CourseBlockAssignment a) {
        String timeslot = a.getTimeslot() != null ? formatBlockTimeslot(a.getTimeslot()) : "UNASSIGNED";
        String teacher = a.getTeacher() != null ? a.getTeacher().getName() : "UNASSIGNED";
        String room = a.getRoom() != null ? a.getRoom().getName() : "UNASSIGNED";
        return String.format("%s [%s] block=%s (%dh) teacher=%s room=%s",
                a.getGroup().getName(), a.getCourse().getName(), timeslot, a.getBlockLength(), teacher, room);
    }

    /**
     * Helper method to format a block timeslot as a string.
     */
    private static String formatBlockTimeslot(BlockTimeslot bt) {
        String day = switch (bt.getDayOfWeek()) {
            case MONDAY -> "Lun";
            case TUESDAY -> "Mar";
            case WEDNESDAY -> "Mie";
            case THURSDAY -> "Jue";
            case FRIDAY -> "Vie";
            case SATURDAY -> "Sáb";
            case SUNDAY -> "Dom";
        };
        int endHour = bt.getStartHour() + bt.getLengthHours();
        return String.format("%s %d-%d", day, bt.getStartHour(), endHour);
    }
}
