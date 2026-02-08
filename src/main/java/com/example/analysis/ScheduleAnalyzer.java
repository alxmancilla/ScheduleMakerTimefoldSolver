package com.example.analysis;

import com.example.domain.CourseAssignment;
import com.example.domain.SchoolSchedule;
import java.time.DayOfWeek;
import java.util.*;

public final class ScheduleAnalyzer {

    private ScheduleAnalyzer() {
    }

    public static Map<String, Integer> analyzeHardConstraintViolations(SchoolSchedule schedule) {
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

        // Same teacher for all course hours (hard constraint)
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

        // Group course hours must be consecutive on same day (hard, component-aware
        // penalties)
        int groupConsecutivenessViolations = 0;
        for (int i = 0; i < list.size(); i++) {
            for (int j = i + 1; j < list.size(); j++) {
                CourseAssignment a1 = list.get(i);
                CourseAssignment a2 = list.get(j);

                // Must be same group and same course
                if (!a1.getGroup().equals(a2.getGroup()) || !a1.getCourse().equals(a2.getCourse())) {
                    continue;
                }

                // Different sequence indices
                if (a1.getSequenceIndex() == a2.getSequenceIndex()) {
                    continue;
                }

                if (a1.getTimeslot() == null || a2.getTimeslot() == null) {
                    continue;
                }

                // Only check if on same day
                if (!a1.getTimeslot().getDayOfWeek().equals(a2.getTimeslot().getDayOfWeek())) {
                    continue;
                }

                // Check if consecutive
                int hour1 = a1.getTimeslot().getHour();
                int hour2 = a2.getTimeslot().getHour();
                int seqDiff = a2.getSequenceIndex() - a1.getSequenceIndex();
                int hourDiff = hour2 - hour1;

                if (hourDiff != seqDiff) {
                    groupConsecutivenessViolations++;
                }
            }
        }
        result.put("Group course hours must be consecutive on same day", groupConsecutivenessViolations);

        return result;
    }

    public static Map<String, List<String>> analyzeHardConstraintViolationsDetailed(SchoolSchedule schedule) {
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

        // Same teacher for all course hours (hard constraint)
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

        // Group course hours must be consecutive on same day (hard, component-aware)
        List<String> groupConsecutivenessDetails = new ArrayList<>();
        for (int i = 0; i < list.size(); i++) {
            for (int j = i + 1; j < list.size(); j++) {
                CourseAssignment a1 = list.get(i);
                CourseAssignment a2 = list.get(j);

                // Must be same group and same course
                if (!a1.getGroup().equals(a2.getGroup()) || !a1.getCourse().equals(a2.getCourse())) {
                    continue;
                }

                // Different sequence indices
                if (a1.getSequenceIndex() == a2.getSequenceIndex()) {
                    continue;
                }

                if (a1.getTimeslot() == null || a2.getTimeslot() == null) {
                    continue;
                }

                // Only check if on same day
                if (!a1.getTimeslot().getDayOfWeek().equals(a2.getTimeslot().getDayOfWeek())) {
                    continue;
                }

                // Check if consecutive
                int hour1 = a1.getTimeslot().getHour();
                int hour2 = a2.getTimeslot().getHour();
                int seqDiff = a2.getSequenceIndex() - a1.getSequenceIndex();
                int hourDiff = hour2 - hour1;

                if (hourDiff != seqDiff) {
                    int gapSize = Math.abs(hourDiff) - Math.abs(seqDiff);
                    String component = a1.getCourse().getComponent() != null ? a1.getCourse().getComponent()
                            : "UNKNOWN";
                    int penalty = gapSize;
                    if (!component.equalsIgnoreCase("BASICAS")) {
                        penalty = gapSize * 3; // 3x penalty for non-BASICAS
                    }
                    groupConsecutivenessDetails.add(
                            String.format("%s [%s] gap=%d, component=%s, penalty=%d: %s <-> %s",
                                    a1.getGroup().getName(),
                                    a1.getCourse().getName(),
                                    gapSize,
                                    component,
                                    penalty,
                                    assignmentToString(a1),
                                    assignmentToString(a2)));
                }
            }
        }
        details.put("Group course hours must be consecutive on same day", groupConsecutivenessDetails);

        return details;
    }

    // Soft constraint analysis
    public static Map<String, Integer> analyzeSoftConstraintViolations(SchoolSchedule schedule) {
        Map<String, Integer> result = new LinkedHashMap<>();
        List<CourseAssignment> list = schedule.getCourseAssignments();

        // Minimize teacher idle gaps (soft, weight 1, availability-aware)
        // Only counts gaps when teacher IS available during gap hours
        int idleGaps = 0;
        Map<String, Map<DayOfWeek, List<CourseAssignment>>> teacherDayAssignments = new HashMap<>();
        for (CourseAssignment a : list) {
            if (a.getTeacher() == null || a.getTimeslot() == null)
                continue;
            String teacherKey = a.getTeacher().getId();
            DayOfWeek day = a.getTimeslot().getDayOfWeek();
            teacherDayAssignments.computeIfAbsent(teacherKey, k -> new HashMap<>())
                    .computeIfAbsent(day, k -> new ArrayList<>())
                    .add(a);
        }

        for (Map.Entry<String, Map<DayOfWeek, List<CourseAssignment>>> teacherEntry : teacherDayAssignments
                .entrySet()) {
            for (Map.Entry<DayOfWeek, List<CourseAssignment>> dayEntry : teacherEntry.getValue().entrySet()) {
                List<CourseAssignment> assigns = dayEntry.getValue();
                assigns.sort(Comparator.comparing(a -> a.getTimeslot().getHour()));

                // Get unique hours
                List<Integer> uniqueHours = new ArrayList<>();
                for (CourseAssignment a : assigns) {
                    int h = a.getTimeslot().getHour();
                    if (uniqueHours.isEmpty() || uniqueHours.get(uniqueHours.size() - 1) != h) {
                        uniqueHours.add(h);
                    }
                }

                // Check for gaps where teacher is available
                for (int i = 1; i < uniqueHours.size(); i++) {
                    int prevHour = uniqueHours.get(i - 1);
                    int currHour = uniqueHours.get(i);
                    int gap = currHour - prevHour - 1;

                    if (gap > 0) {
                        // Check if teacher is available during gap hours
                        CourseAssignment sampleAssignment = assigns.get(0);
                        DayOfWeek day = dayEntry.getKey();
                        boolean availableDuringGap = true;

                        for (int gapHour = prevHour + 1; gapHour < currHour; gapHour++) {
                            if (!sampleAssignment.getTeacher().isAvailableAt(day, gapHour)) {
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

        // Teacher max hours per week (soft constraint)
        Map<String, Integer> teacherCounts = new HashMap<>();
        Map<String, Integer> teacherMax = new HashMap<>();
        for (CourseAssignment a : list) {
            if (a.getTeacher() == null)
                continue;
            String teacherId = a.getTeacher().getId();
            teacherCounts.put(teacherId, teacherCounts.getOrDefault(teacherId, 0) + 1);
            teacherMax.putIfAbsent(teacherId, a.getTeacher().getMaxHoursPerWeek());
        }
        int totalExcess = 0;
        for (Map.Entry<String, Integer> e : teacherCounts.entrySet()) {
            String teacherId = e.getKey();
            int count = e.getValue();
            int max = teacherMax.getOrDefault(teacherId, 40);
            if (count > max) {
                totalExcess += (count - max);
            }
        }
        result.put("Teacher max hours per week", totalExcess);

        // Balance teacher workload (soft constraint)
        // This is a gentle progressive penalty as teachers approach their max hours
        int workloadPenalty = 0;
        for (Map.Entry<String, Integer> e : teacherCounts.entrySet()) {
            String teacherId = e.getKey();
            int assigned = e.getValue();
            int max = Math.max(1, teacherMax.getOrDefault(teacherId, 40));

            // Progressive penalty: increases as teacher approaches max capacity
            if (assigned > 0) {
                double utilization = (double) assigned / (double) max;
                if (utilization > 0.8) { // Only penalize when > 80% utilized
                    workloadPenalty += (int) Math.round((utilization - 0.8) * 100);
                }
            }
        }
        result.put("Balance teacher workload", workloadPenalty);

        // Limit non-BASICAS courses to at most 2 days per group (soft, weight 5)
        // Count violations where non-BASICAS courses span more than 2 days
        int nonBasicasDayViolations = 0;
        Map<String, Map<String, Set<DayOfWeek>>> groupCourseDays = new HashMap<>();

        for (CourseAssignment a : list) {
            if (a.getTimeslot() == null || a.getCourse().getComponent() == null)
                continue;
            if (a.getCourse().getComponent().equalsIgnoreCase("BASICAS"))
                continue; // Only check non-BASICAS courses

            String groupKey = a.getGroup().getId();
            String courseKey = a.getCourse().getId();
            DayOfWeek day = a.getTimeslot().getDayOfWeek();

            groupCourseDays.computeIfAbsent(groupKey, k -> new HashMap<>())
                    .computeIfAbsent(courseKey, k -> new HashSet<>())
                    .add(day);
        }

        for (Map<String, Set<DayOfWeek>> courseDays : groupCourseDays.values()) {
            for (Set<DayOfWeek> days : courseDays.values()) {
                if (days.size() > 2) {
                    int excessDays = days.size() - 2;
                    nonBasicasDayViolations += excessDays * 5; // Weight 5 per excess day
                }
            }
        }
        result.put("Limit non-BASICAS courses to at most 2 days per group", nonBasicasDayViolations);

        return result;
    }

    public static Map<String, List<String>> analyzeSoftConstraintViolationsDetailed(SchoolSchedule schedule) {
        Map<String, List<String>> details = new LinkedHashMap<>();
        List<CourseAssignment> list = schedule.getCourseAssignments();

        // Minimize teacher idle gaps (detailed, availability-aware)
        List<String> idleGapDetails = new ArrayList<>();
        Map<String, Map<DayOfWeek, List<CourseAssignment>>> teacherDayAssign = new HashMap<>();
        for (CourseAssignment a : list) {
            if (a.getTeacher() == null || a.getTimeslot() == null)
                continue;
            String teacherId = a.getTeacher().getId();
            DayOfWeek day = a.getTimeslot().getDayOfWeek();
            teacherDayAssign.computeIfAbsent(teacherId, k -> new HashMap<>())
                    .computeIfAbsent(day, k -> new ArrayList<>())
                    .add(a);
        }

        for (Map.Entry<String, Map<DayOfWeek, List<CourseAssignment>>> teacherEntry : teacherDayAssign.entrySet()) {
            for (Map.Entry<DayOfWeek, List<CourseAssignment>> dayEntry : teacherEntry.getValue().entrySet()) {
                List<CourseAssignment> assigns = dayEntry.getValue();
                assigns.sort(Comparator.comparing(a -> a.getTimeslot().getHour()));

                List<Integer> uniqueHours = new ArrayList<>();
                for (CourseAssignment a : assigns) {
                    int h = a.getTimeslot().getHour();
                    if (uniqueHours.isEmpty() || uniqueHours.get(uniqueHours.size() - 1) != h) {
                        uniqueHours.add(h);
                    }
                }

                for (int i = 1; i < uniqueHours.size(); i++) {
                    int prevHour = uniqueHours.get(i - 1);
                    int currHour = uniqueHours.get(i);
                    int gap = currHour - prevHour - 1;

                    if (gap > 0) {
                        CourseAssignment sampleAssignment = assigns.get(0);
                        DayOfWeek day = dayEntry.getKey();
                        boolean availableDuringGap = true;

                        // Check if teacher is available during gap hours
                        for (int gapHour = prevHour + 1; gapHour < currHour; gapHour++) {
                            if (!sampleAssignment.getTeacher().isAvailableAt(day, gapHour)) {
                                availableDuringGap = false;
                                break;
                            }
                        }

                        // Only report if teacher IS available during gap
                        if (availableDuringGap) {
                            CourseAssignment prev = assigns.stream()
                                    .filter(a -> a.getTimeslot().getHour() == prevHour)
                                    .findFirst().orElse(assigns.get(0));
                            CourseAssignment cur = assigns.stream()
                                    .filter(a -> a.getTimeslot().getHour() == currHour)
                                    .findFirst().orElse(assigns.get(assigns.size() - 1));

                            idleGapDetails.add(
                                    String.format("%s has gap of %d on %s (available during gap): %s <-> %s",
                                            prev.getTeacher().getName(),
                                            gap,
                                            day,
                                            assignmentToString(prev),
                                            assignmentToString(cur)));
                        }
                    }
                }
            }
        }
        details.put("Minimize teacher idle gaps (availability-aware)", idleGapDetails);

        // Teacher max hours per week (soft) - detailed offenders
        List<String> teacherMaxExcessDetails = new ArrayList<>();
        Map<String, Integer> teacherCounts = new HashMap<>();
        Map<String, Integer> teacherMax = new HashMap<>();
        Map<String, String> teacherNames = new HashMap<>();

        for (CourseAssignment a : list) {
            if (a.getTeacher() == null)
                continue;
            String teacherId = a.getTeacher().getId();
            String teacherName = a.getTeacher().getName() + " " + a.getTeacher().getLastName();
            teacherCounts.put(teacherId, teacherCounts.getOrDefault(teacherId, 0) + 1);
            teacherMax.putIfAbsent(teacherId, a.getTeacher().getMaxHoursPerWeek());
            teacherNames.putIfAbsent(teacherId, teacherName);
        }

        for (Map.Entry<String, Integer> e : teacherCounts.entrySet()) {
            String teacherId = e.getKey();
            int count = e.getValue();
            int max = teacherMax.getOrDefault(teacherId, 40);
            if (count > max) {
                int excess = count - max;
                String name = teacherNames.getOrDefault(teacherId, teacherId);
                teacherMaxExcessDetails.add(name + ": assigned=" + count + ", max=" + max + ", excess=" + excess);
            }
        }
        details.put("Teacher max hours per week", teacherMaxExcessDetails);

        // Balance teacher workload (soft) - detailed
        List<String> workloadDetails = new ArrayList<>();
        for (Map.Entry<String, Integer> e : teacherCounts.entrySet()) {
            String teacherId = e.getKey();
            int assigned = e.getValue();
            int max = Math.max(1, teacherMax.getOrDefault(teacherId, 40));
            double utilization = (double) assigned / (double) max;

            if (utilization > 0.8) { // Only show teachers > 80% utilized
                String name = teacherNames.getOrDefault(teacherId, teacherId);
                int penalty = (int) Math.round((utilization - 0.8) * 100);
                workloadDetails.add(
                        String.format("%s: assigned=%d, max=%d, utilization=%.1f%%, penalty=%d",
                                name, assigned, max, utilization * 100, penalty));
            }
        }
        if (workloadDetails.isEmpty()) {
            workloadDetails.add("(all teachers below 80% utilization)");
        }
        details.put("Balance teacher workload", workloadDetails);

        // Limit non-BASICAS courses to at most 2 days per group (soft, weight 5) -
        // detailed
        List<String> nonBasicasDayDetails = new ArrayList<>();
        Map<String, Map<String, Set<DayOfWeek>>> groupCourseDays = new HashMap<>();
        Map<String, String> groupNames = new HashMap<>();
        Map<String, String> courseNames = new HashMap<>();
        Map<String, String> courseComponents = new HashMap<>();

        for (CourseAssignment a : list) {
            if (a.getTimeslot() == null || a.getCourse().getComponent() == null)
                continue;
            if (a.getCourse().getComponent().equalsIgnoreCase("BASICAS"))
                continue; // Only check non-BASICAS courses

            String groupKey = a.getGroup().getId();
            String courseKey = a.getCourse().getId();
            DayOfWeek day = a.getTimeslot().getDayOfWeek();

            groupCourseDays.computeIfAbsent(groupKey, k -> new HashMap<>())
                    .computeIfAbsent(courseKey, k -> new HashSet<>())
                    .add(day);

            groupNames.putIfAbsent(groupKey, a.getGroup().getName());
            courseNames.putIfAbsent(courseKey, a.getCourse().getName());
            courseComponents.putIfAbsent(courseKey, a.getCourse().getComponent());
        }

        for (Map.Entry<String, Map<String, Set<DayOfWeek>>> groupEntry : groupCourseDays.entrySet()) {
            String groupKey = groupEntry.getKey();
            String groupName = groupNames.getOrDefault(groupKey, groupKey);

            for (Map.Entry<String, Set<DayOfWeek>> courseEntry : groupEntry.getValue().entrySet()) {
                String courseKey = courseEntry.getKey();
                Set<DayOfWeek> days = courseEntry.getValue();

                if (days.size() > 2) {
                    String courseName = courseNames.getOrDefault(courseKey, courseKey);
                    String component = courseComponents.getOrDefault(courseKey, "UNKNOWN");
                    int excessDays = days.size() - 2;
                    int penalty = excessDays * 5;

                    // Format days list
                    List<String> dayNames = new ArrayList<>();
                    for (DayOfWeek d : days) {
                        dayNames.add(d.toString());
                    }
                    dayNames.sort(String::compareTo);

                    nonBasicasDayDetails.add(
                            String.format("%s - %s [%s]: %d days (%s), excess=%d, penalty=%d",
                                    groupName,
                                    courseName,
                                    component,
                                    days.size(),
                                    String.join(", ", dayNames),
                                    excessDays,
                                    penalty));
                }
            }
        }

        if (nonBasicasDayDetails.isEmpty()) {
            nonBasicasDayDetails.add("(all non-BASICAS courses scheduled in 1-2 days)");
        }
        details.put("Limit non-BASICAS courses to at most 2 days per group", nonBasicasDayDetails);

        return details;
    }

    private static String assignmentToString(CourseAssignment a) {
        String timeslot = a.getTimeslot() != null ? a.getTimeslot().toString() : "UNASSIGNED";
        String teacher = a.getTeacher() != null ? a.getTeacher().getName() : "UNASSIGNED";
        String room = a.getRoom() != null ? a.getRoom().getName() : "UNASSIGNED";
        return String.format("%s [%s] slot=%s teacher=%s room=%s",
                Integer.valueOf(System.identityHashCode(a)), a.getCourse().getName(), timeslot, teacher, room);
    }
}
