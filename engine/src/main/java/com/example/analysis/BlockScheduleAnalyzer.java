package com.example.analysis;

import com.example.domain.BlockScheduleMath;
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

    /** True when this block belongs to a first-semester (semester == 1) course. */
    private static boolean isSemesterOneBlock(CourseBlockAssignment a) {
        return a.isSemesterOne();
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
                        && BlockScheduleMath.blocksOverlap(a1.getTimeslot(), a2.getTimeslot())) {
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
                        && BlockScheduleMath.blocksOverlap(a1.getTimeslot(), a2.getTimeslot())) {
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

        // Teacher's required room must be used - NOT excluded for pinned
        // assignments (mirrors SchoolConstraintProvider.teacherRequiredRoomMustBeUsed:
        // a non-pinned block's room is already structurally guaranteed correct by
        // CourseBlockAssignment.getMatchingRooms(), so this only ever fires for a
        // pinned row whose room drifted out of sync with its teacher's current
        // required room). Uses isTeacherRequiredRoomApplicable() rather than a
        // blind name comparison, so a multi-subject teacher's blocks that the
        // compatibility fallback correctly routed to the group's room aren't
        // flagged as violating a requirement that never applied to them.
        int teacherRequiredRoomMismatch = 0;
        for (CourseBlockAssignment a : list) {
            if (a.isTeacherRequiredRoomApplicable() && a.getRoom() != null
                    && !a.getTeacher().getRequiredRoomName().equals(a.getRoom().getName())) {
                teacherRequiredRoomMismatch++;
            }
        }
        result.put("Teacher's required room must be used", teacherRequiredRoomMismatch);

        // Semester hour limits must be respected (hard) - NOT excluded for
        // pinned assignments (mirrors SchoolConstraintProvider.semesterHourLimitsMustBeRespected:
        // a non-pinned block of a HARD-limited course can never be assigned
        // a timeslot ending past its limit in the first place -
        // CourseBlockAssignment.getMatchingBlockTimeslots() excludes it from
        // the value range - so this only ever fires for a pinned row whose
        // timeslot predates the limit).
        int semesterHourLimitHardViolations = 0;
        for (CourseBlockAssignment a : list) {
            if (BlockScheduleMath.violatesHardSemesterHourLimit(a)) {
                semesterHourLimitHardViolations++;
            }
        }
        result.put("Semester hour limits must be respected (hard)", semesterHourLimitHardViolations);

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
                        && BlockScheduleMath.blocksOverlap(a1.getTimeslot(), a2.getTimeslot())) {
                    groupConflict++;
                }
            }
        }
        result.put("Group cannot have two courses at same time", groupConflict);

        // Maximum blocks per course per group per day (HARD) - Count
        // Limit is per-component via component_block_rule, falling back to
        // DEFAULT_MAX_BLOCKS_PER_DAY when a component has no configured rule.
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
                        int limit = BlockScheduleMath.maxBlocksPerDay(assignments.get(0).getCourse());
                        if (count > limit) {
                            maxTwoBlocksPerCoursePerDay += (count - limit);
                        }
                    }
                }
            }
        }
        result.put("Maximum blocks per course per group per day", maxTwoBlocksPerCoursePerDay);

        // Course blocks must be consecutive (HARD) - Count
        // Mirror the solver: group unpinned blocks by (group, course, day), then
        // count the breaks (gaps or overlaps between adjacent blocks) in each
        // chain via the same BlockScheduleMath.countChainBreaks the constraint
        // itself uses. This avoids the pairwise over-counting where a valid
        // 7-8 / 8-9 / 9-10 sequence would flag the 7-8 <-> 9-10 pair.
        Map<java.util.List<Object>, java.util.List<CourseBlockAssignment>> blocksByCourseDay = new java.util.HashMap<>();
        for (CourseBlockAssignment a : list) {
            if (a.isPinned() || a.getGroup() == null || a.getCourse() == null || a.getTimeslot() == null) {
                continue;
            }
            java.util.List<Object> key = java.util.Arrays.asList(
                    a.getGroup(), a.getCourse(), a.getTimeslot().getDayOfWeek());
            blocksByCourseDay.computeIfAbsent(key, k -> new java.util.ArrayList<>()).add(a);
        }
        int courseBlocksNonConsecutive = 0;
        for (java.util.List<CourseBlockAssignment> blocks : blocksByCourseDay.values()) {
            if (blocks.size() < 2) {
                continue;
            }
            courseBlocksNonConsecutive += BlockScheduleMath.countChainBreaks(blocks);
        }
        result.put("Course blocks must be consecutive", courseBlocksNonConsecutive);

        // NOTE: "Non-standard rooms should finish by 2pm" is a SOFT constraint in
        // SchoolConstraintProvider (weight 10), so it is reported by
        // analyzeSoftConstraintViolations, not here.

        return result;
    }

    /**
     * Analyze hard constraint violations with detailed descriptions.
     * Returns a map of constraint name to list of violation descriptions.
     */
    public static Map<String, List<ViolationInstance>> analyzeHardConstraintViolationsDetailed(SchoolSchedule schedule) {
        Map<String, List<ViolationInstance>> details = new LinkedHashMap<>();

        if (schedule.getCourseBlockAssignments() == null) {
            return details;
        }

        List<CourseBlockAssignment> list = schedule.getCourseBlockAssignments();

        // Block length must match timeslot length (CRITICAL)
        List<ViolationInstance> blockLengthMismatch = new ArrayList<>();
        for (CourseBlockAssignment a : list) {
            if (a.getTimeslot() != null && a.getBlockLength() != a.getTimeslot().getLengthHours()) {
                blockLengthMismatch.add(ViolationInstance.of(blockAssignmentToString(a) +
                        " (block_length=" + a.getBlockLength() + "h, timeslot_length=" +
                        a.getTimeslot().getLengthHours() + "h)", a.getId()));
            }
        }
        details.put("Block length must match timeslot length", blockLengthMismatch);

        // Teacher must be qualified
        List<ViolationInstance> unqualified = new ArrayList<>();
        for (CourseBlockAssignment a : list) {
            if (a.getTeacher() != null && !a.getTeacher().isQualifiedFor(a.getCourse().getName())) {
                unqualified.add(ViolationInstance.of(blockAssignmentToString(a), a.getId()));
            }
        }
        details.put("Teacher must be qualified", unqualified);

        // Teacher must be available for entire block
        List<ViolationInstance> unavailable = new ArrayList<>();
        for (CourseBlockAssignment a : list) {
            if (!a.isPinned() && a.getTeacher() != null && a.getTimeslot() != null
                    && !a.getTeacher().isAvailableForBlock(a.getTimeslot())) {
                unavailable.add(ViolationInstance.of(blockAssignmentToString(a), a.getId()));
            }
        }
        details.put("Teacher must be available for entire block", unavailable);

        // No teacher double-booking
        List<ViolationInstance> teacherDouble = new ArrayList<>();
        for (int i = 0; i < list.size(); i++) {
            for (int j = i + 1; j < list.size(); j++) {
                CourseBlockAssignment a1 = list.get(i);
                CourseBlockAssignment a2 = list.get(j);
                // FIXED: Changed from && to || to match constraint provider logic
                // Penalize if at least one assignment is unpinned
                if ((!a1.isPinned() || !a2.isPinned())
                        && a1.getTeacher() != null && a1.getTeacher().equals(a2.getTeacher())
                        && a1.getTimeslot() != null && a2.getTimeslot() != null
                        && BlockScheduleMath.blocksOverlap(a1.getTimeslot(), a2.getTimeslot())) {
                    teacherDouble.add(ViolationInstance.of(
                            blockAssignmentToString(a1) + "  <->  " + blockAssignmentToString(a2),
                            a1.getId(), a2.getId()));
                }
            }
        }
        details.put("No teacher double-booking", teacherDouble);

        // No room double-booking
        List<ViolationInstance> roomDouble = new ArrayList<>();
        for (int i = 0; i < list.size(); i++) {
            for (int j = i + 1; j < list.size(); j++) {
                CourseBlockAssignment a1 = list.get(i);
                CourseBlockAssignment a2 = list.get(j);
                // FIXED: Changed from && to || to match constraint provider logic
                // Penalize if at least one assignment is unpinned
                if ((!a1.isPinned() || !a2.isPinned())
                        && a1.getRoom() != null && a1.getRoom().equals(a2.getRoom())
                        && a1.getTimeslot() != null && a2.getTimeslot() != null
                        && BlockScheduleMath.blocksOverlap(a1.getTimeslot(), a2.getTimeslot())) {
                    roomDouble.add(ViolationInstance.of(
                            blockAssignmentToString(a1) + "  <->  " + blockAssignmentToString(a2),
                            a1.getId(), a2.getId()));
                }
            }
        }
        details.put("No room double-booking", roomDouble);

        // Room type must satisfy course requirement (uses dual room requirements)
        List<ViolationInstance> roomTypeMismatch = new ArrayList<>();
        for (CourseBlockAssignment a : list) {
            if (!a.isPinned() && a.getRoom() != null && a.getSatisfiesRoomType() != null
                    && !a.getRoom().satisfiesRequirement(a.getSatisfiesRoomType())) {
                roomTypeMismatch.add(ViolationInstance.of(
                        blockAssignmentToString(a) + " (satisfiesRoomType=" + a.getSatisfiesRoomType()
                                + ", assignedRoomType=" + a.getRoom().getType() + ")",
                        a.getId()));
            }
        }
        details.put("Room type must satisfy course requirement", roomTypeMismatch);

        // Teacher's required room must be used - NOT excluded for pinned
        // assignments, see the count version above for why.
        List<ViolationInstance> teacherRequiredRoomMismatch = new ArrayList<>();
        for (CourseBlockAssignment a : list) {
            if (a.isTeacherRequiredRoomApplicable() && a.getRoom() != null
                    && !a.getTeacher().getRequiredRoomName().equals(a.getRoom().getName())) {
                teacherRequiredRoomMismatch.add(ViolationInstance.of(
                        blockAssignmentToString(a) + " (requiredRoom=" + a.getTeacher().getRequiredRoomName()
                                + ", assignedRoom=" + a.getRoom().getName() + ")",
                        a.getId()));
            }
        }
        details.put("Teacher's required room must be used", teacherRequiredRoomMismatch);

        // Semester hour limits must be respected (hard) - NOT excluded for
        // pinned assignments, see the count version above for why.
        List<ViolationInstance> semesterHourLimitHard = new ArrayList<>();
        for (CourseBlockAssignment a : list) {
            if (BlockScheduleMath.violatesHardSemesterHourLimit(a)) {
                semesterHourLimitHard.add(ViolationInstance.of(blockAssignmentToString(a) +
                        String.format(" (limit=%d:00, severity=HARD)", a.getCourse().getLatestEndHour()),
                        a.getId()));
            }
        }
        details.put("Semester hour limits must be respected (hard)", semesterHourLimitHard);

        // Group cannot have two courses at same time
        List<ViolationInstance> groupConflict = new ArrayList<>();
        for (int i = 0; i < list.size(); i++) {
            for (int j = i + 1; j < list.size(); j++) {
                CourseBlockAssignment a1 = list.get(i);
                CourseBlockAssignment a2 = list.get(j);
                // FIXED: Changed from && to || to match constraint provider logic
                // Penalize if at least one assignment is unpinned
                if ((!a1.isPinned() || !a2.isPinned())
                        && a1.getGroup().equals(a2.getGroup())
                        && a1.getTimeslot() != null && a2.getTimeslot() != null
                        && BlockScheduleMath.blocksOverlap(a1.getTimeslot(), a2.getTimeslot())) {
                    groupConflict.add(ViolationInstance.of(
                            blockAssignmentToString(a1) + "  <->  " + blockAssignmentToString(a2),
                            a1.getId(), a2.getId()));
                }
            }
        }
        details.put("Group cannot have two courses at same time", groupConflict);

        // Maximum blocks per course per group per day (HARD) - Detailed
        // Limit is per-component via component_block_rule, falling back to
        // DEFAULT_MAX_BLOCKS_PER_DAY when a component has no configured rule.
        // Every block in the offending day's chain gets its id attached, not
        // just the first one - any of them could be the one moved to fix it.
        List<ViolationInstance> maxTwoBlocksDetails = new ArrayList<>();
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
                        int limit = BlockScheduleMath.maxBlocksPerDay(assignments.get(0).getCourse());
                        if (count > limit) {
                            String courseName = assignments.get(0).getCourse().getName();
                            String groupName = assignments.get(0).getGroup().getName();
                            String dayName = formatDay(dayEntry.getKey());
                            String reason = String.format("(%s has %d blocks on %s, limit=%d)",
                                    groupName, count, dayName, limit);
                            List<String> ids = new ArrayList<>();
                            for (CourseBlockAssignment a : assignments) {
                                ids.add(a.getId());
                            }
                            maxTwoBlocksDetails.add(new ViolationInstance(ids, courseName + " " + reason));
                        }
                    }
                }
            }
        }
        details.put("Maximum blocks per course per group per day", maxTwoBlocksDetails);

        // Course blocks must be consecutive (HARD) - Detailed
        List<ViolationInstance> courseBlocksNonConsecutiveDetails = new ArrayList<>();
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
                        courseBlocksNonConsecutiveDetails.add(ViolationInstance.of(
                                blockAssignmentToString(a1) + "  <->  " + blockAssignmentToString(a2),
                                a1.getId(), a2.getId()));
                    }
                }
            }
        }
        details.put("Course blocks must be consecutive", courseBlocksNonConsecutiveDetails);

        // NOTE: "Non-standard rooms should finish by 2pm" is a SOFT constraint in
        // SchoolConstraintProvider (weight 10), so its details are reported by
        // analyzeSoftConstraintViolationsDetailed, not here.

        return details;
    }

    /**
     * Analyze soft constraint violations with detailed descriptions.
     * Returns a map of constraint name to list of violation descriptions.
     */
    public static Map<String, List<ViolationInstance>> analyzeSoftConstraintViolationsDetailed(SchoolSchedule schedule) {
        Map<String, List<ViolationInstance>> details = new LinkedHashMap<>();

        if (schedule.getCourseBlockAssignments() == null) {
            return details;
        }

        List<CourseBlockAssignment> list = schedule.getCourseBlockAssignments();

        // Prefer group's preferred room (SOFT, weight 2) - mirrors
        // SchoolConstraintProvider.groupPreferredRoomConstraint / the count
        // version above.
        List<ViolationInstance> preferredRoomDetails = new ArrayList<>();
        for (CourseBlockAssignment a : list) {
            if (!a.isPinned() && a.getGroup() != null && a.getRoom() != null
                    && !a.isTeacherRequiredRoomApplicable()) {
                // Skipped when the teacher's required room governs: it outranks the
                // group's range and makes the block room-fixed, so the penalty would be
                // unavoidable - see SchoolConstraintProvider.groupPreferredRoomConstraint.
                var acceptableRooms = a.getGroup().getAcceptableRooms(a.getSatisfiesRoomType());
                if (acceptableRooms != null && !acceptableRooms.contains(a.getRoom())) {
                    preferredRoomDetails.add(ViolationInstance.of(blockAssignmentToString(a)
                            + String.format(" (assigned=%s, not in group's curated range for %s)",
                                    a.getRoom().getName(), a.getSatisfiesRoomType()),
                            a.getId()));
                }
            }
        }
        details.put("Prefer group's preferred room", preferredRoomDetails);

        // Room capacity should fit group size (SOFT, weight 4) - mirrors the
        // count version above (pinned assignments NOT excluded here either).
        List<ViolationInstance> roomCapacityDetails = new ArrayList<>();
        for (CourseBlockAssignment a : list) {
            if (a.getRoom() != null && a.getGroup() != null) {
                Integer capacity = a.getRoom().getCapacity();
                Integer studentCount = a.getGroup().getStudentCount();
                if (capacity != null && studentCount != null && studentCount > capacity) {
                    roomCapacityDetails.add(ViolationInstance.of(blockAssignmentToString(a)
                            + String.format(" (room capacity=%d, group size=%d, over by %d)",
                                    capacity, studentCount, studentCount - capacity),
                            a.getId()));
                }
            }
        }
        details.put("Room capacity should fit group size", roomCapacityDetails);

        // Teacher max hours per week - an aggregate across ALL of one
        // teacher's blocks, not a single instance, so every one of that
        // teacher's block ids is attached - any of them could be the one
        // moved to bring the total back under the limit.
        List<ViolationInstance> teacherMaxExcess = new ArrayList<>();
        Map<String, Integer> teacherHours = new HashMap<>();
        Map<String, Teacher> teacherMap = new HashMap<>();
        Map<String, List<String>> teacherAssignmentIds = new HashMap<>();
        for (CourseBlockAssignment a : list) {
            if (a.getTeacher() != null && a.getTimeslot() != null) {
                String teacherId = a.getTeacher().getId();
                teacherHours.put(teacherId, teacherHours.getOrDefault(teacherId, 0) + a.getBlockLength());
                teacherMap.put(teacherId, a.getTeacher());
                teacherAssignmentIds.computeIfAbsent(teacherId, k -> new ArrayList<>()).add(a.getId());
            }
        }
        for (Map.Entry<String, Integer> entry : teacherHours.entrySet()) {
            String teacherId = entry.getKey();
            int totalHours = entry.getValue();
            Teacher teacher = teacherMap.get(teacherId);
            if (teacher != null && totalHours > teacher.getMaxHoursPerWeek()) {
                int excess = totalHours - teacher.getMaxHoursPerWeek();
                teacherMaxExcess.add(new ViolationInstance(teacherAssignmentIds.get(teacherId),
                        String.format("%s: assigned=%d hours, max=%d hours, excess=%d hours",
                                teacher.getName(), totalHours, teacher.getMaxHoursPerWeek(), excess)));
            }
        }
        details.put("Teacher exceeds max hours per week", teacherMaxExcess);

        // Minimize teacher idle gaps (SOFT, weight 2, availability-aware) -
        // mirrors the count version above: same per-day grouping and
        // BlockScheduleMath.availableGapHours() call, one description per
        // adjacent pair with a nonzero available gap.
        List<ViolationInstance> teacherIdleGapDetails = new ArrayList<>();
        Map<String, Map<DayOfWeek, List<CourseBlockAssignment>>> teacherDayForIdleGapDetails = new HashMap<>();
        for (CourseBlockAssignment a : list) {
            if (a.getTeacher() == null || a.getTimeslot() == null) {
                continue;
            }
            teacherDayForIdleGapDetails.computeIfAbsent(a.getTeacher().getId(), k -> new HashMap<>())
                    .computeIfAbsent(a.getTimeslot().getDayOfWeek(), k -> new ArrayList<>())
                    .add(a);
        }
        for (Map<DayOfWeek, List<CourseBlockAssignment>> byDay : teacherDayForIdleGapDetails.values()) {
            for (List<CourseBlockAssignment> assigns : byDay.values()) {
                assigns.sort(Comparator.comparingInt(a -> a.getTimeslot().getStartHour()));
                for (int i = 1; i < assigns.size(); i++) {
                    CourseBlockAssignment prev = assigns.get(i - 1);
                    CourseBlockAssignment curr = assigns.get(i);
                    if (prev.isPinned() || curr.isPinned()) {
                        continue; // matches the constraint: pinned pairs aren't penalized
                    }
                    int gap = BlockScheduleMath.availableGapHours(prev, curr);
                    if (gap > 0) {
                        teacherIdleGapDetails.add(ViolationInstance.of(
                                blockAssignmentToString(prev) + "  <->  "
                                        + blockAssignmentToString(curr) + String.format(" (available idle gap=%d hours)", gap),
                                prev.getId(), curr.getId()));
                    }
                }
            }
        }
        details.put("Minimize teacher idle gaps (availability-aware)", teacherIdleGapDetails);

        // Prefer first-semester blocks to start early (SOFT, weight 6) -
        // mirrors the count version above, but reports the specific block
        // that is each (group, day)'s earliest semester-1 one, rather than
        // just the aggregate deviation.
        List<ViolationInstance> semesterOneStartEarlyDetails = new ArrayList<>();
        Map<String, Map<DayOfWeek, CourseBlockAssignment>> earliestSemesterOneBlockByGroupDay = new HashMap<>();
        for (CourseBlockAssignment a : list) {
            if (a.isPinned() || a.getGroup() == null || a.getCourse() == null || a.getTimeslot() == null
                    || !Integer.valueOf(1).equals(a.getCourse().getSemester())) {
                continue;
            }
            Map<DayOfWeek, CourseBlockAssignment> byDay = earliestSemesterOneBlockByGroupDay
                    .computeIfAbsent(a.getGroup().getId(), k -> new HashMap<>());
            CourseBlockAssignment current = byDay.get(a.getTimeslot().getDayOfWeek());
            if (current == null || a.getTimeslot().getStartHour() < current.getTimeslot().getStartHour()) {
                byDay.put(a.getTimeslot().getDayOfWeek(), a);
            }
        }
        for (Map<DayOfWeek, CourseBlockAssignment> byDay : earliestSemesterOneBlockByGroupDay.values()) {
            for (CourseBlockAssignment earliest : byDay.values()) {
                int startHour = earliest.getTimeslot().getStartHour();
                if (startHour > BlockScheduleMath.EARLIEST_START_HOUR) {
                    semesterOneStartEarlyDetails.add(ViolationInstance.of(blockAssignmentToString(earliest)
                            + String.format(" (earliest semester-1 block that day starts at %d:00, should start by %d:00)",
                                    startHour, BlockScheduleMath.EARLIEST_START_HOUR),
                            earliest.getId()));
                }
            }
        }
        details.put("Prefer first-semester blocks to start early", semesterOneStartEarlyDetails);

        // Minimize first-semester group idle gaps (SOFT, weight 6) - mirrors
        // the count version above: same full-day adjacency (any semester, and
        // PINNED or not, breaks it), only sums when BOTH framing blocks are
        // semester-1 AND unpinned.
        List<ViolationInstance> semesterOneIdleGapDetails = new ArrayList<>();
        Map<String, Map<DayOfWeek, List<CourseBlockAssignment>>> fullDayForSemesterOneGapDetails = new HashMap<>();
        for (CourseBlockAssignment a : list) {
            if (a.getGroup() == null || a.getTimeslot() == null) {
                continue;
            }
            fullDayForSemesterOneGapDetails.computeIfAbsent(a.getGroup().getId(), k -> new HashMap<>())
                    .computeIfAbsent(a.getTimeslot().getDayOfWeek(), k -> new ArrayList<>())
                    .add(a);
        }
        for (Map<DayOfWeek, List<CourseBlockAssignment>> byDay : fullDayForSemesterOneGapDetails.values()) {
            for (List<CourseBlockAssignment> assigns : byDay.values()) {
                assigns.sort(Comparator.comparingInt(a -> a.getTimeslot().getStartHour()));
                for (int i = 1; i < assigns.size(); i++) {
                    CourseBlockAssignment prev = assigns.get(i - 1);
                    CourseBlockAssignment curr = assigns.get(i);
                    if (!prev.isPinned() && !curr.isPinned()
                            && isSemesterOneBlock(prev) && isSemesterOneBlock(curr)) {
                        int gap = BlockScheduleMath.gapHours(prev, curr);
                        if (gap > 0) {
                            semesterOneIdleGapDetails.add(ViolationInstance.of(
                                    blockAssignmentToString(prev) + "  <->  "
                                            + blockAssignmentToString(curr) + String.format(" (gap=%d hours)", gap),
                                    prev.getId(), curr.getId()));
                        }
                    }
                }
            }
        }
        details.put("Minimize first-semester group idle gaps", semesterOneIdleGapDetails);

        // Semester hour limits should be respected (soft) - mirrors the count
        // version above / SchoolConstraintProvider.preferSemesterHourLimits.
        List<ViolationInstance> semesterHourLimitSoftDetails = new ArrayList<>();
        for (CourseBlockAssignment a : list) {
            if (!a.isPinned()) {
                int excess = BlockScheduleMath.softSemesterHourLimitExcess(a);
                if (excess > 0) {
                    semesterHourLimitSoftDetails.add(ViolationInstance.of(blockAssignmentToString(a)
                            + String.format(" (%d hour(s) past semester %d's soft limit)", excess,
                                    a.getCourse().getSemester()),
                            a.getId()));
                }
            }
        }
        details.put("Semester hour limits should be respected (soft)", semesterHourLimitSoftDetails);

        // Minimize group idle gaps (SOFT, weight 3) - Detailed - mirrors the count
        // version above (re-enabled 2026-09-08; see it for the pinned-adjacency rule).
        List<ViolationInstance> groupIdleGapsDetails = new ArrayList<>();
        Map<String, Map<DayOfWeek, List<CourseBlockAssignment>>> groupDayForDetails = new HashMap<>();
        for (CourseBlockAssignment a : list) {
            if (a.getGroup() == null || a.getTimeslot() == null)
                continue;
            String groupKey = a.getGroup().getId();
            DayOfWeek day = a.getTimeslot().getDayOfWeek();
            groupDayForDetails.computeIfAbsent(groupKey, k -> new HashMap<>())
                    .computeIfAbsent(day, k -> new ArrayList<>())
                    .add(a);
        }
        for (Map<DayOfWeek, List<CourseBlockAssignment>> dayAssignments : groupDayForDetails.values()) {
            for (List<CourseBlockAssignment> assigns : dayAssignments.values()) {
                assigns.sort(Comparator.comparingInt(a -> a.getTimeslot().getStartHour()));
                for (int i = 1; i < assigns.size(); i++) {
                    CourseBlockAssignment prev = assigns.get(i - 1);
                    CourseBlockAssignment curr = assigns.get(i);
                    if (prev.isPinned() || curr.isPinned()) {
                        continue;
                    }
                    int gap = BlockScheduleMath.gapHours(prev, curr);
                    if (gap > 0) {
                        groupIdleGapsDetails.add(ViolationInstance.of(
                                blockAssignmentToString(prev) + "  <->  "
                                        + blockAssignmentToString(curr) + String.format(" (gap=%d hours)", gap),
                                prev.getId(), curr.getId()));
                    }
                }
            }
        }
        details.put("Minimize group idle gaps", groupIdleGapsDetails);

        // Prefer block's specified room (SOFT) - Detailed
        List<ViolationInstance> blockSpecifiedRoomDetails = new ArrayList<>();
        for (CourseBlockAssignment a : list) {
            if (!a.isPinned() && a.getRoom() != null) {
                String preferredRoomHint = a.getPreferredRoomHint();
                if (preferredRoomHint != null && !preferredRoomHint.isEmpty()) {
                    if (!preferredRoomHint.equals(a.getRoom().getName())) {
                        String reason = String.format("(preferred=%s, assigned=%s)",
                                preferredRoomHint, a.getRoom().getName());
                        blockSpecifiedRoomDetails.add(ViolationInstance.of(
                                blockAssignmentToString(a) + " " + reason, a.getId()));
                    }
                }
            }
        }
        details.put("Prefer block's specified room", blockSpecifiedRoomDetails);

        // Non-standard rooms should finish by 2pm (SOFT, weight 10) - Detailed
        // Mirrors the solver: excludes pinned assignments (fixed from database).
        List<ViolationInstance> nonStandardAfter2pmDetails = new ArrayList<>();
        for (CourseBlockAssignment a : list) {
            if (!a.isPinned() && a.getRoom() != null && a.getTimeslot() != null) {
                String roomType = a.getRoom().getType();
                boolean isNonStandard = (roomType != null && !roomType.equalsIgnoreCase("Standard"));
                if (isNonStandard) {
                    int endHour = a.getTimeslot().getStartHour() + a.getTimeslot().getLengthHours();
                    if (endHour > 14) {
                        nonStandardAfter2pmDetails.add(ViolationInstance.of(blockAssignmentToString(a) +
                                String.format(" (ends at %d:00, should end by 14:00)", endHour),
                                a.getId()));
                    }
                }
            }
        }
        details.put("Non-standard rooms should finish by 2pm", nonStandardAfter2pmDetails);

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

        // Prefer group's preferred room (SOFT, weight 2) - mirrors
        // SchoolConstraintProvider.groupPreferredRoomConstraint: a group's
        // curated acceptable-room range is keyed by room type, so this
        // naturally applies (or doesn't) per type rather than needing a
        // special Mixed-type exclusion.
        int preferredRoomViolations = 0;
        for (CourseBlockAssignment a : list) {
            if (!a.isPinned() && a.getGroup() != null && a.getRoom() != null
                    && !a.isTeacherRequiredRoomApplicable()) {
                // Skipped when the teacher's required room governs: it outranks the
                // group's range and makes the block room-fixed, so the penalty would be
                // unavoidable - see SchoolConstraintProvider.groupPreferredRoomConstraint.
                var acceptableRooms = a.getGroup().getAcceptableRooms(a.getSatisfiesRoomType());
                if (acceptableRooms != null && !acceptableRooms.contains(a.getRoom())) {
                    preferredRoomViolations++;
                }
            }
        }
        result.put("Prefer group's preferred room", preferredRoomViolations);

        // Prefer Core 1h blocks at the same time across days (SOFT, weight 2) -
        // mirrors SchoolConstraintProvider.preferCoreOneHourBlocksAtSameTimeAcrossDays:
        // group by (group, course), then for each group of 1h Core blocks, penalize
        // by how many blocks deviate from the most common ("mode") start hour.
        int coreSameTimeViolations = 0;
        Map<String, Map<String, List<CourseBlockAssignment>>> coreBlocksByGroupAndCourse = new HashMap<>();
        for (CourseBlockAssignment a : list) {
            if (!a.isPinned() && a.getBlockLength() == 1 && a.getGroup() != null && a.getCourse() != null
                    && a.getTimeslot() != null && "Core".equals(a.getCourse().getDesignation())) {
                coreBlocksByGroupAndCourse
                        .computeIfAbsent(a.getGroup().getId(), k -> new HashMap<>())
                        .computeIfAbsent(a.getCourse().getId(), k -> new ArrayList<>())
                        .add(a);
            }
        }
        for (Map<String, List<CourseBlockAssignment>> byCourse : coreBlocksByGroupAndCourse.values()) {
            for (List<CourseBlockAssignment> blocks : byCourse.values()) {
                coreSameTimeViolations += BlockScheduleMath.blocksNotAtModeHour(blocks);
            }
        }
        // TEMP DISABLED 2026-08-26 (mirrors SchoolConstraintProvider - re-enable both together)
        // result.put("Prefer Core 1h blocks at the same time across days", coreSameTimeViolations);

        // Room capacity should fit group size (SOFT, weight 4) - only applies when
        // both room.capacity and group.studentCount are known; mirrors
        // SchoolConstraintProvider.roomCapacityShouldFitGroupSize exactly
        // (including that pinned assignments are NOT excluded, unlike most other
        // room-related soft constraints - overcrowding is worth flagging even when
        // the assignment itself can't be moved by the solver).
        int roomCapacityViolations = 0;
        for (CourseBlockAssignment a : list) {
            if (a.getRoom() != null && a.getGroup() != null) {
                Integer capacity = a.getRoom().getCapacity();
                Integer studentCount = a.getGroup().getStudentCount();
                if (capacity != null && studentCount != null && studentCount > capacity) {
                    roomCapacityViolations++;
                }
            }
        }
        result.put("Room capacity should fit group size", roomCapacityViolations);

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
        // TEMP DISABLED 2026-08-24 (mirrors SchoolConstraintProvider - re-enable both together)
        // result.put("Minimize teacher building changes", buildingChanges);

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

                // Calculate gaps between blocks - BlockScheduleMath.availableGapHours
                // counts each available gap hour individually (partial credit for a
                // gap the teacher is only partly free during), matching exactly what
                // SchoolConstraintProvider.minimizeTeacherIdleGaps penalizes.
                for (int i = 1; i < assigns.size(); i++) {
                    CourseBlockAssignment prev = assigns.get(i - 1);
                    CourseBlockAssignment curr = assigns.get(i);
                    // Pinned blocks stay in the list so they break adjacency (they
                    // still occupy the teacher), but never form a penalized pair -
                    // matching SchoolConstraintProvider.minimizeTeacherIdleGaps.
                    if (prev.isPinned() || curr.isPinned()) {
                        continue;
                    }
                    idleGaps += BlockScheduleMath.availableGapHours(prev, curr);
                }
            }
        }
        result.put("Minimize teacher idle gaps (availability-aware)", idleGaps);

        // Minimize group idle gaps (SOFT, weight 3) - mirrors
        // SchoolConstraintProvider.minimizeGroupIdleGaps. Disabled 2026-08-24,
        // re-enabled 2026-09-08 (it covers every group; the semester-1 rule below
        // stays as a higher-weighted overlay for first-years). Pinned blocks stay in
        // the day list so they break adjacency - they still occupy the student - but
        // never form a penalized pair, since the solver can't move them.
        int groupIdleGaps = 0;
        Map<String, Map<DayOfWeek, List<CourseBlockAssignment>>> groupDayAssignments = new HashMap<>();
        for (CourseBlockAssignment a : list) {
            if (a.getGroup() == null || a.getTimeslot() == null)
                continue;
            String groupKey = a.getGroup().getId();
            DayOfWeek day = a.getTimeslot().getDayOfWeek();
            groupDayAssignments.computeIfAbsent(groupKey, k -> new HashMap<>())
                    .computeIfAbsent(day, k -> new ArrayList<>())
                    .add(a);
        }
        for (Map<DayOfWeek, List<CourseBlockAssignment>> dayAssignments : groupDayAssignments.values()) {
            for (List<CourseBlockAssignment> assigns : dayAssignments.values()) {
                assigns.sort(Comparator.comparingInt(a -> a.getTimeslot().getStartHour()));
                for (int i = 1; i < assigns.size(); i++) {
                    CourseBlockAssignment prev = assigns.get(i - 1);
                    CourseBlockAssignment curr = assigns.get(i);
                    if (prev.isPinned() || curr.isPinned()) {
                        continue;
                    }
                    groupIdleGaps += BlockScheduleMath.gapHours(prev, curr);
                }
            }
        }
        result.put("Minimize group idle gaps", groupIdleGaps);

        // Prefer first-semester blocks to start early (SOFT, weight 4) - mirrors
        // SchoolConstraintProvider.preferSemesterOneBlocksStartEarly: group unpinned
        // semester-1 blocks by (group, day), penalize by how far the earliest one's
        // start hour is from BlockScheduleMath.EARLIEST_START_HOUR (7).
        int semesterOneStartEarlyViolations = 0;
        Map<String, Map<DayOfWeek, Integer>> semesterOneEarliestHourByGroupDay = new HashMap<>();
        for (CourseBlockAssignment a : list) {
            if (a.isPinned() || a.getGroup() == null || a.getCourse() == null || a.getTimeslot() == null
                    || !Integer.valueOf(1).equals(a.getCourse().getSemester())) {
                continue;
            }
            String groupKey = a.getGroup().getId();
            DayOfWeek day = a.getTimeslot().getDayOfWeek();
            int startHour = a.getTimeslot().getStartHour();
            semesterOneEarliestHourByGroupDay.computeIfAbsent(groupKey, k -> new HashMap<>())
                    .merge(day, startHour, Math::min);
        }
        for (Map<DayOfWeek, Integer> byDay : semesterOneEarliestHourByGroupDay.values()) {
            for (int earliestHour : byDay.values()) {
                if (earliestHour > BlockScheduleMath.EARLIEST_START_HOUR) {
                    semesterOneStartEarlyViolations += earliestHour - BlockScheduleMath.EARLIEST_START_HOUR;
                }
            }
        }
        result.put("Prefer first-semester blocks to start early", semesterOneStartEarlyViolations);

        // Minimize first-semester group idle gaps (SOFT, weight 6) - mirrors
        // SchoolConstraintProvider.minimizeSemesterOneGroupIdleGaps: same adjacent-gap
        // logic as the generic group-idle-gaps rule, over the group's FULL day (any
        // semester, and PINNED or not, so a block the student is actually sitting in
        // correctly breaks adjacency instead of being mistaken for idle time), but
        // only summing a gap when BOTH framing blocks are themselves semester-1 and
        // unpinned. Pinned blocks were excluded from the day list entirely until
        // 2026-09-08, which scored their occupied hours as idle - see the constraint's
        // javadoc for the live example that surfaced it.
        int semesterOneIdleGaps = 0;
        Map<String, Map<DayOfWeek, List<CourseBlockAssignment>>> fullDayAssignmentsForSemesterOneGaps = new HashMap<>();
        for (CourseBlockAssignment a : list) {
            if (a.getGroup() == null || a.getTimeslot() == null)
                continue;
            String groupKey = a.getGroup().getId();
            DayOfWeek day = a.getTimeslot().getDayOfWeek();
            fullDayAssignmentsForSemesterOneGaps.computeIfAbsent(groupKey, k -> new HashMap<>())
                    .computeIfAbsent(day, k -> new ArrayList<>())
                    .add(a);
        }
        for (Map<DayOfWeek, List<CourseBlockAssignment>> dayAssignments : fullDayAssignmentsForSemesterOneGaps.values()) {
            for (List<CourseBlockAssignment> assigns : dayAssignments.values()) {
                assigns.sort(Comparator.comparingInt(a -> a.getTimeslot().getStartHour()));
                for (int i = 1; i < assigns.size(); i++) {
                    CourseBlockAssignment prev = assigns.get(i - 1);
                    CourseBlockAssignment curr = assigns.get(i);
                    if (!prev.isPinned() && !curr.isPinned()
                            && isSemesterOneBlock(prev) && isSemesterOneBlock(curr)) {
                        semesterOneIdleGaps += BlockScheduleMath.gapHours(prev, curr);
                    }
                }
            }
        }
        result.put("Minimize first-semester group idle gaps", semesterOneIdleGaps);

        // Semester hour limits should be respected (soft) - mirrors
        // SchoolConstraintProvider.preferSemesterHourLimits: excludes pinned
        // assignments, sums BlockScheduleMath.softSemesterHourLimitExcess()
        // over every block of a SOFT-severity-limited course.
        int semesterHourLimitSoftExcess = 0;
        for (CourseBlockAssignment a : list) {
            if (!a.isPinned()) {
                semesterHourLimitSoftExcess += BlockScheduleMath.softSemesterHourLimitExcess(a);
            }
        }
        result.put("Semester hour limits should be respected (soft)", semesterHourLimitSoftExcess);

        // Prefer block's specified room (SOFT, weight 3)
        int blockSpecifiedRoomViolations = 0;
        for (CourseBlockAssignment a : list) {
            if (!a.isPinned() && a.getRoom() != null) {
                String preferredRoomHint = a.getPreferredRoomHint();
                if (preferredRoomHint != null && !preferredRoomHint.isEmpty()) {
                    if (!preferredRoomHint.equals(a.getRoom().getName())) {
                        blockSpecifiedRoomViolations++;
                    }
                }
            }
        }
        result.put("Prefer block's specified room", blockSpecifiedRoomViolations);

        // Non-standard rooms should finish by 2pm (SOFT, weight 10)
        // Mirrors the solver: excludes pinned assignments (fixed from database).
        int nonStandardAfter2pm = 0;
        for (CourseBlockAssignment a : list) {
            if (!a.isPinned() && a.getRoom() != null && a.getTimeslot() != null) {
                String roomType = a.getRoom().getType();
                boolean isNonStandard = (roomType != null && !roomType.equalsIgnoreCase("Standard"));
                if (isNonStandard) {
                    int endHour = a.getTimeslot().getStartHour() + a.getTimeslot().getLengthHours();
                    if (endHour > 14) {
                        nonStandardAfter2pm++;
                    }
                }
            }
        }
        result.put("Non-standard rooms should finish by 2pm", nonStandardAfter2pm);

        return result;
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
