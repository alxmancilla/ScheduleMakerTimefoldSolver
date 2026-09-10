package com.example.solver;

import static org.junit.Assert.*;

import com.example.analysis.BlockScheduleAnalyzer;
import com.example.domain.*;
import org.junit.Test;

import java.time.DayOfWeek;
import java.util.*;

/**
 * Verifies the generalized, per-semester "must/should finish by hour X" rule
 * (semester_hour_limit table, sourced onto Course via DataLoader as
 * getLatestEndHour()/getLatestEndHourSeverity()) - the Tier 1.5 replacement
 * for the old hardcoded-to-semester-1 "First-semester blocks must finish by
 * 2pm". Unlike that rule, nothing here is implicit from semester == 1: every
 * test explicitly configures a Course's limit/severity, since a freshly
 * constructed Course (like DataLoader would build for an unconfigured
 * semester) has neither set and is therefore entirely unrestricted.
 *
 * Three severities are covered:
 * <ul>
 * <li>HARD - structurally excluded from the entity-scoped value range
 * (CourseBlockAssignment#getMatchingBlockTimeslots()), plus the HARD
 * constraint backstop for pinned rows (BlockScheduleMath#violatesHardSemesterHourLimit()).</li>
 * <li>SOFT - NOT excluded from the value range (the solver may still place a
 * block past the limit); penalized instead by the SOFT constraint, in
 * proportion to how far past the limit it ends (BlockScheduleMath#softSemesterHourLimitExcess()).</li>
 * <li>Unconfigured (no row for that semester) - no restriction of any kind,
 * regardless of which semester the course belongs to.</li>
 * </ul>
 *
 * The final test in each section configures two different semesters with two
 * different severities simultaneously, proving the actual "configure it by
 * semester, reuse the same mechanism" goal this generalization exists for.
 */
public class SemesterHourLimitConstraintTest {

    private static final String HARD_CONSTRAINT_NAME = "Semester hour limits must be respected (hard)";
    private static final String SOFT_CONSTRAINT_NAME = "Semester hour limits should be respected (soft)";

    // ---- HARD severity: structural value-range exclusion ----

    @Test
    public void hardSeverity_excludesTimeslotsEndingAfterLimit() {
        Course course = courseWithLimit(1, 14, "HARD");
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
    public void unconfiguredSemester_allowsTimeslotsEndingAtAnyHour() {
        // No setLatestEndHour/setLatestEndHourSeverity call at all - matches
        // what DataLoader leaves a course with when its semester has no row
        // in semester_hour_limit.
        Course course = new Course("1", "Test Course", "TEST", 3, "Core", "Standard", 4, Boolean.TRUE);
        Group group = new Group("G1", "Test Group", new HashSet<>());
        BlockTimeslot lateSlot = new BlockTimeslot("slot2", DayOfWeek.MONDAY, 13, 2); // ends 15:00

        CourseBlockAssignment assignment = new CourseBlockAssignment("a1", group, course, 2);
        assignment.setAllTimeslots(List.of(lateSlot));

        List<BlockTimeslot> matching = assignment.getMatchingBlockTimeslots();
        assertTrue(matching.contains(lateSlot));
    }

    // ---- HARD severity: constraint backstop for pinned rows ----

    @Test
    public void pinnedHardBlockEndingAfterLimitIsAViolation() {
        int violations = hardViolationCountFor(1, 14, "HARD", 13, 2, true); // 13-15, limit 14
        assertEquals(1, violations);
    }

    @Test
    public void pinnedHardBlockEndingAtLimitIsNotAViolation() {
        int violations = hardViolationCountFor(1, 14, "HARD", 12, 2, true); // 12-14, limit 14
        assertEquals(0, violations);
    }

    @Test
    public void nonPinnedHardBlockEndingAfterLimitIsAlsoAViolation() {
        // Not structurally excluded for non-pinned blocks - a plain
        // data-integrity check, matching teacherRequiredRoomMustBeUsed's own
        // precedent, even though the solver's own value-range logic should
        // never actually produce this state for a movable block.
        int violations = hardViolationCountFor(1, 14, "HARD", 13, 2, false); // 13-15, limit 14
        assertEquals(1, violations);
    }

    @Test
    public void unassignedHardBlockIsNotAViolation() {
        Course course = courseWithLimit(1, 14, "HARD");
        Group group = new Group("G1", "Test Group", new HashSet<>());

        CourseBlockAssignment assignment = new CourseBlockAssignment("a1", group, course, 2);
        assignment.setPinned(false);

        SchoolSchedule schedule = new SchoolSchedule(
                Collections.emptyList(), Collections.emptyList(), Collections.emptyList(),
                Collections.singletonList(course), Collections.singletonList(group),
                Collections.singletonList(assignment));

        Map<String, Integer> hardViolations = BlockScheduleAnalyzer.analyzeHardConstraintViolations(schedule);
        assertEquals(0, hardViolations.getOrDefault(HARD_CONSTRAINT_NAME, 0).intValue());
    }

    // ---- SOFT severity: value range NOT restricted, penalized instead ----

    @Test
    public void softSeverity_doesNotExcludeTimeslotsEndingAfterLimit() {
        Course course = courseWithLimit(5, 14, "SOFT");
        Group group = new Group("G1", "Test Group", new HashSet<>());
        BlockTimeslot lateSlot = new BlockTimeslot("slot2", DayOfWeek.MONDAY, 13, 2); // ends 15:00, limit 14

        CourseBlockAssignment assignment = new CourseBlockAssignment("a1", group, course, 2);
        assignment.setAllTimeslots(List.of(lateSlot));

        List<BlockTimeslot> matching = assignment.getMatchingBlockTimeslots();
        assertTrue("SOFT severity must not structurally exclude late timeslots - the solver "
                + "has to be able to reach them for the soft constraint to penalize", matching.contains(lateSlot));
    }

    @Test
    public void nonPinnedSoftBlockEndingAfterLimitIsPenalizedByDeviation() {
        // Ends at 16:00 against a 14:00 limit - 2 hours of excess, not a flat
        // penalty, mirroring preferSemesterOneBlocksStartEarly's own
        // deviation-based gradient.
        int excess = softViolationExcessFor(5, 14, "SOFT", 14, 2, false); // 14-16
        assertEquals(2, excess);
    }

    @Test
    public void nonPinnedSoftBlockEndingAtLimitIsNotPenalized() {
        int excess = softViolationExcessFor(5, 14, "SOFT", 12, 2, false); // 12-14
        assertEquals(0, excess);
    }

    @Test
    public void pinnedSoftBlockIsNotPenalized() {
        // Soft constraints exclude pinned assignments - fixed from the
        // database, not something the solver can improve.
        int excess = softViolationExcessFor(5, 14, "SOFT", 14, 2, true); // 14-16, but pinned
        assertEquals(0, excess);
    }

    @Test
    public void softBlockNeverReachesTheHardConstraint() {
        int violations = hardViolationCountFor(5, 14, "SOFT", 14, 2, true); // 14-16, pinned
        assertEquals("A SOFT-severity limit must never be reported by the HARD constraint",
                0, violations);
    }

    // ---- The actual goal: two semesters, two severities, simultaneously ----

    @Test
    public void differentSemestersCanHaveIndependentSeverities() {
        Course semesterOneCourse = courseWithLimit(1, 14, "HARD");
        Course semesterFiveCourse = courseWithLimit(5, 14, "SOFT");
        Group group = new Group("G1", "Test Group", new HashSet<>());
        Room room = new Room("R1", "A", "Standard");

        BlockTimeslot lateSlot1 = new BlockTimeslot("slot1", DayOfWeek.MONDAY, 13, 2); // 13-15
        CourseBlockAssignment hardAssignment = new CourseBlockAssignment("a1", group, semesterOneCourse, 2);
        hardAssignment.setTimeslot(lateSlot1);
        hardAssignment.setRoom(room);
        hardAssignment.setPinned(true);

        BlockTimeslot lateSlot2 = new BlockTimeslot("slot2", DayOfWeek.TUESDAY, 13, 2); // 13-15
        CourseBlockAssignment softAssignment = new CourseBlockAssignment("a2", group, semesterFiveCourse, 2);
        softAssignment.setTimeslot(lateSlot2);
        softAssignment.setRoom(room);
        softAssignment.setPinned(false);

        SchoolSchedule schedule = new SchoolSchedule(
                Collections.emptyList(), List.of(lateSlot1, lateSlot2), Collections.singletonList(room),
                List.of(semesterOneCourse, semesterFiveCourse), Collections.singletonList(group),
                List.of(hardAssignment, softAssignment));

        Map<String, Integer> hardViolations = BlockScheduleAnalyzer.analyzeHardConstraintViolations(schedule);
        Map<String, Integer> softViolations = BlockScheduleAnalyzer.analyzeSoftConstraintViolations(schedule);

        // Exactly 1 hard violation - semester 1's HARD-configured block only.
        // Semester 5's SOFT-configured block, even though it also ends past
        // its own limit, must never be counted here.
        assertEquals(1, hardViolations.getOrDefault(HARD_CONSTRAINT_NAME, 0).intValue());
        // Exactly 1 hour of soft excess - semester 5's block ends at 15:00
        // against its 14:00 limit. Semester 1's HARD-configured block, even
        // though it also ends past its own limit, must never be counted here.
        assertEquals(1, softViolations.getOrDefault(SOFT_CONSTRAINT_NAME, 0).intValue());
    }

    private Course courseWithLimit(int semester, int latestEndHour, String severity) {
        Course course = new Course("1", "Test Course", "TEST", semester, "Core", "Standard", 4, Boolean.TRUE);
        course.setLatestEndHour(latestEndHour);
        course.setLatestEndHourSeverity(severity);
        return course;
    }

    private int hardViolationCountFor(int semester, int latestEndHour, String severity, int startHour, int length,
            boolean pinned) {
        Course course = courseWithLimit(semester, latestEndHour, severity);
        Group group = new Group("G1", "Test Group", new HashSet<>());
        BlockTimeslot timeslot = new BlockTimeslot("slot1", DayOfWeek.MONDAY, startHour, length);
        Room room = new Room("R1", "A", "Standard");

        CourseBlockAssignment assignment = new CourseBlockAssignment("a1", group, course, length);
        assignment.setTimeslot(timeslot);
        assignment.setRoom(room);
        assignment.setPinned(pinned);

        SchoolSchedule schedule = new SchoolSchedule(
                Collections.emptyList(), Collections.singletonList(timeslot), Collections.singletonList(room),
                Collections.singletonList(course), Collections.singletonList(group),
                Collections.singletonList(assignment));

        Map<String, Integer> hardViolations = BlockScheduleAnalyzer.analyzeHardConstraintViolations(schedule);
        return hardViolations.getOrDefault(HARD_CONSTRAINT_NAME, 0);
    }

    private int softViolationExcessFor(int semester, int latestEndHour, String severity, int startHour, int length,
            boolean pinned) {
        Course course = courseWithLimit(semester, latestEndHour, severity);
        Group group = new Group("G1", "Test Group", new HashSet<>());
        BlockTimeslot timeslot = new BlockTimeslot("slot1", DayOfWeek.MONDAY, startHour, length);
        Room room = new Room("R1", "A", "Standard");

        CourseBlockAssignment assignment = new CourseBlockAssignment("a1", group, course, length);
        assignment.setTimeslot(timeslot);
        assignment.setRoom(room);
        assignment.setPinned(pinned);

        SchoolSchedule schedule = new SchoolSchedule(
                Collections.emptyList(), Collections.singletonList(timeslot), Collections.singletonList(room),
                Collections.singletonList(course), Collections.singletonList(group),
                Collections.singletonList(assignment));

        Map<String, Integer> softViolations = BlockScheduleAnalyzer.analyzeSoftConstraintViolations(schedule);
        return softViolations.getOrDefault(SOFT_CONSTRAINT_NAME, 0);
    }
}
