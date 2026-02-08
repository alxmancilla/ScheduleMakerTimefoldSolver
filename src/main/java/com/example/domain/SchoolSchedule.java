package com.example.domain;

import ai.timefold.solver.core.api.domain.solution.PlanningEntityCollectionProperty;
import ai.timefold.solver.core.api.domain.solution.ProblemFactCollectionProperty;
import ai.timefold.solver.core.api.domain.solution.PlanningSolution;
import ai.timefold.solver.core.api.domain.valuerange.ValueRangeProvider;
import ai.timefold.solver.core.api.score.buildin.hardsoft.HardSoftScore;
import ai.timefold.solver.core.api.domain.solution.PlanningScore;
import java.util.List;

@PlanningSolution
public class SchoolSchedule {
    @ValueRangeProvider(id = "teacherRange")
    @ProblemFactCollectionProperty
    private List<Teacher> teachers;

    @ValueRangeProvider(id = "timeslotRange")
    @ProblemFactCollectionProperty
    private List<Timeslot> timeslots;

    @ValueRangeProvider(id = "roomRange")
    @ProblemFactCollectionProperty
    private List<Room> rooms;

    @ProblemFactCollectionProperty
    private List<Course> courses;

    @ProblemFactCollectionProperty
    private List<Group> groups;

    @PlanningEntityCollectionProperty
    private List<CourseAssignment> courseAssignments;

    @PlanningScore
    private HardSoftScore score;

    public SchoolSchedule() {
        // No-arg constructor required by Timefold
    }

    public SchoolSchedule(List<Teacher> teachers, List<Timeslot> timeslots, List<Room> rooms,
            List<Course> courses, List<Group> groups, List<CourseAssignment> courseAssignments) {
        this.teachers = teachers;
        this.timeslots = timeslots;
        this.rooms = rooms;
        this.courses = courses;
        this.groups = groups;
        this.courseAssignments = courseAssignments;
    }

    public List<Teacher> getTeachers() {
        return teachers;
    }

    public List<Timeslot> getTimeslots() {
        return timeslots;
    }

    public List<Room> getRooms() {
        return rooms;
    }

    public List<Course> getCourses() {
        return courses;
    }

    public List<Group> getGroups() {
        return groups;
    }

    public List<CourseAssignment> getCourseAssignments() {
        return courseAssignments;
    }

    public void setCourseAssignments(List<CourseAssignment> courseAssignments) {
        this.courseAssignments = courseAssignments;
    }

    public HardSoftScore getScore() {
        return score;
    }

    public void setScore(HardSoftScore score) {
        this.score = score;
    }
}
