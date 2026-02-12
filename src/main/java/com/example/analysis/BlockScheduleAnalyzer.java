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
                if (!a1.isPinned() && !a2.isPinned()
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
                if (!a1.isPinned() && !a2.isPinned()
                        && a1.getRoom() != null && a1.getRoom().equals(a2.getRoom())
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
            if (!a.isPinned() && a.getRoom() != null
                    && !a.getRoom().satisfiesRequirement(a.getCourse().getRoomRequirement()))
                roomTypeMismatch++;
        }
        result.put("Room type must satisfy course requirement", roomTypeMismatch);

        // Group cannot have two courses at same time (blocks overlap)
        int groupConflict = 0;
        for (int i = 0; i < list.size(); i++) {
            for (int j = i + 1; j < list.size(); j++) {
                CourseBlockAssignment a1 = list.get(i);
                CourseBlockAssignment a2 = list.get(j);
                if (!a1.isPinned() && !a2.isPinned()
                        && a1.getGroup().equals(a2.getGroup())
                        && a1.getTimeslot() != null && a2.getTimeslot() != null
                        && blocksOverlap(a1.getTimeslot(), a2.getTimeslot())) {
                    groupConflict++;
                }
            }
        }
        result.put("Group cannot have two courses at same time", groupConflict);

        // Teacher max hours per week (SOFT constraint for block scheduling)
        // IMPORTANT: Includes BOTH pinned and unpinned assignments because pinned
        // assignments
        // represent real teaching hours that count toward the teacher's workload limit.
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

        // Prefer non-BASICAS courses in non-standard rooms to finish by 2pm (SOFT) -
        // COMMENTED OUT
        // int mustFinishBy2pm = 0;
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
        // if (a.getRoom() != null) {
        // String roomType = a.getRoom().getType();
        // isNonStandardRoom = (roomType != null &&
        // !roomType.equalsIgnoreCase("estándar"));
        // }
        //
        // // Penalize if BOTH conditions are true (AND logic)
        // if (isNonBasicas && isNonStandardRoom) {
        // mustFinishBy2pm++;
        // }
        // }
        // }
        // }
        // result.put("Prefer non-BASICAS courses in non-standard rooms to finish by
        // 2pm", mustFinishBy2pm);

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

        // Maximum 1 block per non-BASICAS course per group per day
        int maxOneBlockPerCoursePerDay = 0;
        Map<String, Map<String, Map<DayOfWeek, Integer>>> groupCourseDayCount = new HashMap<>();
        for (CourseBlockAssignment a : list) {
            if (!a.isPinned() && a.getGroup() != null && a.getCourse() != null && a.getTimeslot() != null) {
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
        result.put("Maximum 1 block per non-BASICAS course per group per day",
                maxOneBlockPerCoursePerDay);

        // Prefer BASICAS blocks to be consecutive on same day (SOFT)
        int basicasNonConsecutive = 0;
        for (int i = 0; i < list.size(); i++) {
            for (int j = i + 1; j < list.size(); j++) {
                CourseBlockAssignment a1 = list.get(i);
                CourseBlockAssignment a2 = list.get(j);
                if (!a1.isPinned() && !a2.isPinned()
                        && a1.getGroup() != null && a1.getGroup().equals(a2.getGroup()) &&
                        a1.getCourse() != null && a1.getCourse().equals(a2.getCourse()) &&
                        a1.getTimeslot() != null && a2.getTimeslot() != null &&
                        a1.getTimeslot().getDayOfWeek().equals(a2.getTimeslot().getDayOfWeek())) {
                    String component = a1.getCourse().getComponent();
                    if (component != null && component.equalsIgnoreCase("BASICAS")) {
                        int end1 = a1.getTimeslot().getStartHour() + a1.getTimeslot().getLengthHours();
                        int start2 = a2.getTimeslot().getStartHour();
                        int end2 = a2.getTimeslot().getStartHour() + a2.getTimeslot().getLengthHours();
                        int start1 = a1.getTimeslot().getStartHour();
                        boolean areConsecutive = (end1 == start2 || end2 == start1);
                        if (!areConsecutive) {
                            basicasNonConsecutive++;
                        }
                    }
                }
            }
        }
        result.put("Prefer BASICAS blocks to be consecutive on same day", basicasNonConsecutive);

        // Prefer group's preferred room (SOFT)
        int preferredRoomViolations = 0;
        for (CourseBlockAssignment a : list) {
            if (!a.isPinned() && a.getGroup() != null && a.getRoom() != null && a.getCourse() != null) {
                var preferredRoom = a.getGroup().getPreferredRoom();
                if (preferredRoom != null &&
                        !"laboratorio".equalsIgnoreCase(a.getCourse().getRoomRequirement()) &&
                        !preferredRoom.equals(a.getRoom())) {
                    preferredRoomViolations++;
                }
            }
        }
        result.put("Prefer group's preferred room", preferredRoomViolations);

        // Minimize teacher building changes (SOFT)
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
        details.put("Maximum 1 block per non-BASICAS course per group per day",
                maxOneBlockPerCoursePerDay);

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
