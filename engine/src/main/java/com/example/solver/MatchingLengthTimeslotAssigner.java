package com.example.solver;

import ai.timefold.solver.core.api.score.director.ScoreDirector;
import ai.timefold.solver.core.api.solver.phase.PhaseCommand;
import com.example.domain.BlockTimeslot;
import com.example.domain.CourseBlockAssignment;
import com.example.domain.SchoolSchedule;
import com.example.domain.Teacher;

import java.util.List;
import java.util.Random;
import java.util.function.BooleanSupplier;
import java.util.stream.Collectors;

/**
 * Custom phase that assigns timeslots to course block assignments,
 * ensuring that:
 * 1. The timeslot length matches the block length
 * 2. The teacher is available for the entire block (if teacher is assigned)
 *
 * This phase runs before the standard construction heuristic to ensure
 * that all assignments start with valid timeslot lengths and teacher
 * availability.
 */
public class MatchingLengthTimeslotAssigner implements PhaseCommand<SchoolSchedule> {

    @Override
    public void changeWorkingSolution(ScoreDirector<SchoolSchedule> scoreDirector, BooleanSupplier isPhaseTerminated) {
        SchoolSchedule schedule = scoreDirector.getWorkingSolution();
        Random random = new Random();

        List<BlockTimeslot> allTimeslots = schedule.getBlockTimeslots();

        // For each unassigned course block assignment
        for (CourseBlockAssignment assignment : schedule.getCourseBlockAssignments()) {
            // Check if phase has been terminated
            if (isPhaseTerminated.getAsBoolean()) {
                break;
            }

            // Skip pinned assignments
            if (assignment.isPinned()) {
                continue;
            }

            // Skip if already assigned
            if (assignment.getTimeslot() != null) {
                continue;
            }

            // Find timeslots that match the block length AND teacher is available
            List<BlockTimeslot> matchingTimeslots = allTimeslots.stream()
                    .filter(ts -> ts.getLengthHours() == assignment.getBlockLength())
                    .filter(ts -> isTeacherAvailableForBlock(assignment.getTeacher(), ts))
                    .collect(Collectors.toList());

            if (!matchingTimeslots.isEmpty()) {
                // Randomly select one of the matching timeslots
                BlockTimeslot selectedTimeslot = matchingTimeslots.get(random.nextInt(matchingTimeslots.size()));

                // Assign it
                scoreDirector.beforeVariableChanged(assignment, "timeslot");
                assignment.setTimeslot(selectedTimeslot);
                scoreDirector.afterVariableChanged(assignment, "timeslot");

                scoreDirector.triggerVariableListeners();
            }
        }
    }

    /**
     * Check if teacher is available for all hours in the block timeslot.
     * Returns true if teacher is null (no teacher assigned yet) or if teacher is
     * available.
     */
    private boolean isTeacherAvailableForBlock(Teacher teacher, BlockTimeslot timeslot) {
        // If no teacher assigned, we can't check availability - allow it
        if (teacher == null) {
            return true;
        }

        // Check if teacher is available for the entire block
        return teacher.isAvailableForBlock(timeslot);
    }
}
