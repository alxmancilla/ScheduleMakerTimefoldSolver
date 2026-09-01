package com.example.solver;

import static org.junit.Assert.assertEquals;

import ai.timefold.solver.core.api.domain.solution.ConstraintWeightOverrides;
import ai.timefold.solver.core.api.score.buildin.hardsoft.HardSoftScore;
import ai.timefold.solver.core.api.solver.SolutionManager;
import ai.timefold.solver.core.api.solver.SolverFactory;
import ai.timefold.solver.core.config.solver.SolverConfig;

import com.example.domain.*;
import org.junit.Test;

import java.time.DayOfWeek;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

/**
 * Proves Tier 1 dynamic constraint weights actually take effect through the
 * REAL Timefold score calculation (not BlockScheduleAnalyzer's independent
 * violation counter, which never reads SchoolSchedule.getConstraintWeightOverrides()
 * at all): scoring the same violating schedule with no override uses the
 * hardcoded default from SoftConstraintDefaults, and scoring it again after
 * setting an override on the SchoolSchedule instance picks up the new
 * weight - all without touching SchoolConstraintProvider or recompiling.
 *
 * Uses SolutionManager.update(), which calculates a score for a solution
 * as-is (no local search), so this is a deterministic single-assignment
 * check rather than a timed solve.
 */
public class ConstraintWeightOverrideTest {

    // "Room capacity should fit group size" - default weight 4 (see
    // SoftConstraintDefaults) - chosen because a single over-capacity
    // assignment triggers exactly one violation, isolating the weight
    // change from any other constraint's score contribution.
    private static final String CONSTRAINT_NAME = "Room capacity should fit group size";

    private SolutionManager<SchoolSchedule, HardSoftScore> buildSolutionManager() {
        SolverConfig config = SolverConfig.createFromXmlResource("solverConfig.xml");
        return SolutionManager.create(SolverFactory.<SchoolSchedule>create(config));
    }

    /** A schedule with exactly one over-capacity room assignment (25 students in a 20-seat room). */
    private SchoolSchedule violatingSchedule() {
        Teacher teacher = new Teacher("TEST", "Test", "Teacher", new HashSet<>(), new HashMap<>(), 40);
        Course course = new Course("1", "Test Course", "TEST", 2, "BASICAS", "estándar", 4, Boolean.TRUE);
        Room room = new Room("R1", "A", "estándar", 20);
        Group group = new Group("G1", "Test Group", new HashSet<>(), null, 25);
        BlockTimeslot timeslot = new BlockTimeslot("slot1", DayOfWeek.MONDAY, 7, 1);

        CourseBlockAssignment assignment = new CourseBlockAssignment("a1", group, course, 1);
        assignment.setTimeslot(timeslot);
        assignment.setRoom(room);
        assignment.setTeacher(teacher);
        assignment.setPinned(true); // isolates this from every other constraint's movable-block filtering

        return new SchoolSchedule(
                Collections.singletonList(teacher),
                Collections.singletonList(timeslot),
                Collections.singletonList(room),
                Collections.singletonList(course),
                Collections.singletonList(group),
                Collections.singletonList(assignment));
    }

    @Test
    public void noOverrideUsesHardcodedDefaultWeight() {
        SchoolSchedule schedule = violatingSchedule();
        // Default: SchoolSchedule.constraintWeightOverrides starts as none().
        HardSoftScore score = buildSolutionManager().update(schedule);
        assertEquals(-4, score.softScore());
    }

    @Test
    public void overrideReplacesTheDefaultWeight() {
        SchoolSchedule schedule = violatingSchedule();
        schedule.setConstraintWeightOverrides(
                ConstraintWeightOverrides.of(Map.of(CONSTRAINT_NAME, HardSoftScore.ofSoft(99))));

        HardSoftScore score = buildSolutionManager().update(schedule);
        assertEquals(-99, score.softScore());
    }

    @Test
    public void overrideOnlyAffectsTheNamedConstraint() {
        // A second violation on an UNNAMED (not overridden) constraint - "Prefer
        // block's specified room" (default weight 3) - must keep its default even
        // though a DIFFERENT constraint has an override in play.
        SchoolSchedule schedule = violatingSchedule();
        CourseBlockAssignment assignment = schedule.getCourseBlockAssignments().get(0);
        assignment.setPinned(false); // this constraint excludes pinned assignments
        assignment.setPreferredRoomHint("SOME-OTHER-ROOM");

        schedule.setConstraintWeightOverrides(
                ConstraintWeightOverrides.of(Map.of(CONSTRAINT_NAME, HardSoftScore.ofSoft(99))));

        HardSoftScore score = buildSolutionManager().update(schedule);
        // -99 (overridden capacity violation) + -3 (untouched default preferred-room weight)
        assertEquals(-102, score.softScore());
    }
}
