package com.example.solver;

import static org.junit.Assert.*;

import com.example.analysis.BlockScheduleAnalyzer;
import com.example.domain.*;
import org.junit.Test;

import java.time.DayOfWeek;
import java.util.*;

/**
 * Verifies "Teacher/Group must have a break after consecutive hours" (HARD):
 * a run of back-to-back blocks (zero idle time between them, same day) longer
 * than MAX_CONSECUTIVE_HOURS_WITHOUT_BREAK (4h) is a violation, sized to the
 * excess over the threshold. Exercised via {@link BlockScheduleAnalyzer},
 * which independently mirrors {@link SchoolConstraintProvider}'s rule.
 */
public class MaxConsecutiveHoursConstraintTest {

    private static final String TEACHER_CONSTRAINT = "Teacher must have a break after consecutive hours";
    private static final String GROUP_CONSTRAINT = "Group must have a break after consecutive hours";

    @Test
    public void fourStraightHoursIsNotAViolation() {
        // 7-8, 8-9, 9-10, 10-11: exactly at the 4h threshold
        int[] starts = { 7, 8, 9, 10 };
        assertEquals(0, teacherViolations(starts));
        assertEquals(0, groupViolations(starts));
    }

    @Test
    public void fiveStraightHoursIsAViolationOfOne() {
        // 7-8, 8-9, 9-10, 10-11, 11-12: 5h straight, 1h over the 4h threshold
        int[] starts = { 7, 8, 9, 10, 11 };
        assertEquals(1, teacherViolations(starts));
        assertEquals(1, groupViolations(starts));
    }

    @Test
    public void sixStraightHoursIsAViolationOfTwo() {
        int[] starts = { 7, 8, 9, 10, 11, 12 };
        assertEquals(2, teacherViolations(starts));
        assertEquals(2, groupViolations(starts));
    }

    @Test
    public void fiveHoursWithAGapInTheMiddleIsNotAViolation() {
        // 7-8, 8-9, 9-10 (3h run), gap at 10, 11-12, 12-13 (2h run): no run exceeds 4h
        int[] starts = { 7, 8, 9, 11, 12 };
        assertEquals(0, teacherViolations(starts));
        assertEquals(0, groupViolations(starts));
    }

    @Test
    public void overlappingBlocksDoNotDoubleCountTheRun() {
        // Two overlapping 3h blocks both starting at 7 (a transient
        // double-booked state the solver can freely explore mid-search,
        // before the double-booking constraint resolves it): the occupied
        // span is 7-10 (3h), not 6h from summing both lengths. Regression
        // test for the interval-merge fix - summing lengths caused a solver
        // score-corruption bug (order-dependent result for tied start hours).
        Teacher teacher = new Teacher("T1", "Test", "Teacher", new HashSet<>(), new HashMap<>(), 40);
        Room room = new Room("R1", "A", "Standard");
        Group group = new Group("G1", "Test Group", new HashSet<>());
        Course course = new Course("1", "Test Course", "TEST", 2, "Core", "Standard", 4, Boolean.TRUE);

        BlockTimeslot ts1 = new BlockTimeslot("slotA", DayOfWeek.MONDAY, 7, 3);
        BlockTimeslot ts2 = new BlockTimeslot("slotB", DayOfWeek.MONDAY, 7, 3);
        CourseBlockAssignment a1 = new CourseBlockAssignment("a1", group, course, 3);
        a1.setTimeslot(ts1);
        a1.setRoom(room);
        a1.setTeacher(teacher);
        CourseBlockAssignment a2 = new CourseBlockAssignment("a2", group, course, 3);
        a2.setTimeslot(ts2);
        a2.setRoom(room);
        a2.setTeacher(teacher);

        SchoolSchedule schedule = new SchoolSchedule(
                Collections.singletonList(teacher),
                List.of(ts1, ts2),
                Collections.singletonList(room),
                Collections.singletonList(course),
                Collections.singletonList(group),
                List.of(a1, a2));

        Map<String, Integer> hardViolations = BlockScheduleAnalyzer.analyzeHardConstraintViolations(schedule);
        // 3h occupied span, under the 4h threshold - no break-after-consecutive-hours violation.
        int violations = hardViolations.getOrDefault(TEACHER_CONSTRAINT, 0);
        assertEquals(0, violations);
    }

    @Test
    public void pinnedBlocksAreExcludedFromTheRun() {
        // Same 5-straight-hour shape as fiveStraightHoursIsAViolationOfOne, but
        // every block is pinned - excluded entirely, so no violation.
        int[] starts = { 7, 8, 9, 10, 11 };
        assertEquals(0, teacherViolations(starts, true));
        assertEquals(0, groupViolations(starts, true));
    }

    private int teacherViolations(int[] startHours) {
        return teacherViolations(startHours, false);
    }

    private int teacherViolations(int[] startHours, boolean pinned) {
        Teacher teacher = new Teacher("T1", "Test", "Teacher", new HashSet<>(), new HashMap<>(), 40);
        Room room = new Room("R1", "A", "Standard");
        Group group = new Group("G1", "Test Group", new HashSet<>());
        Course course = new Course("1", "Test Course", "TEST", 2, "Core", "Standard", 4, Boolean.TRUE);

        List<BlockTimeslot> timeslots = new ArrayList<>();
        List<CourseBlockAssignment> assignments = new ArrayList<>();
        for (int i = 0; i < startHours.length; i++) {
            BlockTimeslot ts = new BlockTimeslot("slot" + i, DayOfWeek.MONDAY, startHours[i], 1);
            timeslots.add(ts);
            CourseBlockAssignment a = new CourseBlockAssignment("a" + i, group, course, 1);
            a.setTimeslot(ts);
            a.setRoom(room);
            a.setTeacher(teacher);
            a.setPinned(pinned);
            assignments.add(a);
        }

        SchoolSchedule schedule = new SchoolSchedule(
                Collections.singletonList(teacher),
                timeslots,
                Collections.singletonList(room),
                Collections.singletonList(course),
                Collections.singletonList(group),
                assignments);

        Map<String, Integer> hardViolations = BlockScheduleAnalyzer.analyzeHardConstraintViolations(schedule);
        return hardViolations.getOrDefault(TEACHER_CONSTRAINT, 0);
    }

    private int groupViolations(int[] startHours) {
        return groupViolations(startHours, false);
    }

    private int groupViolations(int[] startHours, boolean pinned) {
        Teacher teacher = new Teacher("T1", "Test", "Teacher", new HashSet<>(), new HashMap<>(), 40);
        Room room = new Room("R1", "A", "Standard");
        Group group = new Group("G1", "Test Group", new HashSet<>());
        Course course = new Course("1", "Test Course", "TEST", 2, "Core", "Standard", 4, Boolean.TRUE);

        List<BlockTimeslot> timeslots = new ArrayList<>();
        List<CourseBlockAssignment> assignments = new ArrayList<>();
        for (int i = 0; i < startHours.length; i++) {
            BlockTimeslot ts = new BlockTimeslot("slot" + i, DayOfWeek.MONDAY, startHours[i], 1);
            timeslots.add(ts);
            CourseBlockAssignment a = new CourseBlockAssignment("a" + i, group, course, 1);
            a.setTimeslot(ts);
            a.setRoom(room);
            a.setPinned(pinned);
            assignments.add(a);
        }

        SchoolSchedule schedule = new SchoolSchedule(
                Collections.singletonList(teacher),
                timeslots,
                Collections.singletonList(room),
                Collections.singletonList(course),
                Collections.singletonList(group),
                assignments);

        Map<String, Integer> hardViolations = BlockScheduleAnalyzer.analyzeHardConstraintViolations(schedule);
        return hardViolations.getOrDefault(GROUP_CONSTRAINT, 0);
    }
}
