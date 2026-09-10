package com.example.solver;

import com.example.domain.Course;
import com.example.domain.CourseBlockAssignment;
import com.example.domain.Group;
import com.example.domain.Room;
import com.example.domain.Teacher;

import org.junit.Test;

import java.time.DayOfWeek;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.assertTrue;

/**
 * Verifies {@link BlockLengthDifficultyComparator}'s tier order: teacher
 * availability, then room-fixed-before-room-movable (see
 * {@link CourseBlockAssignment#isRoomFixed()}), then block length, then the
 * deterministic ID tie-breakers - and that inserting the new room-fixed tier
 * didn't disturb the tiers around it.
 */
public class BlockLengthDifficultyComparatorTest {

    private static final Course COURSE = new Course("1", "Test Course", "TEST", 2, "BASICAS", "estándar", 4,
            Boolean.TRUE);
    private static final BlockLengthDifficultyComparator COMPARATOR = new BlockLengthDifficultyComparator();

    private static Teacher teacher(String id, int availableHours) {
        Set<Integer> hours = new HashSet<>();
        for (int h = 7; h < 7 + availableHours; h++) {
            hours.add(h);
        }
        HashMap<DayOfWeek, Set<Integer>> availability = new HashMap<>();
        availability.put(DayOfWeek.MONDAY, hours);
        return new Teacher(id, "Test", "Teacher", new HashSet<>(), availability, 40);
    }

    /** A group whose only curated range is "Standard" -> the given room. */
    private static Group groupWithStandardRange(String id, String name, Room room) {
        return new Group(id, name, new HashSet<>(), Map.of("Standard", List.of(room)), null);
    }

    private CourseBlockAssignment block(String id, Group group, Teacher teacher, int blockLength) {
        CourseBlockAssignment a = new CourseBlockAssignment(id, group, COURSE, blockLength);
        a.setTeacher(teacher);
        // These tests are all Standard-room scenarios - set consistently so
        // a group's type-keyed curated range (see groupWithStandardRange())
        // is actually looked up under the same key it was stored with.
        a.setSatisfiesRoomType("Standard");
        return a;
    }

    @Test
    public void roomFixedByGroupPreference_sortsBeforeRoomMovable_sameTeacherAndLength() {
        Teacher t = teacher("T1", 20);
        Room preferred = new Room("AULA 1", "A", "Standard");
        Group fixedGroup = groupWithStandardRange("G1", "Fixed Group", preferred);
        Group movableGroup = new Group("G2", "Movable Group", new HashSet<>());

        CourseBlockAssignment fixed = block("a1", fixedGroup, t, 2);
        CourseBlockAssignment movable = block("a2", movableGroup, t, 2);

        assertTrue(COMPARATOR.compare(fixed, movable) < 0);
        assertTrue(COMPARATOR.compare(movable, fixed) > 0);
    }

    @Test
    public void roomFixedByTeacherRequirement_sortsBeforeRoomMovable_sameTeacherAndLength() {
        Teacher fixedTeacher = teacher("T1", 20);
        fixedTeacher.setRequiredRoomName("AULA 1");
        Teacher movableTeacher = teacher("T2", 20);
        Group group = new Group("G1", "Group", new HashSet<>());

        CourseBlockAssignment fixed = block("a1", group, fixedTeacher, 2);
        CourseBlockAssignment movable = block("a2", group, movableTeacher, 2);

        assertTrue(COMPARATOR.compare(fixed, movable) < 0);
    }

    @Test
    public void teacherAvailability_stillOutranksRoomFixedStatus() {
        // A scarcer teacher's room-MOVABLE block must still sort before a
        // more-available teacher's room-FIXED block - teacher availability
        // (tier 1) is not overridden by the new room-fixed tier (tier 2).
        Teacher scarce = teacher("SCARCE", 5);
        Teacher plentiful = teacher("PLENTIFUL", 30);
        Room preferred = new Room("AULA 1", "A", "Standard");
        Group fixedGroup = groupWithStandardRange("G1", "Fixed Group", preferred);
        Group movableGroup = new Group("G2", "Movable Group", new HashSet<>());

        CourseBlockAssignment scarceMovable = block("a1", movableGroup, scarce, 1);
        CourseBlockAssignment plentifulFixed = block("a2", fixedGroup, plentiful, 1);

        assertTrue(COMPARATOR.compare(scarceMovable, plentifulFixed) < 0);
    }

    @Test
    public void blockLength_stillBreaksTiesWithinSameTeacherAndRoomFixedStatus() {
        Teacher t = teacher("T1", 20);
        Group group = new Group("G1", "Group", new HashSet<>());

        CourseBlockAssignment longer = block("a1", group, t, 3);
        CourseBlockAssignment shorter = block("a2", group, t, 1);

        // Both movable (isRoomFixed() false for both) - length is the next tier.
        assertTrue(COMPARATOR.compare(longer, shorter) < 0);
    }
}
