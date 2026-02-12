package com.example.domain;

import ai.timefold.solver.core.api.domain.entity.PlanningEntity;
import ai.timefold.solver.core.api.domain.entity.PlanningPin;
import ai.timefold.solver.core.api.domain.variable.PlanningVariable;
import ai.timefold.solver.core.api.domain.lookup.PlanningId;
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

    @PlanningVariable(valueRangeProviderRefs = { "blockTimeslotRange" })
    private BlockTimeslot timeslot;

    // @PlanningVariable(valueRangeProviderRefs = { "roomRange" })
    private Room room;

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
