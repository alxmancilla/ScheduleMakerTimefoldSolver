package com.example.common;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * The canonical test for the overlap/consecutiveness math both the engine
 * module's {@code BlockScheduleMath} (over {@code CourseBlockAssignment}/
 * {@code BlockTimeslot} domain objects) and web's move-validation logic
 * (over plain entity rows) delegate to.
 */
public class BlockTimingMathTest {

    private static List<int[]> blocks(int... startLengthPairs) {
        List<int[]> result = new ArrayList<>();
        for (int i = 0; i < startLengthPairs.length; i += 2) {
            result.add(new int[] { startLengthPairs[i], startLengthPairs[i + 1] });
        }
        return result;
    }

    // ---- overlaps ----

    @Test
    public void overlaps_identicalRanges_true() {
        assertTrue(BlockTimingMath.overlaps(8, 2, 8, 2));
    }

    @Test
    public void overlaps_partialOverlap_true() {
        // [8,10) vs [9,11)
        assertTrue(BlockTimingMath.overlaps(8, 2, 9, 2));
    }

    @Test
    public void overlaps_oneEndsWhereOtherStarts_false() {
        // [8,10) vs [10,12) - touching, not overlapping
        assertFalse(BlockTimingMath.overlaps(8, 2, 10, 2));
    }

    @Test
    public void overlaps_disjoint_false() {
        assertFalse(BlockTimingMath.overlaps(7, 1, 12, 1));
    }

    @Test
    public void overlaps_oneRangeFullyInsideAnother_true() {
        // [7,14) vs [9,10)
        assertTrue(BlockTimingMath.overlaps(7, 7, 9, 1));
    }

    @Test
    public void overlaps_isSymmetric() {
        assertEquals(BlockTimingMath.overlaps(8, 3, 9, 2), BlockTimingMath.overlaps(9, 2, 8, 3));
    }

    // ---- countChainBreaks ----

    @Test
    public void countChainBreaks_singleBlock_zero() {
        assertEquals(0, BlockTimingMath.countChainBreaks(blocks(8, 1)));
    }

    @Test
    public void countChainBreaks_contiguousChain_zero() {
        // 7-8, 8-9, 9-10
        assertEquals(0, BlockTimingMath.countChainBreaks(blocks(7, 1, 8, 1, 9, 1)));
    }

    @Test
    public void countChainBreaks_oneGap_one() {
        // 7-8, then a gap, 10-11
        assertEquals(1, BlockTimingMath.countChainBreaks(blocks(7, 1, 10, 1)));
    }

    @Test
    public void countChainBreaks_countsOneBreakPerGapNotPerHour() {
        // 7-8, gap of 3 hours, 11-12 - still exactly one break, regardless of gap size
        assertEquals(1, BlockTimingMath.countChainBreaks(blocks(7, 1, 11, 1)));
    }

    @Test
    public void countChainBreaks_doesNotDependOnInputOrder() {
        List<int[]> inOrder = blocks(7, 1, 8, 1, 11, 1);
        List<int[]> reversed = new ArrayList<>(inOrder);
        java.util.Collections.reverse(reversed);
        assertEquals(BlockTimingMath.countChainBreaks(inOrder), BlockTimingMath.countChainBreaks(reversed));
    }

    @Test
    public void countChainBreaks_overlappingBlocksCountAsABreakToo() {
        // 7-9 and 8-10 overlap rather than chain cleanly - prevEnd (9) != next start (8)
        assertEquals(1, BlockTimingMath.countChainBreaks(blocks(7, 2, 8, 2)));
    }

    @Test
    public void countChainBreaks_multipleGaps_countsEachOne() {
        // 7-8, gap, 9-10... wait make it unambiguous: 7-8 | gap | 10-11 | gap | 13-14
        assertEquals(2, BlockTimingMath.countChainBreaks(blocks(7, 1, 10, 1, 13, 1)));
    }

    @Test
    public void countChainBreaks_emptyList_zero() {
        assertEquals(0, BlockTimingMath.countChainBreaks(Arrays.asList()));
    }
}
