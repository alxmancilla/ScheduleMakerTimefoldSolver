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

    // @PlanningVariable(valueRangeProviderRefs = { "roomRange" })
    private Room room;

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
