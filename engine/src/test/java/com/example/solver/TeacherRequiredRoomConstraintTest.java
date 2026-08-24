package com.example.solver;

import static org.junit.Assert.*;

import com.example.analysis.BlockScheduleAnalyzer;
import com.example.domain.*;
import org.junit.Test;

import java.time.DayOfWeek;
import java.util.*;

/**
 * Verifies "Teacher's required room must be used" (HARD): a violation when a
 * block's teacher has a {@code requiredRoomName} that doesn't match the
 * block's assigned room. Exercised via {@link BlockScheduleAnalyzer}, which
 * mirrors the same boolean logic as
 * {@link SchoolConstraintProvider#teacherRequiredRoomMustBeUsed}.
 *
 * <p>
 * Unlike most room-related HARD constraints, this one is NOT excluded for
 * pinned assignments - see the constraint's own Javadoc: a non-pinned
 * block's room is already structurally guaranteed correct by
 * {@code CourseBlockAssignment.getMatchingRooms()}, so in practice this only
 * ever fires for a pinned row (the case {@code TeacherController.
 * backfillRequiredRoom()} explicitly skips when a teacher's required room
 * changes). The tests below cover both pinned and non-pinned mismatches to
 * document that the constraint checks the data regardless of pinned status.
 * </p>
 */
public class TeacherRequiredRoomConstraintTest {

    private static final String CONSTRAINT_NAME = "Teacher's required room must be used";

    @Test
    public void pinnedBlockWithMismatchedRoomIsAViolation() {
        int violations = violationCountFor("ROOM1", "ROOM2", true);
        assertEquals(1, violations);
    }

    @Test
    public void nonPinnedBlockWithMismatchedRoomIsAlsoAViolation() {
        // Not structurally excluded for non-pinned blocks - this is a plain
        // data-integrity check, matching blockLengthMustMatchTimeslotLength's
        // own precedent, even though the solver's own value-range logic
        // should never actually produce this state for a movable block.
        int violations = violationCountFor("ROOM1", "ROOM2", false);
        assertEquals(1, violations);
    }

    @Test
    public void matchingRoomIsNotAViolation() {
        int violations = violationCountFor("ROOM1", "ROOM1", true);
        assertEquals(0, violations);
    }

    @Test
    public void teacherWithNoRequiredRoomIsNotAViolation() {
        int violations = violationCountFor(null, "ROOM2", true);
        assertEquals(0, violations);
    }

    private int violationCountFor(String requiredRoomName, String assignedRoomName, boolean pinned) {
        Teacher teacher = new Teacher("T1", "Test", "Teacher", new HashSet<>(), new HashMap<>(), 40);
        teacher.setRequiredRoomName(requiredRoomName);
        Course course = new Course("1", "Test Course", "TEST", 2, "BASICAS", "estándar", 4, Boolean.TRUE);
        Group group = new Group("G1", "Test Group", new HashSet<>());
        Room assignedRoom = new Room(assignedRoomName, "A", "estándar");
        BlockTimeslot timeslot = new BlockTimeslot("slot1", DayOfWeek.MONDAY, 7, 1);

        CourseBlockAssignment assignment = new CourseBlockAssignment("a1", group, course, 1);
        assignment.setTimeslot(timeslot);
        assignment.setTeacher(teacher);
        assignment.setRoom(assignedRoom);
        assignment.setPinned(pinned);

        SchoolSchedule schedule = new SchoolSchedule(
                Collections.singletonList(teacher),
                Collections.singletonList(timeslot),
                Collections.singletonList(assignedRoom),
                Collections.singletonList(course),
                Collections.singletonList(group),
                Collections.singletonList(assignment));

        Map<String, Integer> hardViolations = BlockScheduleAnalyzer.analyzeHardConstraintViolations(schedule);
        return hardViolations.getOrDefault(CONSTRAINT_NAME, 0);
    }
}
