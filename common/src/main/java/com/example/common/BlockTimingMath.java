package com.example.common;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Shared, pure timing math for a set of same-day blocks, each represented as
 * the primitive pair {@code int[]{startHour, lengthHours}} - the same
 * convention {@link CalendarPacking} already uses for a day's windows, kept
 * here for the same reason: it lets one implementation serve both engine's
 * {@code BlockScheduleMath} (over {@code CourseBlockAssignment}/
 * {@code BlockTimeslot} domain objects) and web's own move-validation logic
 * (over plain {@code CourseBlockAssignmentEntity}/{@code BlockTimeslotEntity}
 * rows), rather than each hand-maintaining its own copy of overlap/
 * consecutiveness math - exactly the drift risk
 * {@code engine.BlockScheduleMath}'s own class javadoc describes as the
 * reason IT exists, one level up.
 *
 * <p>Day equality is deliberately left to the caller, same as
 * {@code CalendarPacking}'s day-key genericity: a block set passed to
 * {@link #countChainBreaks} is assumed to already share one day, and
 * {@link #overlaps} compares two blocks' hour ranges only - a caller with two
 * different-typed "day" values of its own (a plain {@code int} dayOfWeek in
 * one module, a {@code java.time.DayOfWeek} in the other) checks that
 * equality itself before calling.
 */
public final class BlockTimingMath {

    private BlockTimingMath() {
    }

    /**
     * True if two same-day {@code [start, start+length)} ranges intersect.
     * The caller is responsible for having already confirmed the two blocks
     * are on the same day - this compares hour ranges only.
     */
    public static boolean overlaps(int startHour1, int lengthHours1, int startHour2, int lengthHours2) {
        int end1 = startHour1 + lengthHours1;
        int end2 = startHour2 + lengthHours2;
        return startHour1 < end2 && startHour2 < end1;
    }

    /**
     * Count the number of breaks (gaps or overlaps between adjacent blocks)
     * in a set of same-day blocks once sorted by start hour. Zero means the
     * blocks form a single contiguous chain; each break contributes one
     * violation. Expects every block to already share the same grouping
     * (e.g. group/course/day) - only start-hour ordering is computed here.
     * Each block is {@code int[]{startHour, lengthHours}}.
     */
    public static int countChainBreaks(List<int[]> blocks) {
        List<int[]> sorted = new ArrayList<>(blocks);
        sorted.sort(Comparator.comparingInt(b -> b[0]));
        int breaks = 0;
        for (int i = 1; i < sorted.size(); i++) {
            int prevEnd = sorted.get(i - 1)[0] + sorted.get(i - 1)[1];
            if (prevEnd != sorted.get(i)[0]) {
                breaks++;
            }
        }
        return breaks;
    }
}
