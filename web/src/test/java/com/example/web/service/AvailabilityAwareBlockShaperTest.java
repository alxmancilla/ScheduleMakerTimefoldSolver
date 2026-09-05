package com.example.web.service;

import com.example.web.entity.TeacherEntity;
import org.junit.Test;

import java.util.List;

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
        org.junit.Assert.assertTrue(AvailabilityAwareBlockShaper.fitsWithinDayCap(4, 2, 2));
    }

    @Test
    public void fitsWithinDayCap_needsCeiling() {
        // 5 blocks at 2/day needs 3 days, not 2
        org.junit.Assert.assertFalse(AvailabilityAwareBlockShaper.fitsWithinDayCap(5, 2, 2));
        org.junit.Assert.assertTrue(AvailabilityAwareBlockShaper.fitsWithinDayCap(5, 2, 3));
    }

    @Test
    public void fitsWithinDayCap_zeroOrNegativeCap_isFalse() {
        org.junit.Assert.assertFalse(AvailabilityAwareBlockShaper.fitsWithinDayCap(1, 0, 5));
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
        // Teacher only has 2 days, each with a 4h contiguous window.
        TeacherEntity teacher = teacherWithHours(new int[][] {
                { 1, 7 }, { 1, 8 }, { 1, 9 }, { 1, 10 },
                { 2, 7 }, { 2, 8 }, { 2, 9 }, { 2, 10 },
        });

        List<Integer> shape = AvailabilityAwareBlockShaper.tryAvailabilityAwareShape(5, 1, 1, teacher);

        // Size 3 is the first that fits: packBlocks(5,3) = [3,2], 2 blocks at 1/day = 2 days.
        assertEquals(List.of(3, 2), shape);
    }

    @Test
    public void tryAvailabilityAwareShape_noSizeFits_returnsNull() {
        // Teacher only ever available 1 day, 1 hour - nothing can make this fit.
        TeacherEntity teacher = teacherWithHours(new int[][] { { 1, 7 } });
        assertNull(AvailabilityAwareBlockShaper.tryAvailabilityAwareShape(5, 1, 1, teacher));
    }

    @Test
    public void tryAvailabilityAwareShape_windowSmallerThanPreferredSize_returnsNull() {
        // Largest window (1h) is below the preferred size (2h) - nothing to even try.
        TeacherEntity teacher = teacherWithHours(new int[][] { { 1, 7 }, { 2, 8 }, { 3, 9 } });
        assertNull(AvailabilityAwareBlockShaper.tryAvailabilityAwareShape(6, 2, 1, teacher));
    }

    @Test
    public void tryAvailabilityAwareShape_noAvailabilityAtAll_returnsNull() {
        TeacherEntity teacher = new TeacherEntity("T1", "Ada", "Lovelace", 40);
        assertNull(AvailabilityAwareBlockShaper.tryAvailabilityAwareShape(5, 1, 1, teacher));
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
}
