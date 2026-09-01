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
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Proves the load-bearing assumption behind the 4 severity-configurable HARD
 * constraints (see scheduler-common's ConfigurableHardConstraints): a
 * constraint_config row can flip a constraint whose hardcoded literal is
 * {@code HardSoftScore.ONE_HARD} into SOFT, with zero
 * SchoolConstraintProvider code change - the SAME ConstraintWeightOverrides
 * mechanism Tier 1 already uses for constraints that were soft to begin
 * with, applied here to one that starts HARD. Uses "Teacher must be
 * qualified" as the representative case; the other 3 configurable
 * constraints (teacherMustBeAvailable, maxTwoBlocksPerCoursePerGroupPerDay,
 * courseBlocksMustBeConsecutive) share the exact same mechanism, so this one
 * case is the load-bearing proof, not a per-constraint requirement.
 */
public class ConfigurableHardConstraintSeverityTest {

    private static final String CONSTRAINT_NAME = "Teacher must be qualified";

    private SolutionManager<SchoolSchedule, HardSoftScore> buildSolutionManager() {
        SolverConfig config = SolverConfig.createFromXmlResource("solverConfig.xml");
        return SolutionManager.create(SolverFactory.<SchoolSchedule>create(config));
    }

    /** A schedule with exactly one unqualified-teacher violation (and nothing else). */
    private SchoolSchedule violatingSchedule() {
        // Available Monday 7:00 so teacherMustBeAvailable doesn't also fire,
        // isolating this test to the qualification violation alone.
        Map<DayOfWeek, Set<Integer>> availability = new HashMap<>();
        availability.put(DayOfWeek.MONDAY, new HashSet<>(List.of(7)));
        Teacher unqualifiedTeacher = new Teacher("TEST", "Test", "Teacher", new HashSet<>(), availability, 40);
        Course course = new Course("1", "Math", "MATH", 2, "Core", "Standard", 4, Boolean.TRUE);
        Room room = new Room("R1", "A", "Standard");
        Group group = new Group("G1", "Test Group", new HashSet<>());
        BlockTimeslot timeslot = new BlockTimeslot("slot1", DayOfWeek.MONDAY, 7, 1);

        CourseBlockAssignment assignment = new CourseBlockAssignment("a1", group, course, 1);
        assignment.setTimeslot(timeslot);
        assignment.setRoom(room);
        assignment.setTeacher(unqualifiedTeacher);
        assignment.setPinned(false); // teacherMustBeQualified excludes pinned assignments

        return new SchoolSchedule(
                Collections.singletonList(unqualifiedTeacher),
                Collections.singletonList(timeslot),
                Collections.singletonList(room),
                Collections.singletonList(course),
                Collections.singletonList(group),
                Collections.singletonList(assignment));
    }

    @Test
    public void noOverride_staysHard() {
        SchoolSchedule schedule = violatingSchedule();
        HardSoftScore score = buildSolutionManager().update(schedule);
        assertEquals(-1, score.hardScore());
        assertEquals(0, score.softScore());
    }

    @Test
    public void overrideToSoft_removesTheHardViolationAndAppliesTheSoftWeight() {
        SchoolSchedule schedule = violatingSchedule();
        schedule.setConstraintWeightOverrides(
                ConstraintWeightOverrides.of(Map.of(CONSTRAINT_NAME, HardSoftScore.ofSoft(7))));

        HardSoftScore score = buildSolutionManager().update(schedule);
        assertEquals("The hard violation must disappear entirely once overridden to soft",
                0, score.hardScore());
        assertEquals("The soft violation must apply the overridden weight",
                -7, score.softScore());
    }
}
