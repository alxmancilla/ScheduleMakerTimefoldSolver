package com.example.validation;

import com.example.domain.BlockTimeslot;
import com.example.domain.Course;
import com.example.domain.CourseBlockAssignment;
import com.example.domain.Group;
import com.example.domain.Room;
import com.example.domain.SchoolSchedule;
import com.example.domain.Teacher;
import org.junit.Test;

import java.time.DayOfWeek;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.*;

/**
 * Verifies that {@link PreSolveValidator} catches invalid pinned assignments
 * that the solver would otherwise silently accept (pinned blocks are excluded
 * from the solver's hard constraints).
 */
public class PreSolveValidatorTest {

    private static final Course MATH = new Course("1", "Matemáticas", "MAT", 1, "BASICAS", "estándar", 4, true);

    /** Teacher qualified for Matemáticas, available Mon 7:00-14:00. */
    private static Teacher qualifiedAvailableTeacher() {
        Set<String> quals = new HashSet<>(Arrays.asList("Matemáticas"));
        Map<DayOfWeek, Set<Integer>> avail = new HashMap<>();
        Set<Integer> hours = new HashSet<>();
        for (int h = 7; h < 14; h++) {
            hours.add(h);
        }
        avail.put(DayOfWeek.MONDAY, hours);
        return new Teacher("T1", "Ada", "Lovelace", quals, avail, 40);
    }

    private static SchoolSchedule scheduleWith(CourseBlockAssignment... assignments) {
        List<CourseBlockAssignment> list = new ArrayList<>(Arrays.asList(assignments));
        return SchoolSchedule.forBlockScheduling(new ArrayList<>(), new ArrayList<>(), new ArrayList<>(),
                new ArrayList<>(), new ArrayList<>(), list);
    }

    private static CourseBlockAssignment pinnedBlock(String id, int blockLength, BlockTimeslot slot, Room room,
            Teacher teacher, String satisfiesRoomType) {
        return pinnedBlock(id, blockLength, slot, room, teacher, satisfiesRoomType,
                new Group("G1", "Group 1", new HashSet<>()));
    }

    /** Same as above, but with an explicit group - needed to isolate the group-clash check from the others. */
    private static CourseBlockAssignment pinnedBlock(String id, int blockLength, BlockTimeslot slot, Room room,
            Teacher teacher, String satisfiesRoomType, Group group) {
        CourseBlockAssignment a = new CourseBlockAssignment(id, group, MATH, blockLength);
        a.setTimeslot(slot);
        a.setRoom(room);
        a.setTeacher(teacher);
        a.setSatisfiesRoomType(satisfiesRoomType);
        a.setPinned(true);
        return a;
    }

    /** A second teacher, qualified and available like {@link #qualifiedAvailableTeacher()}, for two-teacher tests. */
    private static Teacher secondQualifiedAvailableTeacher() {
        Set<String> quals = new HashSet<>(Arrays.asList("Matemáticas"));
        Map<DayOfWeek, Set<Integer>> avail = new HashMap<>();
        avail.put(DayOfWeek.MONDAY, new HashSet<>(Arrays.asList(7, 8, 9, 10)));
        return new Teacher("T2", "Bob", "Smith", quals, avail, 40);
    }

    @Test
    public void validPinnedAssignmentPasses() {
        BlockTimeslot slot = new BlockTimeslot("s1", DayOfWeek.MONDAY, 7, 2);
        Room room = new Room("AULA 1", "A", "estándar");
        SchoolSchedule schedule = scheduleWith(
                pinnedBlock("A1", 2, slot, room, qualifiedAvailableTeacher(), "estándar"));
        assertTrue(PreSolveValidator.validate(schedule).isValid());
    }

    @Test
    public void unpinnedInvalidAssignmentsAreIgnored() {
        BlockTimeslot slot = new BlockTimeslot("s1", DayOfWeek.MONDAY, 7, 3);
        Room room = new Room("AULA 1", "A", "estándar");
        CourseBlockAssignment a = pinnedBlock("A1", 2, slot, room, qualifiedAvailableTeacher(), "mixto");
        a.setPinned(false); // solver handles unpinned blocks; validator must skip them
        assertTrue(PreSolveValidator.validate(scheduleWith(a)).isValid());
    }

    @Test
    public void blockLengthMismatchIsReported() {
        BlockTimeslot slot = new BlockTimeslot("s1", DayOfWeek.MONDAY, 7, 3);
        Room room = new Room("AULA 1", "A", "estándar");
        SchoolSchedule schedule = scheduleWith(
                pinnedBlock("A1", 2, slot, room, qualifiedAvailableTeacher(), "estándar"));
        ValidationResult r = PreSolveValidator.validate(schedule);
        assertFalse(r.isValid());
        assertTrue(r.getProblems().stream().anyMatch(p -> p.contains("block length")));
    }

    @Test
    public void roomTypeMismatchIsReported() {
        BlockTimeslot slot = new BlockTimeslot("s1", DayOfWeek.MONDAY, 7, 2);
        Room standard = new Room("AULA 1", "A", "estándar"); // cannot satisfy mixto
        SchoolSchedule schedule = scheduleWith(
                pinnedBlock("A1", 2, slot, standard, qualifiedAvailableTeacher(), "mixto"));
        ValidationResult r = PreSolveValidator.validate(schedule);
        assertFalse(r.isValid());
        assertTrue(r.getProblems().stream().anyMatch(p -> p.contains("room type")));
    }

    @Test
    public void unqualifiedTeacherIsReported() {
        BlockTimeslot slot = new BlockTimeslot("s1", DayOfWeek.MONDAY, 7, 2);
        Room room = new Room("AULA 1", "A", "estándar");
        Map<DayOfWeek, Set<Integer>> avail = new HashMap<>();
        avail.put(DayOfWeek.MONDAY, new HashSet<>(Arrays.asList(7, 8, 9)));
        Teacher unqualified = new Teacher("T2", "Bob", "Smith", new HashSet<>(), avail, 40);
        ValidationResult r = PreSolveValidator.validate(
                scheduleWith(pinnedBlock("A1", 2, slot, room, unqualified, "estándar")));
        assertFalse(r.isValid());
        assertTrue(r.getProblems().stream().anyMatch(p -> p.contains("not qualified")));
    }

    @Test
    public void unavailableTeacherIsReported() {
        BlockTimeslot slot = new BlockTimeslot("s1", DayOfWeek.TUESDAY, 7, 2); // teacher only free Monday
        Room room = new Room("AULA 1", "A", "estándar");
        ValidationResult r = PreSolveValidator.validate(
                scheduleWith(pinnedBlock("A1", 2, slot, room, qualifiedAvailableTeacher(), "estándar")));
        assertFalse(r.isValid());
        assertTrue(r.getProblems().stream().anyMatch(p -> p.contains("not available")));
    }

    @Test
    public void pinnedTeacherDoubleBookingIsReported() {
        Teacher t = qualifiedAvailableTeacher();
        Room r1 = new Room("AULA 1", "A", "estándar");
        Room r2 = new Room("AULA 2", "A", "estándar");
        BlockTimeslot s1 = new BlockTimeslot("s1", DayOfWeek.MONDAY, 7, 2);
        BlockTimeslot s2 = new BlockTimeslot("s2", DayOfWeek.MONDAY, 8, 2); // overlaps 8-9
        CourseBlockAssignment a1 = pinnedBlock("A1", 2, s1, r1, t, "estándar");
        CourseBlockAssignment a2 = pinnedBlock("A2", 2, s2, r2, t, "estándar");
        ValidationResult r = PreSolveValidator.validate(scheduleWith(a1, a2));
        assertFalse(r.isValid());
        assertTrue(r.getProblems().stream().anyMatch(p -> p.contains("teacher double-booking")));
    }

    @Test
    public void pinnedGroupClashIsReported() {
        // Same group, different teachers and rooms, so only the group-clash branch fires.
        Teacher t1 = qualifiedAvailableTeacher();
        Teacher t2 = secondQualifiedAvailableTeacher();
        Room r1 = new Room("AULA 1", "A", "estándar");
        Room r2 = new Room("AULA 2", "A", "estándar");
        BlockTimeslot s1 = new BlockTimeslot("s1", DayOfWeek.MONDAY, 7, 2);
        BlockTimeslot s2 = new BlockTimeslot("s2", DayOfWeek.MONDAY, 8, 2); // overlaps 8-9
        Group group = new Group("G1", "Group 1", new HashSet<>());
        CourseBlockAssignment a1 = pinnedBlock("A1", 2, s1, r1, t1, "estándar", group);
        CourseBlockAssignment a2 = pinnedBlock("A2", 2, s2, r2, t2, "estándar", group);

        ValidationResult r = PreSolveValidator.validate(scheduleWith(a1, a2));
        assertFalse(r.isValid());
        assertTrue(r.getProblems().stream().anyMatch(p -> p.contains("Pinned group clash")));
        assertFalse(r.getProblems().stream().anyMatch(p -> p.contains("teacher double-booking")));
        assertFalse(r.getProblems().stream().anyMatch(p -> p.contains("room double-booking")));
    }

    @Test
    public void pinnedRoomDoubleBookingIsReported() {
        // Same room, different teachers and groups, so only the room-double-booking branch fires.
        Teacher t1 = qualifiedAvailableTeacher();
        Teacher t2 = secondQualifiedAvailableTeacher();
        Room sharedRoom = new Room("AULA 1", "A", "estándar");
        BlockTimeslot s1 = new BlockTimeslot("s1", DayOfWeek.MONDAY, 7, 2);
        BlockTimeslot s2 = new BlockTimeslot("s2", DayOfWeek.MONDAY, 8, 2); // overlaps 8-9
        Group g1 = new Group("G1", "Group 1", new HashSet<>());
        Group g2 = new Group("G2", "Group 2", new HashSet<>());
        CourseBlockAssignment a1 = pinnedBlock("A1", 2, s1, sharedRoom, t1, "estándar", g1);
        CourseBlockAssignment a2 = pinnedBlock("A2", 2, s2, sharedRoom, t2, "estándar", g2);

        ValidationResult r = PreSolveValidator.validate(scheduleWith(a1, a2));
        assertFalse(r.isValid());
        assertTrue(r.getProblems().stream().anyMatch(p -> p.contains("Pinned room double-booking")));
        assertFalse(r.getProblems().stream().anyMatch(p -> p.contains("teacher double-booking")));
        assertFalse(r.getProblems().stream().anyMatch(p -> p.contains("group clash")));
    }

    @Test
    public void missingTimeslotIsReported() {
        Room room = new Room("AULA 1", "A", "estándar");
        CourseBlockAssignment a = pinnedBlock("A1", 2, null, room, qualifiedAvailableTeacher(), "estándar");
        ValidationResult r = PreSolveValidator.validate(scheduleWith(a));
        assertFalse(r.isValid());
        assertTrue(r.getProblems().stream().anyMatch(p -> p.contains("no timeslot")));
    }

    @Test
    public void missingRoomIsReported() {
        BlockTimeslot slot = new BlockTimeslot("s1", DayOfWeek.MONDAY, 7, 2);
        CourseBlockAssignment a = pinnedBlock("A1", 2, slot, null, qualifiedAvailableTeacher(), "estándar");
        ValidationResult r = PreSolveValidator.validate(scheduleWith(a));
        assertFalse(r.isValid());
        assertTrue(r.getProblems().stream().anyMatch(p -> p.contains("no room")));
    }

    // ---- Capacity warning: assigned hours vs total availability, pinned or not ----

    @Test
    public void teacherOverCapacity_returnsWarningButStaysValid() {
        // qualifiedAvailableTeacher() has 7h of availability (Mon 7-14).
        Teacher teacher = qualifiedAvailableTeacher();
        Room room = new Room("AULA 1", "A", "estándar");
        BlockTimeslot s1 = new BlockTimeslot("s1", DayOfWeek.MONDAY, 7, 4);
        BlockTimeslot s2 = new BlockTimeslot("s2", DayOfWeek.MONDAY, 11, 4); // 4h+4h=8h > 7h available
        CourseBlockAssignment a1 = pinnedBlock("A1", 4, s1, room, teacher, "estándar");
        CourseBlockAssignment a2 = pinnedBlock("A2", 4, s2, room, teacher, "estándar");
        a2.setPinned(false); // capacity check must count movable assignments too

        ValidationResult r = PreSolveValidator.validate(scheduleWith(a1, a2));
        // The pinned block alone (4h) doesn't overlap/violate anything pinned-specific,
        // and the movable block is skipped by the pinned-only checks above - so this
        // is purely a capacity warning, not a blocking problem.
        assertTrue(r.isValid());
        assertEquals(1, r.getWarnings().size());
        String warning = r.getWarnings().get(0);
        assertTrue(warning.contains("T1"));
        assertTrue(warning.contains("8h/week"));
        assertTrue(warning.contains("7h/week"));
        assertTrue(warning.contains("short by 1h"));
    }

    @Test
    public void teacherWithinCapacity_noWarning() {
        Teacher teacher = qualifiedAvailableTeacher(); // 7h available
        Room room = new Room("AULA 1", "A", "estándar");
        BlockTimeslot slot = new BlockTimeslot("s1", DayOfWeek.MONDAY, 7, 4);
        CourseBlockAssignment a = pinnedBlock("A1", 4, slot, room, teacher, "estándar");

        ValidationResult r = PreSolveValidator.validate(scheduleWith(a));
        assertTrue(r.getWarnings().isEmpty());
    }

    @Test
    public void describeIncludesCapacityWarningSection() {
        Teacher teacher = qualifiedAvailableTeacher(); // 7h available
        Room room = new Room("AULA 1", "A", "estándar");
        BlockTimeslot s1 = new BlockTimeslot("s1", DayOfWeek.MONDAY, 7, 4);
        BlockTimeslot s2 = new BlockTimeslot("s2", DayOfWeek.MONDAY, 11, 4);
        CourseBlockAssignment a1 = pinnedBlock("A1", 4, s1, room, teacher, "estándar");
        CourseBlockAssignment a2 = pinnedBlock("A2", 4, s2, room, teacher, "estándar");
        a2.setPinned(false);

        String description = PreSolveValidator.validate(scheduleWith(a1, a2)).describe();
        assertTrue(description.contains("Pre-solve capacity warning"));
        assertTrue(description.contains("short by 1h"));
    }
}
