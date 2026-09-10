package com.example.common;

import org.junit.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * The canonical test for the day-by-day bin-packing algorithm both the web
 * module's {@code AvailabilityAwareBlockShaper} (day keys as plain
 * {@code Integer}) and the engine module's {@code PreSolveValidator} (day
 * keys as {@code java.time.DayOfWeek}) delegate to. Several tests below use
 * neither of those types - plain {@code String} day labels - specifically to
 * prove the generic methods genuinely don't care what {@code D} is, not just
 * that they happen to work for the two concrete types currently in use.
 */
public class CalendarPackingTest {

    private static Map<String, List<int[]>> windows(Object... dayWindowTriples) {
        // Each triple: day (String), startHour (Integer), length (Integer).
        Map<String, List<int[]>> result = new LinkedHashMap<>();
        for (int i = 0; i < dayWindowTriples.length; i += 3) {
            String day = (String) dayWindowTriples[i];
            int start = (Integer) dayWindowTriples[i + 1];
            int length = (Integer) dayWindowTriples[i + 2];
            result.computeIfAbsent(day, d -> new java.util.ArrayList<>()).add(new int[] { start, length });
        }
        return result;
    }

    // ---- packBlocks ----

    @Test
    public void packBlocks_evenDivision_allSameSize() {
        assertEquals(List.of(2, 2, 2), CalendarPacking.packBlocks(6, 2));
    }

    @Test
    public void packBlocks_unevenDivision_trailingRemainder() {
        assertEquals(List.of(2, 2, 1), CalendarPacking.packBlocks(5, 2));
    }

    @Test
    public void packBlocks_sizeOne_oneBlockPerHour() {
        assertEquals(List.of(1, 1, 1), CalendarPacking.packBlocks(3, 1));
    }

    // ---- fitsWithinDayCap ----

    @Test
    public void fitsWithinDayCap_zeroOrNegativeCap_isFalse() {
        assertFalse(CalendarPacking.fitsWithinDayCap(1, 0, 5, 0));
    }

    @Test
    public void fitsWithinDayCap_margin_requiresSpareDaysBeyondTheMinimum() {
        assertTrue(CalendarPacking.fitsWithinDayCap(2, 1, 2, 0)); // bare fit
        assertFalse(CalendarPacking.fitsWithinDayCap(2, 1, 2, 1)); // no spare day
        assertTrue(CalendarPacking.fitsWithinDayCap(2, 1, 3, 1)); // one spare day
    }

    // ---- contiguousWindows ----

    @Test
    public void contiguousWindows_singleRun_oneWindow() {
        List<int[]> result = CalendarPacking.contiguousWindows(new TreeSet<>(List.of(7, 8, 9)));
        assertEquals(1, result.size());
        assertArrayEquals(new int[] { 7, 3 }, result.get(0));
    }

    @Test
    public void contiguousWindows_gapSplitsIntoTwoWindows() {
        List<int[]> result = CalendarPacking.contiguousWindows(new TreeSet<>(List.of(7, 8, 11, 12)));
        assertEquals(2, result.size());
        assertArrayEquals(new int[] { 7, 2 }, result.get(0));
        assertArrayEquals(new int[] { 11, 2 }, result.get(1));
    }

    @Test
    public void contiguousWindows_empty_noWindows() {
        assertTrue(CalendarPacking.contiguousWindows(new TreeSet<>()).isEmpty());
    }

    // ---- distinctAvailableDayCount / largestContiguousWindow (generic day keys) ----

    @Test
    public void distinctAvailableDayCount_ignoresDaysExhaustedToZero_withStringDayKeys() {
        Map<String, List<int[]>> w = windows("Mon", 7, 0, "Tue", 7, 3);
        assertEquals(1, CalendarPacking.distinctAvailableDayCount(w));
    }

    @Test
    public void largestContiguousWindow_reflectsRemainingLength_withStringDayKeys() {
        Map<String, List<int[]>> w = windows("Mon", 7, 1, "Tue", 7, 3);
        assertEquals(3, CalendarPacking.largestContiguousWindow(w));
    }

    // ---- tryAvailabilityAwareShape ----

    @Test
    public void tryAvailabilityAwareShape_findsLongerShapeThatFits() {
        // 5 hours, preferredSize=1, maxBlocksPerDay=1 -> naive needs 5 days.
        // Only 2 days, each with a 4h contiguous window - no spare day exists
        // at any size, so margin 0 (bare feasibility only) is used here.
        Map<String, List<int[]>> w = windows("Mon", 7, 4, "Tue", 7, 4);
        List<Integer> shape = CalendarPacking.tryAvailabilityAwareShape(5, 1, 1, w, 0);
        // Size 3 is the first that fits: packBlocks(5,3) = [3,2], 2 blocks at 1/day = 2 days.
        assertEquals(List.of(3, 2), shape);
    }

    @Test
    public void tryAvailabilityAwareShape_prefersMarginOverBareFeasible() {
        Map<String, List<int[]>> w = windows("Mon", 7, 2, "Tue", 7, 2, "Wed", 7, 2);
        // 4 hours, preferredSize=1, maxBlocksPerDay=1: naive needs 4 days (only 3
        // available). Size 2 -> [2,2], needs 2 days, margin 2+1=3 <= 3 -> fits with margin.
        List<Integer> shape = CalendarPacking.tryAvailabilityAwareShape(4, 1, 1, w, 1);
        assertEquals(List.of(2, 2), shape);
    }

    @Test
    public void tryAvailabilityAwareShape_noSizeFits_returnsNull() {
        Map<String, List<int[]>> w = windows("Mon", 7, 1);
        assertNull(CalendarPacking.tryAvailabilityAwareShape(5, 1, 1, w, 0));
    }

    @Test
    public void tryAvailabilityAwareShape_worksAgainstAnAlreadyPartiallyConsumedCalendar() {
        // Only 1 day realistically left (as if another pairing sharing this
        // calendar already consumed the rest), with a 3h window.
        Map<String, List<int[]>> w = windows("Wed", 7, 3);
        List<Integer> shape = CalendarPacking.tryAvailabilityAwareShape(3, 1, 1, w, 0);
        assertEquals(List.of(3), shape); // single 3h block is the only way to fit in one remaining day
    }

    // ---- tryMinimalUpgradeShape ----

    @Test
    public void tryMinimalUpgradeShape_baseSizeOne_upgradesOnlyTheFewestBlocksNeeded() {
        // 4 hours needing to drop from 4 blocks to 3 (margin: 3 + 1 <= 4) -
        // merging just one pair (into a single 2h block) is enough.
        List<Integer> shape = CalendarPacking.tryMinimalUpgradeShape(4, 1, 1, 4, 1);
        assertEquals(List.of(2, 1, 1), shape);
    }

    @Test
    public void tryMinimalUpgradeShape_baseSizeTwo_mergesIntoDoubleSizeNotFlatIncrement() {
        // baseSize 2 - the upgrade size (4h) comes from doubling, not adding 1
        // (which wouldn't preserve total hours for any base other than 1). 6
        // hours naive-packs as [2,2,2] (3 blocks, fails margin: 3+1=4 > 3
        // available); dropping to 2 blocks reaches margin (2+1=3 <= 3) via one
        // merge: two of the 2h blocks combine into a single 4h block.
        List<Integer> shape = CalendarPacking.tryMinimalUpgradeShape(6, 2, 1, 3, 1);
        assertEquals(List.of(4, 2), shape);
    }

    @Test
    public void tryMinimalUpgradeShape_leavesAnUnevenRemainderBlockUntouchedByMerging() {
        // baseSize 2, 5 hours -> naive [2,2,1] (2 full blocks + a 1h remainder).
        // The remainder is never a merge candidate; only the two full 2h
        // blocks can merge, into a single 4h block: [4,1].
        List<Integer> shape = CalendarPacking.tryMinimalUpgradeShape(5, 2, 1, 3, 1);
        assertEquals(List.of(4, 1), shape);
    }

    @Test
    public void tryMinimalUpgradeShape_baseSizeThreeOrMore_cannotMergeAtAll_returnsNullImmediately() {
        // baseSize 3 would need to double to 6h to merge - past the 4h
        // structural maximum - so there's nowhere to merge to at all.
        assertNull(CalendarPacking.tryMinimalUpgradeShape(6, 3, 1, 1, 1));
    }

    @Test
    public void tryMinimalUpgradeShape_marginUnreachable_fallsBackToBareFeasible() {
        List<Integer> shape = CalendarPacking.tryMinimalUpgradeShape(5, 1, 1, 3, 1);
        assertEquals(List.of(2, 2, 1), shape);
    }

    @Test
    public void tryMinimalUpgradeShape_evenAllBlocksUpgraded_isNotEnough_returnsNull() {
        assertNull(CalendarPacking.tryMinimalUpgradeShape(5, 1, 1, 2, 1));
    }

    // ---- assignWindows (generic day keys, transactional) ----

    @Test
    public void assignWindows_withStringDayKeys_placesEachLengthAndReturnsWhereItLanded() {
        Map<String, List<int[]>> w = windows("Mon", 7, 4);

        List<CalendarPacking.Placement<String>> placements = CalendarPacking.assignWindows(List.of(2), 1, w);

        assertEquals(1, placements.size());
        assertEquals("Mon", placements.get(0).day());
        assertEquals(7, placements.get(0).startHour());
        assertEquals(2, w.get("Mon").get(0)[1]); // 2 of the 4 hours are now consumed
    }

    @Test
    public void assignWindows_mutatesTheSameMapAcrossCalls() {
        Map<String, List<int[]>> w = windows("Mon", 7, 4);

        CalendarPacking.assignWindows(List.of(2), 1, w);
        assertEquals(2, w.get("Mon").get(0)[1]);

        List<CalendarPacking.Placement<String>> second = CalendarPacking.assignWindows(List.of(2), 1, w);
        assertEquals(9, second.get(0).startHour());
        assertEquals(0, w.get("Mon").get(0)[1]);
    }

    @Test
    public void assignWindows_failedAttemptLeavesMapCompletelyUntouched() {
        Map<String, List<int[]>> w = windows("Mon", 7, 3);
        int[] before = w.get("Mon").get(0).clone();

        // First block (2h) fits; second (5h) doesn't - the whole call must
        // fail and roll back, not leave the first block's consumption applied.
        List<CalendarPacking.Placement<String>> result = CalendarPacking.assignWindows(List.of(2, 5), 2, w);

        assertNull(result);
        assertArrayEquals(before, w.get("Mon").get(0));
    }

    @Test
    public void assignWindows_respectsMaxBlocksPerDay_evenWithHoursStillFree() {
        // A single day with plenty of hours (4h), but a cap of 1 block/day -
        // the second 1h block must go to a different day even though this
        // one still has 3h free.
        Map<String, List<int[]>> w = windows("Mon", 7, 4, "Tue", 7, 1);

        List<CalendarPacking.Placement<String>> placements = CalendarPacking.assignWindows(List.of(1, 1), 1, w);

        assertEquals("Mon", placements.get(0).day());
        assertEquals("Tue", placements.get(1).day());
    }
}
