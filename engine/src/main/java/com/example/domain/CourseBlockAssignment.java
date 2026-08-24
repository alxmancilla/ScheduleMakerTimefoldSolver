package com.example.domain;

import ai.timefold.solver.core.api.domain.entity.PlanningEntity;
import ai.timefold.solver.core.api.domain.entity.PlanningPin;
import ai.timefold.solver.core.api.domain.valuerange.ValueRangeProvider;
import ai.timefold.solver.core.api.domain.variable.PlanningVariable;
import ai.timefold.solver.core.api.domain.lookup.PlanningId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

@PlanningEntity(difficultyComparatorClass = com.example.solver.BlockLengthDifficultyComparator.class)
public class CourseBlockAssignment {
    @PlanningId
    private String id;
    private Group group;
    private Course course;

    private int blockLength;

    @PlanningPin
    private boolean pinned;

    // @PlanningVariable(valueRangeProviderRefs = { "teacherRange" })
    private Teacher teacher;

    @PlanningVariable(valueRangeProviderRefs = { "matchingBlockTimeslotRange" })
    private BlockTimeslot timeslot;

    // Not a planning variable or shadow variable - just a reference to the
    // solution's full timeslot list, set once by the data-loading path
    // (DataLoader/DemoDataGenerator) so getMatchingBlockTimeslots() below can
    // filter it. This is the standard Timefold pattern for a planning
    // variable whose valid values depend on another field of the same
    // entity: the value range lives on the entity, not the solution, so a
    // 1h block's ChangeMove candidates can never include a 3h timeslot in
    // the first place - no move filter or pre-fill phase has to reject it
    // after the fact.
    private List<BlockTimeslot> allTimeslots;

    @PlanningVariable(valueRangeProviderRefs = { "matchingRoomRange" }, allowsUnassigned = true)
    private Room room;

    // Not a planning variable or shadow variable - the full room pool, set once
    // by the data-loading path (DataLoader/DemoDataGenerator), mirroring
    // allTimeslots above. getMatchingRooms() below filters it down per entity:
    // to a singleton (this entity's own current room) when the room is "fixed"
    // (see isRoomFixed()), or to the full type-compatible list otherwise - so a
    // fixed entity's room is structurally unreachable to change, the same way
    // a block-length mismatch is structurally unreachable for timeslot.
    private List<Room> allRooms;

    // NEW: Support for dual room requirements and custom block decomposition
    private String satisfiesRoomType; // Which room requirement this block satisfies
    private String preferredRoomHint; // Preferred room for soft constraint optimization

    public CourseBlockAssignment() {
        // No-arg constructor required by Timefold
    }

    public CourseBlockAssignment(String id, Group group, Course course, int blockLength) {
        this.id = id;
        this.group = group;
        this.course = course;
        this.blockLength = blockLength;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public Group getGroup() {
        return group;
    }

    public void setGroup(Group group) {
        this.group = group;
    }

    public Course getCourse() {
        return course;
    }

    public void setCourse(Course course) {
        this.course = course;
    }

    public int getBlockLength() {
        return blockLength;
    }

    public void setBlockLength(int blockLength) {
        this.blockLength = blockLength;
    }

    public Teacher getTeacher() {
        return teacher;
    }

    public void setTeacher(Teacher teacher) {
        this.teacher = teacher;
    }

    public BlockTimeslot getTimeslot() {
        return timeslot;
    }

    public void setTimeslot(BlockTimeslot timeslot) {
        this.timeslot = timeslot;
    }

    public List<BlockTimeslot> getAllTimeslots() {
        return allTimeslots;
    }

    public void setAllTimeslots(List<BlockTimeslot> allTimeslots) {
        this.allTimeslots = allTimeslots;
    }

    /**
     * This block's valid timeslot candidates: every timeslot whose length
     * matches blockLength. Entity-scoped value range, so a 1h block's
     * ChangeMove/construction-heuristic candidates can never include a 3h
     * timeslot - the mismatch this filters out is a hard constraint
     * (blockLengthMustMatchTimeslotLength) for a reason, but for movable
     * (non-pinned) assignments it should now be structurally unreachable
     * rather than merely penalized.
     */
    @ValueRangeProvider(id = "matchingBlockTimeslotRange")
    public List<BlockTimeslot> getMatchingBlockTimeslots() {
        if (allTimeslots == null) {
            return Collections.emptyList();
        }
        List<BlockTimeslot> matching = new ArrayList<>();
        for (BlockTimeslot candidate : allTimeslots) {
            if (candidate.getLengthHours() == blockLength) {
                matching.add(candidate);
            }
        }
        return matching;
    }

    public Room getRoom() {
        return room;
    }

    public void setRoom(Room room) {
        this.room = room;
    }

    public List<Room> getAllRooms() {
        return allRooms;
    }

    public void setAllRooms(List<Room> allRooms) {
        this.allRooms = allRooms;
    }

    /**
     * Whether this block's room has already been decided outside the solver:
     * true when the group has a preferred room, or the teacher has a required
     * room (which overrides the group's preference - see
     * BlockGenerationService.defaultRoomFor() in the web module for the same
     * priority order). Used both by getMatchingRooms() below (to collapse the
     * room value range to a singleton) and by BlockLengthDifficultyComparator
     * (to schedule fixed-room blocks before movable ones).
     */
    public boolean isRoomFixed() {
        return (group != null && group.getPreferredRoom() != null)
                || (teacher != null && teacher.getRequiredRoomName() != null);
    }

    /**
     * This block's valid room candidates. When isRoomFixed() is true, the
     * range collapses to a singleton derived from stable, solver-untouched
     * facts - group.getPreferredRoom() (already a resolved Room reference)
     * or teacher.getRequiredRoomName() (resolved against allRooms by name) -
     * deliberately NOT this entity's own current room field. Timefold's
     * construction heuristic nulls a variable before consulting its own
     * value-range provider to decide what to assign it, so a fixed-branch
     * that read `room` here would always see null and return an empty range,
     * permanently unassigning every fixed block (confirmed empirically: an
     * earlier version of this method did exactly that and left ~400 fixed
     * blocks roomless after one real solve). Otherwise every room whose type
     * satisfies this block's requirement.
     */
    @ValueRangeProvider(id = "matchingRoomRange")
    public List<Room> getMatchingRooms() {
        if (allRooms == null) {
            return Collections.emptyList();
        }
        // Teacher's required room takes precedence over the group's preferred
        // room (matches BlockGenerationService.defaultRoomFor()'s priority in
        // the web module: teacher's required room > group's preferred room).
        if (teacher != null && teacher.getRequiredRoomName() != null) {
            for (Room candidate : allRooms) {
                if (candidate.getName().equals(teacher.getRequiredRoomName())) {
                    return Collections.singletonList(candidate);
                }
            }
            return Collections.emptyList();
        }
        if (group != null && group.getPreferredRoom() != null) {
            return Collections.singletonList(group.getPreferredRoom());
        }
        if (satisfiesRoomType == null) {
            return allRooms;
        }
        List<Room> matching = new ArrayList<>();
        for (Room candidate : allRooms) {
            if (candidate.satisfiesRequirement(satisfiesRoomType)) {
                matching.add(candidate);
            }
        }
        return matching;
    }

    public boolean isPinned() {
        return pinned;
    }

    public void setPinned(boolean pinned) {
        this.pinned = pinned;
    }

    // NEW: Getters and setters for room requirement fields

    public String getSatisfiesRoomType() {
        return satisfiesRoomType;
    }

    public void setSatisfiesRoomType(String satisfiesRoomType) {
        this.satisfiesRoomType = satisfiesRoomType;
    }

    public String getPreferredRoomHint() {
        return preferredRoomHint;
    }

    public void setPreferredRoomHint(String preferredRoomHint) {
        this.preferredRoomHint = preferredRoomHint;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        CourseBlockAssignment that = (CourseBlockAssignment) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return String.format("%s [%s] slot=%s teacher=%s room=%s %s blockLength=%d",
                id, course, timeslot, teacher, room, pinned ? "PINNED" : "UNPINNED", blockLength);
    }
}
