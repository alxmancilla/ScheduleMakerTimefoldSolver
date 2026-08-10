package com.example.solver;

import ai.timefold.solver.core.api.score.director.ScoreDirector;
import ai.timefold.solver.core.impl.heuristic.move.Move;
import ai.timefold.solver.core.impl.heuristic.selector.common.decorator.SelectionFilter;
import ai.timefold.solver.core.impl.heuristic.selector.move.generic.SwapMove;
import com.example.domain.BlockTimeslot;
import com.example.domain.CourseBlockAssignment;
import com.example.domain.SchoolSchedule;
import com.example.domain.Teacher;

/**
 * Move filter that rejects SwapMove instances where:
 * 1. The two assignments have different block lengths
 * 2. Either teacher would be unavailable after the swap
 *
 * This prevents swapping a 3-hour block with a 1-hour block, which would
 * create block length mismatches after the swap, and ensures teachers
 * remain available for their assigned timeslots.
 */
public class MatchingLengthSwapFilter implements SelectionFilter<SchoolSchedule, Move<SchoolSchedule>> {

    @Override
    public boolean accept(ScoreDirector<SchoolSchedule> scoreDirector, Move<SchoolSchedule> move) {
        // Only filter SwapMove instances
        if (!(move instanceof SwapMove)) {
            return true; // Accept all other move types
        }

        SwapMove<SchoolSchedule> swapMove = (SwapMove<SchoolSchedule>) move;
        Object leftEntity = swapMove.getLeftEntity();
        Object rightEntity = swapMove.getRightEntity();

        // Only filter swaps between CourseBlockAssignment entities
        if (!(leftEntity instanceof CourseBlockAssignment) || !(rightEntity instanceof CourseBlockAssignment)) {
            return true;
        }

        CourseBlockAssignment leftAssignment = (CourseBlockAssignment) leftEntity;
        CourseBlockAssignment rightAssignment = (CourseBlockAssignment) rightEntity;

        // Reject if block lengths don't match
        if (leftAssignment.getBlockLength() != rightAssignment.getBlockLength()) {
            return false;
        }

        // After swap, left assignment will have right's timeslot and vice versa
        BlockTimeslot leftTimeslot = leftAssignment.getTimeslot();
        BlockTimeslot rightTimeslot = rightAssignment.getTimeslot();

        // Skip if either timeslot is null
        if (leftTimeslot == null || rightTimeslot == null) {
            return true;
        }

        // Check if left teacher would be available at right's timeslot
        Teacher leftTeacher = leftAssignment.getTeacher();
        if (leftTeacher != null && !leftTeacher.isAvailableForBlock(rightTimeslot)) {
            return false;
        }

        // Check if right teacher would be available at left's timeslot
        Teacher rightTeacher = rightAssignment.getTeacher();
        if (rightTeacher != null && !rightTeacher.isAvailableForBlock(leftTimeslot)) {
            return false;
        }

        return true;
    }
}
