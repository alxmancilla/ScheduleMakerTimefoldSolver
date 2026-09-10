package com.example.web.service;

import com.example.web.entity.TeacherEntity;
import org.junit.Test;

import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class AvailabilityAwareBlockShaperTest {

    private TeacherEntity teacherWithHours(int[][] dayHourPairs) {
        TeacherEntity teacher = new TeacherEntity("T1", "Ada", "Lovelace", 40);
        for (int[] pair : dayHourPairs) {
            teacher.addAvailability(pair[0], pair[1]);
        }
        return teacher;
    }

    // ---- packBlocks ----

    @Test
    public void packBlocks_dividesEvenly() {
        assertEquals(List.of(2, 2, 2), AvailabilityAwareBlockShaper.packBlocks(6, 2));
    }

    @Test
    public void packBlocks_leavesRemainder() {
        assertEquals(List.of(2, 2, 1), AvailabilityAwareBlockShaper.packBlocks(5, 2));
    }

    @Test
    public void packBlocks_singleBlockWhenHoursFitInOne() {
        assertEquals(List.of(3), AvailabilityAwareBlockShaper.packBlocks(3, 4));
    }

    // ---- fitsWithinDayCap ----

    @Test
    public void fitsWithinDayCap_exactFit() {
        // 4 blocks at 2/day needs 2 days
        org.junit.Assert.assertTrue(AvailabilityAwareBlockShaper.fitsWithinDayCap(4, 2, 2, 0));
    }

    @Test
    public void fitsWithinDayCap_needsCeiling() {
        // 5 blocks at 2/day needs 3 days, not 2
        org.junit.Assert.assertFalse(AvailabilityAwareBlockShaper.fitsWithinDayCap(5, 2, 2, 0));
        org.junit.Assert.assertTrue(AvailabilityAwareBlockShaper.fitsWithinDayCap(5, 2, 3, 0));
    }

    @Test
    public void fitsWithinDayCap_zeroOrNegativeCap_isFalse() {
        org.junit.Assert.assertFalse(AvailabilityAwareBlockShaper.fitsWithinDayCap(1, 0, 5, 0));
    }

    @Test
    public void fitsWithinDayCap_margin_requiresSpareDaysBeyondTheMinimum() {
        // 2 blocks at 1/day needs exactly 2 days.
        org.junit.Assert.assertTrue(AvailabilityAwareBlockShaper.fitsWithinDayCap(2, 1, 2, 0)); // bare fit
        org.junit.Assert.assertFalse(AvailabilityAwareBlockShaper.fitsWithinDayCap(2, 1, 2, 1)); // no spare day
        org.junit.Assert.assertTrue(AvailabilityAwareBlockShaper.fitsWithinDayCap(2, 1, 3, 1)); // one spare day
    }

    // ---- distinctAvailableDayCount / largestContiguousWindow ----

    @Test
    public void distinctAvailableDayCount_countsUniqueDaysOnly() {
        TeacherEntity teacher = teacherWithHours(new int[][] { { 1, 7 }, { 1, 8 }, { 2, 9 } });
        assertEquals(2, AvailabilityAwareBlockShaper.distinctAvailableDayCount(teacher));
    }

    @Test
    public void largestContiguousWindow_findsLongestRunAcrossDays() {
        TeacherEntity teacher = teacherWithHours(
                new int[][] { { 1, 7 }, { 1, 8 }, { 1, 9 }, { 2, 11 }, { 2, 12 } });
        assertEquals(3, AvailabilityAwareBlockShaper.largestContiguousWindow(teacher));
    }

    @Test
    public void largestContiguousWindow_gapWithinADaySplitsIntoTwoWindows() {
        TeacherEntity teacher = teacherWithHours(new int[][] { { 1, 7 }, { 1, 8 }, { 1, 10 } });
        // {7,8} is a window of 2, {10} is a window of 1 - largest is 2, not 3.
        assertEquals(2, AvailabilityAwareBlockShaper.largestContiguousWindow(teacher));
    }

    // ---- tryAvailabilityAwareShape ----

    @Test
    public void tryAvailabilityAwareShape_findsLongerShapeThatFits() {
        // 5 hours, Core-style preferredSize=1, maxBlocksPerDay=1 -> naive needs 5 days.
        // Teacher only has 2 days, each with a 4h contiguous window - no spare day
        // exists at any size, so margin 0 (bare feasibility only) is used here.
        TeacherEntity teacher = teacherWithHours(new int[][] {
                { 1, 7 }, { 1, 8 }, { 1, 9 }, { 1, 10 },
                { 2, 7 }, { 2, 8 }, { 2, 9 }, { 2, 10 },
        });

        List<Integer> shape = AvailabilityAwareBlockShaper.tryAvailabilityAwareShape(5, 1, 1, teacher, 0);

        // Size 3 is the first that fits: packBlocks(5,3) = [3,2], 2 blocks at 1/day = 2 days.
        assertEquals(List.of(3, 2), shape);
    }

    @Test
    public void tryAvailabilityAwareShape_margin_prefersASizeWithASpareDay() {
        // Same 5 hours/preferredSize 1/maxPerDay 1, but this teacher has a 3rd
        // available day - size 3 ([3,2], 2 days) now has one day to spare out of 3.
        TeacherEntity teacher = teacherWithHours(new int[][] {
                { 1, 7 }, { 1, 8 }, { 1, 9 }, { 1, 10 },
                { 2, 7 }, { 2, 8 }, { 2, 9 }, { 2, 10 },
                { 3, 7 }, { 3, 8 }, { 3, 9 }, { 3, 10 },
        });

        List<Integer> shape = AvailabilityAwareBlockShaper.tryAvailabilityAwareShape(5, 1, 1, teacher, 1);

        assertEquals(List.of(3, 2), shape);
    }

    @Test
    public void tryAvailabilityAwareShape_marginUnreachable_gracefullyFallsBackToBareFeasible() {
        // Same as the no-spare-day case above, but asked for margin 1 instead of 0 -
        // no size can ever free up a 3rd day this teacher doesn't have, so this
        // should settle for the same bare-feasible [3,2] rather than returning null.
        TeacherEntity teacher = teacherWithHours(new int[][] {
                { 1, 7 }, { 1, 8 }, { 1, 9 }, { 1, 10 },
                { 2, 7 }, { 2, 8 }, { 2, 9 }, { 2, 10 },
        });

        List<Integer> shape = AvailabilityAwareBlockShaper.tryAvailabilityAwareShape(5, 1, 1, teacher, 1);

        assertEquals(List.of(3, 2), shape);
    }

    @Test
    public void tryAvailabilityAwareShape_noSizeFits_returnsNull() {
        // Teacher only ever available 1 day, 1 hour - nothing can make this fit.
        TeacherEntity teacher = teacherWithHours(new int[][] { { 1, 7 } });
        assertNull(AvailabilityAwareBlockShaper.tryAvailabilityAwareShape(5, 1, 1, teacher, 0));
    }

    @Test
    public void tryAvailabilityAwareShape_windowSmallerThanPreferredSize_returnsNull() {
        // Largest window (1h) is below the preferred size (2h) - nothing to even try.
        TeacherEntity teacher = teacherWithHours(new int[][] { { 1, 7 }, { 2, 8 }, { 3, 9 } });
        assertNull(AvailabilityAwareBlockShaper.tryAvailabilityAwareShape(6, 2, 1, teacher, 0));
    }

    @Test
    public void tryAvailabilityAwareShape_noAvailabilityAtAll_returnsNull() {
        TeacherEntity teacher = new TeacherEntity("T1", "Ada", "Lovelace", 40);
        assertNull(AvailabilityAwareBlockShaper.tryAvailabilityAwareShape(5, 1, 1, teacher, 0));
    }

    // ---- assignWindows ----

    @Test
    public void assignWindows_packsMultipleBlocksIntoSameDayWhenCapAllows() {
        TeacherEntity teacher = teacherWithHours(new int[][] { { 1, 7 }, { 1, 8 }, { 1, 9 }, { 1, 10 } });

        List<int[]> slots = AvailabilityAwareBlockShaper.assignWindows(List.of(2, 2), 2, teacher);

        assertEquals(2, slots.size());
        assertArrayEquals(new int[] { 1, 7 }, slots.get(0));
        assertArrayEquals(new int[] { 1, 9 }, slots.get(1));
    }

    @Test
    public void assignWindows_respectsMaxBlocksPerDay_spillsToNextDay() {
        TeacherEntity teacher = teacherWithHours(
                new int[][] { { 1, 7 }, { 1, 8 }, { 1, 9 }, { 1, 10 }, { 2, 7 }, { 2, 8 } });

        List<int[]> slots = AvailabilityAwareBlockShaper.assignWindows(List.of(2, 2), 1, teacher);

        assertEquals(2, slots.size());
        assertArrayEquals(new int[] { 1, 7 }, slots.get(0));
        assertArrayEquals(new int[] { 2, 7 }, slots.get(1)); // day 1 already at its cap of 1
    }

    @Test
    public void assignWindows_cannotFitEverything_returnsNullNotPartial() {
        TeacherEntity teacher = teacherWithHours(new int[][] { { 1, 7 }, { 1, 8 }, { 1, 9 } }); // only 3h total
        assertNull(AvailabilityAwareBlockShaper.assignWindows(List.of(5), 2, teacher));
    }

    // ---- Map-based overloads (the shared-calendar mechanism) ----

    private Map<Integer, List<int[]>> mapWindows(int[]... dayWindows) {
        // each entry: {day, startHour, length}
        Map<Integer, List<int[]>> result = new java.util.TreeMap<>();
        for (int[] w : dayWindows) {
            result.computeIfAbsent(w[0], d -> new java.util.ArrayList<>()).add(new int[] { w[1], w[2] });
        }
        return result;
    }

    @Test
    public void distinctAvailableDayCount_map_ignoresDaysExhaustedToZero() {
        Map<Integer, List<int[]>> windows = mapWindows(
                new int[] { 1, 7, 0 }, // day 1's only window is fully consumed
                new int[] { 2, 7, 3 });
        assertEquals(1, AvailabilityAwareBlockShaper.distinctAvailableDayCount(windows));
    }

    @Test
    public void largestContiguousWindow_map_reflectsRemainingLength() {
        Map<Integer, List<int[]>> windows = mapWindows(new int[] { 1, 7, 1 }, new int[] { 2, 7, 3 });
        assertEquals(3, AvailabilityAwareBlockShaper.largestContiguousWindow(windows));
    }

    @Test
    public void assignWindows_map_mutatesTheSameMapAcrossCalls() {
        Map<Integer, List<int[]>> windows = mapWindows(new int[] { 1, 7, 4 });

        List<int[]> first = AvailabilityAwareBlockShaper.assignWindows(List.of(2), 1, windows);
        assertArrayEquals(new int[] { 1, 7 }, first.get(0));
        // 2 of the 4 hours are now consumed - a second call sees the same map, shrunk.
        assertEquals(2, windows.get(1).get(0)[1]);

        List<int[]> second = AvailabilityAwareBlockShaper.assignWindows(List.of(2), 1, windows);
        assertArrayEquals(new int[] { 1, 9 }, second.get(0));
        assertEquals(0, windows.get(1).get(0)[1]);
    }

    @Test
    public void assignWindows_map_failedAttemptLeavesMapCompletelyUntouched() {
        Map<Integer, List<int[]>> windows = mapWindows(new int[] { 1, 7, 3 });
        int[] before = windows.get(1).get(0).clone();

        // First block (2h) fits; second (5h) doesn't - the whole call must fail
        // and roll back, not leave the first block's consumption applied.
        List<int[]> result = AvailabilityAwareBlockShaper.assignWindows(List.of(2, 5), 2, windows);

        assertNull(result);
        assertArrayEquals(before, windows.get(1).get(0));
    }

    @Test
    public void tryAvailabilityAwareShape_map_worksAgainstAnAlreadyPartiallyConsumedCalendar() {
        // Only 1 day realistically left (as if another pairing sharing this
        // teacher already consumed the rest), with a 3h window.
        Map<Integer, List<int[]>> windows = mapWindows(new int[] { 3, 7, 3 });

        List<Integer> shape = AvailabilityAwareBlockShaper.tryAvailabilityAwareShape(3, 1, 1, windows, 0);

        assertEquals(List.of(3), shape); // single 3h block is the only way to fit in one remaining day
    }

    // ---- windowsByDay(teacher, consumedRanges) ----

    @Test
    public void windowsByDay_withNoConsumedRanges_matchesTheZeroArgForm() {
        TeacherEntity teacher = teacherWithHours(new int[][] { { 1, 7 }, { 1, 8 }, { 2, 9 } });

        Map<Integer, List<int[]>> withEmptyList = AvailabilityAwareBlockShaper.windowsByDay(teacher, List.of());
        Map<Integer, List<int[]>> zeroArg = AvailabilityAwareBlockShaper.windowsByDay(teacher);

        assertEquals(zeroArg.keySet(), withEmptyList.keySet());
        for (int day : zeroArg.keySet()) {
            assertArrayEquals(zeroArg.get(day).get(0), withEmptyList.get(day).get(0));
        }
    }

    @Test
    public void windowsByDay_consumedRangeInTheMiddle_splitsTheWindowInTwo() {
        // Day 1: 7,8,9,10,11 (one 5h window). Consuming hour 9 alone should
        // split it into {7,8} and {10,11} - exactly what a pinned assignment
        // sitting in the middle of an otherwise-open day would do.
        TeacherEntity teacher = teacherWithHours(
                new int[][] { { 1, 7 }, { 1, 8 }, { 1, 9 }, { 1, 10 }, { 1, 11 } });

        Map<Integer, List<int[]>> windows = AvailabilityAwareBlockShaper.windowsByDay(teacher,
                List.of(new int[] { 1, 9, 1 }));

        List<int[]> dayOneWindows = windows.get(1);
        assertEquals(2, dayOneWindows.size());
        assertArrayEquals(new int[] { 7, 2 }, dayOneWindows.get(0));
        assertArrayEquals(new int[] { 10, 2 }, dayOneWindows.get(1));
    }

    @Test
    public void windowsByDay_consumedRangeCoveringWholeDay_leavesThatDayEmpty() {
        TeacherEntity teacher = teacherWithHours(new int[][] { { 1, 7 } });

        Map<Integer, List<int[]>> windows = AvailabilityAwareBlockShaper.windowsByDay(teacher,
                List.of(new int[] { 1, 7, 1 }));

        assertEquals(0, AvailabilityAwareBlockShaper.distinctAvailableDayCount(windows));
    }

    @Test
    public void windowsByDay_consumedRangeOnADayTheTeacherHasNoAvailability_isIgnored() {
        TeacherEntity teacher = teacherWithHours(new int[][] { { 1, 7 } });

        // Day 3 isn't in the teacher's availability at all - nothing to remove from.
        Map<Integer, List<int[]>> windows = AvailabilityAwareBlockShaper.windowsByDay(teacher,
                List.of(new int[] { 3, 7, 1 }));

        assertEquals(1, AvailabilityAwareBlockShaper.distinctAvailableDayCount(windows));
    }

    // ---- tryMinimalUpgradeShape ----

    @Test
    public void tryMinimalUpgradeShape_naiveAlreadyFailedMargin_upgradesOnlyOneBlock() {
        // baseSize 1: 4 hours needing to drop from 4 blocks to 3 (margin: 3 + 1
        // <= 4) - merging just one pair (into a single 2h block) is enough; the
        // rest stay 1h.
        List<Integer> shape = AvailabilityAwareBlockShaper.tryMinimalUpgradeShape(4, 1, 1, 4, 1);
        assertEquals(List.of(2, 1, 1), shape);
    }

    @Test
    public void tryMinimalUpgradeShape_tighterCalendar_upgradesAsManyAsMarginRequires() {
        // Only 3 available days - reaching margin (needed + 1 <= 3) requires
        // dropping all the way to 2 blocks, i.e. both pairs merged into 2h.
        List<Integer> shape = AvailabilityAwareBlockShaper.tryMinimalUpgradeShape(4, 1, 1, 3, 1);
        assertEquals(List.of(2, 2), shape);
    }

    @Test
    public void tryMinimalUpgradeShape_marginUnreachable_fallsBackToBareFeasible() {
        // 5 hours, only 3 available days: no merge count reaches margin (needed
        // + 1 <= 3), but merging 2 pairs ([2,2,1], 3 blocks) is bare-feasible
        // (3 <= 3, margin 0).
        List<Integer> shape = AvailabilityAwareBlockShaper.tryMinimalUpgradeShape(5, 1, 1, 3, 1);
        assertEquals(List.of(2, 2, 1), shape);
    }

    @Test
    public void tryMinimalUpgradeShape_evenAllBlocksUpgraded_isNotEnough_returnsNull() {
        // 5 hours, only 2 available days: even every full pair merged to 2h
        // ([2,2,1], 3 blocks) needs more days than are available (3 > 2) -
        // nothing in the 1h/2h range is even bare-feasible.
        List<Integer> shape = AvailabilityAwareBlockShaper.tryMinimalUpgradeShape(5, 1, 1, 2, 1);
        assertNull(shape);
    }

    @Test
    public void tryMinimalUpgradeShape_naiveAlreadyMarginSafe_returnsNaiveUnchanged() {
        // Included for completeness: the caller only invokes this after the
        // naive shape has already failed margin, but the method itself is a
        // pure search and should still behave sensibly if it weren't - the
        // naive block count (5, zero merges) is the first one tried, and
        // reaching margin at the very first (least-merged) count it checks is
        // the "no upgrade needed at all" case.
        List<Integer> shape = AvailabilityAwareBlockShaper.tryMinimalUpgradeShape(5, 1, 1, 6, 1);
        assertEquals(List.of(1, 1, 1, 1, 1), shape);
    }

    @Test
    public void tryMinimalUpgradeShape_baseSizeTwo_mergesIntoDoubleSizeNotFlatIncrement() {
        // baseSize 2 - the generalization derives the upgrade size (4h) by
        // doubling, not by adding 1 (which wouldn't preserve total hours for
        // any base other than 1). 6 hours naive-packs as [2,2,2] (3 blocks,
        // fails margin: 3 + 1 = 4 <= 3 available is false); dropping to 2
        // blocks reaches margin (2 + 1 = 3 <= 3) via one merge: two of the 2h
        // blocks combine into a single 4h block, leaving [4,2].
        List<Integer> shape = AvailabilityAwareBlockShaper.tryMinimalUpgradeShape(6, 2, 1, 3, 1);
        assertEquals(List.of(4, 2), shape);
    }

    @Test
    public void tryMinimalUpgradeShape_baseSizeTwo_leavesAnUnevenRemainderBlockUntouchedByMerging() {
        // baseSize 2, 5 hours -> naive [2,2,1] (2 full blocks + a 1h remainder,
        // 3 blocks total, fails margin: 3 + 1 = 4 <= 3 available is false). The
        // remainder is never a merge candidate; only the two full 2h blocks can
        // merge, into a single 4h block: [4,1] reaches margin (2 + 1 = 3 <= 3).
        List<Integer> shape = AvailabilityAwareBlockShaper.tryMinimalUpgradeShape(5, 2, 1, 3, 1);
        assertEquals(List.of(4, 1), shape);
    }

    @Test
    public void tryMinimalUpgradeShape_baseSizeThreeOrMore_cannotMergeAtAll_returnsNullImmediately() {
        // baseSize 3 would need to double to 6h to merge - past the 4h
        // structural maximum - so there's nowhere to merge to at all,
        // regardless of how tight the calendar is.
        assertNull(AvailabilityAwareBlockShaper.tryMinimalUpgradeShape(6, 3, 1, 1, 1));
    }
}
