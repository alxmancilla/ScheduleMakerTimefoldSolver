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
 * available for the entire block, or that would reassign a "room-fixed"
 * block's room.
 *
 * Block-length matching doesn't need a filter here: CourseBlockAssignment's
 * timeslot value range is entity-scoped (CourseBlockAssignment.
 * getMatchingBlockTimeslots(), filtered by blockLength), so ChangeMoveSelector
 * can't generate a length-mismatched move in the first place. Teacher
 * availability isn't part of that value range (an unavailable slot is
 * scored, not structurally excluded), so it's still filtered here.
 *
 * Room, similarly, has an entity-scoped value range (CourseBlockAssignment.
 * getMatchingRooms()) that collapses to a singleton for a "room-fixed" block
 * - but that alone isn't sufficient: room is @PlanningVariable(allowsUnassigned
 * = true) (needed because some legacy pinned rows have a null room), and
 * Timefold's ChangeMoveSelector treats "unassign" as a legal move target for
 * any allowsUnassigned variable regardless of what its own value range
 * contains. Confirmed empirically: without this filter, local search
 * unassigned ~70% of correctly room-fixed blocks by picking that "set room to
 * null" move, even though construction heuristic alone never produced this
 * (0 nulled out of 527). So this filter, not the value range, is what
 * actually makes a fixed block's room untouchable once local search runs.
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

        // Only filter moves on CourseBlockAssignment entities
        if (!(entity instanceof CourseBlockAssignment)) {
            return true;
        }
        CourseBlockAssignment assignment = (CourseBlockAssignment) entity;

        if ("room".equals(changeMove.getVariableName())) {
            // toPlanningValue may be null here (an "unassign" move, only possible
            // because allowsUnassigned = true) - reject any change at all to a
            // fixed block's room, since nothing should ever move it once set.
            return !assignment.isRoomFixed();
        }

        Object toPlanningValue = changeMove.getToPlanningValue();

        // Only filter moves that change the timeslot variable
        if (!(toPlanningValue instanceof BlockTimeslot)) {
            return true;
        }

        BlockTimeslot timeslot = (BlockTimeslot) toPlanningValue;

        // Reject the move if teacher is not available for the entire block
        Teacher teacher = assignment.getTeacher();
        if (teacher != null && !teacher.isAvailableForBlock(timeslot)) {
            return false;
        }

        return true;
    }
}
