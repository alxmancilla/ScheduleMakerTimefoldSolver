package com.example.domain;

import org.junit.Test;

import java.time.DayOfWeek;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;

/**
 * Verifies {@link CourseBlockAssignment#getPinnedOccupiedTimeslots()} and its
 * effect on {@link CourseBlockAssignment#getMatchingBlockTimeslots()}: a
 * non-pinned block's own teacher/group pinned-occupied timeslots must be
 * structurally excluded from its own value range, the same "unreachable, not
 * just scored" treatment already given to block-length mismatches and the
 * HARD semester hour limit.
 */
public class CourseBlockAssignmentPinnedOccupancyTest {

    private static final Course COURSE = new Course("1", "Test Course", "TEST", 2, "BASICAS", "estándar", 4,
            Boolean.TRUE);

    private static final List<BlockTimeslot> ALL_TIMESLOTS = List.of(
            new BlockTimeslot("mon7", DayOfWeek.MONDAY, 7, 2),
            new BlockTimeslot("mon9", DayOfWeek.MONDAY, 9, 2),
            new BlockTimeslot("tue7", DayOfWeek.TUESDAY, 7, 2),
            new BlockTimeslot("tue9", DayOfWeek.TUESDAY, 9, 2));

    private static Teacher teacher(String id) {
        return new Teacher(id, "Teacher", id, new HashSet<>(), new HashMap<>(), 40);
    }

    private static Group group(String id) {
        return new Group(id, "Group " + id, new HashSet<>());
    }

    private CourseBlockAssignment movableAssignment(String id, Teacher teacher, Group group,
            Map<String, List<BlockTimeslot>> pinnedByTeacher, Map<String, List<BlockTimeslot>> pinnedByGroup) {
        CourseBlockAssignment a = new CourseBlockAssignment(id, group, COURSE, 2);
        a.setTeacher(teacher);
        a.setAllTimeslots(ALL_TIMESLOTS);
        a.setPinnedTimeslotsByTeacherId(pinnedByTeacher);
        a.setPinnedTimeslotsByGroupId(pinnedByGroup);
        a.setPinned(false);
        return a;
    }

    @Test
    public void noPinnedOccupancy_allMatchingLengthTimeslotsAvailable() {
        CourseBlockAssignment a = movableAssignment("a1", teacher("T1"), group("G1"), Map.of(), Map.of());

        assertEquals(ALL_TIMESLOTS, a.getMatchingBlockTimeslots());
        assertTrue(a.getPinnedOccupiedTimeslots().isEmpty());
    }

    @Test
    public void teacherPinnedElsewhere_excludesOverlappingTimeslot() {
        BlockTimeslot teacherPinnedAt = ALL_TIMESLOTS.get(0); // mon7 (Mon 7-9)
        Map<String, List<BlockTimeslot>> pinnedByTeacher = Map.of("T1", List.of(teacherPinnedAt));

        CourseBlockAssignment a = movableAssignment("a1", teacher("T1"), group("G1"), pinnedByTeacher, Map.of());

        assertEquals(List.of(teacherPinnedAt), a.getPinnedOccupiedTimeslots());
        assertFalse(a.getMatchingBlockTimeslots().contains(teacherPinnedAt));
        assertEquals(ALL_TIMESLOTS.size() - 1, a.getMatchingBlockTimeslots().size());
    }

    @Test
    public void groupPinnedElsewhere_excludesOverlappingTimeslot() {
        BlockTimeslot groupPinnedAt = ALL_TIMESLOTS.get(2); // tue7 (Tue 7-9)
        Map<String, List<BlockTimeslot>> pinnedByGroup = Map.of("G1", List.of(groupPinnedAt));

        CourseBlockAssignment a = movableAssignment("a1", teacher("T1"), group("G1"), Map.of(), pinnedByGroup);

        assertFalse(a.getMatchingBlockTimeslots().contains(groupPinnedAt));
        assertEquals(ALL_TIMESLOTS.size() - 1, a.getMatchingBlockTimeslots().size());
    }

    @Test
    public void differentTeacherOrGroupPinnedOccupancy_doesNotAffectThisAssignment() {
        // T2's / G2's pinned commitments shouldn't touch T1's / G1's assignment.
        Map<String, List<BlockTimeslot>> pinnedByTeacher = Map.of("T2", List.of(ALL_TIMESLOTS.get(0)));
        Map<String, List<BlockTimeslot>> pinnedByGroup = Map.of("G2", List.of(ALL_TIMESLOTS.get(1)));

        CourseBlockAssignment a = movableAssignment("a1", teacher("T1"), group("G1"), pinnedByTeacher, pinnedByGroup);

        assertEquals(ALL_TIMESLOTS, a.getMatchingBlockTimeslots());
        assertTrue(a.getPinnedOccupiedTimeslots().isEmpty());
    }

    @Test
    public void pinnedAssignment_ownValueRangeIgnoresOccupancy() {
        // A pinned assignment's own timeslot/room are never reassigned by the
        // solver (Timefold's @PlanningPin), so its own occupancy exclusion is
        // irrelevant to it - even if (implausibly) its own teacher/group also
        // appears in the pinned-occupancy maps, this must return empty.
        BlockTimeslot teacherPinnedAt = ALL_TIMESLOTS.get(0);
        Map<String, List<BlockTimeslot>> pinnedByTeacher = Map.of("T1", List.of(teacherPinnedAt));

        CourseBlockAssignment a = movableAssignment("a1", teacher("T1"), group("G1"), pinnedByTeacher, Map.of());
        a.setPinned(true);

        assertTrue(a.getPinnedOccupiedTimeslots().isEmpty());
    }

    @Test
    public void allValidTimeslotsExcluded_valueRangeBecomesEmpty() {
        // Both matching-length timeslots for this teacher's day are already
        // pinned elsewhere - nothing legal remains at all.
        Map<String, List<BlockTimeslot>> pinnedByTeacher = Map.of("T1",
                List.of(ALL_TIMESLOTS.get(0), ALL_TIMESLOTS.get(1), ALL_TIMESLOTS.get(2), ALL_TIMESLOTS.get(3)));

        CourseBlockAssignment a = movableAssignment("a1", teacher("T1"), group("G1"), pinnedByTeacher, Map.of());

        assertTrue(a.getMatchingBlockTimeslots().isEmpty());
    }
}
