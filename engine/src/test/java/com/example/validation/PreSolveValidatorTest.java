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
    public void pinnedHardSemesterHourLimitViolationIsReported() {
        // Mirrors SchoolConstraintProvider.semesterHourLimitsMustBeRespected():
        // a non-pinned block of a HARD-limited course can never reach a
        // violating timeslot (excluded from its own value range), so this can
        // only ever fire for a pinned row whose timeslot predates the limit.
        Course limitedCourse = new Course("2", "Matemáticas", "MAT", 1, "BASICAS", "estándar", 4, true);
        limitedCourse.setLatestEndHour(13);
        limitedCourse.setLatestEndHourSeverity("HARD");
        Teacher teacher = qualifiedAvailableTeacher(); // available Mon 7-14
        Room room = new Room("AULA 1", "A", "estándar");
        BlockTimeslot slot = new BlockTimeslot("s1", DayOfWeek.MONDAY, 12, 2); // ends 14:00, past the 13:00 limit
        Group group = new Group("G1", "Group 1", new HashSet<>());
        CourseBlockAssignment a = new CourseBlockAssignment("A1", group, limitedCourse, 2);
        a.setTimeslot(slot);
        a.setRoom(room);
        a.setTeacher(teacher);
        a.setSatisfiesRoomType("estándar");
        a.setPinned(true);

        ValidationResult r = PreSolveValidator.validate(scheduleWith(a));
        assertFalse(r.isValid());
        assertTrue(r.getProblems().stream().anyMatch(p -> p.contains("HARD limit")));
    }

    @Test
    public void pinnedHardSemesterHourLimitWithinBoundsPasses() {
        Course limitedCourse = new Course("2", "Matemáticas", "MAT", 1, "BASICAS", "estándar", 4, true);
        limitedCourse.setLatestEndHour(13);
        limitedCourse.setLatestEndHourSeverity("HARD");
        Teacher teacher = qualifiedAvailableTeacher();
        Room room = new Room("AULA 1", "A", "estándar");
        BlockTimeslot slot = new BlockTimeslot("s1", DayOfWeek.MONDAY, 7, 2); // ends 9:00, within the limit
        Group group = new Group("G1", "Group 1", new HashSet<>());
        CourseBlockAssignment a = new CourseBlockAssignment("A1", group, limitedCourse, 2);
        a.setTimeslot(slot);
        a.setRoom(room);
        a.setTeacher(teacher);
        a.setSatisfiesRoomType("estándar");
        a.setPinned(true);

        assertTrue(PreSolveValidator.validate(scheduleWith(a)).isValid());
    }

    @Test
    public void pinnedTeacherRequiredRoomMismatchIsReported() {
        // Mirrors SchoolConstraintProvider.teacherRequiredRoomMustBeUsed():
        // a non-pinned block's room is already structurally guaranteed
        // correct, so this can only ever fire for a pinned row whose room
        // drifted out of sync with its teacher's current requirement.
        Teacher teacher = qualifiedAvailableTeacher();
        teacher.setRequiredRoomName("AULA 1");
        Room requiredRoom = new Room("AULA 1", "A", "estándar");
        Room actualRoom = new Room("AULA 2", "A", "estándar"); // mismatch
        BlockTimeslot slot = new BlockTimeslot("s1", DayOfWeek.MONDAY, 7, 2);
        CourseBlockAssignment a = pinnedBlock("A1", 2, slot, actualRoom, teacher, "estándar");
        a.setAllRooms(Arrays.asList(requiredRoom, actualRoom));

        ValidationResult r = PreSolveValidator.validate(scheduleWith(a));
        assertFalse(r.isValid());
        assertTrue(r.getProblems().stream().anyMatch(p -> p.contains("required room")));
    }

    @Test
    public void pinnedTeacherRequiredRoomMatchPasses() {
        Teacher teacher = qualifiedAvailableTeacher();
        teacher.setRequiredRoomName("AULA 1");
        Room requiredRoom = new Room("AULA 1", "A", "estándar");
        BlockTimeslot slot = new BlockTimeslot("s1", DayOfWeek.MONDAY, 7, 2);
        CourseBlockAssignment a = pinnedBlock("A1", 2, slot, requiredRoom, teacher, "estándar");
        a.setAllRooms(Arrays.asList(requiredRoom));

        assertTrue(PreSolveValidator.validate(scheduleWith(a)).isValid());
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
    public void movableUnqualifiedTeacherIsReported() {
        // Teacher qualification doesn't depend on where the solver places
        // anything, so - unlike availability or room type - it's checked for
        // every assignment, not just pinned ones.
        BlockTimeslot slot = new BlockTimeslot("s1", DayOfWeek.MONDAY, 7, 2);
        Room room = new Room("AULA 1", "A", "estándar");
        Map<DayOfWeek, Set<Integer>> avail = new HashMap<>();
        avail.put(DayOfWeek.MONDAY, new HashSet<>(Arrays.asList(7, 8, 9)));
        Teacher unqualified = new Teacher("T2", "Bob", "Smith", new HashSet<>(), avail, 40);
        CourseBlockAssignment a = pinnedBlock("A1", 2, slot, room, unqualified, "estándar");
        a.setPinned(false);

        ValidationResult r = PreSolveValidator.validate(scheduleWith(a));
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
    public void teacherOverCapacity_isBlockingProblem() {
        // qualifiedAvailableTeacher() has 7h of availability (Mon 7-14).
        Teacher teacher = qualifiedAvailableTeacher();
        Room room = new Room("AULA 1", "A", "estándar");
        BlockTimeslot s1 = new BlockTimeslot("s1", DayOfWeek.MONDAY, 7, 4);
        BlockTimeslot s2 = new BlockTimeslot("s2", DayOfWeek.MONDAY, 11, 4); // 4h+4h=8h > 7h available
        CourseBlockAssignment a1 = pinnedBlock("A1", 4, s1, room, teacher, "estándar");
        CourseBlockAssignment a2 = pinnedBlock("A2", 4, s2, room, teacher, "estándar");
        a2.setPinned(false); // capacity check must count movable assignments too

        ValidationResult r = PreSolveValidator.validate(scheduleWith(a1, a2));
        // Exceeding total availability is a proven mathematical fact (at least
        // one double-booking is unavoidable), so it blocks like the pinned
        // checks above - it doesn't just warn and let the solve run anyway.
        assertFalse(r.isValid());
        assertEquals(1, r.getProblems().size());
        String problem = r.getProblems().get(0);
        assertTrue(problem.contains("T1"));
        assertTrue(problem.contains("8h/week"));
        assertTrue(problem.contains("7h/week"));
        assertTrue(problem.contains("short by 1h"));
        assertTrue(r.getWarnings().isEmpty());
    }

    @Test
    public void teacherWithinCapacity_noProblem() {
        Teacher teacher = qualifiedAvailableTeacher(); // 7h available
        Room room = new Room("AULA 1", "A", "estándar");
        BlockTimeslot slot = new BlockTimeslot("s1", DayOfWeek.MONDAY, 7, 4);
        CourseBlockAssignment a = pinnedBlock("A1", 4, slot, room, teacher, "estándar");

        ValidationResult r = PreSolveValidator.validate(scheduleWith(a));
        assertTrue(r.isValid());
        assertTrue(r.getProblems().isEmpty());
    }

    @Test
    public void describeIncludesCapacityProblem() {
        Teacher teacher = qualifiedAvailableTeacher(); // 7h available
        Room room = new Room("AULA 1", "A", "estándar");
        BlockTimeslot s1 = new BlockTimeslot("s1", DayOfWeek.MONDAY, 7, 4);
        BlockTimeslot s2 = new BlockTimeslot("s2", DayOfWeek.MONDAY, 11, 4);
        CourseBlockAssignment a1 = pinnedBlock("A1", 4, s1, room, teacher, "estándar");
        CourseBlockAssignment a2 = pinnedBlock("A2", 4, s2, room, teacher, "estándar");
        a2.setPinned(false);

        String description = PreSolveValidator.validate(scheduleWith(a1, a2)).describe();
        assertTrue(description.contains("Pre-solve validation found 1 problem:"));
        assertTrue(description.contains("short by 1h"));
    }

    // ---- Inactive courses: nothing else in the solve path checks this ----

    @Test
    public void inactiveCourseAssignmentIsReported() {
        // Same name as MATH so the teacher's qualification stays satisfied -
        // isolates the inactive-course check from the qualification one.
        Course inactiveCourse = new Course("9", "Matemáticas", "MAT", 1, "BASICAS", "estándar", 2, false);
        BlockTimeslot slot = new BlockTimeslot("s1", DayOfWeek.MONDAY, 7, 2);
        Room room = new Room("AULA 1", "A", "estándar");
        Group group = new Group("G1", "Group 1", new HashSet<>());
        CourseBlockAssignment a = new CourseBlockAssignment("A1", group, inactiveCourse, 2);
        a.setTimeslot(slot);
        a.setRoom(room);
        a.setTeacher(qualifiedAvailableTeacher());
        a.setSatisfiesRoomType("estándar");
        a.setPinned(false); // movable - confirms this isn't pinned-only, unlike validateSingle's checks

        ValidationResult r = PreSolveValidator.validate(scheduleWith(a));
        assertFalse(r.isValid());
        assertTrue(r.getProblems().stream().anyMatch(p -> p.contains("inactive")));
    }

    @Test
    public void inactiveCourseAggregatesAcrossMultipleBlocksAndGroups() {
        // Grouped per course (one problem, not one per block) to avoid
        // flooding the report when a course has many leftover blocks.
        Course inactiveCourse = new Course("9", "Matemáticas", "MAT", 1, "BASICAS", "estándar", 2, false);
        Teacher teacher = qualifiedAvailableTeacher();
        Group g1 = new Group("G1", "Group 1", new HashSet<>());
        Group g2 = new Group("G2", "Group 2", new HashSet<>());
        CourseBlockAssignment a1 = new CourseBlockAssignment("A1", g1, inactiveCourse, 2);
        a1.setTeacher(teacher);
        a1.setSatisfiesRoomType("estándar");
        a1.setPinned(false);
        CourseBlockAssignment a2 = new CourseBlockAssignment("A2", g2, inactiveCourse, 2);
        a2.setTeacher(teacher);
        a2.setSatisfiesRoomType("estándar");
        a2.setPinned(false);

        ValidationResult r = PreSolveValidator.validate(scheduleWith(a1, a2));
        long inactiveProblemCount = r.getProblems().stream().filter(p -> p.contains("inactive")).count();
        assertEquals(1, inactiveProblemCount);
        assertTrue(r.getProblems().stream().anyMatch(p -> p.contains("G1") && p.contains("G2") && p.contains("2 block")));
    }

    // ---- Room-fixed capacity: two teachers sharing one required room ----

    private static Teacher fullWeekTeacher(String id, String lastName) {
        Set<String> quals = new HashSet<>(Arrays.asList("Matemáticas"));
        Map<DayOfWeek, Set<Integer>> avail = new HashMap<>();
        Set<Integer> hours = new HashSet<>();
        for (int h = 7; h < 15; h++) {
            hours.add(h);
        }
        for (DayOfWeek day : Arrays.asList(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY,
                DayOfWeek.THURSDAY, DayOfWeek.FRIDAY)) {
            avail.put(day, new HashSet<>(hours));
        }
        return new Teacher(id, "Teacher", lastName, quals, avail, 999);
    }

    @Test
    public void roomFixedCapacityExceededIsReported() {
        // Two different teachers, each fully available (40h/week - the whole
        // school week), both required into the same room. Neither exceeds
        // their own capacity individually (25h each), but the room they
        // share can only ever host 40h/week total.
        Room requiredRoom = new Room("AULA 1", "A", "estándar");
        Teacher t1 = fullWeekTeacher("T1", "One");
        Teacher t2 = fullWeekTeacher("T2", "Two");
        t1.setRequiredRoomName("AULA 1");
        t2.setRequiredRoomName("AULA 1");

        Group g1 = new Group("G1", "Group 1", new HashSet<>());
        Group g2 = new Group("G2", "Group 2", new HashSet<>());
        CourseBlockAssignment a1 = new CourseBlockAssignment("A1", g1, MATH, 25);
        a1.setTeacher(t1);
        a1.setSatisfiesRoomType("estándar");
        a1.setAllRooms(Arrays.asList(requiredRoom));
        a1.setPinned(false);
        CourseBlockAssignment a2 = new CourseBlockAssignment("A2", g2, MATH, 25);
        a2.setTeacher(t2);
        a2.setSatisfiesRoomType("estándar");
        a2.setAllRooms(Arrays.asList(requiredRoom));
        a2.setPinned(false);

        ValidationResult r = PreSolveValidator.validate(scheduleWith(a1, a2));
        assertFalse(r.isValid());
        assertTrue(r.getProblems().stream().anyMatch(p -> p.contains("AULA 1") && p.contains("fixed")));
    }

    @Test
    public void roomFixedCapacityWithinBoundsPasses() {
        Room requiredRoom = new Room("AULA 1", "A", "estándar");
        Teacher t1 = fullWeekTeacher("T1", "One");
        t1.setRequiredRoomName("AULA 1");
        Group g1 = new Group("G1", "Group 1", new HashSet<>());
        CourseBlockAssignment a1 = new CourseBlockAssignment("A1", g1, MATH, 4);
        a1.setTeacher(t1);
        a1.setSatisfiesRoomType("estándar");
        a1.setAllRooms(Arrays.asList(requiredRoom));
        a1.setPinned(false);

        assertTrue(PreSolveValidator.validate(scheduleWith(a1)).isValid());
    }

    // ---- Block-spread capacity: enough hours, not enough distinct days ----

    @Test
    public void blockSpreadExceedsAvailableDaysIsReported() {
        // 5 required 1-hour blocks at the default max-2/day cap need 3
        // distinct days; this teacher only has 2 (Mon+Tue) - not a raw-hours
        // shortfall (5h assigned vs 10h available), a days shortfall.
        Set<String> quals = new HashSet<>(Arrays.asList("Matemáticas"));
        Map<DayOfWeek, Set<Integer>> avail = new HashMap<>();
        avail.put(DayOfWeek.MONDAY, new HashSet<>(Arrays.asList(7, 8, 9, 10, 11)));
        avail.put(DayOfWeek.TUESDAY, new HashSet<>(Arrays.asList(7, 8, 9, 10, 11)));
        Teacher teacher = new Teacher("T1", "Ada", "Lovelace", quals, avail, 40);
        Group group = new Group("G1", "Group 1", new HashSet<>());

        List<CourseBlockAssignment> blocks = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            CourseBlockAssignment a = new CourseBlockAssignment("A" + i, group, MATH, 1);
            a.setTeacher(teacher);
            a.setSatisfiesRoomType("estándar");
            a.setPinned(false);
            blocks.add(a);
        }

        ValidationResult r = PreSolveValidator.validate(scheduleWith(blocks.toArray(new CourseBlockAssignment[0])));
        assertFalse(r.isValid());
        assertTrue(r.getProblems().stream().anyMatch(p -> p.contains("distinct day")));
    }

    @Test
    public void blockSpreadWithinAvailableDaysPasses() {
        // 4 blocks at max-2/day need 2 distinct days - exactly what this
        // teacher has.
        Set<String> quals = new HashSet<>(Arrays.asList("Matemáticas"));
        Map<DayOfWeek, Set<Integer>> avail = new HashMap<>();
        avail.put(DayOfWeek.MONDAY, new HashSet<>(Arrays.asList(7, 8, 9, 10, 11)));
        avail.put(DayOfWeek.TUESDAY, new HashSet<>(Arrays.asList(7, 8, 9, 10, 11)));
        Teacher teacher = new Teacher("T1", "Ada", "Lovelace", quals, avail, 40);
        Group group = new Group("G1", "Group 1", new HashSet<>());

        List<CourseBlockAssignment> blocks = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            CourseBlockAssignment a = new CourseBlockAssignment("A" + i, group, MATH, 1);
            a.setTeacher(teacher);
            a.setSatisfiesRoomType("estándar");
            a.setPinned(false);
            blocks.add(a);
        }

        assertTrue(PreSolveValidator.validate(scheduleWith(blocks.toArray(new CourseBlockAssignment[0]))).isValid());
    }

    // ---- Empty timeslot range: every candidate excluded by pinned occupancy ----

    @Test
    public void emptyTimeslotRangeFromPinnedConflict_isReported() {
        // Only one 2h timeslot exists at all for this block's teacher/day, and
        // it's already pinned elsewhere by the same teacher - nothing legal
        // remains for this movable block.
        BlockTimeslot onlyMatchingSlot = new BlockTimeslot("s1", DayOfWeek.MONDAY, 7, 2);
        Teacher teacher = new Teacher("T1", "Ada", "Lovelace", new HashSet<>(Arrays.asList("Matemáticas")),
                new HashMap<>(), 40);
        Group group = new Group("G1", "Group 1", new HashSet<>());

        CourseBlockAssignment a = new CourseBlockAssignment("A1", group, MATH, 2);
        a.setTeacher(teacher);
        a.setSatisfiesRoomType("estándar");
        a.setPinned(false);
        a.setAllTimeslots(List.of(onlyMatchingSlot));
        a.setPinnedTimeslotsByTeacherId(Map.of("T1", List.of(onlyMatchingSlot)));

        ValidationResult r = PreSolveValidator.validate(scheduleWith(a));
        assertFalse(r.isValid());
        assertTrue(r.getProblems().stream().anyMatch(p -> p.contains("no valid timeslot")));
    }

    @Test
    public void nonEmptyTimeslotRange_passes() {
        BlockTimeslot pinnedElsewhere = new BlockTimeslot("s1", DayOfWeek.MONDAY, 7, 2);
        BlockTimeslot stillFree = new BlockTimeslot("s2", DayOfWeek.TUESDAY, 7, 2);
        // Enough declared availability to also satisfy validateCapacity - this
        // test is only about the timeslot-range check, not capacity.
        Map<DayOfWeek, Set<Integer>> avail = new HashMap<>();
        avail.put(DayOfWeek.MONDAY, new HashSet<>(Arrays.asList(7, 8)));
        avail.put(DayOfWeek.TUESDAY, new HashSet<>(Arrays.asList(7, 8)));
        Teacher teacher = new Teacher("T1", "Ada", "Lovelace", new HashSet<>(Arrays.asList("Matemáticas")), avail, 40);
        Group group = new Group("G1", "Group 1", new HashSet<>());

        CourseBlockAssignment a = new CourseBlockAssignment("A1", group, MATH, 2);
        a.setTeacher(teacher);
        a.setSatisfiesRoomType("estándar");
        a.setPinned(false);
        a.setAllTimeslots(List.of(pinnedElsewhere, stillFree));
        a.setPinnedTimeslotsByTeacherId(Map.of("T1", List.of(pinnedElsewhere)));

        assertTrue(PreSolveValidator.validate(scheduleWith(a)).isValid());
    }

    @Test
    public void pinnedAssignmentWithNoTimeslotCandidates_isNotFlaggedByEmptyRangeCheck() {
        // A pinned assignment's own value range is irrelevant to the solver
        // (Timefold never reassigns a pinned entity), so this check must
        // skip it regardless of what getMatchingBlockTimeslots() would say.
        Teacher teacher = qualifiedAvailableTeacher();
        Room room = new Room("AULA 1", "A", "estándar");
        BlockTimeslot slot = new BlockTimeslot("s1", DayOfWeek.MONDAY, 7, 2);
        CourseBlockAssignment a = pinnedBlock("A1", 2, slot, room, teacher, "estándar");
        // allTimeslots deliberately left unset, as pinnedBlock() always leaves it -
        // if this check didn't skip pinned rows, it would false-positive here too.

        assertTrue(PreSolveValidator.validate(scheduleWith(a)).isValid());
    }

    // ---- Shared teacher load (warning, not blocking - the validation-side
    // mirror of the shape-adaptation blind spot BlockGenerationService fixed) ----

    @Test
    public void sharedTeacherLoad_greedyPackingFails_isReportedAsWarningNotProblem() {
        // 3 groups share one teacher for the same course (maxPerDay=1), each
        // needing 2 distinct-day 1h blocks - individually fine (2 <= 3
        // available days), and aggregate hours (6h needed) exactly match the
        // teacher's total capacity (3 days x 2h = 6h), so neither existing
        // check fires. But maxPerDay=1 forces each group's 2 blocks onto 2
        // SEPARATE days, and greedily packing largest-first exhausts
        // Monday+Tuesday for the first two groups, leaving the third group
        // only Wednesday - one day short of the 2 it needs.
        Course course = new Course("C1", "Compartido", "COMP", 1, "BASICAS", "estándar", 2, true);
        course.setMaxBlocksPerDay(1);

        Map<DayOfWeek, Set<Integer>> avail = new HashMap<>();
        avail.put(DayOfWeek.MONDAY, new HashSet<>(Arrays.asList(7, 8)));
        avail.put(DayOfWeek.TUESDAY, new HashSet<>(Arrays.asList(7, 8)));
        avail.put(DayOfWeek.WEDNESDAY, new HashSet<>(Arrays.asList(7, 8)));
        Set<String> quals = new HashSet<>(Arrays.asList("Compartido"));
        Teacher teacher = new Teacher("T1", "Ada", "Lovelace", quals, avail, 40);

        List<CourseBlockAssignment> blocks = new ArrayList<>();
        for (int g = 1; g <= 3; g++) {
            Group group = new Group("G" + g, "Group " + g, new HashSet<>());
            for (int i = 0; i < 2; i++) {
                CourseBlockAssignment a = new CourseBlockAssignment("G" + g + "_" + i, group, course, 1);
                a.setTeacher(teacher);
                a.setSatisfiesRoomType("estándar");
                a.setPinned(false);
                blocks.add(a);
            }
        }

        ValidationResult r = PreSolveValidator.validate(scheduleWith(blocks.toArray(new CourseBlockAssignment[0])));

        assertTrue("neither existing check should fire", r.isValid());
        assertFalse("the shared-load simulation should flag the risk", r.getWarnings().isEmpty());
        assertTrue(r.getWarnings().stream().anyMatch(w -> w.contains("T1") && w.contains("couldn't be greedily fit")));
    }

    @Test
    public void sharedTeacherLoad_ampleSlack_noWarning() {
        // Same 3 groups/shapes as above, but a 4th day of slack lets
        // everything pack cleanly - confirms this isn't a false positive on
        // every multi-group teacher, only a genuinely tight one.
        Course course = new Course("C1", "Compartido", "COMP", 1, "BASICAS", "estándar", 2, true);
        course.setMaxBlocksPerDay(1);

        Map<DayOfWeek, Set<Integer>> avail = new HashMap<>();
        avail.put(DayOfWeek.MONDAY, new HashSet<>(Arrays.asList(7, 8)));
        avail.put(DayOfWeek.TUESDAY, new HashSet<>(Arrays.asList(7, 8)));
        avail.put(DayOfWeek.WEDNESDAY, new HashSet<>(Arrays.asList(7, 8)));
        avail.put(DayOfWeek.THURSDAY, new HashSet<>(Arrays.asList(7, 8)));
        Set<String> quals = new HashSet<>(Arrays.asList("Compartido"));
        Teacher teacher = new Teacher("T1", "Ada", "Lovelace", quals, avail, 40);

        List<CourseBlockAssignment> blocks = new ArrayList<>();
        for (int g = 1; g <= 3; g++) {
            Group group = new Group("G" + g, "Group " + g, new HashSet<>());
            for (int i = 0; i < 2; i++) {
                CourseBlockAssignment a = new CourseBlockAssignment("G" + g + "_" + i, group, course, 1);
                a.setTeacher(teacher);
                a.setSatisfiesRoomType("estándar");
                a.setPinned(false);
                blocks.add(a);
            }
        }

        ValidationResult r = PreSolveValidator.validate(scheduleWith(blocks.toArray(new CourseBlockAssignment[0])));

        assertTrue(r.isValid());
        assertTrue("with enough slack, the simulation should find a fit", r.getWarnings().isEmpty());
    }

    @Test
    public void sharedTeacherLoad_singlePairingTeacher_neverTriggersThisCheck() {
        // Only one (group, course) pairing for this teacher, even though its
        // own day-spread is at capacity - validateBlockSpreadCapacity already
        // covers this exactly; the shared-load simulation has nothing to
        // share and must stay silent regardless.
        Course course = new Course("C1", "Solo", "SOLO", 1, "BASICAS", "estándar", 2, true);
        course.setMaxBlocksPerDay(1);
        Map<DayOfWeek, Set<Integer>> avail = new HashMap<>();
        avail.put(DayOfWeek.MONDAY, new HashSet<>(Arrays.asList(7)));
        avail.put(DayOfWeek.TUESDAY, new HashSet<>(Arrays.asList(7)));
        Set<String> quals = new HashSet<>(Arrays.asList("Solo"));
        Teacher teacher = new Teacher("T1", "Ada", "Lovelace", quals, avail, 40);
        Group group = new Group("G1", "Group 1", new HashSet<>());

        List<CourseBlockAssignment> blocks = new ArrayList<>();
        for (int i = 0; i < 2; i++) {
            CourseBlockAssignment a = new CourseBlockAssignment("G1_" + i, group, course, 1);
            a.setTeacher(teacher);
            a.setSatisfiesRoomType("estándar");
            a.setPinned(false);
            blocks.add(a);
        }

        ValidationResult r = PreSolveValidator.validate(scheduleWith(blocks.toArray(new CourseBlockAssignment[0])));

        assertTrue(r.isValid());
        assertTrue(r.getWarnings().isEmpty());
    }

    @Test
    public void sharedTeacherLoad_pinnedHoursAreSubtractedBeforeSimulatingMovableOnes() {
        // A PINNED 2h block for one pairing consumes Monday entirely (a fixed
        // fact); a second, MOVABLE pairing needing 2 distinct 1h/day blocks
        // is then left with only Tuesday - one day short. Neither existing
        // check fires (each pairing's own day-spread and the aggregate hours
        // both fit exactly), so only the pinned-subtraction-aware simulation
        // catches this.
        Course courseA = new Course("CA", "Fijo", "FIJ", 1, "BASICAS", "estándar", 2, true);
        Course courseB = new Course("CB", "Movil", "MOV", 1, "BASICAS", "estándar", 2, true);
        courseA.setMaxBlocksPerDay(1);
        courseB.setMaxBlocksPerDay(1);

        Map<DayOfWeek, Set<Integer>> avail = new HashMap<>();
        avail.put(DayOfWeek.MONDAY, new HashSet<>(Arrays.asList(7, 8)));
        avail.put(DayOfWeek.TUESDAY, new HashSet<>(Arrays.asList(7, 8)));
        Set<String> quals = new HashSet<>(Arrays.asList("Fijo", "Movil"));
        Teacher teacher = new Teacher("T1", "Ada", "Lovelace", quals, avail, 40);

        Group groupA = new Group("GA", "Group A", new HashSet<>());
        CourseBlockAssignment pinned = new CourseBlockAssignment("GA_0", groupA, courseA, 2);
        pinned.setTeacher(teacher);
        pinned.setSatisfiesRoomType("estándar");
        pinned.setTimeslot(new BlockTimeslot("s1", DayOfWeek.MONDAY, 7, 2));
        pinned.setRoom(new Room("AULA 1", "A", "estándar"));
        pinned.setPinned(true);

        Group groupB = new Group("GB", "Group B", new HashSet<>());
        List<CourseBlockAssignment> blocks = new ArrayList<>();
        blocks.add(pinned);
        for (int i = 0; i < 2; i++) {
            CourseBlockAssignment a = new CourseBlockAssignment("GB_" + i, groupB, courseB, 1);
            a.setTeacher(teacher);
            a.setSatisfiesRoomType("estándar");
            a.setPinned(false);
            blocks.add(a);
        }

        ValidationResult r = PreSolveValidator.validate(scheduleWith(blocks.toArray(new CourseBlockAssignment[0])));

        assertTrue(r.isValid());
        assertFalse(r.getWarnings().isEmpty());
        assertTrue(r.getWarnings().stream().anyMatch(w -> w.contains("GB") && w.contains("Movil")));
    }
}
