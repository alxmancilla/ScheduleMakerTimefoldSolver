package com.example.domain;

import org.junit.Test;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;

import static org.junit.Assert.*;

/**
 * Verifies {@link CourseBlockAssignment#isRoomFixed()} and
 * {@link CourseBlockAssignment#getMatchingRooms()} - the entity-scoped room
 * value range that makes a "fixed" block's room structurally unreachable to
 * the solver, mirroring how {@link CourseBlockAssignment#getMatchingBlockTimeslots()}
 * already does for {@code timeslot}/{@code blockLength}.
 */
public class CourseBlockAssignmentTest {

    private static final Course COURSE = new Course("1", "Test Course", "TEST", 2, "BASICAS", "estándar", 4,
            Boolean.TRUE);

    private static Teacher teacherWithRequiredRoom(String requiredRoomName) {
        Teacher teacher = new Teacher("T1", "Test", "Teacher", new HashSet<>(), new HashMap<>(), 40);
        teacher.setRequiredRoomName(requiredRoomName);
        return teacher;
    }

    private CourseBlockAssignment assignment(Group group, Teacher teacher, List<Room> allRooms,
            String satisfiesRoomType) {
        CourseBlockAssignment a = new CourseBlockAssignment("a1", group, COURSE, 1);
        a.setTeacher(teacher);
        a.setAllRooms(allRooms);
        a.setSatisfiesRoomType(satisfiesRoomType);
        return a;
    }

    @Test
    public void groupWithPreferredRoom_isFixedToASingletonOfIt() {
        Room preferred = new Room("AULA 1", "A", "Standard");
        Room other = new Room("AULA 2", "A", "Standard");
        Group group = new Group("G1", "Group 1", new HashSet<>(), preferred, null);

        CourseBlockAssignment a = assignment(group, null, List.of(preferred, other), "Standard");

        assertTrue(a.isRoomFixed());
        assertEquals(List.of(preferred), a.getMatchingRooms());
    }

    @Test
    public void teacherWithRequiredRoom_isFixedToASingletonOfIt_evenWithADifferentGroupPreference() {
        Room required = new Room("AULA 1", "A", "Standard");
        Room groupPreferred = new Room("AULA 2", "A", "Standard");
        Teacher teacher = teacherWithRequiredRoom("AULA 1");
        Group group = new Group("G1", "Group 1", new HashSet<>(), groupPreferred, null);

        CourseBlockAssignment a = assignment(group, teacher, List.of(required, groupPreferred), "Standard");

        // Teacher's required room wins over the group's own (different) preference.
        assertTrue(a.isRoomFixed());
        assertEquals(List.of(required), a.getMatchingRooms());
    }

    @Test
    public void neitherGroupNorTeacherHasAPreference_rangeIsTheFullTypeFilteredList() {
        Room standard1 = new Room("AULA 1", "A", "Standard");
        Room standard2 = new Room("AULA 2", "A", "Standard");
        Room mixed = new Room("LQ 1", "A", "Mixed");
        Group group = new Group("G1", "Group 1", new HashSet<>());

        CourseBlockAssignment a = assignment(group, null, List.of(standard1, standard2, mixed), "Standard");

        assertFalse(a.isRoomFixed());
        // Mixed also satisfies Standard, so all three are valid candidates.
        assertEquals(3, a.getMatchingRooms().size());
    }

    @Test
    public void neitherGroupNorTeacherHasAPreference_rangeExcludesIncompatibleRoomTypes() {
        Room standard = new Room("AULA 1", "A", "Standard");
        Room computerLab = new Room("CC 1", "A", "Specialized - Computer Lab");
        Group group = new Group("G1", "Group 1", new HashSet<>());

        CourseBlockAssignment a = assignment(group, null, List.of(standard, computerLab), "Specialized - Computer Lab");

        assertFalse(a.isRoomFixed());
        assertEquals(List.of(computerLab), a.getMatchingRooms());
    }

    @Test
    public void teacherRequiredRoomNotFoundAmongKnownRooms_rangeIsEmpty() {
        Teacher teacher = teacherWithRequiredRoom("DELETED-ROOM");
        Room onlyKnownRoom = new Room("AULA 1", "A", "Standard");
        Group group = new Group("G1", "Group 1", new HashSet<>());

        CourseBlockAssignment a = assignment(group, teacher, List.of(onlyKnownRoom), "Standard");

        assertTrue(a.isRoomFixed());
        assertTrue(a.getMatchingRooms().isEmpty());
    }

    @Test
    public void allRoomsNotYetSet_rangeIsEmpty() {
        Group group = new Group("G1", "Group 1", new HashSet<>());
        CourseBlockAssignment a = new CourseBlockAssignment("a1", group, COURSE, 1);
        // setAllRooms() never called.
        assertTrue(a.getMatchingRooms().isEmpty());
    }
}
