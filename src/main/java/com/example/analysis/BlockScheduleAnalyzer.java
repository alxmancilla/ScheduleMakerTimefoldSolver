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

        // Block length must match timeslot length (CRITICAL)
        // NOTE: This constraint is NOT excluded for pinned assignments because it's a
        // data integrity constraint. If a pinned assignment violates this, it indicates
        // a database error that must be reported and fixed.
        int blockLengthMismatch = 0;
        for (CourseBlockAssignment a : list) {
            if (a.getTimeslot() != null && a.getBlockLength() != a.getTimeslot().getLengthHours()) {
                blockLengthMismatch++;
            }
        }
        result.put("Block length must match timeslot length", blockLengthMismatch);

        // Teacher must be qualified
        int unqualified = 0;
        for (CourseBlockAssignment a : list) {
            if (!a.isPinned() && a.getTeacher() != null && !a.getTeacher().isQualifiedFor(a.getCourse().getName()))
                unqualified++;
        }
        result.put("Teacher must be qualified", unqualified);

        // Teacher must be available for entire block
        int unavailable = 0;
        for (CourseBlockAssignment a : list) {
            if (!a.isPinned() && a.getTeacher() != null && a.getTimeslot() != null
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
                // FIXED: Changed from && to || to match constraint provider logic
                // Penalize if at least one assignment is unpinned
                if ((!a1.isPinned() || !a2.isPinned())
                        && a1.getTeacher() != null && a1.getTeacher().equals(a2.getTeacher())
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
                // FIXED: Changed from && to || to match constraint provider logic
                // Penalize if at least one assignment is unpinned
                if ((!a1.isPinned() || !a2.isPinned())
                        && a1.getRoom() != null && a1.getRoom().equals(a2.getRoom())
                        && a1.getTimeslot() != null && a2.getTimeslot() != null
                        && blocksOverlap(a1.getTimeslot(), a2.getTimeslot())) {
                    roomDouble++;
                }
            }
        }
        result.put("No room double-booking", roomDouble);

        // Room type must satisfy course requirement (uses dual room requirements)
        int roomTypeMismatch = 0;
        for (CourseBlockAssignment a : list) {
            if (!a.isPinned() && a.getRoom() != null && a.getSatisfiesRoomType() != null
                    && !a.getRoom().satisfiesRequirement(a.getSatisfiesRoomType()))
                roomTypeMismatch++;
        }
        result.put("Room type must satisfy course requirement", roomTypeMismatch);

        // Group cannot have two courses at same time (blocks overlap)
        int groupConflict = 0;
        for (int i = 0; i < list.size(); i++) {
            for (int j = i + 1; j < list.size(); j++) {
                CourseBlockAssignment a1 = list.get(i);
                CourseBlockAssignment a2 = list.get(j);
                // FIXED: Changed from && to || to match constraint provider logic
                // Penalize if at least one assignment is unpinned
                if ((!a1.isPinned() || !a2.isPinned())
                        && a1.getGroup().equals(a2.getGroup())
                        && a1.getTimeslot() != null && a2.getTimeslot() != null
                        && blocksOverlap(a1.getTimeslot(), a2.getTimeslot())) {
                    groupConflict++;
                }
            }
        }
        result.put("Group cannot have two courses at same time", groupConflict);

        // Maximum 2 blocks per course per group per day (HARD) - Count
        // BASICAS: max 1 block per day
        // Non-BASICAS: max 2 blocks per day ONLY if total > 4 hours
        int maxTwoBlocksPerCoursePerDay = 0;
        Map<String, Map<String, Map<DayOfWeek, List<CourseBlockAssignment>>>> groupCourseDayAssignments = new HashMap<>();
        for (CourseBlockAssignment a : list) {
            if (!a.isPinned() && a.getGroup() != null && a.getCourse() != null && a.getTimeslot() != null) {
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
        for (Map<String, Map<DayOfWeek, List<CourseBlockAssignment>>> courseDayCounts : groupCourseDayAssignments
                .values()) {
            for (Map<DayOfWeek, List<CourseBlockAssignment>> dayCounts : courseDayCounts.values()) {
                for (Map.Entry<DayOfWeek, List<CourseBlockAssignment>> dayEntry : dayCounts.entrySet()) {
                    List<CourseBlockAssignment> assignments = dayEntry.getValue();
                    int count = assignments.size();
                    if (count > 0) {
                        String component = assignments.get(0).getCourse().getComponent();
                        boolean isBasicas = "BASICAS".equals(component);

                        if (isBasicas && count > 1) {
                            // BASICAS: max 1 block per day
                            maxTwoBlocksPerCoursePerDay += (count - 1);
                        } else if (!isBasicas) {
                            // Non-BASICAS: calculate total hours
                            int totalHours = assignments.stream()
                                    .mapToInt(a -> a.getTimeslot().getLengthHours())
                                    .sum();

                            // Only violation if total > 4 hours AND count > 2
                            if (totalHours > 4 && count > 2) {
                                maxTwoBlocksPerCoursePerDay += (count - 2);
                            }
                        }
                    }
                }
            }
        }
        result.put("Maximum 2 blocks per course per group per day", maxTwoBlocksPerCoursePerDay);

        // Course blocks must be consecutive (HARD) - Count
        int courseBlocksNonConsecutive = 0;
        for (int i = 0; i < list.size(); i++) {
            for (int j = i + 1; j < list.size(); j++) {
                CourseBlockAssignment a1 = list.get(i);
                CourseBlockAssignment a2 = list.get(j);
                if (!a1.isPinned() && !a2.isPinned()
                        && a1.getGroup() != null && a1.getGroup().equals(a2.getGroup())
                        && a1.getCourse() != null && a1.getCourse().equals(a2.getCourse())
                        && a1.getTimeslot() != null && a2.getTimeslot() != null
                        && a1.getTimeslot().getDayOfWeek().equals(a2.getTimeslot().getDayOfWeek())) {

                    // Apply to ALL courses
                    // Check if blocks are NOT consecutive
                    int end1 = a1.getTimeslot().getStartHour() + a1.getTimeslot().getLengthHours();
                    int start2 = a2.getTimeslot().getStartHour();
                    int end2 = a2.getTimeslot().getStartHour() + a2.getTimeslot().getLengthHours();
                    int start1 = a1.getTimeslot().getStartHour();

                    boolean areConsecutive = (end1 == start2 || end2 == start1);
                    if (!areConsecutive) {
                        courseBlocksNonConsecutive++;
                    }
                }
            }
        }
        result.put("Course blocks must be consecutive", courseBlocksNonConsecutive);

        // COMMENTED OUT: Prefer BASICAS blocks to be consecutive on same day (SOFT)
        // int basicasNonConsecutive = 0;
        // Map<String, Map<String, Map<DayOfWeek, List<CourseBlockAssignment>>>>
        // basicasGroupCourseDayBlocks = new HashMap<>();
        // for (CourseBlockAssignment a : list) {
        // if (a.getGroup() != null && a.getCourse() != null && a.getTimeslot() != null)
        // {
        // String component = a.getCourse().getComponent();
        // if (component != null && component.equalsIgnoreCase("BASICAS")) {
        // String groupId = a.getGroup().getId();
        // String courseId = a.getCourse().getId();
        // DayOfWeek day = a.getTimeslot().getDayOfWeek();
        // basicasGroupCourseDayBlocks
        // .computeIfAbsent(groupId, k -> new HashMap<>())
        // .computeIfAbsent(courseId, k -> new HashMap<>())
        // .computeIfAbsent(day, k -> new ArrayList<>())
        // .add(a);
        // }
        // }
        // }
        // // Check for non-consecutive blocks
        // for (Map<String, Map<DayOfWeek, List<CourseBlockAssignment>>> courseDayBlocks
        // : basicasGroupCourseDayBlocks
        // .values()) {
        // for (Map<DayOfWeek, List<CourseBlockAssignment>> dayBlocks :
        // courseDayBlocks.values()) {
        // for (List<CourseBlockAssignment> blocks : dayBlocks.values()) {
        // if (blocks.size() > 1) {
        // // Check if all blocks are consecutive
        // for (int i = 0; i < blocks.size(); i++) {
        // for (int j = i + 1; j < blocks.size(); j++) {
        // CourseBlockAssignment a1 = blocks.get(i);
        // CourseBlockAssignment a2 = blocks.get(j);
        // int end1 = a1.getTimeslot().getStartHour() +
        // a1.getTimeslot().getLengthHours();
        // int start2 = a2.getTimeslot().getStartHour();
        // int end2 = a2.getTimeslot().getStartHour() +
        // a2.getTimeslot().getLengthHours();
        // int start1 = a1.getTimeslot().getStartHour();
        // boolean areConsecutive = (end1 == start2 || end2 == start1);
        // if (!areConsecutive) {
        // basicasNonConsecutive++;
        // }
        // }
        // }
        // }
        // }
        // }
        // }
        // result.put("Prefer BASICAS blocks to be consecutive on same day",
        // basicasNonConsecutive);

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

        // Block length must match timeslot length (CRITICAL)
        List<String> blockLengthMismatch = new ArrayList<>();
        for (CourseBlockAssignment a : list) {
            if (a.getTimeslot() != null && a.getBlockLength() != a.getTimeslot().getLengthHours()) {
                blockLengthMismatch.add(blockAssignmentToString(a) +
                        " (block_length=" + a.getBlockLength() + "h, timeslot_length=" +
                        a.getTimeslot().getLengthHours() + "h)");
            }
        }
        details.put("Block length must match timeslot length", blockLengthMismatch);

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
            if (!a.isPinned() && a.getTeacher() != null && a.getTimeslot() != null
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
                // FIXED: Changed from && to || to match constraint provider logic
                // Penalize if at least one assignment is unpinned
                if ((!a1.isPinned() || !a2.isPinned())
                        && a1.getTeacher() != null && a1.getTeacher().equals(a2.getTeacher())
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
                // FIXED: Changed from && to || to match constraint provider logic
                // Penalize if at least one assignment is unpinned
                if ((!a1.isPinned() || !a2.isPinned())
                        && a1.getRoom() != null && a1.getRoom().equals(a2.getRoom())
                        && a1.getTimeslot() != null && a2.getTimeslot() != null
                        && blocksOverlap(a1.getTimeslot(), a2.getTimeslot())) {
                    roomDouble.add(blockAssignmentToString(a1) + "  <->  " + blockAssignmentToString(a2));
                }
            }
        }
        details.put("No room double-booking", roomDouble);

        // Room type must satisfy course requirement (uses dual room requirements)
        List<String> roomTypeMismatch = new ArrayList<>();
        for (CourseBlockAssignment a : list) {
            if (!a.isPinned() && a.getRoom() != null && a.getSatisfiesRoomType() != null
                    && !a.getRoom().satisfiesRequirement(a.getSatisfiesRoomType())) {
                roomTypeMismatch.add(
                        blockAssignmentToString(a) + " (satisfiesRoomType=" + a.getSatisfiesRoomType()
                                + ", assignedRoomType=" + a.getRoom().getType() + ")");
            }
        }
        details.put("Room type must satisfy course requirement", roomTypeMismatch);

        // Group cannot have two courses at same time
        List<String> groupConflict = new ArrayList<>();
        for (int i = 0; i < list.size(); i++) {
            for (int j = i + 1; j < list.size(); j++) {
                CourseBlockAssignment a1 = list.get(i);
                CourseBlockAssignment a2 = list.get(j);
                // FIXED: Changed from && to || to match constraint provider logic
                // Penalize if at least one assignment is unpinned
                if ((!a1.isPinned() || !a2.isPinned())
                        && a1.getGroup().equals(a2.getGroup())
                        && a1.getTimeslot() != null && a2.getTimeslot() != null
                        && blocksOverlap(a1.getTimeslot(), a2.getTimeslot())) {
                    groupConflict.add(blockAssignmentToString(a1) + "  <->  " + blockAssignmentToString(a2));
                }
            }
        }
        details.put("Group cannot have two courses at same time", groupConflict);

        // Maximum 2 blocks per course per group per day (HARD) - Detailed
        // BASICAS: max 1 block per day
        // Non-BASICAS: max 2 blocks per day ONLY if total > 4 hours
        List<String> maxTwoBlocksDetails = new ArrayList<>();
        Map<String, Map<String, Map<DayOfWeek, List<CourseBlockAssignment>>>> groupCourseDayAssignments2 = new HashMap<>();
        for (CourseBlockAssignment a : list) {
            if (!a.isPinned() && a.getGroup() != null && a.getCourse() != null && a.getTimeslot() != null) {
                String groupId = a.getGroup().getId();
                String courseId = a.getCourse().getId();
                DayOfWeek day = a.getTimeslot().getDayOfWeek();
                groupCourseDayAssignments2
                        .computeIfAbsent(groupId, k -> new HashMap<>())
                        .computeIfAbsent(courseId, k -> new HashMap<>())
                        .computeIfAbsent(day, k -> new ArrayList<>())
                        .add(a);
            }
        }
        for (Map<String, Map<DayOfWeek, List<CourseBlockAssignment>>> courseDayCounts : groupCourseDayAssignments2
                .values()) {
            for (Map<DayOfWeek, List<CourseBlockAssignment>> dayCounts : courseDayCounts.values()) {
                for (Map.Entry<DayOfWeek, List<CourseBlockAssignment>> dayEntry : dayCounts.entrySet()) {
                    List<CourseBlockAssignment> assignments = dayEntry.getValue();
                    int count = assignments.size();
                    if (count > 0) {
                        String component = assignments.get(0).getCourse().getComponent();
                        boolean isBasicas = "BASICAS".equals(component);
                        String courseName = assignments.get(0).getCourse().getName();
                        String groupName = assignments.get(0).getGroup().getName();
                        String dayName = formatDay(dayEntry.getKey());

                        if (isBasicas && count > 1) {
                            // BASICAS: max 1 block per day
                            String reason = String.format("(%s has %d blocks on %s, limit=1 for BASICAS)",
                                    groupName, count, dayName);
                            maxTwoBlocksDetails.add(courseName + " " + reason);
                        } else if (!isBasicas) {
                            // Non-BASICAS: calculate total hours
                            int totalHours = assignments.stream()
                                    .mapToInt(a -> a.getTimeslot().getLengthHours())
                                    .sum();

                            // Only violation if total > 4 hours AND count > 2
                            if (totalHours > 4 && count > 2) {
                                String reason = String.format(
                                        "(%s has %d blocks on %s totaling %dh, limit=2 blocks when >4h)",
                                        groupName, count, dayName, totalHours);
                                maxTwoBlocksDetails.add(courseName + " " + reason);
                            }
                        }
                    }
                }
            }
        }
        details.put("Maximum 2 blocks per course per group per day", maxTwoBlocksDetails);

        // Course blocks must be consecutive (HARD) - Detailed
        List<String> courseBlocksNonConsecutiveDetails = new ArrayList<>();
        for (int i = 0; i < list.size(); i++) {
            for (int j = i + 1; j < list.size(); j++) {
                CourseBlockAssignment a1 = list.get(i);
                CourseBlockAssignment a2 = list.get(j);
                if (!a1.isPinned() && !a2.isPinned()
                        && a1.getGroup() != null && a1.getGroup().equals(a2.getGroup())
                        && a1.getCourse() != null && a1.getCourse().equals(a2.getCourse())
                        && a1.getTimeslot() != null && a2.getTimeslot() != null
                        && a1.getTimeslot().getDayOfWeek().equals(a2.getTimeslot().getDayOfWeek())) {

                    // Apply to ALL courses
                    // Check if blocks are NOT consecutive
                    int end1 = a1.getTimeslot().getStartHour() + a1.getTimeslot().getLengthHours();
                    int start2 = a2.getTimeslot().getStartHour();
                    int end2 = a2.getTimeslot().getStartHour() + a2.getTimeslot().getLengthHours();
                    int start1 = a1.getTimeslot().getStartHour();

                    boolean areConsecutive = (end1 == start2 || end2 == start1);
                    if (!areConsecutive) {
                        courseBlocksNonConsecutiveDetails.add(
                                blockAssignmentToString(a1) + "  <->  " + blockAssignmentToString(a2));
                    }
                }
            }
        }
        details.put("Course blocks must be consecutive", courseBlocksNonConsecutiveDetails);

        return details;
    }

    /**
     * Analyze soft constraint violations with detailed descriptions.
     * Returns a map of constraint name to list of violation descriptions.
     */
    public static Map<String, List<String>> analyzeSoftConstraintViolationsDetailed(SchoolSchedule schedule) {
        Map<String, List<String>> details = new LinkedHashMap<>();

        if (schedule.getCourseBlockAssignments() == null) {
            return details;
        }

        List<CourseBlockAssignment> list = schedule.getCourseBlockAssignments();

        // Non-standard rooms should finish by 2pm (SOFT, weight 10) - Detailed
        List<String> nonStandardAfter2pmDetails = new ArrayList<>();
        for (CourseBlockAssignment a : list) {
            if (!a.isPinned() && a.getTimeslot() != null && a.getRoom() != null) {
                int endHour = a.getTimeslot().getStartHour() + a.getTimeslot().getLengthHours();
                if (endHour > 14) {
                    String roomType = a.getRoom().getType();
                    if (roomType != null && !roomType.equalsIgnoreCase("estándar")) {
                        String reason = String.format("(room=%s, type=%s, ends at %d:00)",
                                a.getRoom().getName(), roomType, endHour);
                        nonStandardAfter2pmDetails.add(blockAssignmentToString(a) + " " + reason);
                    }
                }
            }
        }
        details.put("Non-standard rooms should finish by 2pm", nonStandardAfter2pmDetails);

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

        // Minimize group idle gaps (SOFT) - Detailed
        List<String> groupIdleGapsDetails = new ArrayList<>();
        for (int i = 0; i < list.size(); i++) {
            for (int j = i + 1; j < list.size(); j++) {
                CourseBlockAssignment a1 = list.get(i);
                CourseBlockAssignment a2 = list.get(j);
                if (!a1.isPinned() && !a2.isPinned()
                        && a1.getGroup() != null && a1.getGroup().equals(a2.getGroup()) &&
                        a1.getTimeslot() != null && a2.getTimeslot() != null &&
                        a1.getTimeslot().getDayOfWeek().equals(a2.getTimeslot().getDayOfWeek())) {
                    int end1 = a1.getTimeslot().getStartHour() + a1.getTimeslot().getLengthHours();
                    int start2 = a2.getTimeslot().getStartHour();
                    int end2 = a2.getTimeslot().getStartHour() + a2.getTimeslot().getLengthHours();
                    int start1 = a1.getTimeslot().getStartHour();

                    int gapSize = 0;
                    if (end1 < start2) {
                        gapSize = start2 - end1;
                    } else if (end2 < start1) {
                        gapSize = start1 - end2;
                    }

                    if (gapSize > 0) {
                        String reason = String.format("(gap=%d hours)", gapSize);
                        groupIdleGapsDetails.add(blockAssignmentToString(a1) + "  <->  " +
                                blockAssignmentToString(a2) + " " + reason);
                    }
                }
            }
        }
        details.put("Minimize group idle gaps", groupIdleGapsDetails);

        // Prefer block's specified room (SOFT) - Detailed
        List<String> blockSpecifiedRoomDetails = new ArrayList<>();
        for (CourseBlockAssignment a : list) {
            if (!a.isPinned() && a.getRoom() != null) {
                String preferredRoomName = a.getPreferredRoomName();
                if (preferredRoomName != null && !preferredRoomName.isEmpty()) {
                    if (!preferredRoomName.equals(a.getRoom().getName())) {
                        String reason = String.format("(preferred=%s, assigned=%s)",
                                preferredRoomName, a.getRoom().getName());
                        blockSpecifiedRoomDetails.add(blockAssignmentToString(a) + " " + reason);
                    }
                }
            }
        }
        details.put("Prefer block's specified room", blockSpecifiedRoomDetails);

        // Prefer non-BASICAS courses in non-standard rooms to finish by 2pm (SOFT) -
        // COMMENTED OUT
        // List<String> mustFinishBy2pm = new ArrayList<>();
        // for (CourseBlockAssignment a : list) {
        // if (a.getCourse() != null && a.getTimeslot() != null && !a.isPinned()) {
        // int endHour = a.getTimeslot().getStartHour() +
        // a.getTimeslot().getLengthHours();
        // if (endHour >= 14) {
        // // Check if non-BASICAS course
        // String component = a.getCourse().getComponent();
        // boolean isNonBasicas = (component == null ||
        // !component.equalsIgnoreCase("BASICAS"));
        //
        // // Check if non-standard room
        // boolean isNonStandardRoom = false;
        // String roomType = null;
        // if (a.getRoom() != null) {
        // roomType = a.getRoom().getType();
        // isNonStandardRoom = (roomType != null &&
        // !roomType.equalsIgnoreCase("estándar"));
        // }
        //
        // // Penalize if BOTH conditions are true (AND logic)
        // if (isNonBasicas && isNonStandardRoom) {
        // String reason = String.format("(component=%s, room_type=%s, ends at %d:00)",
        // component, roomType, endHour);
        // mustFinishBy2pm.add(blockAssignmentToString(a) + " " + reason);
        // }
        // }
        // }
        // }
        // details.put("Prefer non-BASICAS courses in non-standard rooms to finish by
        // 2pm", mustFinishBy2pm);

        // COMMENTED OUT: Prefer BASICAS blocks to be consecutive on same day (SOFT)
        // List<String> basicasNonConsecutiveDetails = new ArrayList<>();
        // Map<String, Map<String, Map<DayOfWeek, List<CourseBlockAssignment>>>>
        // basicasGroupCourseDayBlocks2 = new HashMap<>();
        // for (CourseBlockAssignment a : list) {
        // if (a.getGroup() != null && a.getCourse() != null && a.getTimeslot() != null)
        // {
        // String component = a.getCourse().getComponent();
        // if (component != null && component.equalsIgnoreCase("BASICAS")) {
        // String groupId = a.getGroup().getId();
        // String courseId = a.getCourse().getId();
        // DayOfWeek day = a.getTimeslot().getDayOfWeek();
        // basicasGroupCourseDayBlocks2
        // .computeIfAbsent(groupId, k -> new HashMap<>())
        // .computeIfAbsent(courseId, k -> new HashMap<>())
        // .computeIfAbsent(day, k -> new ArrayList<>())
        // .add(a);
        // }
        // }
        // }
        // // Generate detailed violation messages
        // for (Map.Entry<String, Map<String, Map<DayOfWeek,
        // List<CourseBlockAssignment>>>> groupEntry : basicasGroupCourseDayBlocks2
        // .entrySet()) {
        // for (Map.Entry<String, Map<DayOfWeek, List<CourseBlockAssignment>>>
        // courseEntry : groupEntry.getValue()
        // .entrySet()) {
        // for (Map.Entry<DayOfWeek, List<CourseBlockAssignment>> dayEntry :
        // courseEntry.getValue().entrySet()) {
        // List<CourseBlockAssignment> blocks = dayEntry.getValue();
        // if (blocks.size() > 1) {
        // for (int i = 0; i < blocks.size(); i++) {
        // for (int j = i + 1; j < blocks.size(); j++) {
        // CourseBlockAssignment a1 = blocks.get(i);
        // CourseBlockAssignment a2 = blocks.get(j);
        // int end1 = a1.getTimeslot().getStartHour() +
        // a1.getTimeslot().getLengthHours();
        // int start2 = a2.getTimeslot().getStartHour();
        // int end2 = a2.getTimeslot().getStartHour() +
        // a2.getTimeslot().getLengthHours();
        // int start1 = a1.getTimeslot().getStartHour();
        // boolean areConsecutive = (end1 == start2 || end2 == start1);
        // if (!areConsecutive) {
        // basicasNonConsecutiveDetails.add(
        // blockAssignmentToString(a1) + " <-> " + blockAssignmentToString(a2));
        // }
        // }
        // }
        // }
        // }
        // }
        // }
        // details.put("Prefer BASICAS blocks to be consecutive on same day",
        // basicasNonConsecutiveDetails);

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

        // Non-standard rooms should finish by 2pm (SOFT, weight 10)
        int nonStandardAfter2pm = 0;
        for (CourseBlockAssignment a : list) {
            if (!a.isPinned() && a.getTimeslot() != null && a.getRoom() != null) {
                int endHour = a.getTimeslot().getStartHour() + a.getTimeslot().getLengthHours();
                if (endHour > 14) {
                    String roomType = a.getRoom().getType();
                    if (roomType != null && !roomType.equalsIgnoreCase("estándar")) {
                        nonStandardAfter2pm++;
                    }
                }
            }
        }
        result.put("Non-standard rooms should finish by 2pm", nonStandardAfter2pm);

        // Prefer group's preferred room (SOFT, weight 2) - uses dual room requirements
        int preferredRoomViolations = 0;
        for (CourseBlockAssignment a : list) {
            if (!a.isPinned() && a.getGroup() != null && a.getRoom() != null) {
                var preferredRoom = a.getGroup().getPreferredRoom();
                if (preferredRoom != null &&
                        !(a.getSatisfiesRoomType() != null && "laboratorio".equalsIgnoreCase(a.getSatisfiesRoomType()))
                        &&
                        !preferredRoom.equals(a.getRoom())) {
                    preferredRoomViolations++;
                }
            }
        }
        result.put("Prefer group's preferred room", preferredRoomViolations);

        // Minimize teacher building changes (SOFT, weight 1)
        int buildingChanges = 0;
        for (int i = 0; i < list.size(); i++) {
            for (int j = i + 1; j < list.size(); j++) {
                CourseBlockAssignment a1 = list.get(i);
                CourseBlockAssignment a2 = list.get(j);
                if (!a1.isPinned() && !a2.isPinned()
                        && a1.getTeacher() != null && a1.getTeacher().equals(a2.getTeacher()) &&
                        a1.getTimeslot() != null && a2.getTimeslot() != null &&
                        a1.getTimeslot().getDayOfWeek().equals(a2.getTimeslot().getDayOfWeek()) &&
                        a1.getRoom() != null && a2.getRoom() != null) {
                    String building1 = a1.getRoom().getBuilding();
                    String building2 = a2.getRoom().getBuilding();
                    if (building1 != null && building2 != null && !building1.equals(building2)) {
                        buildingChanges++;
                    }
                }
            }
        }
        result.put("Minimize teacher building changes", buildingChanges);

        // Teacher max hours per week (SOFT, weight 5)
        // IMPORTANT: Includes BOTH pinned and unpinned assignments because pinned
        // assignments represent real teaching hours that count toward the teacher's
        // workload limit.
        int teacherMaxViolations = 0;
        Map<String, Integer> teacherHours = new HashMap<>();
        Map<String, Teacher> teacherMap = new HashMap<>();
        for (CourseBlockAssignment a : list) {
            if (a.getTeacher() != null && a.getTimeslot() != null) { // Include ALL assignments
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
                teacherMaxViolations++;
            }
        }
        result.put("Teacher exceeds max hours per week", teacherMaxViolations);

        // Minimize teacher idle gaps (SOFT, weight 2, availability-aware)
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

        // Minimize group idle gaps (SOFT, weight 3)
        int groupIdleGaps = 0;
        for (int i = 0; i < list.size(); i++) {
            for (int j = i + 1; j < list.size(); j++) {
                CourseBlockAssignment a1 = list.get(i);
                CourseBlockAssignment a2 = list.get(j);
                if (!a1.isPinned() && !a2.isPinned()
                        && a1.getGroup() != null && a1.getGroup().equals(a2.getGroup()) &&
                        a1.getTimeslot() != null && a2.getTimeslot() != null &&
                        a1.getTimeslot().getDayOfWeek().equals(a2.getTimeslot().getDayOfWeek())) {
                    int end1 = a1.getTimeslot().getStartHour() + a1.getTimeslot().getLengthHours();
                    int start2 = a2.getTimeslot().getStartHour();
                    int end2 = a2.getTimeslot().getStartHour() + a2.getTimeslot().getLengthHours();
                    int start1 = a1.getTimeslot().getStartHour();

                    int gapSize = 0;
                    if (end1 < start2) {
                        gapSize = start2 - end1;
                    } else if (end2 < start1) {
                        gapSize = start1 - end2;
                    }

                    if (gapSize > 0) {
                        groupIdleGaps += gapSize;
                    }
                }
            }
        }
        result.put("Minimize group idle gaps", groupIdleGaps);

        // Prefer block's specified room (SOFT, weight 3)
        int blockSpecifiedRoomViolations = 0;
        for (CourseBlockAssignment a : list) {
            if (!a.isPinned() && a.getRoom() != null) {
                String preferredRoomName = a.getPreferredRoomName();
                if (preferredRoomName != null && !preferredRoomName.isEmpty()) {
                    if (!preferredRoomName.equals(a.getRoom().getName())) {
                        blockSpecifiedRoomViolations++;
                    }
                }
            }
        }
        result.put("Prefer block's specified room", blockSpecifiedRoomViolations);

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

    /**
     * Helper method to format a day of week as a string.
     */
    private static String formatDay(DayOfWeek day) {
        return switch (day) {
            case MONDAY -> "Lun";
            case TUESDAY -> "Mar";
            case WEDNESDAY -> "Mie";
            case THURSDAY -> "Jue";
            case FRIDAY -> "Vie";
            case SATURDAY -> "Sáb";
            case SUNDAY -> "Dom";
        };
    }
}
