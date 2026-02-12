package com.example.solver;

import ai.timefold.solver.core.api.score.director.ScoreDirector;
import ai.timefold.solver.core.impl.heuristic.move.Move;
import ai.timefold.solver.core.impl.heuristic.selector.common.decorator.SelectionFilter;
import ai.timefold.solver.core.impl.heuristic.selector.move.generic.ChangeMove;
import com.example.domain.BlockTimeslot;
import com.example.domain.CourseBlockAssignment;
import com.example.domain.SchoolSchedule;
import com.example.domain.Teacher;

/**
 * Move filter that rejects ChangeMove instances where:
 * 1. The timeslot length doesn't match the block length
 * 2. The teacher is not available for the entire block
 *
 * This filter ensures that the solver never creates assignments where
 * block_length != timeslot_length or where teachers are unavailable.
 */
public class MatchingLengthMoveFilter implements SelectionFilter<SchoolSchedule, Move<SchoolSchedule>> {

    @Override
    public boolean accept(ScoreDirector<SchoolSchedule> scoreDirector, Move<SchoolSchedule> move) {
        // Only filter ChangeMove instances
        if (!(move instanceof ChangeMove)) {
            return true; // Accept all other move types
        }

        ChangeMove<SchoolSchedule> changeMove = (ChangeMove<SchoolSchedule>) move;
        Object entity = changeMove.getEntity();
        Object toPlanningValue = changeMove.getToPlanningValue();

        // Only filter moves on CourseBlockAssignment entities
        if (!(entity instanceof CourseBlockAssignment)) {
            return true;
        }

        // Only filter moves that change the timeslot variable
        if (!(toPlanningValue instanceof BlockTimeslot)) {
            return true;
        }

        CourseBlockAssignment assignment = (CourseBlockAssignment) entity;
        BlockTimeslot timeslot = (BlockTimeslot) toPlanningValue;

        // Reject the move if block length doesn't match timeslot length
        if (assignment.getBlockLength() != timeslot.getLengthHours()) {
            return false;
        }

        // Reject the move if teacher is not available for the entire block
        Teacher teacher = assignment.getTeacher();
        if (teacher != null && !teacher.isAvailableForBlock(timeslot)) {
            return false;
        }

        return true;
    }
}
