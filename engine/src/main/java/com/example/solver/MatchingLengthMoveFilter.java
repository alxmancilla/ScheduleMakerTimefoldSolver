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
 * Move filter that rejects ChangeMove instances where the teacher is not
 * available for the entire block.
 *
 * Block-length matching doesn't need a filter here: CourseBlockAssignment's
 * timeslot value range is entity-scoped (CourseBlockAssignment.
 * getMatchingBlockTimeslots(), filtered by blockLength), so ChangeMoveSelector
 * can't generate a length-mismatched move in the first place. Teacher
 * availability isn't part of that value range (an unavailable slot is
 * scored, not structurally excluded), so it's still filtered here.
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

        // Reject the move if teacher is not available for the entire block
        Teacher teacher = assignment.getTeacher();
        if (teacher != null && !teacher.isAvailableForBlock(timeslot)) {
            return false;
        }

        return true;
    }
}
