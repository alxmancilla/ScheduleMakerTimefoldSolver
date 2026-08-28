package com.example.solver;

import static org.junit.Assert.*;

import com.example.analysis.BlockScheduleAnalyzer;
import com.example.domain.*;
import org.junit.Test;

import java.time.DayOfWeek;
import java.util.*;

/**
 * Verifies "First-semester blocks must finish by 2pm" (HARD): a first-semester
 * (semester == 1) course's blocks may never be assigned a timeslot whose end
 * hour (start + length) runs past 14:00.
 *
 * <p>
 * This is enforced two ways, both covered below:
 * <ul>
 * <li>Structurally, for movable blocks: {@link CourseBlockAssignment#getMatchingBlockTimeslots()}
 * excludes any such timeslot from the entity-scoped value range, so the
 * solver can never pick one in the first place.</li>
 * <li>As a HARD constraint ({@link SchoolConstraintProvider#semesterOneBlocksMustFinishBy2pm}),
 * a plain data-integrity check NOT excluded for pinned assignments - the same
 * precedent as {@code teacherRequiredRoomMustBeUsed} - since a pinned row's
 * timeslot could predate this rule (or predate its course's semester being
 * set to 1) and the structural filter above never applies to pinned rows.</li>
 * </ul>
 */
public class SemesterOneFinishBy2pmConstraintTest {

    private static final String CONSTRAINT_NAME = "First-semester blocks must finish by 2pm";

    // ---- Structural: entity-scoped value range ----

    @Test
    public void semesterOneBlock_excludesTimeslotsEndingAfter2pm() {
        Course course = new Course("1", "Test Course", "TEST", 1, "Core", "Standard", 4, Boolean.TRUE);
        Group group = new Group("G1", "Test Group", new HashSet<>());
        BlockTimeslot okSlot = new BlockTimeslot("slot1", DayOfWeek.MONDAY, 12, 2); // ends 14:00 - allowed
        BlockTimeslot lateSlot = new BlockTimeslot("slot2", DayOfWeek.MONDAY, 13, 2); // ends 15:00 - excluded

        CourseBlockAssignment assignment = new CourseBlockAssignment("a1", group, course, 2);
        assignment.setAllTimeslots(List.of(okSlot, lateSlot));

        List<BlockTimeslot> matching = assignment.getMatchingBlockTimeslots();
        assertTrue(matching.contains(okSlot));
        assertFalse(matching.contains(lateSlot));
    }

    @Test
    public void nonSemesterOneBlock_stillAllowsTimeslotsEndingAfter2pm() {
        Course course = new Course("1", "Test Course", "TEST", 3, "Core", "Standard", 4, Boolean.TRUE);
        Group group = new Group("G1", "Test Group", new HashSet<>());
        BlockTimeslot lateSlot = new BlockTimeslot("slot2", DayOfWeek.MONDAY, 13, 2); // ends 15:00

        CourseBlockAssignment assignment = new CourseBlockAssignment("a1", group, course, 2);
        assignment.setAllTimeslots(List.of(lateSlot));

        List<BlockTimeslot> matching = assignment.getMatchingBlockTimeslots();
        assertTrue(matching.contains(lateSlot));
    }

    // ---- HARD constraint: data-integrity check for pinned rows ----

    @Test
    public void pinnedSemesterOneBlockEndingAfter2pmIsAViolation() {
        int violations = violationCountFor(1, 13, 2, true); // 13-15
        assertEquals(1, violations);
    }

    @Test
    public void pinnedSemesterOneBlockEndingAt2pmIsNotAViolation() {
        int violations = violationCountFor(1, 12, 2, true); // 12-14
        assertEquals(0, violations);
    }

    @Test
    public void nonPinnedSemesterOneBlockEndingAfter2pmIsAlsoAViolation() {
        // Not structurally excluded for non-pinned blocks - a plain
        // data-integrity check, matching teacherRequiredRoomMustBeUsed's own
        // precedent, even though the solver's own value-range logic should
        // never actually produce this state for a movable block.
        int violations = violationCountFor(1, 13, 2, false); // 13-15
        assertEquals(1, violations);
    }

    @Test
    public void nonSemesterOneBlockEndingAfter2pmIsNotAViolation() {
        int violations = violationCountFor(3, 13, 2, true); // semester 3, 13-15
        assertEquals(0, violations);
    }

    @Test
    public void unassignedSemesterOneBlockIsNotAViolation() {
        Course course = new Course("1", "Test Course", "TEST", 1, "Core", "Standard", 4, Boolean.TRUE);
        Group group = new Group("G1", "Test Group", new HashSet<>());

        CourseBlockAssignment assignment = new CourseBlockAssignment("a1", group, course, 2);
        assignment.setPinned(false);

        SchoolSchedule schedule = new SchoolSchedule(
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.emptyList(),
                Collections.singletonList(course),
                Collections.singletonList(group),
                Collections.singletonList(assignment));

        Map<String, Integer> hardViolations = BlockScheduleAnalyzer.analyzeHardConstraintViolations(schedule);
        assertEquals(0, hardViolations.getOrDefault(CONSTRAINT_NAME, 0).intValue());
    }

    private int violationCountFor(int semester, int startHour, int length, boolean pinned) {
        Course course = new Course("1", "Test Course", "TEST", semester, "Core", "Standard", 4, Boolean.TRUE);
        Group group = new Group("G1", "Test Group", new HashSet<>());
        BlockTimeslot timeslot = new BlockTimeslot("slot1", DayOfWeek.MONDAY, startHour, length);
        Room room = new Room("R1", "A", "Standard");

        CourseBlockAssignment assignment = new CourseBlockAssignment("a1", group, course, length);
        assignment.setTimeslot(timeslot);
        assignment.setRoom(room);
        assignment.setPinned(pinned);

        SchoolSchedule schedule = new SchoolSchedule(
                Collections.emptyList(),
                Collections.singletonList(timeslot),
                Collections.singletonList(room),
                Collections.singletonList(course),
                Collections.singletonList(group),
                Collections.singletonList(assignment));

        Map<String, Integer> hardViolations = BlockScheduleAnalyzer.analyzeHardConstraintViolations(schedule);
        return hardViolations.getOrDefault(CONSTRAINT_NAME, 0);
    }
}
