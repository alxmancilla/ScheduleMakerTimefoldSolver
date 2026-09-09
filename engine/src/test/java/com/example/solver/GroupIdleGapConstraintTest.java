package com.example.solver;

import static org.junit.Assert.assertEquals;

import ai.timefold.solver.core.api.score.buildin.hardsoft.HardSoftScore;
import ai.timefold.solver.core.api.score.constraint.ConstraintMatchTotal;
import ai.timefold.solver.core.api.solver.SolutionManager;
import ai.timefold.solver.core.api.solver.SolverFactory;
import ai.timefold.solver.core.config.solver.SolverConfig;

import com.example.domain.*;
import org.junit.Test;

import java.time.DayOfWeek;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;

/**
 * Solver-level coverage for "Minimize group idle gaps" (re-enabled 2026-09-08
 * at weight 3), asserting the score Timefold actually calculates rather than
 * {@link com.example.analysis.BlockScheduleAnalyzer}'s independent mirror.
 *
 * <p>That distinction is the whole point of this class: every other idle-gap
 * test in this repo goes through the analyzer, and ConstraintConsistencyTest
 * only checks that the two agree on constraint NAMES. So nothing asserted that
 * a constraint listed in SchoolConstraintProvider actually contributes to the
 * score - a re-enabled constraint could report violations through the analyzer
 * (and through the console/PDF report, and schedule_run_violation) while being
 * silently inert in the solve that matters.
 *
 * <p>Uses SolutionManager.explain() to read the one constraint's own score
 * contribution, so unrelated constraints firing on the fixture can't mask or
 * fake the result.
 */
public class GroupIdleGapConstraintTest {

    private static final String CONSTRAINT_NAME = "Minimize group idle gaps";
    private static final int WEIGHT = 3; // SoftConstraintDefaults

    // Semester 3 deliberately: keeps the semester-1-only constraints
    // (minimizeSemesterOneGroupIdleGaps / preferSemesterOneBlocksStartEarly)
    // out of the fixture entirely.
    private static final Course COURSE_A =
            new Course("1", "Course A", "A", 3, "Core", "Standard", 4, Boolean.TRUE);
    private static final Course COURSE_B =
            new Course("2", "Course B", "B", 3, "Core", "Standard", 4, Boolean.TRUE);

    private static final Group GROUP = new Group("G1", "Group 1", new HashSet<>());

    /** Unpinned 1-hour Monday block. Teacher/room left null so no other constraint has anything to match on. */
    private static CourseBlockAssignment block(String id, Course course, int startHour, int lengthHours) {
        CourseBlockAssignment a = new CourseBlockAssignment(id, GROUP, course, lengthHours);
        a.setTimeslot(new BlockTimeslot("slot-" + id, DayOfWeek.MONDAY, startHour, lengthHours));
        a.setPinned(false);
        return a;
    }

    private static SchoolSchedule scheduleWith(CourseBlockAssignment... assignments) {
        List<CourseBlockAssignment> list = new ArrayList<>(Arrays.asList(assignments));
        return new SchoolSchedule(Collections.emptyList(), Collections.emptyList(), Collections.emptyList(),
                new ArrayList<>(Arrays.asList(COURSE_A, COURSE_B)), Collections.singletonList(GROUP), list);
    }

    /** The score this one constraint contributes, isolated from everything else on the schedule. */
    private static int groupIdleGapScore(SchoolSchedule schedule) {
        SolverConfig config = SolverConfig.createFromXmlResource("solverConfig.xml");
        SolutionManager<SchoolSchedule, HardSoftScore> solutionManager =
                SolutionManager.create(SolverFactory.<SchoolSchedule>create(config));
        return solutionManager.explain(schedule).getConstraintMatchTotalMap().values().stream()
                .filter(total -> CONSTRAINT_NAME.equals(total.getConstraintRef().constraintName()))
                .mapToInt(total -> ((HardSoftScore) total.getScore()).softScore())
                .sum();
    }

    @Test
    public void aGapBetweenTwoMovableBlocksIsPenalizedByTheSolver() {
        // 7-8 then 10-11: two idle hours (8->10).
        SchoolSchedule schedule = scheduleWith(
                block("A1", COURSE_A, 7, 1),
                block("A2", COURSE_B, 10, 1));
        assertEquals(-2 * WEIGHT, groupIdleGapScore(schedule));
    }

    @Test
    public void backToBackBlocksAreNotPenalized() {
        SchoolSchedule schedule = scheduleWith(
                block("A1", COURSE_A, 7, 1),
                block("A2", COURSE_B, 8, 1));
        assertEquals(0, groupIdleGapScore(schedule));
    }

    /**
     * The solver-side half of the phantom-gap regression (see
     * GroupIdleGapAnalyzerTest for the analyzer half): a PINNED block between two
     * movable ones must break adjacency, because the student is sitting in it.
     * Until 2026-09-08 the constraint's ifNotExists mid-check filtered on
     * {@code !mid.isPinned()}, so the pinned block was invisible to adjacency and
     * its own occupied hours were scored as idle.
     */
    @Test
    public void aPinnedBlockBetweenTwoMovableOnesBreaksAdjacency() {
        CourseBlockAssignment pinnedMid = block("P", COURSE_B, 8, 2); // 8-10
        pinnedMid.setPinned(true);
        // Genuinely back-to-back 7-11; before the fix this scored -6 (the 8->10
        // span the pinned class actually occupies).
        SchoolSchedule schedule = scheduleWith(
                block("A1", COURSE_A, 7, 1),
                pinnedMid,
                block("A2", COURSE_A, 10, 1));
        assertEquals(0, groupIdleGapScore(schedule));
    }

    /** Sanity check that the fixture can produce a nonzero score at all, per day. */
    @Test
    public void gapsAreCountedPerDayNotAcrossDays() {
        CourseBlockAssignment tuesday = new CourseBlockAssignment("T1", GROUP, COURSE_B, 1);
        tuesday.setTimeslot(new BlockTimeslot("slot-T1", DayOfWeek.TUESDAY, 14, 1));
        tuesday.setPinned(false);
        // Monday 7-8 and Tuesday 14-15 are on different days: no gap between them.
        SchoolSchedule schedule = scheduleWith(block("A1", COURSE_A, 7, 1), tuesday);
        assertEquals(0, groupIdleGapScore(schedule));
    }

    /** Guards the constraint against silently disappearing from the solver again. */
    @Test
    public void constraintIsRegisteredWithTheSolver() {
        SolverConfig config = SolverConfig.createFromXmlResource("solverConfig.xml");
        SolutionManager<SchoolSchedule, HardSoftScore> solutionManager =
                SolutionManager.create(SolverFactory.<SchoolSchedule>create(config));
        SchoolSchedule schedule = scheduleWith(
                block("A1", COURSE_A, 7, 1),
                block("A2", COURSE_B, 10, 1));
        boolean present = solutionManager.explain(schedule).getConstraintMatchTotalMap().values().stream()
                .map(ConstraintMatchTotal::getConstraintRef)
                .anyMatch(ref -> CONSTRAINT_NAME.equals(ref.constraintName()));
        org.junit.Assert.assertTrue(
                CONSTRAINT_NAME + " is not registered with the solver - it may be commented out of "
                        + "SchoolConstraintProvider's constraint list even though the analyzer still reports it",
                present);
    }
}
