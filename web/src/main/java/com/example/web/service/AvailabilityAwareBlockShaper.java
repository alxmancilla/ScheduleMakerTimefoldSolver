package com.example.web.service;

import com.example.web.entity.TeacherAvailabilityEntity;
import com.example.web.entity.TeacherEntity;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Pure, DB-free algorithm for shaping a course's blocks around a specific
 * teacher's actual availability, used by {@link BlockGenerationService} for a
 * (group, course) pair with no explicit course_block_template.
 *
 * Two capabilities, used for two distinct situations:
 *
 * <p><b>Shape adaptation</b> ({@link #tryAvailabilityAwareShape}): when the
 * component's default block decomposition (its configured preferredBlockSize,
 * or {@code DEFAULT_BLOCK_SIZE}) would need more distinct days than this
 * teacher actually has available - the exact "days, not hours" problem
 * PreSolveValidator's validateBlockSpreadCapacity otherwise only catches
 * after the fact - try progressively longer block sizes (fewer, bigger
 * blocks need fewer days under the same maxBlocksPerDay cap) up to the 4h
 * structural maximum, stopping at the first size that fits. This never
 * assigns a specific day; the solver still freely places each block among
 * the teacher's available days, exactly as for any other generated block.
 *
 * <p><b>Window assignment</b> ({@link #assignWindows}): only used when a
 * teacher's entire teaching load is this one (group, course) pair (checked
 * by the caller), in which case there is no longer any real placement
 * decision left for the solver to make - each block's day and hour are
 * already forced by being the only room left in the teacher's calendar. This
 * greedily consumes the teacher's contiguous available windows (hour runs
 * with no gaps) to give each block a concrete day/hour, respecting the same
 * maxBlocksPerDay cap - which, unlike most hard constraints, is NOT
 * re-checked by PreSolveValidator for pinned rows, so getting this right
 * here is the only thing standing between a pin and a silently-violated
 * school rule.
 *
 * Both are heuristics over the teacher's declared availability alone, not a
 * guarantee: neither knows what else the solver will eventually place for
 * this teacher's other commitments (shape adaptation is safe regardless,
 * since it never fixes a day; window assignment is only invoked for a
 * teacher with no other commitments in the first place, which is what makes
 * it safe there too).
 */
final class AvailabilityAwareBlockShaper {

    /** DB check_block_length constraint: a block is 1-4 hours. */
    static final int MAX_BLOCK_LENGTH = 4;

    private AvailabilityAwareBlockShaper() {
    }

    /**
     * Packs {@code hours} into blocks of exactly {@code blockSize}, with a
     * smaller trailing remainder block if it doesn't divide evenly - the same
     * greedy packing BlockGenerationService's decomposeHours always used,
     * extracted so both the teacher-blind and availability-aware paths share
     * one implementation.
     */
    static List<Integer> packBlocks(int hours, int blockSize) {
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

    /** True if this many blocks, capped at maxBlocksPerDay/day, fit within availableDays. */
    static boolean fitsWithinDayCap(int blockCount, int maxBlocksPerDay, int availableDays) {
        if (maxBlocksPerDay <= 0) {
            return false;
        }
        int neededDays = (blockCount + maxBlocksPerDay - 1) / maxBlocksPerDay; // ceil division
        return neededDays <= availableDays;
    }

    /**
     * Tries block sizes from {@code preferredBlockSize} up to
     * min(MAX_BLOCK_LENGTH, this teacher's largest single contiguous
     * available window - a block longer than that could never be placed for
     * them regardless of day count), returning the packing for the first
     * (smallest) size whose resulting block count fits within
     * maxBlocksPerDay across the teacher's distinct available days.
     *
     * @return the adapted shape, or null if no size in that range fits - the
     *         caller should fall back to the teacher-blind shape in that
     *         case (a genuinely infeasible pairing PreSolveValidator will
     *         still report, exactly as it does today)
     */
    static List<Integer> tryAvailabilityAwareShape(int hours, int preferredBlockSize, int maxBlocksPerDay,
            TeacherEntity teacher) {
        int availableDays = distinctAvailableDayCount(teacher);
        if (availableDays == 0) {
            return null;
        }
        int upperBound = Math.min(MAX_BLOCK_LENGTH, largestContiguousWindow(teacher));
        for (int blockSize = preferredBlockSize; blockSize <= upperBound; blockSize++) {
            List<Integer> candidate = packBlocks(hours, blockSize);
            if (fitsWithinDayCap(candidate.size(), maxBlocksPerDay, availableDays)) {
                return candidate;
            }
        }
        return null;
    }

    /**
     * Greedily assigns each block length a concrete (dayOfWeek, startHour) by
     * consuming this teacher's contiguous available windows in day/window
     * order, respecting maxBlocksPerDay. All-or-nothing: if any block length
     * can't be placed, returns null rather than a partial assignment - a
     * teacher whose whole schedule is supposedly fully determined by this
     * course but who still can't fit all of it means an assumption here was
     * wrong, not a case to patch over.
     *
     * @return one {@code [dayOfWeek, startHour]} per input length, in the
     *         same order, or null if any length couldn't be placed
     */
    static List<int[]> assignWindows(List<Integer> blockLengths, int maxBlocksPerDay, TeacherEntity teacher) {
        Map<Integer, List<int[]>> windows = windowsByDay(teacher);
        Map<Integer, Integer> blocksPlacedToday = new TreeMap<>();
        List<int[]> result = new ArrayList<>();
        for (int length : blockLengths) {
            int[] placement = placeOne(windows, blocksPlacedToday, maxBlocksPerDay, length);
            if (placement == null) {
                return null;
            }
            result.add(placement);
        }
        return result;
    }

    private static int[] placeOne(Map<Integer, List<int[]>> windows, Map<Integer, Integer> blocksPlacedToday,
            int maxBlocksPerDay, int length) {
        for (Map.Entry<Integer, List<int[]>> dayEntry : windows.entrySet()) {
            int day = dayEntry.getKey();
            if (blocksPlacedToday.getOrDefault(day, 0) >= maxBlocksPerDay) {
                continue;
            }
            for (int[] window : dayEntry.getValue()) {
                if (window[1] >= length) {
                    int startHour = window[0];
                    window[0] += length;
                    window[1] -= length;
                    blocksPlacedToday.merge(day, 1, Integer::sum);
                    return new int[] { day, startHour };
                }
            }
        }
        return null;
    }

    /** This teacher's available hours per day, e.g. day 1 -> {7, 8, 9, 11, 12}. */
    private static Map<Integer, SortedSet<Integer>> hoursByDay(TeacherEntity teacher) {
        Map<Integer, SortedSet<Integer>> result = new TreeMap<>();
        for (TeacherAvailabilityEntity a : teacher.getAvailability()) {
            result.computeIfAbsent(a.getDayOfWeek(), d -> new TreeSet<>()).add(a.getHour());
        }
        return result;
    }

    /**
     * This teacher's contiguous available windows per day, each as a mutable
     * {@code [startHour, remainingLength]} pair - mutable so assignWindows
     * can consume them in place without needing a second data structure.
     */
    private static Map<Integer, List<int[]>> windowsByDay(TeacherEntity teacher) {
        Map<Integer, List<int[]>> result = new TreeMap<>();
        for (Map.Entry<Integer, SortedSet<Integer>> entry : hoursByDay(teacher).entrySet()) {
            result.put(entry.getKey(), contiguousWindows(entry.getValue()));
        }
        return result;
    }

    private static List<int[]> contiguousWindows(SortedSet<Integer> hours) {
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

    static int distinctAvailableDayCount(TeacherEntity teacher) {
        return (int) teacher.getAvailability().stream().map(TeacherAvailabilityEntity::getDayOfWeek).distinct()
                .count();
    }

    static int largestContiguousWindow(TeacherEntity teacher) {
        int max = 0;
        for (List<int[]> windows : windowsByDay(teacher).values()) {
            for (int[] w : windows) {
                max = Math.max(max, w[1]);
            }
        }
        return max;
    }
}
