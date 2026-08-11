package com.example.analysis;

import com.example.domain.BlockTimeslot;
import com.example.domain.Course;
import com.example.domain.CourseBlockAssignment;
import com.example.domain.Group;
import com.example.domain.SchoolSchedule;
import org.junit.Test;

import java.time.DayOfWeek;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;

/**
 * Locks in the ADJACENT-pair semantics of the "Minimize group idle gaps" soft
 * constraint as reported by {@link BlockScheduleAnalyzer}. A chain of 3+ blocks
 * on the same day must have its idle hours counted once per gap (adjacent pairs
 * only), not re-counted by the non-adjacent pair (block 1 <-> block 3), which
 * would inflate the penalty. This mirrors the solver's forEachUniquePair +
 * ifNotExists adjacency formulation in SchoolConstraintProvider.
 */
public class GroupIdleGapAnalyzerTest {

    private static final Course MATH =
            new Course("1", "Matemáticas", "MAT", "I", "BASICAS", "estándar", 4, true);

    private static SchoolSchedule scheduleWith(CourseBlockAssignment... assignments) {
        List<CourseBlockAssignment> list = new ArrayList<>(Arrays.asList(assignments));
        return SchoolSchedule.forBlockScheduling(new ArrayList<>(), new ArrayList<>(), new ArrayList<>(),
                new ArrayList<>(), new ArrayList<>(), list);
    }

    /** Unpinned 1-hour block for the given group starting at the given hour on Monday. */
    private static CourseBlockAssignment block(String id, Group group, int startHour) {
        CourseBlockAssignment a = new CourseBlockAssignment(id, group, MATH, 1);
        a.setTimeslot(new BlockTimeslot("slot-" + id, DayOfWeek.MONDAY, startHour, 1));
        a.setPinned(false);
        return a;
    }

    private static int groupIdleGaps(SchoolSchedule schedule) {
        return BlockScheduleAnalyzer.analyzeSoftConstraintViolations(schedule)
                .get("Minimize group idle gaps");
    }

    @Test
    public void threeBlockChainCountsOnlyAdjacentGaps() {
        Group g = new Group("G1", "Group 1", new java.util.HashSet<>());
        // 7-8, 10-11, 13-14 on Monday: adjacent gaps are 8->10 (2h) and 11->13 (2h) = 4h.
        // A pairwise formulation would also add block1<->block3 (5h), inflating to 9h.
        SchoolSchedule schedule = scheduleWith(
                block("A1", g, 7),
                block("A2", g, 10),
                block("A3", g, 13));
        assertEquals(4, groupIdleGaps(schedule));
    }

    @Test
    public void consecutiveBlocksHaveNoIdleGap() {
        Group g = new Group("G1", "Group 1", new java.util.HashSet<>());
        SchoolSchedule schedule = scheduleWith(
                block("A1", g, 7),
                block("A2", g, 8),
                block("A3", g, 9));
        assertEquals(0, groupIdleGaps(schedule));
    }

    @Test
    public void gapsOnDifferentDaysAreNotMerged() {
        Group g = new Group("G1", "Group 1", new java.util.HashSet<>());
        CourseBlockAssignment mon1 = block("A1", g, 7);
        CourseBlockAssignment mon2 = block("A2", g, 10); // Monday gap 8->10 = 2h
        CourseBlockAssignment tue1 = new CourseBlockAssignment("B1", g, MATH, 1);
        tue1.setTimeslot(new BlockTimeslot("slot-B1", DayOfWeek.TUESDAY, 7, 1));
        tue1.setPinned(false);
        CourseBlockAssignment tue2 = new CourseBlockAssignment("B2", g, MATH, 1);
        tue2.setTimeslot(new BlockTimeslot("slot-B2", DayOfWeek.TUESDAY, 9, 1)); // Tuesday gap 8->9 = 1h
        tue2.setPinned(false);
        SchoolSchedule schedule = scheduleWith(mon1, mon2, tue1, tue2);
        assertEquals(3, groupIdleGaps(schedule));
    }

    @Test
    public void pinnedBlocksAreExcluded() {
        Group g = new Group("G1", "Group 1", new java.util.HashSet<>());
        CourseBlockAssignment a1 = block("A1", g, 7);
        CourseBlockAssignment a2 = block("A2", g, 10); // would create a 2h gap if counted
        a2.setPinned(true);
        SchoolSchedule schedule = scheduleWith(a1, a2);
        assertEquals(0, groupIdleGaps(schedule));
    }
}
