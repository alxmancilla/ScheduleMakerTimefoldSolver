package com.example.solver;

import static org.junit.Assert.assertEquals;

import ai.timefold.solver.core.api.score.buildin.hardsoft.HardSoftScore;
import ai.timefold.solver.core.api.solver.SolutionManager;
import ai.timefold.solver.core.api.solver.SolverFactory;
import ai.timefold.solver.core.config.solver.SolverConfig;

import com.example.domain.*;
import org.junit.Test;

import java.time.DayOfWeek;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

/**
 * Solver-level coverage for "Prefer group's preferred room", asserting the score
 * Timefold actually calculates rather than BlockScheduleAnalyzer's mirror.
 *
 * <p>Deliberately solver-level: earlier this same day three idle-gap constraints
 * were found contributing exactly 0 to the real score while their analyzer
 * mirrors reported violations happily, and ConstraintConsistencyTest didn't
 * notice because it compares constraint NAMES, not values. An analyzer test
 * alone would not have caught that, so a constraint whose scoring behaviour has
 * just changed gets checked where it counts.
 */
public class GroupPreferredRoomConstraintTest {

    private static final String CONSTRAINT_NAME = "Prefer group's preferred room";
    private static final int WEIGHT = 2; // SoftConstraintDefaults

    private static final Course COURSE =
            new Course("1", "Course", "C", 2, "Core", "Standard", 4, Boolean.TRUE);

    private static int preferredRoomScore(SchoolSchedule schedule) {
        SolutionManager<SchoolSchedule, HardSoftScore> sm = SolutionManager.create(
                SolverFactory.<SchoolSchedule>create(SolverConfig.createFromXmlResource("solverConfig.xml")));
        return sm.explain(schedule).getConstraintMatchTotalMap().values().stream()
                .filter(t -> CONSTRAINT_NAME.equals(t.getConstraintRef().constraintName()))
                .mapToInt(t -> ((HardSoftScore) t.getScore()).softScore())
                .sum();
    }

    private static CourseBlockAssignment block(Group group, Room room, Teacher teacher, List<Room> allRooms) {
        CourseBlockAssignment a = new CourseBlockAssignment("a1", group, COURSE, 1);
        a.setTimeslot(new BlockTimeslot("slot1", DayOfWeek.MONDAY, 7, 1));
        a.setSatisfiesRoomType("Standard");
        a.setPinned(false);
        a.setTeacher(teacher);
        a.setRoom(room);
        a.setAllRooms(new ArrayList<>(allRooms));
        return a;
    }

    private static Group groupWithRange(Room... range) {
        Map<String, List<Room>> ranges = new HashMap<>();
        ranges.put("Standard", List.of(range));
        return new Group("G1", "Group 1", new HashSet<>(), ranges);
    }

    private static SchoolSchedule scheduleOf(CourseBlockAssignment a, List<Room> rooms, Group group) {
        List<Teacher> teachers = a.getTeacher() == null ? List.of() : List.of(a.getTeacher());
        return new SchoolSchedule(teachers, List.of(a.getTimeslot()), rooms,
                List.of(COURSE), List.of(group), new ArrayList<>(List.of(a)));
    }

    @Test
    public void outsideTheGroupsRangeWithNoTeacherRequirement_isPenalised() {
        Room inRange = new Room("R1", "A", "Standard");
        Room outOfRange = new Room("R2", "A", "Standard");
        Group group = groupWithRange(inRange);
        // Two candidate rooms and no teacher requirement: the block is genuinely
        // movable, so the preference is something the solver can act on.
        CourseBlockAssignment a = block(group, outOfRange, null, List.of(inRange, outOfRange));

        assertEquals(-WEIGHT, preferredRoomScore(scheduleOf(a, List.of(inRange, outOfRange), group)));
    }

    /**
     * The fix (2026-09-09). The teacher's required room outranks the group's
     * curated range and makes the block room-fixed, so this penalty would be
     * unpayable - no move can change the room. On the live dataset every one of
     * the 43 penalised blocks was this case, making the whole -86 a constant.
     */
    @Test
    public void forcedThereByTheTeachersRequiredRoom_isNotPenalised() {
        Room groupsRoom = new Room("R1", "A", "Standard");
        Room teachersRoom = new Room("R2", "A", "Standard");
        Group group = groupWithRange(groupsRoom);
        Teacher teacher = new Teacher("T1", "Ada", "Lovelace", new HashSet<>(), new HashMap<>(), 40);
        teacher.setRequiredRoomName("R2");

        CourseBlockAssignment a = block(group, teachersRoom, teacher, List.of(groupsRoom, teachersRoom));

        assertEquals("a room the teacher's requirement forces is not a group-preference violation",
                0, preferredRoomScore(scheduleOf(a, List.of(groupsRoom, teachersRoom), group)));
    }

    /** A required room of the wrong type never applied, so the group's range still governs. */
    @Test
    public void requiredRoomOfIncompatibleType_stillPenalised() {
        Room groupsRoom = new Room("R1", "A", "Standard");
        Room outOfRange = new Room("R2", "A", "Standard");
        Room lab = new Room("LAB", "A", "Specialized - Computer Lab");
        Group group = groupWithRange(groupsRoom);
        Teacher teacher = new Teacher("T1", "Ada", "Lovelace", new HashSet<>(), new HashMap<>(), 40);
        teacher.setRequiredRoomName("LAB"); // can't satisfy a Standard block

        CourseBlockAssignment a = block(group, outOfRange, teacher, List.of(groupsRoom, outOfRange, lab));

        assertEquals(-WEIGHT, preferredRoomScore(scheduleOf(a, List.of(groupsRoom, outOfRange, lab), group)));
    }
}
