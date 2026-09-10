package com.example.domain;

import org.junit.Test;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

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

    /** A group with no curated room range for any type. */
    private static Group groupWithNoRange(String id) {
        return new Group(id, "Group " + id, new HashSet<>());
    }

    /** A group whose only curated range is roomType -> the given rooms. */
    private static Group groupWithRange(String id, String roomType, Room... rooms) {
        Map<String, List<Room>> ranges = new HashMap<>();
        ranges.put(roomType, List.of(rooms));
        return new Group(id, "Group " + id, new HashSet<>(), ranges, null);
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
    public void groupWithSingleRoomRange_isFixedToASingletonOfIt() {
        Room preferred = new Room("AULA 1", "A", "Standard");
        Room other = new Room("AULA 2", "A", "Standard");
        Group group = groupWithRange("G1", "Standard", preferred);

        CourseBlockAssignment a = assignment(group, null, List.of(preferred, other), "Standard");

        assertTrue(a.isRoomFixed());
        assertEquals(List.of(preferred), a.getMatchingRooms());
    }

    @Test
    public void groupWithMultiRoomRange_isMovableButNarrowedToThatRange() {
        // A group that can move between two equivalent rooms for this room
        // type - genuinely movable (not "fixed"), but the solver only ever
        // sees these two candidates, not the whole type-filtered room list.
        Room roomA = new Room("AULA 1", "A", "Standard");
        Room roomB = new Room("AULA 2", "A", "Standard");
        Room outsideRange = new Room("AULA 3", "A", "Standard");
        Group group = groupWithRange("G1", "Standard", roomA, roomB);

        CourseBlockAssignment a = assignment(group, null, List.of(roomA, roomB, outsideRange), "Standard");

        assertFalse(a.isRoomFixed());
        List<Room> matching = a.getMatchingRooms();
        assertEquals(2, matching.size());
        assertTrue(matching.containsAll(List.of(roomA, roomB)));
        assertFalse(matching.contains(outsideRange));
    }

    @Test
    public void groupRangeCuratedForADifferentType_fallsThroughToFullTypeFilteredList() {
        // The group curated a range for Standard courses, but this block
        // needs Mixed - a room type the group hasn't curated anything for -
        // so it falls through to the unrestricted, full type-filtered list.
        Room standardPreferred = new Room("AULA 1", "A", "Standard");
        Room mixedRoom = new Room("LQ 1", "A", "Mixed");
        Group group = groupWithRange("G1", "Standard", standardPreferred);

        CourseBlockAssignment a = assignment(group, null, List.of(standardPreferred, mixedRoom), "Mixed");

        assertFalse(a.isRoomFixed());
        assertEquals(List.of(mixedRoom), a.getMatchingRooms());
    }

    @Test
    public void teacherWithRequiredRoom_isFixedToASingletonOfIt_evenWithADifferentGroupRange() {
        Room required = new Room("AULA 1", "A", "Standard");
        Room groupPreferred = new Room("AULA 2", "A", "Standard");
        Teacher teacher = teacherWithRequiredRoom("AULA 1");
        Group group = groupWithRange("G1", "Standard", groupPreferred);

        CourseBlockAssignment a = assignment(group, teacher, List.of(required, groupPreferred), "Standard");

        // Teacher's required room wins over the group's own (different) range.
        assertTrue(a.isRoomFixed());
        assertEquals(List.of(required), a.getMatchingRooms());
    }

    @Test
    public void neitherGroupNorTeacherHasAPreference_rangeIsTheFullTypeFilteredList() {
        Room standard1 = new Room("AULA 1", "A", "Standard");
        Room standard2 = new Room("AULA 2", "A", "Standard");
        Room mixed = new Room("LQ 1", "A", "Mixed");
        Group group = groupWithNoRange("G1");

        CourseBlockAssignment a = assignment(group, null, List.of(standard1, standard2, mixed), "Standard");

        assertFalse(a.isRoomFixed());
        // Mixed also satisfies Standard, so all three are valid candidates.
        assertEquals(3, a.getMatchingRooms().size());
    }

    @Test
    public void neitherGroupNorTeacherHasAPreference_rangeExcludesIncompatibleRoomTypes() {
        Room standard = new Room("AULA 1", "A", "Standard");
        Room computerLab = new Room("CC 1", "A", "Specialized - Computer Lab");
        Group group = groupWithNoRange("G1");

        CourseBlockAssignment a = assignment(group, null, List.of(standard, computerLab), "Specialized - Computer Lab");

        assertFalse(a.isRoomFixed());
        assertEquals(List.of(computerLab), a.getMatchingRooms());
    }

    @Test
    public void teacherRequiredRoomNotFoundAmongKnownRooms_rangeIsEmpty() {
        Teacher teacher = teacherWithRequiredRoom("DELETED-ROOM");
        Room onlyKnownRoom = new Room("AULA 1", "A", "Standard");
        Group group = groupWithNoRange("G1");

        CourseBlockAssignment a = assignment(group, teacher, List.of(onlyKnownRoom), "Standard");

        assertTrue(a.isRoomFixed());
        assertTrue(a.getMatchingRooms().isEmpty());
    }

    @Test
    public void allRoomsNotYetSet_rangeIsEmpty() {
        Group group = groupWithNoRange("G1");
        CourseBlockAssignment a = new CourseBlockAssignment("a1", group, COURSE, 1);
        // setAllRooms() never called.
        assertTrue(a.getMatchingRooms().isEmpty());
    }

    @Test
    public void teacherRequiredRoomWrongTypeForThisBlock_fallsBackToGroupsRange() {
        // A teacher who mostly needs a Computer Lab (e.g. for a PLC course)
        // but also teaches this Mixed-type block shouldn't have that block
        // locked to the computer lab - it should fall back to the group's
        // (compatible) curated range instead, exactly as if the teacher had
        // no requirement at all for this particular block.
        Room cc3 = new Room("CC3", "A", "Specialized - Computer Lab");
        Room groupPreferred = new Room("TEM1", "A", "Mixed");
        Teacher teacher = teacherWithRequiredRoom("CC3");
        Group group = groupWithRange("G1", "Mixed", groupPreferred);

        CourseBlockAssignment a = assignment(group, teacher, List.of(cc3, groupPreferred), "Mixed");

        assertTrue(a.isRoomFixed());
        assertEquals(List.of(groupPreferred), a.getMatchingRooms());
    }

    @Test
    public void teacherRequiredRoomWrongType_andGroupHasNoRange_fallsBackToFullTypeFilteredList() {
        Room cc3 = new Room("CC3", "A", "Specialized - Computer Lab");
        Room standard = new Room("AULA 1", "A", "Standard");
        Teacher teacher = teacherWithRequiredRoom("CC3");
        Group group = groupWithNoRange("G1");

        CourseBlockAssignment a = assignment(group, teacher, List.of(cc3, standard), "Standard");

        assertFalse(a.isRoomFixed());
        assertEquals(List.of(standard), a.getMatchingRooms());
    }

    @Test
    public void teacherRequiredRoomWrongType_andGroupsRangeAlsoWrongType_fallsBackToFullTypeFilteredList() {
        // A misfiled range (a Standard room curated under the "Mixed" key -
        // a data error) doesn't satisfy this Mixed-type block either, so
        // both the teacher's and the group's fixed candidates are filtered
        // out, falling all the way through to the full type-filtered list.
        Room cc3 = new Room("CC3", "A", "Specialized - Computer Lab");
        Room misfiledRoom = new Room("AULA 1", "A", "Standard");
        Room otherMixed = new Room("LQ1", "A", "Mixed");
        Teacher teacher = teacherWithRequiredRoom("CC3");
        Group group = groupWithRange("G1", "Mixed", misfiledRoom);

        CourseBlockAssignment a = assignment(group, teacher, List.of(cc3, misfiledRoom, otherMixed), "Mixed");

        assertFalse(a.isRoomFixed());
        assertEquals(List.of(otherMixed), a.getMatchingRooms());
    }

    @Test
    public void teacherRequiredRoomRightTypeForThisBlock_winsOverGroupsRangeForADifferentType() {
        // The genuine "needs the computer lab for this specific course" case:
        // the teacher's required room matches this block's type, so it still
        // wins even though the group has a range curated for a different type.
        Room cc3 = new Room("CC3", "A", "Specialized - Computer Lab");
        Room groupPreferred = new Room("TEM1", "A", "Mixed");
        Teacher teacher = teacherWithRequiredRoom("CC3");
        Group group = groupWithRange("G1", "Mixed", groupPreferred);

        CourseBlockAssignment a = assignment(group, teacher, List.of(cc3, groupPreferred),
                "Specialized - Computer Lab");

        assertTrue(a.isRoomFixed());
        assertEquals(List.of(cc3), a.getMatchingRooms());
    }
}
