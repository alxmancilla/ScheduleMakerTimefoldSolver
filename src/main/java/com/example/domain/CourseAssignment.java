package com.example.domain;

import ai.timefold.solver.core.api.domain.entity.PlanningEntity;
import ai.timefold.solver.core.api.domain.variable.PlanningVariable;
import ai.timefold.solver.core.api.domain.lookup.PlanningId;
import java.util.Objects;

@PlanningEntity
public class CourseAssignment {
    @PlanningId
    private String id;
    private Group group;
    private Course course;
    private int sequenceIndex; // Which hour of the course (0, 1, 2, etc.)

    // @PlanningVariable(valueRangeProviderRefs = { "teacherRange" })
    private Teacher teacher;

    @PlanningVariable(valueRangeProviderRefs = { "timeslotRange" })
    private Timeslot timeslot;

    // @PlanningVariable(valueRangeProviderRefs = { "roomRange" })
    private Room room;

    public CourseAssignment() {
        // No-arg constructor required by Timefold
    }

    public CourseAssignment(String id, Group group, Course course, int sequenceIndex) {
        this.id = id;
        this.group = group;
        this.course = course;
        this.sequenceIndex = sequenceIndex;
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

    public int getSequenceIndex() {
        return sequenceIndex;
    }

    public void setSequenceIndex(int sequenceIndex) {
        this.sequenceIndex = sequenceIndex;
    }

    public Teacher getTeacher() {
        return teacher;
    }

    public void setTeacher(Teacher teacher) {
        this.teacher = teacher;
    }

    public Timeslot getTimeslot() {
        return timeslot;
    }

    public void setTimeslot(Timeslot timeslot) {
        this.timeslot = timeslot;
    }

    public Room getRoom() {
        return room;
    }

    public void setRoom(Room room) {
        this.room = room;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        CourseAssignment that = (CourseAssignment) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return String.format("%s [%s] slot=%s teacher=%s room=%s",
                id, course, timeslot, teacher, room);
    }
}
