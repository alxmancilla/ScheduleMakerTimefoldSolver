package com.example.analysis;

import com.example.domain.BlockTimeslot;
import com.example.domain.Course;
import com.example.domain.CourseBlockAssignment;
import com.example.domain.Group;
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

import static org.junit.Assert.assertEquals;

/**
 * Locks in the ADJACENT-pair AND availability-aware semantics of the "Minimize
 * teacher idle gaps" soft constraint as reported by {@link BlockScheduleAnalyzer}.
 *
 * Two behaviors are asserted:
 *  1. Adjacency: a chain of 3+ blocks on the same day counts idle hours once per
 *     gap (adjacent pairs only), never re-counting the non-adjacent pair, mirroring
 *     the solver's forEachUniquePair + ifNotExists formulation.
 *  2. Availability-awareness: an idle hour is counted only if the teacher is
 *     actually available during THAT hour - partial credit per available hour
 *     (via {@code BlockScheduleMath.availableGapHours}, the same function
 *     {@code SchoolConstraintProvider.minimizeTeacherIdleGaps} penalizes with),
 *     not an all-or-nothing verdict on the whole gap.
 */
public class TeacherIdleGapAnalyzerTest {

    private static final Course MATH =
            new Course("1", "Matemáticas", "MAT", 1, "BASICAS", "estándar", 4, true);
    private static final Group GROUP = new Group("G1", "Group 1", new HashSet<>());

    /** Teacher available Monday for the given inclusive hours. */
    private static Teacher teacherAvailable(String id, int... hours) {
        Set<String> quals = new HashSet<>(Arrays.asList("Matemáticas"));
        Map<DayOfWeek, Set<Integer>> avail = new HashMap<>();
        Set<Integer> set = new HashSet<>();
        for (int h : hours) {
            set.add(h);
        }
        avail.put(DayOfWeek.MONDAY, set);
        return new Teacher(id, "Ada", "Lovelace", quals, avail, 40);
    }

    /** Fully available Monday 7:00-14:00. */
    private static Teacher fullyAvailable(String id) {
        return teacherAvailable(id, 7, 8, 9, 10, 11, 12, 13);
    }

    private static SchoolSchedule scheduleWith(CourseBlockAssignment... assignments) {
        List<CourseBlockAssignment> list = new ArrayList<>(Arrays.asList(assignments));
        return SchoolSchedule.forBlockScheduling(new ArrayList<>(), new ArrayList<>(), new ArrayList<>(),
                new ArrayList<>(), new ArrayList<>(), list);
    }

    /** Unpinned 1-hour block for the given teacher starting at the given hour on Monday. */
    private static CourseBlockAssignment block(String id, Teacher teacher, int startHour) {
        CourseBlockAssignment a = new CourseBlockAssignment(id, GROUP, MATH, 1);
        a.setTimeslot(new BlockTimeslot("slot-" + id, DayOfWeek.MONDAY, startHour, 1));
        a.setTeacher(teacher);
        a.setPinned(false);
        return a;
    }

    private static int teacherIdleGaps(SchoolSchedule schedule) {
        return BlockScheduleAnalyzer.analyzeSoftConstraintViolations(schedule)
                .get("Minimize teacher idle gaps (availability-aware)");
    }

    @Test
    public void threeBlockChainCountsOnlyAdjacentGaps() {
        Teacher t = fullyAvailable("T1");
        // 7-8, 10-11, 13-14: adjacent gaps 8->10 (2h) and 11->13 (2h) = 4h.
        // A pairwise formulation would also add block1<->block3 (5h), inflating to 9h.
        SchoolSchedule schedule = scheduleWith(
                block("A1", t, 7),
                block("A2", t, 10),
                block("A3", t, 13));
        assertEquals(4, teacherIdleGaps(schedule));
    }

    @Test
    public void consecutiveBlocksHaveNoIdleGap() {
        Teacher t = fullyAvailable("T1");
        SchoolSchedule schedule = scheduleWith(
                block("A1", t, 7),
                block("A2", t, 8),
                block("A3", t, 9));
        assertEquals(0, teacherIdleGaps(schedule));
    }

    @Test
    public void gapPartiallyUnavailableCountsOnlyAvailableHours() {
        // Blocks 7-8 and 12-13; gap hours are 8,9,10,11. Teacher is NOT available
        // at hour 10, but IS available at 8, 9, and 11 - those 3 hours are still
        // penalized (partial credit), unlike the all-or-nothing behavior this
        // used to have before it shared BlockScheduleMath.availableGapHours with
        // the constraint provider.
        Teacher t = teacherAvailable("T1", 7, 8, 9, 11, 12);
        SchoolSchedule schedule = scheduleWith(
                block("A1", t, 7),
                block("A2", t, 12));
        assertEquals(3, teacherIdleGaps(schedule));
    }

    @Test
    public void gapEntirelyUnavailableIsNotCounted() {
        // Blocks 7-8 and 9-10; the sole gap hour is 8. Teacher is NOT available
        // at 8, so this (single-hour) gap contributes 0.
        Teacher t = teacherAvailable("T1", 7, 9);
        SchoolSchedule schedule = scheduleWith(
                block("A1", t, 7),
                block("A2", t, 9));
        assertEquals(0, teacherIdleGaps(schedule));
    }

    @Test
    public void gapCountedOnlyWhenTeacherAvailableForWholeGap() {
        // Blocks 7-8 and 10-11; gap hours are 8,9. Teacher available for both -> 2h.
        Teacher t = teacherAvailable("T1", 7, 8, 9, 10);
        SchoolSchedule schedule = scheduleWith(
                block("A1", t, 7),
                block("A2", t, 10));
        assertEquals(2, teacherIdleGaps(schedule));
    }

    @Test
    public void gapsOnDifferentDaysAreNotMerged() {
        Teacher t = fullyAvailable("T1");
        CourseBlockAssignment mon1 = block("A1", t, 7);
        CourseBlockAssignment mon2 = block("A2", t, 10); // Monday gap 8->10 = 2h
        // Add Tuesday availability and two Tuesday blocks with a 1h gap.
        t.getAvailabilityPerDay().put(DayOfWeek.TUESDAY,
                new HashSet<>(Arrays.asList(7, 8, 9)));
        CourseBlockAssignment tue1 = new CourseBlockAssignment("B1", GROUP, MATH, 1);
        tue1.setTimeslot(new BlockTimeslot("slot-B1", DayOfWeek.TUESDAY, 7, 1));
        tue1.setTeacher(t);
        tue1.setPinned(false);
        CourseBlockAssignment tue2 = new CourseBlockAssignment("B2", GROUP, MATH, 1);
        tue2.setTimeslot(new BlockTimeslot("slot-B2", DayOfWeek.TUESDAY, 9, 1)); // gap 8->9 = 1h
        tue2.setTeacher(t);
        tue2.setPinned(false);
        SchoolSchedule schedule = scheduleWith(mon1, mon2, tue1, tue2);
        assertEquals(3, teacherIdleGaps(schedule));
    }
}
