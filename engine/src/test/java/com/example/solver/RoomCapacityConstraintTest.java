package com.example.solver;

import static org.junit.Assert.*;

import com.example.analysis.BlockScheduleAnalyzer;
import com.example.domain.*;
import org.junit.Test;

import java.time.DayOfWeek;
import java.util.*;

/**
 * Verifies "Room capacity should fit group size" (SOFT, weight 4): a
 * violation only when BOTH room.capacity and group.studentCount are known
 * and the group's headcount exceeds the room's capacity. Exercised via
 * {@link BlockScheduleAnalyzer}, which mirrors the same boolean logic as
 * {@link SchoolConstraintProvider#roomCapacityShouldFitGroupSize}.
 */
public class RoomCapacityConstraintTest {

    private static final String CONSTRAINT_NAME = "Room capacity should fit group size";

    @Test
    public void groupLargerThanRoomCapacityIsAViolation() {
        int violations = violationCountFor(new Room("R1", "A", "estándar", 20), groupOfSize(25));
        assertEquals(1, violations);
    }

    @Test
    public void groupWithinRoomCapacityIsNotAViolation() {
        int violations = violationCountFor(new Room("R1", "A", "estándar", 30), groupOfSize(25));
        assertEquals(0, violations);
    }

    @Test
    public void groupExactlyAtCapacityIsNotAViolation() {
        int violations = violationCountFor(new Room("R1", "A", "estándar", 25), groupOfSize(25));
        assertEquals(0, violations);
    }

    @Test
    public void missingCapacityIsSkipped() {
        int violations = violationCountFor(new Room("R1", "A", "estándar"), groupOfSize(25));
        assertEquals(0, violations);
    }

    @Test
    public void missingStudentCountIsSkipped() {
        Group groupWithNoCount = new Group("G1", "Test Group", new HashSet<>());
        int violations = violationCountFor(new Room("R1", "A", "estándar", 20), groupWithNoCount);
        assertEquals(0, violations);
    }

    private Group groupOfSize(int size) {
        return new Group("G1", "Test Group", new HashSet<>(), null, size);
    }

    private int violationCountFor(Room room, Group group) {
        Teacher teacher = new Teacher("TEST", "Test", "Teacher", new HashSet<>(), new HashMap<>(), 40);
        Course course = new Course("1", "Test Course", "TEST", 2, "BASICAS", "estándar", 4, Boolean.TRUE);
        BlockTimeslot timeslot = new BlockTimeslot("slot1", DayOfWeek.MONDAY, 7, 1);

        CourseBlockAssignment assignment = new CourseBlockAssignment("a1", group, course, 1);
        assignment.setTimeslot(timeslot);
        assignment.setRoom(room);

        SchoolSchedule schedule = new SchoolSchedule(
                Collections.singletonList(teacher),
                Collections.singletonList(timeslot),
                Collections.singletonList(room),
                Collections.singletonList(course),
                Collections.singletonList(group),
                Collections.singletonList(assignment));

        Map<String, Integer> softViolations = BlockScheduleAnalyzer.analyzeSoftConstraintViolations(schedule);
        return softViolations.getOrDefault(CONSTRAINT_NAME, 0);
    }
}
