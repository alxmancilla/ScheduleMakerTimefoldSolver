package com.example.solver;

import ai.timefold.solver.test.api.score.stream.ConstraintVerifier;
import com.example.domain.Course;
import com.example.domain.CourseAssignment;
import com.example.domain.Group;
import com.example.domain.Room;
import com.example.domain.SchoolSchedule;
import com.example.domain.Teacher;
import com.example.domain.Timeslot;
import java.time.DayOfWeek;
import java.util.Collections;
import java.util.Set;
import org.junit.Test;

public class SchoolConstraintProviderTest {

    private final ConstraintVerifier<SchoolConstraintProvider, SchoolSchedule> constraintVerifier = ConstraintVerifier
            .build(new SchoolConstraintProvider(), SchoolSchedule.class, CourseAssignment.class);

    // --- Hard Constraints ---

    @Test
    public void teacherMustBeQualified() {
        Teacher qualifiedTeacher = new Teacher("1", "Qualified Teacher", Set.of("Math"), Collections.emptyMap(), 20);
        Teacher unqualifiedTeacher = new Teacher("2", "Unqualified Teacher", Set.of("History"), Collections.emptyMap(),
                20);
        Course mathCourse = new Course("1", "Math", 3, "Math");
        Timeslot timeslot = new Timeslot(1, DayOfWeek.MONDAY, 9);
        Room room = new Room("1", "Room A", "Building X", "Standard", 30);

        CourseAssignment assignment = new CourseAssignment(1L, mathCourse,
                new Group("1", "Group A", Collections.emptyList(), null));
        assignment.setTeacher(unqualifiedTeacher);
        assignment.setTimeslot(timeslot);
        assignment.setRoom(room);

        constraintVerifier.verifyThat(SchoolConstraintProvider::teacherMustBeQualified)
                .given(assignment)
                .penalizesBy(1);

        assignment.setTeacher(qualifiedTeacher);
        constraintVerifier.verifyThat(SchoolConstraintProvider::teacherMustBeQualified)
                .given(assignment)
                .rewardedBy(0);
    }

    @Test
    public void noTeacherDoubleBooking() {
        Teacher teacher = new Teacher("1", "Teacher A", Set.of("Math"), Collections.emptyMap(), 20);
        Course course1 = new Course("1", "Math 101", 3, "Math");
        Course course2 = new Course("2", "Math 102", 3, "Math");
        Timeslot timeslot = new Timeslot(1, DayOfWeek.MONDAY, 9);
        Room room1 = new Room("1", "Room A", "Building X", "Standard", 30);
        Room room2 = new Room("2", "Room B", "Building X", "Standard", 30);

        CourseAssignment assignment1 = new CourseAssignment(1L, course1,
                new Group("1", "Group A", Collections.emptyList(), null));
        assignment1.setTeacher(teacher);
        assignment1.setTimeslot(timeslot);
        assignment1.setRoom(room1);

        CourseAssignment assignment2 = new CourseAssignment(2L, course2,
                new Group("2", "Group B", Collections.emptyList(), null));
        assignment2.setTeacher(teacher);
        assignment2.setTimeslot(timeslot);
        assignment2.setRoom(room2);

        constraintVerifier.verifyThat(SchoolConstraintProvider::noTeacherDoubleBooking)
                .given(assignment1, assignment2)
                .penalizesBy(1);

        assignment2.setTimeslot(new Timeslot(2, DayOfWeek.MONDAY, 10));
        constraintVerifier.verifyThat(SchoolConstraintProvider::noTeacherDoubleBooking)
                .given(assignment1, assignment2)
                .rewardedBy(0);
    }

    @Test
    public void noRoomDoubleBooking() {
        Teacher teacher1 = new Teacher("1", "Teacher A", Set.of("Math"), Collections.emptyMap(), 20);
        Teacher teacher2 = new Teacher("2", "Teacher B", Set.of("History"), Collections.emptyMap(), 20);
        Course course1 = new Course("1", "Math 101", 3, "Math");
        Course course2 = new Course("2", "History 101", 3, "History");
        Timeslot timeslot = new Timeslot(1, DayOfWeek.MONDAY, 9);
        Room room = new Room("1", "Room A", "Building X", "Standard", 30);

        CourseAssignment assignment1 = new CourseAssignment(1L, course1,
                new Group("1", "Group A", Collections.emptyList(), null));
        assignment1.setTeacher(teacher1);
        assignment1.setTimeslot(timeslot);
        assignment1.setRoom(room);

        CourseAssignment assignment2 = new CourseAssignment(2L, course2,
                new Group("2", "Group B", Collections.emptyList(), null));
        assignment2.setTeacher(teacher2);
        assignment2.setTimeslot(timeslot);
        assignment2.setRoom(room);

        constraintVerifier.verifyThat(SchoolConstraintProvider::noRoomDoubleBooking)
                .given(assignment1, assignment2)
                .penalizesBy(1);

        assignment2.setRoom(new Room("2", "Room B", "Building X", "Standard", 30));
        constraintVerifier.verifyThat(SchoolConstraintProvider::noRoomDoubleBooking)
                .given(assignment1, assignment2)
                .rewardedBy(0);
    }

    // --- Soft Constraints ---

    @Test
    public void minimizeTeacherIdleGaps() {
        Teacher teacher = new Teacher("1", "Teacher A", Set.of("Math"), Collections.emptyMap(), 20);
        Course course1 = new Course("1", "Math 101", 3, "Math");
        Course course2 = new Course("2", "Math 102", 3, "Math");
        Room room = new Room("1", "Room A", "Building X", "Standard", 30);

        // Two assignments on the same day with a gap of 1 hour (9am and 11am)
        CourseAssignment assignment1 = new CourseAssignment(1L, course1,
                new Group("1", "Group A", Collections.emptyList(), null));
        assignment1.setTeacher(teacher);
        assignment1.setTimeslot(new Timeslot(1, DayOfWeek.MONDAY, 9));
        assignment1.setRoom(room);

        CourseAssignment assignment2 = new CourseAssignment(2L, course2,
                new Group("2", "Group B", Collections.emptyList(), null));
        assignment2.setTeacher(teacher);
        assignment2.setTimeslot(new Timeslot(2, DayOfWeek.MONDAY, 11));
        assignment2.setRoom(room);

        constraintVerifier.verifyThat(SchoolConstraintProvider::minimizeTeacherIdleGaps)
                .given(assignment1, assignment2)
                .penalizesBy(1); // One hour gap

        // No gap
        assignment2.setTimeslot(new Timeslot(3, DayOfWeek.MONDAY, 10));
        constraintVerifier.verifyThat(SchoolConstraintProvider::minimizeTeacherIdleGaps)
                .given(assignment1, assignment2)
                .rewardedBy(0);

        // Different days, so no gap penalty
        assignment2.setTimeslot(new Timeslot(4, DayOfWeek.TUESDAY, 9));
        constraintVerifier.verifyThat(SchoolConstraintProvider::minimizeTeacherIdleGaps)
                .given(assignment1, assignment2)
                .rewardedBy(0);
    }

    @Test
    public void minimizeTeacherBuildingChanges() {
        Teacher teacher = new Teacher("1", "Teacher A", Set.of("Math"), Collections.emptyMap(), 20);
        Course course1 = new Course("1", "Math 101", 3, "Math");
        Course course2 = new Course("2", "Math 102", 3, "Math");
        Timeslot timeslot1 = new Timeslot(1, DayOfWeek.MONDAY, 9);
        Timeslot timeslot2 = new Timeslot(2, DayOfWeek.MONDAY, 10);
        Room roomBuildingA = new Room("1", "Room A", "Building A", "Standard", 30);
        Room roomBuildingB = new Room("2", "Room B", "Building B", "Standard", 30);

        CourseAssignment assignment1 = new CourseAssignment(1L, course1,
                new Group("1", "Group A", Collections.emptyList(), null));
        assignment1.setTeacher(teacher);
        assignment1.setTimeslot(timeslot1);
        assignment1.setRoom(roomBuildingA);

        CourseAssignment assignment2 = new CourseAssignment(2L, course2,
                new Group("2", "Group B", Collections.emptyList(), null));
        assignment2.setTeacher(teacher);
        assignment2.setTimeslot(timeslot2);
        assignment2.setRoom(roomBuildingB);

        // Consecutive assignments in different buildings should be penalized
        constraintVerifier.verifyThat(SchoolConstraintProvider::minimizeTeacherBuildingChanges)
                .given(assignment1, assignment2)
                .penalizesBy(1);

        // Same building, no penalty
        assignment2.setRoom(new Room("3", "Room C", "Building A", "Standard", 30));
        constraintVerifier.verifyThat(SchoolConstraintProvider::minimizeTeacherBuildingChanges)
                .given(assignment1, assignment2)
                .rewardedBy(0);
    }
}