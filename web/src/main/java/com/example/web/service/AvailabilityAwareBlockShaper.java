package com.example.web.service;

import com.example.common.CalendarPacking;
import com.example.web.entity.TeacherAvailabilityEntity;
import com.example.web.entity.TeacherEntity;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * {@link TeacherEntity}-facing facade over {@link CalendarPacking}'s
 * day-by-day packing algorithm, used by {@link BlockGenerationService} for a
 * (group, course) pair with no explicit course_block_template.
 *
 * <p>The actual bin-packing algorithm (packing hours into blocks, checking a
 * block count against a day cap, greedily placing blocks into a calendar)
 * lives in {@code common}'s {@link CalendarPacking} - shared with the engine
 * module's {@code PreSolveValidator}, which needs the identical reasoning
 * over its own {@code Teacher} domain object (keyed by
 * {@code java.time.DayOfWeek} rather than the plain {@code Integer} this
 * class uses). Before this consolidation (2026-09-05), the two modules each
 * hand-maintained their own copy of the same algorithm - this class now
 * exists purely as web's day-key representation (plain {@code Integer}, from
 * {@link TeacherAvailabilityEntity}) plus its {@link TeacherEntity}-specific
 * calendar-building glue ({@link #hoursByDay}, {@link #windowsByDay}),
 * delegating every actual packing decision to {@code CalendarPacking}.
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
 * {@link #tryMinimalUpgradeShape} is an alternative to this for components
 * that specifically want to keep as many small (1h) blocks as possible -
 * see its own javadoc.
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
 *
 * <p>Every method here has a {@link java.util.Map}-based form alongside its
 * {@link TeacherEntity}-based one. The map is the same shape
 * {@link #windowsByDay} produces - {@code dayOfWeek -> [startHour,
 * remainingLength]} pairs - and letting a caller build one once and pass it
 * through several calls is what makes {@code BlockGenerationService}'s
 * shared-calendar grouping possible: several (group, course) pairings that
 * share one teacher can reason against, and progressively consume from, the
 * <em>same</em> calendar instead of each independently assuming they have
 * that teacher's whole week to themselves. {@link #assignWindows(List, int,
 * Map)} is transactional for exactly this reason - a map that outlives a
 * single call must never end up partially consumed by a failed attempt.
 *
 * <p>{@link #windowsByDay(TeacherEntity, List)} additionally lets a caller
 * carve specific hours out of the teacher's raw declared availability before
 * any window is computed - used by {@code BlockGenerationService} to seed a
 * teacher's calendar with hours already committed to a PINNED existing
 * assignment elsewhere, so a brand-new pairing for a teacher who already has
 * other work doesn't get shaped against a calendar that pretends that work
 * doesn't exist.
 */
final class AvailabilityAwareBlockShaper {

    /** DB check_block_length constraint: a block is 1-4 hours. */
    static final int MAX_BLOCK_LENGTH = CalendarPacking.MAX_BLOCK_LENGTH;

    /**
     * How many spare distinct days a shape should leave beyond the bare
     * minimum needed, when a size achieving that margin exists at all. A
     * teacher with exactly enough days and not one more has zero room for
     * any other scheduling pressure that day (the group already busy, a room
     * conflict) to be absorbed - the solver has to fail somewhere. This is a
     * probabilistic hedge, not a guarantee: it lowers how often that happens,
     * it doesn't prove it can't.
     */
    static final int DEFAULT_MARGIN_DAYS = CalendarPacking.DEFAULT_MARGIN_DAYS;

    private AvailabilityAwareBlockShaper() {
    }

    /**
     * Packs {@code hours} into blocks of exactly {@code blockSize}, with a
     * smaller trailing remainder block if it doesn't divide evenly. See
     * {@link CalendarPacking#packBlocks}.
     */
    static List<Integer> packBlocks(int hours, int blockSize) {
        return CalendarPacking.packBlocks(hours, blockSize);
    }

    /**
     * True if this many blocks, capped at maxBlocksPerDay/day, fit within
     * availableDays with at least marginDays to spare. See
     * {@link CalendarPacking#fitsWithinDayCap}.
     */
    static boolean fitsWithinDayCap(int blockCount, int maxBlocksPerDay, int availableDays, int marginDays) {
        return CalendarPacking.fitsWithinDayCap(blockCount, maxBlocksPerDay, availableDays, marginDays);
    }

    /**
     * Tries block sizes from {@code preferredBlockSize} up to
     * min(MAX_BLOCK_LENGTH, this teacher's largest single contiguous
     * available window - a block longer than that could never be placed for
     * them regardless of day count). Prefers the first (smallest) size whose
     * resulting block count fits within maxBlocksPerDay across the teacher's
     * distinct available days <em>with marginDays to spare</em> - a shape
     * that's only just barely possible leaves the solver no room to absorb
     * any other scheduling pressure that day. When no size reaches that
     * margin, falls back to the first size that's at least bare-feasible
     * (margin 0) rather than giving up outright - a course already needing
     * every available day has nowhere left to find margin from, but "just
     * feasible" is still strictly better than the untouched naive shape.
     *
     * @return the adapted shape, preferring one with margin but settling for
     *         bare feasibility if margin is unreachable at any size; null
     *         only if no size in that range is even bare-feasible - the
     *         caller should fall back to the teacher-blind shape in that
     *         case (a genuinely infeasible pairing PreSolveValidator will
     *         still report, exactly as it does today)
     */
    static List<Integer> tryAvailabilityAwareShape(int hours, int preferredBlockSize, int maxBlocksPerDay,
            TeacherEntity teacher, int marginDays) {
        return tryAvailabilityAwareShape(hours, preferredBlockSize, maxBlocksPerDay, windowsByDay(teacher), marginDays);
    }

    /**
     * Same as {@link #tryAvailabilityAwareShape(int, int, int, TeacherEntity, int)},
     * but reasoning against an already-built windows map instead of a raw
     * teacher - the map may be a teacher's full, untouched calendar, or one
     * already partially consumed by another (group, course) pairing sharing
     * the same teacher earlier in the same generation run (see
     * BlockGenerationService's per-teacher shared-calendar grouping). Purely
     * read-only: never mutates {@code windows}. See
     * {@link CalendarPacking#tryAvailabilityAwareShape}.
     */
    static List<Integer> tryAvailabilityAwareShape(int hours, int preferredBlockSize, int maxBlocksPerDay,
            Map<Integer, List<int[]>> windows, int marginDays) {
        return CalendarPacking.tryAvailabilityAwareShape(hours, preferredBlockSize, maxBlocksPerDay, windows,
                marginDays);
    }

    /**
     * Tries to reach a safe day count by "merging" the FEWEST possible pairs
     * of the component's configured preferred-size blocks into a single
     * double-size block, instead of uniformly resizing every block the way
     * {@link #tryAvailabilityAwareShape} does - e.g. 4 hours at
     * {@code baseSize} 1, needing to drop from 4 blocks to 3, becomes
     * {@code [2, 1, 1]} (one merge) rather than {@code [2, 2]} (every block
     * merged), whenever the smaller merge is enough. See
     * {@link CalendarPacking#tryMinimalUpgradeShape} for the full mechanics
     * (doubling as the only hours-preserving upgrade, the deliberate hard
     * cap at double the base size, remainder-block handling).
     */
    static List<Integer> tryMinimalUpgradeShape(int hours, int baseSize, int maxBlocksPerDay, int availableDays,
            int marginDays) {
        return CalendarPacking.tryMinimalUpgradeShape(hours, baseSize, maxBlocksPerDay, availableDays, marginDays);
    }

    /**
     * Greedily assigns each block length a concrete (dayOfWeek, startHour) by
     * consuming this teacher's contiguous available windows in day/window
     * order, respecting maxBlocksPerDay. All-or-nothing: if any block length
     * can't be placed, returns null rather than a partial assignment - a
     * teacher whose whole schedule is supposedly fully determined by this
     * course but who still can't fit all of it means an assumption here was
     * wrong, not a case to patch over. Builds a fresh, single-use calendar
     * from the teacher's raw availability every call.
     *
     * @return one {@code [dayOfWeek, startHour]} per input length, in the
     *         same order, or null if any length couldn't be placed
     */
    static List<int[]> assignWindows(List<Integer> blockLengths, int maxBlocksPerDay, TeacherEntity teacher) {
        return assignWindows(blockLengths, maxBlocksPerDay, windowsByDay(teacher));
    }

    /**
     * Same as {@link #assignWindows(List, int, TeacherEntity)}, but consuming
     * from (and mutating) an already-built, externally-owned windows map -
     * the caller may reuse the same map across several calls for pairings
     * that share one teacher, so each subsequent call sees exactly what
     * earlier ones already claimed. Transactional - see
     * {@link CalendarPacking#assignWindows}. Converts between this class's
     * {@code int[]{day, startHour}} return shape and {@code CalendarPacking}'s
     * generic {@link CalendarPacking.Placement} so every existing caller
     * (and test) keeps working unchanged.
     */
    static List<int[]> assignWindows(List<Integer> blockLengths, int maxBlocksPerDay,
            Map<Integer, List<int[]>> windows) {
        List<CalendarPacking.Placement<Integer>> placements = CalendarPacking.assignWindows(blockLengths,
                maxBlocksPerDay, windows);
        if (placements == null) {
            return null;
        }
        List<int[]> result = new ArrayList<>(placements.size());
        for (CalendarPacking.Placement<Integer> placement : placements) {
            result.add(new int[] { placement.day(), placement.startHour() });
        }
        return result;
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
     * Package-private (not private): BlockGenerationService builds one of
     * these once per teacher to share, and progressively consume, across
     * every (group, course) pairing that teacher has in a single
     * generateBlocks() run - see the class javadoc's "shared calendar" note.
     */
    static Map<Integer, List<int[]>> windowsByDay(TeacherEntity teacher) {
        return windowsByDay(teacher, List.of());
    }

    /**
     * Same as {@link #windowsByDay(TeacherEntity)}, but with specific hours
     * removed from the teacher's raw declared availability before any window
     * is computed - used to seed a teacher's calendar with hours genuinely
     * already committed to a PINNED existing assignment elsewhere (a known,
     * exact day/hour), so a brand-new pairing for that teacher reasons
     * against what's actually left rather than their full raw week. Removing
     * an hour from the middle of a contiguous run naturally splits it into
     * two windows once {@link CalendarPacking#contiguousWindows} re-scans
     * what remains, so no separate window-splitting logic is needed here.
     *
     * @param consumedRanges each {@code [dayOfWeek, startHour, length]} to
     *                        remove before computing windows; a day this
     *                        teacher has no availability for at all is
     *                        silently ignored
     */
    static Map<Integer, List<int[]>> windowsByDay(TeacherEntity teacher, List<int[]> consumedRanges) {
        Map<Integer, SortedSet<Integer>> hours = hoursByDay(teacher);
        for (int[] range : consumedRanges) {
            SortedSet<Integer> dayHours = hours.get(range[0]);
            if (dayHours == null) {
                continue;
            }
            for (int h = range[1]; h < range[1] + range[2]; h++) {
                dayHours.remove(h);
            }
        }
        Map<Integer, List<int[]>> result = new TreeMap<>();
        for (Map.Entry<Integer, SortedSet<Integer>> entry : hours.entrySet()) {
            result.put(entry.getKey(), CalendarPacking.contiguousWindows(entry.getValue()));
        }
        return result;
    }

    static int distinctAvailableDayCount(TeacherEntity teacher) {
        return distinctAvailableDayCount(windowsByDay(teacher));
    }

    /**
     * Days that still have at least one window with hours actually left in
     * it - a day whose windows have all been consumed down to zero (by
     * earlier pairings sharing this calendar) no longer counts as available,
     * exactly as if the teacher had never had that day free at all. See
     * {@link CalendarPacking#distinctAvailableDayCount}.
     */
    static int distinctAvailableDayCount(Map<Integer, List<int[]>> windows) {
        return CalendarPacking.distinctAvailableDayCount(windows);
    }

    static int largestContiguousWindow(TeacherEntity teacher) {
        return largestContiguousWindow(windowsByDay(teacher));
    }

    static int largestContiguousWindow(Map<Integer, List<int[]>> windows) {
        return CalendarPacking.largestContiguousWindow(windows);
    }
}
