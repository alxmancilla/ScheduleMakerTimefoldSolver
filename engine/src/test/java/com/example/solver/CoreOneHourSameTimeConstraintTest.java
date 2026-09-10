package com.example.solver;

import static org.junit.Assert.*;

import com.example.analysis.BlockScheduleAnalyzer;
import com.example.domain.*;
import org.junit.Test;

import java.time.DayOfWeek;
import java.util.*;

/**
 * Verifies "Prefer Core 1h blocks at the same time across days" (SOFT,
 * weight 2): for a Core course's 1-hour blocks (same group, one per day),
 * prefer they all land on the same start hour. Penalty is the deviation from
 * the most common ("mode") start hour, not a flat all-or-nothing penalty.
 * Exercised via {@link BlockScheduleAnalyzer}, which independently mirrors
 * {@link SchoolConstraintProvider}'s rule.
 *
 * TEMP DISABLED 2026-08-26 (per request) - see SchoolConstraintProvider and
 * BlockScheduleAnalyzer. The constraint no longer reports a value, so every
 * case below now asserts 0 via getOrDefault; re-enable by reverting those
 * assertions to their commented expected values below.
 */
public class CoreOneHourSameTimeConstraintTest {

    private static final String CONSTRAINT = "Prefer Core 1h blocks at the same time across days";

    @Test
    public void allSameHourIsNotAViolation() {
        int[] starts = { 8, 8, 8 };
        assertEquals(0, violations(starts, "Core", 1, false));
    }

    @Test
    public void oneOutlierIsAViolationOfOne() {
        // Mode is 8 (count 2), one block at 9 deviates.
        // TEMP DISABLED 2026-08-26: expected 1 when enabled.
        int[] starts = { 8, 8, 9 };
        assertEquals(0, violations(starts, "Core", 1, false));
    }

    @Test
    public void allDifferentHoursIsAViolationOfSizeMinusOne() {
        // No repeated hour: mode count is 1, so penalty = size - 1.
        // TEMP DISABLED 2026-08-26: expected 2 when enabled.
        int[] starts = { 7, 8, 9 };
        assertEquals(0, violations(starts, "Core", 1, false));
    }

    @Test
    public void singleBlockIsNotAViolation() {
        int[] starts = { 8 };
        assertEquals(0, violations(starts, "Core", 1, false));
    }

    @Test
    public void pinnedBlocksAreExcluded() {
        int[] starts = { 7, 8, 9 };
        assertEquals(0, violations(starts, "Core", 1, true));
    }

    @Test
    public void nonCoreDesignationIsExcluded() {
        int[] starts = { 7, 8, 9 };
        assertEquals(0, violations(starts, "Elective", 1, false));
    }

    @Test
    public void multiHourBlocksAreExcluded() {
        int[] starts = { 7, 8, 9 };
        assertEquals(0, violations(starts, "Core", 2, false));
    }

    private int violations(int[] startHours, String designation, int blockLength, boolean pinned) {
        Teacher teacher = new Teacher("T1", "Test", "Teacher", new HashSet<>(), new HashMap<>(), 40);
        Room room = new Room("R1", "A", "Standard");
        Group group = new Group("G1", "Test Group", new HashSet<>());
        Course course = new Course("1", "Test Course", "TEST", 2, designation, "Standard", 4, Boolean.TRUE);

        List<BlockTimeslot> timeslots = new ArrayList<>();
        List<CourseBlockAssignment> assignments = new ArrayList<>();
        for (int i = 0; i < startHours.length; i++) {
            BlockTimeslot ts = new BlockTimeslot("slot" + i, DayOfWeek.values()[i % 5], startHours[i], blockLength);
            timeslots.add(ts);
            CourseBlockAssignment a = new CourseBlockAssignment("a" + i, group, course, blockLength);
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

        Map<String, Integer> softViolations = BlockScheduleAnalyzer.analyzeSoftConstraintViolations(schedule);
        return softViolations.getOrDefault(CONSTRAINT, 0);
    }
}
