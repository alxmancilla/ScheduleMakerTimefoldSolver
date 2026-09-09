package com.example.solver;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import ai.timefold.solver.core.impl.domain.entity.descriptor.EntityDescriptor;
import ai.timefold.solver.core.impl.domain.solution.descriptor.SolutionDescriptor;
import ai.timefold.solver.core.impl.domain.variable.descriptor.GenuineVariableDescriptor;
import ai.timefold.solver.core.impl.heuristic.move.Move;
import ai.timefold.solver.core.impl.heuristic.selector.move.generic.ChangeMove;

import com.example.domain.*;
import org.junit.Test;

import java.time.DayOfWeek;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.List;

/**
 * Guards {@link MatchingLengthMoveFilter}'s handling of "set room to null"
 * moves. room is @PlanningVariable(allowsUnassigned = true), so Timefold's
 * ChangeMoveSelector offers unassign as a legal target for every block
 * regardless of its own value range - and unassigning is strictly free score,
 * because every room-related soft constraint skips a null room while nothing
 * penalizes its absence. Local search will therefore take that move whenever a
 * block's room costs anything at all.
 *
 * <p>The filter already blocked this for room-FIXED blocks (see its javadoc:
 * ~70% of them were being nulled out). The same exploit one tier out - blocks
 * with a genuine multi-room choice - showed up on schedule_run #80, the first
 * solve where idle-gap constraints applied real pressure: roomless blocks went
 * 25 -> 31 via moves like "{CC2 -> null}".
 */
public class RoomUnassignMoveFilterTest {

    private static final MatchingLengthMoveFilter FILTER = new MatchingLengthMoveFilter();

    private static final GenuineVariableDescriptor<SchoolSchedule> ROOM_DESCRIPTOR;
    static {
        SolutionDescriptor<SchoolSchedule> solutionDescriptor =
                SolutionDescriptor.buildSolutionDescriptor(SchoolSchedule.class, CourseBlockAssignment.class);
        EntityDescriptor<SchoolSchedule> entityDescriptor =
                solutionDescriptor.findEntityDescriptorOrFail(CourseBlockAssignment.class);
        ROOM_DESCRIPTOR = entityDescriptor.getGenuineVariableDescriptor("room");
    }

    private static final Course COURSE =
            new Course("1", "Course", "C", 3, "Core", "Standard", 4, Boolean.TRUE);

    private static Room room(String name) {
        return new Room(name, "Building", "Standard", 30);
    }

    /**
     * A movable block whose group range leaves it a genuine choice between the
     * given rooms (2+ rooms => not room-fixed), already sitting in the first.
     */
    private static CourseBlockAssignment blockChoosingAmong(Room... rooms) {
        List<Room> roomList = new ArrayList<>(Arrays.asList(rooms));
        Group group = new Group("G1", "Group 1", new HashSet<>());
        CourseBlockAssignment a = new CourseBlockAssignment("a1", group, COURSE, 1);
        a.setTimeslot(new BlockTimeslot("slot1", DayOfWeek.MONDAY, 7, 1));
        a.setPinned(false);
        a.setSatisfiesRoomType("Standard");
        a.setAllRooms(roomList);
        if (rooms.length > 0) {
            a.setRoom(rooms[0]);
        }
        return a;
    }

    private static Move<SchoolSchedule> setRoomTo(CourseBlockAssignment assignment, Room to) {
        return new ChangeMove<>(ROOM_DESCRIPTOR, assignment, to);
    }

    @Test
    public void unassigningARoomIsRejectedWhenTheBlockHasRoomsToChooseFrom() {
        CourseBlockAssignment a = blockChoosingAmong(room("R1"), room("R2"));
        assertFalse("dropping the room must not be offered as a free way to shed room penalties",
                FILTER.accept(null, setRoomTo(a, null)));
    }

    @Test
    public void movingToADifferentRealRoomIsStillAllowed() {
        Room r2 = room("R2");
        CourseBlockAssignment a = blockChoosingAmong(room("R1"), r2);
        assertTrue("the block must stay free to move between its candidate rooms",
                FILTER.accept(null, setRoomTo(a, r2)));
    }

    @Test
    public void unassigningIsAllowedWhenTheBlockHasNoAssignableRoomAtAll() {
        // The legitimate case allowsUnassigned exists for: nothing to assign.
        CourseBlockAssignment a = blockChoosingAmong();
        a.setAllRooms(new ArrayList<>());
        assertTrue("a block with no candidate rooms must still be allowed to hold null",
                FILTER.accept(null, setRoomTo(a, null)));
    }

    @Test
    public void aRoomFixedBlocksRoomIsStillUntouchable() {
        // isRoomFixed() needs a genuine single-room CURATED range (or a teacher
        // requirement) - a one-entry allRooms list is just tier 3 (the full
        // type-filtered list) and is deliberately NOT "fixed".
        Room only = room("ONLY");
        Map<String, List<Room>> singleRoomRange =
                Collections.singletonMap("Standard", Collections.singletonList(only));
        Group group = new Group("G1", "Group 1", new HashSet<>(), singleRoomRange);

        CourseBlockAssignment a = new CourseBlockAssignment("a1", group, COURSE, 1);
        a.setTimeslot(new BlockTimeslot("slot1", DayOfWeek.MONDAY, 7, 1));
        a.setPinned(false);
        a.setSatisfiesRoomType("Standard");
        a.setAllRooms(new ArrayList<>(Arrays.asList(only, room("OTHER"))));
        a.setRoom(only);

        assertTrue("fixture must actually be room-fixed for this test to mean anything",
                a.isRoomFixed());
        assertFalse(FILTER.accept(null, setRoomTo(a, null)));
        assertFalse(FILTER.accept(null, setRoomTo(a, room("OTHER"))));
    }
}
