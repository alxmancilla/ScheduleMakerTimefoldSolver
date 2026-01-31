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

        // Teacher max hours per week (hard): sum of excess hours across all teachers
        Map<String, Integer> teacherCounts = new HashMap<>();
        for (CourseAssignment a : schedule.getCourseAssignments()) {
            if (a.getTeacher() == null)
                continue;
            String teacher = a.getTeacher().getName();
            teacherCounts.put(teacher, teacherCounts.getOrDefault(teacher, 0) + 1);
        }
        int totalExcess = 0;
        Map<String, Integer> teacherMax = new HashMap<>();
        for (CourseAssignment a : schedule.getCourseAssignments()) {
            if (a.getTeacher() == null)
                continue;
            String teacher = a.getTeacher().getName();
            teacherMax.putIfAbsent(teacher, a.getTeacher().getMaxHoursPerWeek());
        }
        for (Map.Entry<String, Integer> e : teacherCounts.entrySet()) {
            String teacher = e.getKey();
            int count = e.getValue();
            int max = teacherMax.getOrDefault(teacher, 20);
            if (count > max) {
                totalExcess += (count - max);
            }
        }
        result.put("Teacher exceeds max hours per week", totalExcess);

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

        // Teacher max hours per week (hard) - detailed offenders
        List<String> teacherMaxExcessDetails = new ArrayList<>();
        Map<String, Integer> teacherCounts = new HashMap<>();
        Map<String, Integer> teacherMax = new HashMap<>();
        for (CourseAssignment a : list) {
            if (a.getTeacher() == null)
                continue;
            String name = a.getTeacher().getName() + " " + a.getTeacher().getLastName();
            teacherCounts.put(name, teacherCounts.getOrDefault(name, 0) + 1);
            teacherMax.putIfAbsent(name, a.getTeacher().getMaxHoursPerWeek());
        }
        for (Map.Entry<String, Integer> e : teacherCounts.entrySet()) {
            String name = e.getKey();
            int count = e.getValue();
            int max = teacherMax.getOrDefault(name, 40);
            if (count > max) {
                int excess = count - max;
                teacherMaxExcessDetails.add(name + ": assigned=" + count + ", max=" + max + ", excess=" + excess);
            }
        }
        details.put("Teacher exceeds max hours per week", teacherMaxExcessDetails);

        return details;
    }

    // Soft constraint analysis
    public static Map<String, Integer> analyzeSoftConstraintViolations(SchoolSchedule schedule) {
        Map<String, Integer> result = new LinkedHashMap<>();
        List<CourseAssignment> list = schedule.getCourseAssignments();

        // Minimize teacher idle gaps (soft, weight 1): count gaps > 1 hour between
        // consecutive assignments
        int idleGaps = 0;
        Map<String, Map<DayOfWeek, List<Integer>>> teacherDayHours = new HashMap<>();
        for (CourseAssignment a : list) {
            if (a.getTeacher() == null || a.getTimeslot() == null)
                continue;
            String teacher = a.getTeacher().getName();
            DayOfWeek day = a.getTimeslot().getDayOfWeek();
            teacherDayHours.computeIfAbsent(teacher, k -> new HashMap<>()).computeIfAbsent(day, k -> new ArrayList<>())
                    .add(a.getTimeslot().getHour());
        }
        for (Map<DayOfWeek, List<Integer>> dayMap : teacherDayHours.values()) {
            for (List<Integer> hours : dayMap.values()) {
                Collections.sort(hours);
                // remove duplicates
                List<Integer> uniq = new ArrayList<>();
                for (Integer h : hours) {
                    if (uniq.isEmpty() || !uniq.get(uniq.size() - 1).equals(h))
                        uniq.add(h);
                }
                for (int i = 1; i < uniq.size(); i++) {
                    if (uniq.get(i) - uniq.get(i - 1) > 1)
                        idleGaps++;
                }
            }
        }
        result.put("Minimize teacher idle gaps", idleGaps);

        // Minimize teacher building changes (soft, weight 1): count adjacent
        // assignments on same day with different buildings
        int buildingChanges = 0;
        Map<String, Map<DayOfWeek, List<CourseAssignment>>> teacherDayAssignments = new HashMap<>();
        for (CourseAssignment a : list) {
            if (a.getTeacher() == null || a.getTimeslot() == null)
                continue;
            String teacher = a.getTeacher().getName();
            DayOfWeek day = a.getTimeslot().getDayOfWeek();
            teacherDayAssignments.computeIfAbsent(teacher, k -> new HashMap<>())
                    .computeIfAbsent(day, k -> new ArrayList<>())
                    .add(a);
        }
        for (Map<DayOfWeek, List<CourseAssignment>> dayMap : teacherDayAssignments.values()) {
            for (List<CourseAssignment> assigns : dayMap.values()) {
                assigns.sort(Comparator.comparing(a -> a.getTimeslot().getHour()));
                for (int i = 1; i < assigns.size(); i++) {
                    CourseAssignment prev = assigns.get(i - 1);
                    CourseAssignment cur = assigns.get(i);
                    if (prev.getRoom() != null && cur.getRoom() != null
                            && prev.getRoom().getBuilding() != null && cur.getRoom().getBuilding() != null
                            && !prev.getRoom().getBuilding().equals(cur.getRoom().getBuilding())) {
                        buildingChanges++;
                    }
                }
            }
        }
        result.put("Minimize teacher building changes", buildingChanges);

        // Prefer group's pre-assigned room (soft, weight 3)
        int preferredRoomSoft = 0;
        for (CourseAssignment a : list) {
            if (a.getGroup() == null || a.getGroup().getPreferredRoom() == null)
                continue;
            if ("lab".equalsIgnoreCase(a.getGroup().getPreferredRoom().getType()))
                continue;
            if (a.getRoom() == null || !a.getRoom().equals(a.getGroup().getPreferredRoom()))
                preferredRoomSoft++;
        }
        result.put("Prefer group's pre-assigned room", preferredRoomSoft);

        // Prefer assigning to lower-capacity teachers (dynamic reward)
        final int SCALE = 1000;
        int preferenceRewardTotal = 0;
        Map<String, Integer> teacherAssigned = new HashMap<>();
        Map<String, Integer> teacherMaxMap = new HashMap<>();
        for (CourseAssignment a : list) {
            if (a.getTeacher() == null)
                continue;
            String name = a.getTeacher().getName();
            teacherAssigned.put(name, teacherAssigned.getOrDefault(name, 0) + 1);
            teacherMaxMap.putIfAbsent(name, a.getTeacher().getMaxHoursPerWeek());
        }
        for (Map.Entry<String, Integer> e : teacherAssigned.entrySet()) {
            String name = e.getKey();
            int assigned = e.getValue();
            int max = Math.max(1, teacherMaxMap.getOrDefault(name, 20));
            int remaining = Math.max(0, max - assigned);
            double value = ((double) remaining * SCALE) / (double) (max * max);
            preferenceRewardTotal += (int) Math.round(value);
        }
        result.put("Prefer assigning to lower-capacity teachers (dynamic)", preferenceRewardTotal);

        return result;
    }

    public static Map<String, List<String>> analyzeSoftConstraintViolationsDetailed(SchoolSchedule schedule) {
        Map<String, List<String>> details = new LinkedHashMap<>();
        List<CourseAssignment> list = schedule.getCourseAssignments();

        // Minimize teacher idle gaps (detailed)
        List<String> idleGapDetails = new ArrayList<>();
        Map<String, Map<DayOfWeek, List<CourseAssignment>>> teacherDayAssign = new HashMap<>();
        for (CourseAssignment a : list) {
            if (a.getTeacher() == null || a.getTimeslot() == null)
                continue;
            String teacher = a.getTeacher().getName();
            DayOfWeek day = a.getTimeslot().getDayOfWeek();
            teacherDayAssign.computeIfAbsent(teacher, k -> new HashMap<>()).computeIfAbsent(day, k -> new ArrayList<>())
                    .add(a);
        }
        for (Map<DayOfWeek, List<CourseAssignment>> dayMap : teacherDayAssign.values()) {
            for (Map.Entry<DayOfWeek, List<CourseAssignment>> e : dayMap.entrySet()) {
                List<CourseAssignment> assigns = e.getValue();
                assigns.sort(Comparator.comparing(a -> a.getTimeslot().getHour()));
                List<Integer> seen = new ArrayList<>();
                for (CourseAssignment a : assigns) {
                    int h = a.getTimeslot().getHour();
                    if (seen.isEmpty() || seen.get(seen.size() - 1) != h)
                        seen.add(h);
                }
                for (int i = 1; i < seen.size(); i++) {
                    int gap = seen.get(i) - seen.get(i - 1) - 1;
                    if (gap > 0) {
                        CourseAssignment prev = assigns.get(i - 1);
                        CourseAssignment cur = assigns.get(i);
                        idleGapDetails.add(prev.getTeacher().getName() + " has gap of " + gap + " on "
                                + e.getKey() + ": " + assignmentToString(prev) + "  <->  " + assignmentToString(cur));
                    }
                }
            }
        }
        details.put("Minimize teacher idle gaps", idleGapDetails);

        // Building change details
        List<String> buildingChangeDetails = new ArrayList<>();
        for (Map<DayOfWeek, List<CourseAssignment>> dayMap : teacherDayAssign.values()) {
            for (List<CourseAssignment> assigns : dayMap.values()) {
                assigns.sort(Comparator.comparing(a -> a.getTimeslot().getHour()));
                for (int i = 1; i < assigns.size(); i++) {
                    CourseAssignment prev = assigns.get(i - 1);
                    CourseAssignment cur = assigns.get(i);
                    if (prev.getRoom() != null && cur.getRoom() != null
                            && prev.getRoom().getBuilding() != null && cur.getRoom().getBuilding() != null
                            && !prev.getRoom().getBuilding().equals(cur.getRoom().getBuilding())) {
                        buildingChangeDetails.add(prev.getTeacher().getName() + " building change on "
                                + prev.getTimeslot().getDayOfWeek() + ": " + assignmentToString(prev) + "  ->  "
                                + assignmentToString(cur));
                    }
                }
            }
        }
        details.put("Minimize teacher building changes", buildingChangeDetails);

        // Preferred room details (soft)
        List<String> preferredRoomDetails = new ArrayList<>();
        for (CourseAssignment a : list) {
            if (a.getGroup() == null || a.getGroup().getPreferredRoom() == null)
                continue;
            if ("lab".equalsIgnoreCase(a.getGroup().getPreferredRoom().getType()))
                continue;
            if (a.getRoom() == null || !a.getRoom().equals(a.getGroup().getPreferredRoom())) {
                preferredRoomDetails
                        .add(assignmentToString(a) + " preferred=" + a.getGroup().getPreferredRoom().getName());
            }
        }
        details.put("Prefer group's pre-assigned room", preferredRoomDetails);

        // Details for dynamic preference: prefer assigning to lower-capacity teachers
        final int SCALE = 1000;
        List<String> preferenceDetails = new ArrayList<>();
        Map<String, Integer> teacherAssigned = new HashMap<>();
        Map<String, Integer> teacherMaxMap = new HashMap<>();
        for (CourseAssignment a : list) {
            if (a.getTeacher() == null)
                continue;
            String name = a.getTeacher().getName();
            teacherAssigned.put(name, teacherAssigned.getOrDefault(name, 0) + 1);
            teacherMaxMap.putIfAbsent(name, a.getTeacher().getMaxHoursPerWeek());
        }
        for (Map.Entry<String, Integer> e : teacherAssigned.entrySet()) {
            String name = e.getKey();
            int assigned = e.getValue();
            int max = Math.max(1, teacherMaxMap.getOrDefault(name, 20));
            int remaining = Math.max(0, max - assigned);
            double value = ((double) remaining * SCALE) / (double) (max * max);
            int reward = (int) Math.round(value);
            preferenceDetails.add(name + ": assigned=" + assigned + ", max=" + max + ", remaining=" + remaining
                    + ", reward=" + reward);
        }
        if (preferenceDetails.isEmpty()) {
            preferenceDetails.add("(no assigned teachers)");
        }
        details.put("Prefer assigning to lower-capacity teachers (dynamic)", preferenceDetails);

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
