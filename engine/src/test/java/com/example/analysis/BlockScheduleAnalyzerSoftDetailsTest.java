package com.example.analysis;

import com.example.domain.BlockTimeslot;
import com.example.domain.Course;
import com.example.domain.CourseBlockAssignment;
import com.example.domain.Group;
import com.example.domain.Room;
import com.example.domain.SchoolSchedule;
import com.example.domain.Teacher;
import org.junit.Test;

import java.time.DayOfWeek;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Verifies analyzeSoftConstraintViolationsDetailed() covers every ACTIVE soft
 * constraint, not just the 3 it originally had (Teacher exceeds max hours per
 * week / Prefer block's specified room / Non-standard rooms should finish by
 * 2pm) - added 2026-09-06 after discovering 6 of the 9 active soft
 * constraints had aggregate counts (analyzeSoftConstraintViolations) but no
 * per-instance description, silently undercounting what the web Schedule
 * view's violations panel could show. Each test below checks the detailed
 * list's size against the SAME scenario's aggregate count where the
 * constraint counts one violation per instance; the two adjacent-gap-summing
 * constraints (teacher/semester-one idle gaps) check the description content
 * directly instead, since their aggregate count is a summed hour total, not
 * an instance count.
 */
public class BlockScheduleAnalyzerSoftDetailsTest {

    private static SchoolSchedule scheduleOf(List<Teacher> teachers, List<BlockTimeslot> timeslots,
            List<Room> rooms, List<Course> courses, List<Group> groups, List<CourseBlockAssignment> assignments) {
        return new SchoolSchedule(teachers, timeslots, rooms, courses, groups, assignments);
    }

    private static Map<String, List<ViolationInstance>> softDetails(SchoolSchedule schedule) {
        return BlockScheduleAnalyzer.analyzeSoftConstraintViolationsDetailed(schedule);
    }

    private static Map<String, Integer> softCounts(SchoolSchedule schedule) {
        return BlockScheduleAnalyzer.analyzeSoftConstraintViolations(schedule);
    }

    // ---- Prefer group's preferred room ----

    @Test
    public void preferredRoom_outOfCuratedRange_oneDetailPerInstance() {
        Room inRange = new Room("R1", "A", "Standard");
        Room outOfRange = new Room("R2", "A", "Standard");
        Map<String, List<Room>> ranges = new HashMap<>();
        ranges.put("Standard", List.of(inRange, new Room("R3", "A", "Standard")));
        Group group = new Group("G1", "Test Group", new HashSet<>(), ranges);
        Course course = new Course("1", "Test Course", "TEST", 2, "Core", "Standard", 4, Boolean.TRUE);
        BlockTimeslot ts = new BlockTimeslot("slot1", DayOfWeek.MONDAY, 7, 1);

        CourseBlockAssignment a = new CourseBlockAssignment("a1", group, course, 1);
        a.setTimeslot(ts);
        a.setRoom(outOfRange);
        a.setSatisfiesRoomType("Standard");
        a.setPinned(false);

        SchoolSchedule schedule = scheduleOf(List.of(), List.of(ts), List.of(inRange, outOfRange),
                List.of(course), List.of(group), List.of(a));

        String name = "Prefer group's preferred room";
        assertEquals(softCounts(schedule).get(name).intValue(), softDetails(schedule).get(name).size());
        assertEquals(1, softDetails(schedule).get(name).size());
    }

    // ---- Room capacity should fit group size ----

    @Test
    public void roomCapacity_groupLargerThanRoom_oneDetailPerInstance() {
        Room room = new Room("R1", "A", "Standard", 20);
        Group group = new Group("G1", "Test Group", new HashSet<>(), null, 25);
        Course course = new Course("1", "Test Course", "TEST", 2, "Core", "Standard", 4, Boolean.TRUE);
        BlockTimeslot ts = new BlockTimeslot("slot1", DayOfWeek.MONDAY, 7, 1);

        CourseBlockAssignment a = new CourseBlockAssignment("a1", group, course, 1);
        a.setTimeslot(ts);
        a.setRoom(room);

        SchoolSchedule schedule = scheduleOf(List.of(), List.of(ts), List.of(room), List.of(course),
                List.of(group), List.of(a));

        String name = "Room capacity should fit group size";
        assertEquals(1, softCounts(schedule).get(name).intValue());
        assertEquals(1, softDetails(schedule).get(name).size());
        assertTrue(softDetails(schedule).get(name).get(0).description().contains("capacity=20"));
    }

    // ---- Minimize teacher idle gaps (availability-aware) ----

    @Test
    public void teacherIdleGaps_oneAdjacentPairGap_oneDescription() {
        Map<DayOfWeek, Set<Integer>> avail = new HashMap<>();
        avail.put(DayOfWeek.MONDAY, new HashSet<>(List.of(7, 8, 9, 10, 11)));
        Teacher teacher = new Teacher("T1", "Ada", "Lovelace", new HashSet<>(), avail, 40);
        Group group = new Group("G1", "Test Group", new HashSet<>());
        Course course = new Course("1", "Test Course", "TEST", 2, "Core", "Standard", 4, Boolean.TRUE);
        BlockTimeslot ts1 = new BlockTimeslot("slot1", DayOfWeek.MONDAY, 7, 1);
        BlockTimeslot ts2 = new BlockTimeslot("slot2", DayOfWeek.MONDAY, 10, 1);

        CourseBlockAssignment a1 = new CourseBlockAssignment("a1", group, course, 1);
        a1.setTimeslot(ts1);
        a1.setTeacher(teacher);
        CourseBlockAssignment a2 = new CourseBlockAssignment("a2", group, course, 1);
        a2.setTimeslot(ts2);
        a2.setTeacher(teacher);

        SchoolSchedule schedule = scheduleOf(List.of(teacher), List.of(ts1, ts2), List.of(), List.of(course),
                List.of(group), List.of(a1, a2));

        String name = "Minimize teacher idle gaps (availability-aware)";
        assertEquals(2, softCounts(schedule).get(name).intValue()); // hours 8,9 idle
        List<ViolationInstance> details = softDetails(schedule).get(name);
        assertEquals(1, details.size()); // one adjacent-pair description
        assertTrue(details.get(0).description().contains("available idle gap=2 hours"));
    }

    // ---- Prefer first-semester blocks to start early ----

    @Test
    public void semesterOneStartEarly_lateStart_oneDetailNamingTheEarliestBlock() {
        Group group = new Group("G1", "Test Group", new HashSet<>());
        Course course = new Course("1", "Test Course", "TEST", 1, "Core", "Standard", 4, Boolean.TRUE);
        BlockTimeslot ts = new BlockTimeslot("slot1", DayOfWeek.MONDAY, 9, 1); // starts at 9, not 7

        CourseBlockAssignment a = new CourseBlockAssignment("a1", group, course, 1);
        a.setTimeslot(ts);
        a.setPinned(false);

        SchoolSchedule schedule = scheduleOf(List.of(), List.of(ts), List.of(), List.of(course),
                List.of(group), List.of(a));

        String name = "Prefer first-semester blocks to start early";
        assertEquals(2, softCounts(schedule).get(name).intValue()); // 9 - 7 = 2
        List<ViolationInstance> details = softDetails(schedule).get(name);
        assertEquals(1, details.size());
        assertTrue(details.get(0).description().contains("starts at 9:00"));
    }

    // ---- Minimize first-semester group idle gaps ----

    @Test
    public void semesterOneIdleGaps_bothBlocksSemesterOne_oneDescription() {
        Group group = new Group("G1", "Test Group", new HashSet<>());
        Course course = new Course("1", "Test Course", "TEST", 1, "Core", "Standard", 4, Boolean.TRUE);
        BlockTimeslot ts1 = new BlockTimeslot("slot1", DayOfWeek.MONDAY, 7, 1);
        BlockTimeslot ts2 = new BlockTimeslot("slot2", DayOfWeek.MONDAY, 9, 1); // gap hour 8

        CourseBlockAssignment a1 = new CourseBlockAssignment("a1", group, course, 1);
        a1.setTimeslot(ts1);
        CourseBlockAssignment a2 = new CourseBlockAssignment("a2", group, course, 1);
        a2.setTimeslot(ts2);

        SchoolSchedule schedule = scheduleOf(List.of(), List.of(ts1, ts2), List.of(), List.of(course),
                List.of(group), List.of(a1, a2));

        String name = "Minimize first-semester group idle gaps";
        assertEquals(1, softCounts(schedule).get(name).intValue());
        List<ViolationInstance> details = softDetails(schedule).get(name);
        assertEquals(1, details.size());
        assertTrue(details.get(0).description().contains("gap=1 hours"));
    }

    // ---- Semester hour limits should be respected (soft) ----

    @Test
    public void semesterHourLimitSoft_exceeded_oneDetailPerInstance() {
        Course course = new Course("1", "Test Course", "TEST", 5, "Core", "Standard", 4, Boolean.TRUE);
        course.setLatestEndHour(14);
        course.setLatestEndHourSeverity("SOFT");
        Group group = new Group("G1", "Test Group", new HashSet<>());
        BlockTimeslot ts = new BlockTimeslot("slot1", DayOfWeek.MONDAY, 13, 2); // ends 15:00, 1h over

        CourseBlockAssignment a = new CourseBlockAssignment("a1", group, course, 2);
        a.setTimeslot(ts);
        a.setPinned(false);

        SchoolSchedule schedule = scheduleOf(List.of(), List.of(ts), List.of(), List.of(course),
                List.of(group), List.of(a));

        String name = "Semester hour limits should be respected (soft)";
        assertEquals(1, softCounts(schedule).get(name).intValue());
        List<ViolationInstance> details = softDetails(schedule).get(name);
        assertEquals(1, details.size());
        assertTrue(details.get(0).description().contains("1 hour(s) past semester 5's soft limit"));
    }

    // ---- Full coverage: every active soft constraint has SOME detail entry (a list, even if empty) ----

    @Test
    public void everyActiveSoftConstraint_hasADetailedEntryInTheMap() {
        SchoolSchedule empty = scheduleOf(List.of(), List.of(), List.of(), List.of(), List.of(),
                new ArrayList<>());
        Map<String, List<ViolationInstance>> details = softDetails(empty);
        for (String constraintName : softCounts(empty).keySet()) {
            assertTrue("missing detailed entry for: " + constraintName, details.containsKey(constraintName));
        }
    }
}
