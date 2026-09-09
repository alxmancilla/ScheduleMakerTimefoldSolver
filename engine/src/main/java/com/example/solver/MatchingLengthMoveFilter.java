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
 *
 * The same "set room to null" exploit applies one tier out, to blocks that
 * AREN'T room-fixed (added 2026-09-08). Unassigning is strictly free score for
 * any block: every room-related soft constraint skips a null room
 * (groupPreferredRoomConstraint, preferBlockSpecifiedRoom,
 * roomCapacityShouldFitGroupSize, nonStandardRoomsShouldFinishBy2pm all guard
 * on getRoom() == null) and nothing penalizes the absence, so dropping the room
 * zeroes those penalties outright. Observed on schedule_run #80 - the first
 * solve in which the idle-gap constraints actually applied any pressure, after
 * the day-joiner fix - where roomless blocks rose 25 -> 31 via moves like
 * "{CC2 -> null}". An unassign is now rejected unless the block genuinely has
 * no assignable room, which is the case allowsUnassigned exists for. Note this
 * only closes the ChangeMove vector: a room SWAP merely relocates a null
 * between two blocks, so it can't increase how many blocks lack a room.
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
            if (assignment.isRoomFixed()) {
                return false;
            }
            // Same "set room to null" exploit, one tier out (added 2026-09-08):
            // for a block that ISN'T room-fixed, unassigning is still strictly
            // free score. Every room-related soft constraint skips a null room
            // (groupPreferredRoomConstraint, preferBlockSpecifiedRoom,
            // roomCapacityShouldFitGroupSize, nonStandardRoomsShouldFinishBy2pm
            // all guard on getRoom() == null) and nothing penalizes the absence,
            // so dropping the room zeroes those penalties outright and local
            // search takes it. Observed on schedule_run #80, the first solve
            // where idle-gap constraints actually applied any pressure: roomless
            // blocks went 25 -> 31, via moves like "{CC2 -> null}". An unassign
            // is only legitimate when the block has no assignable room at all,
            // which is the case allowsUnassigned exists for.
            if (changeMove.getToPlanningValue() == null) {
                return assignment.getMatchingRooms().isEmpty();
            }
            return true;
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
