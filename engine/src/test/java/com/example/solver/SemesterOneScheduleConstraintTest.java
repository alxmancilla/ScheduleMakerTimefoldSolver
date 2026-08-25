package com.example.solver;

import static org.junit.Assert.*;

import com.example.analysis.BlockScheduleAnalyzer;
import com.example.domain.*;
import org.junit.Test;

import java.time.DayOfWeek;
import java.util.*;

/**
 * Verifies the two first-semester schedule-quality constraints (SOFT, weight
 * 4 each), added 2026-08-24 to replace the generic (now disabled)
 * "Minimize group idle gaps" for semester-1 groups specifically:
 * <ul>
 * <li>{@code preferSemesterOneBlocksStartEarly} - a group's earliest
 * semester-1 block each day should start at 7:00; penalty is the deviation
 * in hours.</li>
 * <li>{@code minimizeSemesterOneGroupIdleGaps} - same adjacent-pair idle-gap
 * logic as the old generic constraint, but only counting a gap when BOTH
 * framing blocks are themselves semester-1 (adjacency still considers a
 * block of any semester, so a higher-semester block correctly breaks
 * adjacency instead of being mistaken for idle time).</li>
 * </ul>
 * Exercised via {@link BlockScheduleAnalyzer}, which independently mirrors
 * {@link SchoolConstraintProvider}'s rules.
 */
public class SemesterOneScheduleConstraintTest {

    private static final String START_EARLY_CONSTRAINT = "Prefer first-semester blocks to start early";
    private static final String IDLE_GAPS_CONSTRAINT = "Minimize first-semester group idle gaps";

    private static final Course SEMESTER_ONE_COURSE =
            new Course("1", "Semester One Course", "S1", 1, "BASICAS", "estándar", 4, Boolean.TRUE);
    private static final Course SEMESTER_THREE_COURSE =
            new Course("2", "Semester Three Course", "S3", 3, "BASICAS", "estándar", 4, Boolean.TRUE);

    private static CourseBlockAssignment block(String id, Group group, Course course, int startHour) {
        CourseBlockAssignment a = new CourseBlockAssignment(id, group, course, 1);
        a.setTimeslot(new BlockTimeslot("slot-" + id, DayOfWeek.MONDAY, startHour, 1));
        a.setPinned(false);
        return a;
    }

    private static SchoolSchedule scheduleWith(CourseBlockAssignment... assignments) {
        return SchoolSchedule.forBlockScheduling(new ArrayList<>(), new ArrayList<>(), new ArrayList<>(),
                new ArrayList<>(), new ArrayList<>(), new ArrayList<>(Arrays.asList(assignments)));
    }

    private static int startEarlyViolations(SchoolSchedule schedule) {
        return BlockScheduleAnalyzer.analyzeSoftConstraintViolations(schedule)
                .getOrDefault(START_EARLY_CONSTRAINT, 0);
    }

    private static int idleGapViolations(SchoolSchedule schedule) {
        return BlockScheduleAnalyzer.analyzeSoftConstraintViolations(schedule)
                .getOrDefault(IDLE_GAPS_CONSTRAINT, 0);
    }

    // ---- preferSemesterOneBlocksStartEarly ----

    @Test
    public void semesterOneBlockStartingAt7amIsNotAViolation() {
        Group g = new Group("G1", "Group 1", new HashSet<>());
        SchoolSchedule schedule = scheduleWith(block("A1", g, SEMESTER_ONE_COURSE, 7));
        assertEquals(0, startEarlyViolations(schedule));
    }

    @Test
    public void semesterOneBlockStartingLateIsPenalizedByTheDeviation() {
        Group g = new Group("G1", "Group 1", new HashSet<>());
        SchoolSchedule schedule = scheduleWith(block("A1", g, SEMESTER_ONE_COURSE, 9));
        assertEquals(2, startEarlyViolations(schedule));
    }

    @Test
    public void onlyTheEarliestSemesterOneBlockOfTheDayCounts() {
        Group g = new Group("G1", "Group 1", new HashSet<>());
        SchoolSchedule schedule = scheduleWith(
                block("A1", g, SEMESTER_ONE_COURSE, 9),
                block("A2", g, SEMESTER_ONE_COURSE, 10),
                block("A3", g, SEMESTER_ONE_COURSE, 11));
        assertEquals(2, startEarlyViolations(schedule));
    }

    @Test
    public void nonSemesterOneBlocksAreIgnored() {
        Group g = new Group("G1", "Group 1", new HashSet<>());
        SchoolSchedule schedule = scheduleWith(block("A1", g, SEMESTER_THREE_COURSE, 12));
        assertEquals(0, startEarlyViolations(schedule));
    }

    @Test
    public void pinnedSemesterOneBlocksAreExcluded() {
        Group g = new Group("G1", "Group 1", new HashSet<>());
        CourseBlockAssignment a = block("A1", g, SEMESTER_ONE_COURSE, 11);
        a.setPinned(true);
        assertEquals(0, startEarlyViolations(scheduleWith(a)));
    }

    // ---- minimizeSemesterOneGroupIdleGaps ----

    @Test
    public void gapBetweenTwoSemesterOneBlocksIsCounted() {
        Group g = new Group("G1", "Group 1", new HashSet<>());
        // 7-8, then 10-11: 2h idle gap, both blocks semester-1.
        SchoolSchedule schedule = scheduleWith(
                block("A1", g, SEMESTER_ONE_COURSE, 7),
                block("A2", g, SEMESTER_ONE_COURSE, 10));
        assertEquals(2, idleGapViolations(schedule));
    }

    @Test
    public void gapBrokenByAHigherSemesterBlockIsNotCounted() {
        // 7-8 (semester 1), 8-9 (semester 3, no idle time on either side), 9-10
        // (semester 1): the semester-3 block fills the middle exactly - no actual
        // idle time exists, so this must NOT be counted as a semester-1 gap
        // (the bug this design deliberately avoids: mistaking "occupied by
        // another course" for "idle").
        Group g = new Group("G1", "Group 1", new HashSet<>());
        SchoolSchedule schedule = scheduleWith(
                block("A1", g, SEMESTER_ONE_COURSE, 7),
                block("M", g, SEMESTER_THREE_COURSE, 8),
                block("A2", g, SEMESTER_ONE_COURSE, 9));
        assertEquals(0, idleGapViolations(schedule));
    }

    @Test
    public void gapPartiallyBrokenByAHigherSemesterBlockCountsOnlyTheRemainingIdleTime() {
        // 7-8 (semester 1), 10-11 (semester 3), 12-13 (semester 1): the semester-3
        // block breaks adjacency entirely between the two semester-1 blocks, so
        // neither the 8->10 nor the 11->12 idle stretch is tracked by this
        // semester-1-scoped constraint (those aren't semester-1-to-semester-1 gaps).
        Group g = new Group("G1", "Group 1", new HashSet<>());
        SchoolSchedule schedule = scheduleWith(
                block("A1", g, SEMESTER_ONE_COURSE, 7),
                block("M", g, SEMESTER_THREE_COURSE, 10),
                block("A2", g, SEMESTER_ONE_COURSE, 12));
        assertEquals(0, idleGapViolations(schedule));
    }

    @Test
    public void threeSemesterOneBlockChainCountsOnlyAdjacentGaps() {
        Group g = new Group("G1", "Group 1", new HashSet<>());
        // 7-8, 10-11, 13-14: adjacent gaps 8->10 (2h) and 11->13 (2h) = 4h, not the
        // inflated 9h a pairwise (non-adjacent-aware) formulation would produce.
        SchoolSchedule schedule = scheduleWith(
                block("A1", g, SEMESTER_ONE_COURSE, 7),
                block("A2", g, SEMESTER_ONE_COURSE, 10),
                block("A3", g, SEMESTER_ONE_COURSE, 13));
        assertEquals(4, idleGapViolations(schedule));
    }

    @Test
    public void consecutiveSemesterOneBlocksHaveNoIdleGap() {
        Group g = new Group("G1", "Group 1", new HashSet<>());
        SchoolSchedule schedule = scheduleWith(
                block("A1", g, SEMESTER_ONE_COURSE, 7),
                block("A2", g, SEMESTER_ONE_COURSE, 8));
        assertEquals(0, idleGapViolations(schedule));
    }

    @Test
    public void pinnedBlocksAreExcludedFromIdleGaps() {
        Group g = new Group("G1", "Group 1", new HashSet<>());
        CourseBlockAssignment a1 = block("A1", g, SEMESTER_ONE_COURSE, 7);
        CourseBlockAssignment a2 = block("A2", g, SEMESTER_ONE_COURSE, 10);
        a2.setPinned(true);
        assertEquals(0, idleGapViolations(scheduleWith(a1, a2)));
    }

    @Test
    public void gapBetweenTwoNonSemesterOneBlocksIsNotCounted() {
        Group g = new Group("G1", "Group 1", new HashSet<>());
        SchoolSchedule schedule = scheduleWith(
                block("A1", g, SEMESTER_THREE_COURSE, 7),
                block("A2", g, SEMESTER_THREE_COURSE, 10));
        assertEquals(0, idleGapViolations(schedule));
    }
}
