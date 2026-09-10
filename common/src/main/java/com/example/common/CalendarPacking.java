package com.example.common;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.SortedSet;

/**
 * The single, canonical implementation of "pack these hours into blocks, and
 * fit those blocks into a day-by-day availability calendar" - previously two
 * independently-hand-maintained copies of the same bin-packing algorithm:
 * the web module's {@code AvailabilityAwareBlockShaper} (day keys as plain
 * {@code Integer}, from {@code TeacherAvailabilityEntity}) and the engine
 * module's {@code PreSolveValidator} (day keys as {@code java.time.DayOfWeek},
 * from {@code Teacher.getAvailabilityPerDay()}). Both modules depend on this
 * one instead, exactly the reason this module exists (see {@link
 * RoomTypeCompatibility}'s own javadoc for the same pattern applied earlier).
 *
 * <p>The day-key type is a type parameter ({@code D}) wherever the algorithm
 * needs to track per-day state, rather than fixed to either module's own
 * representation - {@link #assignWindows} works identically whether the
 * caller's calendar is keyed by {@code Integer} or {@code DayOfWeek} or
 * anything else with working {@code equals}/{@code hashCode}. Everywhere else
 * (packing hours into blocks, checking a block count against a day cap,
 * building one day's contiguous windows from its available hours) doesn't
 * need to know about days at all, or only needs to read a windows map's
 * values, so those methods take no type parameter.
 *
 * <p>A "windows" map, throughout, is {@code Map<D, List<int[]>>}: each day
 * maps to a list of mutable {@code [startHour, remainingLength]} pairs - one
 * per contiguous run of available hours that day. Building the initial
 * calendar from a teacher's raw availability (and any pinned-hours
 * subtraction) is deliberately NOT part of this class - that's genuinely
 * different glue per caller (different source entities/domain types), only
 * {@link #contiguousWindows} (turning one day's sorted available hours into
 * windows) is shared.
 */
public final class CalendarPacking {

    /** DB check_block_length constraint: a block is 1-4 hours. */
    public static final int MAX_BLOCK_LENGTH = 4;

    /**
     * How many spare distinct days a shape should leave beyond the bare
     * minimum needed, when a size achieving that margin exists at all. A
     * teacher with exactly enough days and not one more has zero room for
     * any other scheduling pressure that day (the group already busy, a room
     * conflict) to be absorbed - the solver has to fail somewhere. This is a
     * probabilistic hedge, not a guarantee: it lowers how often that
     * happens, it doesn't prove it can't.
     */
    public static final int DEFAULT_MARGIN_DAYS = 1;

    private CalendarPacking() {
    }

    /**
     * Packs {@code hours} into blocks of exactly {@code blockSize}, with a
     * smaller trailing remainder block if it doesn't divide evenly.
     */
    public static List<Integer> packBlocks(int hours, int blockSize) {
        List<Integer> lengths = new ArrayList<>();
        int remaining = hours;
        while (remaining > 0) {
            if (remaining >= blockSize) {
                lengths.add(blockSize);
                remaining -= blockSize;
            } else {
                lengths.add(remaining);
                remaining = 0;
            }
        }
        return lengths;
    }

    /**
     * True if this many blocks, capped at maxBlocksPerDay/day, fit within
     * availableDays with at least marginDays to spare. Pass 0 for the bare
     * "is this even possible at all" check; a positive margin additionally
     * requires headroom beyond the minimum, so the solver isn't left with
     * zero room to absorb any other scheduling pressure that day.
     */
    public static boolean fitsWithinDayCap(int blockCount, int maxBlocksPerDay, int availableDays, int marginDays) {
        if (maxBlocksPerDay <= 0) {
            return false;
        }
        int neededDays = (blockCount + maxBlocksPerDay - 1) / maxBlocksPerDay; // ceil division
        return neededDays + marginDays <= availableDays;
    }

    /**
     * Turns one day's sorted available hours into contiguous windows (no
     * gaps), each a mutable {@code [startHour, remainingLength]} pair -
     * mutable so {@link #assignWindows} can consume them in place.
     */
    public static List<int[]> contiguousWindows(SortedSet<Integer> hours) {
        List<int[]> windows = new ArrayList<>();
        Integer start = null;
        Integer prev = null;
        for (int h : hours) {
            if (start == null) {
                start = h;
                prev = h;
                continue;
            }
            if (h == prev + 1) {
                prev = h;
                continue;
            }
            windows.add(new int[] { start, prev - start + 1 });
            start = h;
            prev = h;
        }
        if (start != null) {
            windows.add(new int[] { start, prev - start + 1 });
        }
        return windows;
    }

    /**
     * Days that still have at least one window with hours actually left in
     * it - a day whose windows have all been consumed down to zero no longer
     * counts as available, exactly as if it had never been free at all. Only
     * reads {@code windows}' values, never its keys, so the day-key type
     * doesn't matter here.
     */
    public static int distinctAvailableDayCount(Map<?, List<int[]>> windows) {
        int count = 0;
        for (List<int[]> dayWindows : windows.values()) {
            for (int[] w : dayWindows) {
                if (w[1] > 0) {
                    count++;
                    break;
                }
            }
        }
        return count;
    }

    /** Max remaining window length across every day - a block longer than this can't be placed anywhere. */
    public static int largestContiguousWindow(Map<?, List<int[]>> windows) {
        int max = 0;
        for (List<int[]> dayWindows : windows.values()) {
            for (int[] w : dayWindows) {
                max = Math.max(max, w[1]);
            }
        }
        return max;
    }

    /**
     * Tries block sizes from {@code preferredBlockSize} up to
     * min(MAX_BLOCK_LENGTH, the calendar's largest single contiguous
     * available window - a block longer than that could never be placed
     * regardless of day count). Prefers the first (smallest) size whose
     * resulting block count fits within maxBlocksPerDay across the
     * calendar's distinct available days <em>with marginDays to spare</em> -
     * a shape that's only just barely possible leaves the solver no room to
     * absorb any other scheduling pressure that day. When no size reaches
     * that margin, falls back to the first size that's at least
     * bare-feasible (margin 0) rather than giving up outright.
     *
     * @return the adapted shape, preferring one with margin but settling for
     *         bare feasibility if margin is unreachable; null only if no
     *         size in that range is even bare-feasible
     */
    public static List<Integer> tryAvailabilityAwareShape(int hours, int preferredBlockSize, int maxBlocksPerDay,
            Map<?, List<int[]>> windows, int marginDays) {
        int availableDays = distinctAvailableDayCount(windows);
        if (availableDays == 0) {
            return null;
        }
        int upperBound = Math.min(MAX_BLOCK_LENGTH, largestContiguousWindow(windows));
        List<Integer> bareFeasible = null;
        for (int blockSize = preferredBlockSize; blockSize <= upperBound; blockSize++) {
            List<Integer> candidate = packBlocks(hours, blockSize);
            if (fitsWithinDayCap(candidate.size(), maxBlocksPerDay, availableDays, marginDays)) {
                return candidate;
            }
            if (bareFeasible == null && fitsWithinDayCap(candidate.size(), maxBlocksPerDay, availableDays, 0)) {
                bareFeasible = candidate;
            }
        }
        return bareFeasible;
    }

    /**
     * Tries to reach a safe day count by "merging" the FEWEST possible pairs
     * of {@code baseSize} blocks into a single double-size block, instead of
     * uniformly resizing every block the way {@link #tryAvailabilityAwareShape}
     * does - e.g. 4 hours at baseSize 1, needing to drop from 4 blocks to 3,
     * becomes {@code [2, 1, 1]} (one merge) rather than {@code [2, 2]}
     * (every block merged), whenever the smaller merge is enough. Doubling
     * is the only upgrade size that exactly preserves total hours when
     * merging exactly two same-size blocks into one - unlike a flat "+1"
     * size step, which would silently lose or gain hours for any baseSize
     * other than 1.
     *
     * <p>Any leftover remainder block from packing at {@code baseSize} (when
     * hours don't divide evenly) is never itself a merge candidate - it's
     * already smaller than a full baseSize block.
     *
     * <p>Deliberately caps out at double {@code baseSize} by design: unlike
     * {@link #tryAvailabilityAwareShape}, which escalates uniformly up to
     * the 4h structural maximum, this never considers a block larger than
     * {@code baseSize * 2}. Returns null immediately - without trying
     * anything - when {@code baseSize * 2} would already exceed
     * {@link #MAX_BLOCK_LENGTH} (nowhere to merge to at all, e.g. baseSize 3
     * or 4), or when even every full-size block merged isn't bare-feasible.
     *
     * @return the shape, preferring one with margin but settling for bare
     *         feasibility if margin is unreachable at any merge count; null
     *         if merging isn't structurally possible at all, or even every
     *         full-size block merged isn't bare-feasible
     */
    public static List<Integer> tryMinimalUpgradeShape(int hours, int baseSize, int maxBlocksPerDay,
            int availableDays, int marginDays) {
        int upgradeSize = baseSize * 2;
        if (upgradeSize > MAX_BLOCK_LENGTH) {
            return null;
        }
        int fullBlocks = hours / baseSize;
        int remainder = hours % baseSize;
        int maxMerges = fullBlocks / 2;
        List<Integer> bareFeasible = null;
        for (int merges = 0; merges <= maxMerges; merges++) {
            int blockCount = (fullBlocks - merges) + (remainder > 0 ? 1 : 0);
            if (fitsWithinDayCap(blockCount, maxBlocksPerDay, availableDays, marginDays)) {
                return buildMixedShape(baseSize, upgradeSize, merges, fullBlocks - 2 * merges, remainder);
            }
            if (bareFeasible == null && fitsWithinDayCap(blockCount, maxBlocksPerDay, availableDays, 0)) {
                bareFeasible = buildMixedShape(baseSize, upgradeSize, merges, fullBlocks - 2 * merges, remainder);
            }
        }
        return bareFeasible;
    }

    private static List<Integer> buildMixedShape(int baseSize, int upgradeSize, int upgradeCount, int baseCount,
            int remainder) {
        List<Integer> shape = new ArrayList<>(upgradeCount + baseCount + (remainder > 0 ? 1 : 0));
        for (int i = 0; i < upgradeCount; i++) {
            shape.add(upgradeSize);
        }
        for (int i = 0; i < baseCount; i++) {
            shape.add(baseSize);
        }
        if (remainder > 0) {
            shape.add(remainder);
        }
        return shape;
    }

    /** Where one block ended up: which day, and its start hour. */
    public record Placement<D>(D day, int startHour) {
    }

    /**
     * Greedily assigns each block length a concrete {@link Placement} by
     * consuming {@code windows} in day/window order, respecting
     * maxBlocksPerDay. All-or-nothing: if any length can't be placed,
     * returns null rather than a partial assignment.
     *
     * <p>Transactional: {@code windows} is only mutated when every length
     * places successfully. A failure partway through leaves it completely
     * untouched, not partially consumed - essential when the same map is
     * shared and reused across several calls (e.g. several (group, course)
     * pairings sharing one teacher's calendar), since a caller must be able
     * to trust a failed attempt didn't silently eat into what the next
     * caller sees.
     *
     * <p>Iterates {@code windows.entrySet()} in whatever order the caller's
     * map provides - a caller wanting a deterministic (e.g. day-of-week
     * order) placement should pass a {@link java.util.TreeMap} or similarly
     * ordered map; this method doesn't require {@code D} to be
     * {@link Comparable} itself, it just preserves whatever iteration order
     * it's handed.
     *
     * @return one {@link Placement} per input length, in the same order, or
     *         null if any length couldn't be placed
     */
    public static <D> List<Placement<D>> assignWindows(List<Integer> blockLengths, int maxBlocksPerDay,
            Map<D, List<int[]>> windows) {
        Map<D, List<int[]>> trial = deepCopyWindows(windows);
        Map<D, Integer> placedToday = new HashMap<>();
        List<Placement<D>> result = new ArrayList<>();
        for (int length : blockLengths) {
            Placement<D> placement = placeOne(trial, placedToday, maxBlocksPerDay, length);
            if (placement == null) {
                return null; // windows untouched
            }
            result.add(placement);
        }
        windows.clear();
        windows.putAll(trial);
        return result;
    }

    private static <D> Map<D, List<int[]>> deepCopyWindows(Map<D, List<int[]>> src) {
        // LinkedHashMap to preserve the caller's own iteration order (see assignWindows' javadoc)
        // without requiring D to be Comparable.
        Map<D, List<int[]>> copy = new LinkedHashMap<>();
        for (Map.Entry<D, List<int[]>> entry : src.entrySet()) {
            List<int[]> windowsCopy = new ArrayList<>();
            for (int[] w : entry.getValue()) {
                windowsCopy.add(new int[] { w[0], w[1] });
            }
            copy.put(entry.getKey(), windowsCopy);
        }
        return copy;
    }

    private static <D> Placement<D> placeOne(Map<D, List<int[]>> windows, Map<D, Integer> placedToday,
            int maxBlocksPerDay, int length) {
        for (Map.Entry<D, List<int[]>> dayEntry : windows.entrySet()) {
            D day = dayEntry.getKey();
            if (placedToday.getOrDefault(day, 0) >= maxBlocksPerDay) {
                continue;
            }
            for (int[] window : dayEntry.getValue()) {
                if (window[1] >= length) {
                    int startHour = window[0];
                    window[0] += length;
                    window[1] -= length;
                    placedToday.merge(day, 1, Integer::sum);
                    return new Placement<>(day, startHour);
                }
            }
        }
        return null;
    }
}
