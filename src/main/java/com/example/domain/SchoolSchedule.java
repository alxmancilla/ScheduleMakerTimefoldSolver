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
    // @ValueRangeProvider(id = "teacherRange")
    @ProblemFactCollectionProperty
    private List<Teacher> teachers;

    @ValueRangeProvider(id = "blockTimeslotRange")
    @ProblemFactCollectionProperty
    private List<BlockTimeslot> blockTimeslots;

    // @ValueRangeProvider(id = "roomRange")
    @ProblemFactCollectionProperty
    private List<Room> rooms;

    @ProblemFactCollectionProperty
    private List<Course> courses;

    @ProblemFactCollectionProperty
    private List<Group> groups;

    @PlanningEntityCollectionProperty
    private List<CourseBlockAssignment> courseBlockAssignments;

    @PlanningScore
    private HardSoftScore score;

    public SchoolSchedule() {
        // No-arg constructor required by Timefold
    }

    /**
     * Constructor for block-based scheduling.
     *
     * @param teachers               list of teachers
     * @param blockTimeslots         list of block timeslots
     * @param rooms                  list of rooms
     * @param courses                list of courses
     * @param groups                 list of student groups
     * @param courseBlockAssignments list of course block assignments
     */
    public SchoolSchedule(List<Teacher> teachers, List<BlockTimeslot> blockTimeslots, List<Room> rooms,
            List<Course> courses, List<Group> groups, List<CourseBlockAssignment> courseBlockAssignments) {
        this.teachers = teachers;
        this.blockTimeslots = blockTimeslots;
        this.rooms = rooms;
        this.courses = courses;
        this.groups = groups;
        this.courseBlockAssignments = courseBlockAssignments;
    }

    public List<Teacher> getTeachers() {
        return teachers;
    }

    public List<BlockTimeslot> getBlockTimeslots() {
        return blockTimeslots;
    }

    public void setBlockTimeslots(List<BlockTimeslot> blockTimeslots) {
        this.blockTimeslots = blockTimeslots;
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

    public List<CourseBlockAssignment> getCourseBlockAssignments() {
        return courseBlockAssignments;
    }

    public void setCourseBlockAssignments(List<CourseBlockAssignment> courseBlockAssignments) {
        this.courseBlockAssignments = courseBlockAssignments;
    }

    public HardSoftScore getScore() {
        return score;
    }

    public void setScore(HardSoftScore score) {
        this.score = score;
    }

    /**
     * Static factory method for creating a block-based schedule.
     * Provides a convenient way to construct a SchoolSchedule instance.
     *
     * @param teachers               list of teachers
     * @param blockTimeslots         list of block timeslots
     * @param rooms                  list of rooms
     * @param courses                list of courses
     * @param groups                 list of student groups
     * @param courseBlockAssignments list of course block assignments
     * @return new SchoolSchedule instance for block-based scheduling
     */
    public static SchoolSchedule forBlockScheduling(
            List<Teacher> teachers,
            List<BlockTimeslot> blockTimeslots,
            List<Room> rooms,
            List<Course> courses,
            List<Group> groups,
            List<CourseBlockAssignment> courseBlockAssignments) {
        SchoolSchedule schedule = new SchoolSchedule();
        schedule.teachers = teachers;
        schedule.blockTimeslots = blockTimeslots;
        schedule.rooms = rooms;
        schedule.courses = courses;
        schedule.groups = groups;
        schedule.courseBlockAssignments = courseBlockAssignments;
        return schedule;
    }
}
