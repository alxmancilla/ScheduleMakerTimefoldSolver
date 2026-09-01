package com.example.domain;

import java.time.DayOfWeek;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Shared, pure scheduling math for block-based schedules - overlap, gap, and
 * consecutiveness calculations - used identically by both
 * {@code SchoolConstraintProvider} (during solving) and
 * {@code BlockScheduleAnalyzer} (post-solve reporting). Previously each side
 * carried its own copy of every one of these calculations, each commented
 * "Mirrors SchoolConstraintProvider.X" / "see the constraint provider's copy
 * of this method" - real drift risk (a constraint's math changing without its
 * mirror following) that this class removes by giving both a single source
 * of truth. No framework/persistence dependencies, same spirit as
 * scheduler-common's RoomTypeCompatibility, just scoped to engine-internal
 * domain types (CourseBlockAssignment/BlockTimeslot) rather than shared with
 * web.
 */
public final class BlockScheduleMath {

    private BlockScheduleMath() {
    }

    // A component with no row in component_block_rule falls back to this,
    // matching the old default cap for non-Core courses.
    public static final int DEFAULT_MAX_BLOCKS_PER_DAY = 2;

    // Half the 8h (7:00-15:00) school day - a teacher/group scheduled this many
    // consecutive hours with zero idle time between blocks must get a break
    // before continuing.
    public static final int MAX_CONSECUTIVE_HOURS_WITHOUT_BREAK = 4;

    // The school day's earliest possible start hour (matches the earliest
    // BlockTimeslot start hour, 7:00) - the target
    // preferSemesterOneBlocksStartEarly penalizes deviation from.
    public static final int EARLIEST_START_HOUR = 7;

    /** This course's configured per-day block cap, or DEFAULT_MAX_BLOCKS_PER_DAY when none is set. */
    public static int maxBlocksPerDay(Course course) {
        Integer configured = course.getMaxBlocksPerDay();
        return configured != null ? configured : DEFAULT_MAX_BLOCKS_PER_DAY;
    }

    /**
     * True if two block timeslots overlap: same day, and their [start, end)
     * ranges intersect.
     */
    public static boolean blocksOverlap(BlockTimeslot block1, BlockTimeslot block2) {
        if (block1 == null || block2 == null) {
            return false;
        }
        if (!block1.getDayOfWeek().equals(block2.getDayOfWeek())) {
            return false;
        }
        int start1 = block1.getStartHour();
        int end1 = block1.getStartHour() + block1.getLengthHours();
        int start2 = block2.getStartHour();
        int end2 = block2.getStartHour() + block2.getLengthHours();
        return start1 < end2 && start2 < end1;
    }

    /**
     * Count the number of breaks (gaps or overlaps between adjacent blocks) in
     * a set of blocks once sorted by start hour. Zero means the blocks form a
     * single contiguous chain; each break contributes one violation. Expects
     * every block to already share the same (group, course, day) grouping -
     * only start-hour ordering is computed here.
     */
    public static int countChainBreaks(List<CourseBlockAssignment> blocks) {
        List<CourseBlockAssignment> sorted = new ArrayList<>(blocks);
        sorted.sort(Comparator.comparingInt(a -> a.getTimeslot().getStartHour()));
        int breaks = 0;
        for (int i = 1; i < sorted.size(); i++) {
            BlockTimeslot prev = sorted.get(i - 1).getTimeslot();
            BlockTimeslot curr = sorted.get(i).getTimeslot();
            int prevEnd = prev.getStartHour() + prev.getLengthHours();
            if (prevEnd != curr.getStartHour()) {
                breaks++;
            }
        }
        return breaks;
    }

    /**
     * The longest run of occupied hours (merging touching or overlapping
     * blocks into one span) once sorted by start hour, tie-broken by id for a
     * deterministic total order. A gap of any size - even one hour - starts a
     * new run. Uses interval-merge (track [runStart, runEnd), extend on
     * overlap/touch) rather than summing block lengths: mid-search the solver
     * freely explores states where two blocks for the same teacher/group
     * overlap (the double-booking constraint hasn't resolved it yet), and
     * summing lengths would double-count that overlap - which also made the
     * result depend on which of two equal-start-hour blocks an
     * otherwise-unstable-for-ties sort visited first, silently violating
     * Timefold's requirement that a constraint be a pure, order-independent
     * function of the group's contents. The explicit id tie-break plus
     * interval-merge fixes both the correctness and the determinism.
     */
    public static int longestConsecutiveRunHours(List<CourseBlockAssignment> blocks) {
        List<CourseBlockAssignment> sorted = new ArrayList<>(blocks);
        sorted.sort(Comparator
                .comparingInt((CourseBlockAssignment a) -> a.getTimeslot().getStartHour())
                .thenComparing(CourseBlockAssignment::getId));
        int longest = 0;
        int runStart = -1;
        int runEnd = -1;
        for (CourseBlockAssignment assignment : sorted) {
            BlockTimeslot timeslot = assignment.getTimeslot();
            int start = timeslot.getStartHour();
            int end = start + timeslot.getLengthHours();
            if (runEnd == -1 || start > runEnd) {
                runStart = start;
                runEnd = end;
            } else {
                runEnd = Math.max(runEnd, end);
            }
            longest = Math.max(longest, runEnd - runStart);
        }
        return longest;
    }

    /**
     * The idle-hour count in the gap between two same-day blocks (0 if they
     * overlap or are consecutive).
     */
    public static int gapHours(CourseBlockAssignment a1, CourseBlockAssignment a2) {
        int start1 = a1.getTimeslot().getStartHour();
        int end1 = start1 + a1.getTimeslot().getLengthHours();
        int start2 = a2.getTimeslot().getStartHour();
        int end2 = start2 + a2.getTimeslot().getLengthHours();
        if (end1 <= start2) {
            return start2 - end1;
        } else if (end2 <= start1) {
            return start1 - end2;
        }
        return 0;
    }

    /**
     * The idle-hour count in the gap between two same-day teacher blocks,
     * counting only hours during which the teacher is actually available
     * (unavailable gap hours are unavoidable and therefore not penalized).
     */
    public static int availableGapHours(CourseBlockAssignment a1, CourseBlockAssignment a2) {
        int start1 = a1.getTimeslot().getStartHour();
        int end1 = start1 + a1.getTimeslot().getLengthHours();
        int start2 = a2.getTimeslot().getStartHour();
        int end2 = start2 + a2.getTimeslot().getLengthHours();
        int gapStart, gapEnd;
        if (end1 <= start2) {
            gapStart = end1;
            gapEnd = start2;
        } else if (end2 <= start1) {
            gapStart = end2;
            gapEnd = start1;
        } else {
            return 0;
        }
        DayOfWeek day = a1.getTimeslot().getDayOfWeek();
        Teacher teacher = a1.getTeacher();
        int available = 0;
        for (int gapHour = gapStart; gapHour < gapEnd; gapHour++) {
            if (teacher.isAvailableAt(day, gapHour)) {
                available++;
            }
        }
        return available;
    }

    /**
     * True if {@code mid} starts within the open span between the earlier
     * block's end and the later block's start of the {@code (a1, a2)} pair.
     * Used to detect that the pair is NOT adjacent (a third block lies
     * between them), so its gap must not be penalized directly.
     */
    public static boolean liesBetween(CourseBlockAssignment a1, CourseBlockAssignment a2,
            CourseBlockAssignment mid) {
        int start1 = a1.getTimeslot().getStartHour();
        int end1 = start1 + a1.getTimeslot().getLengthHours();
        int start2 = a2.getTimeslot().getStartHour();
        int end2 = start2 + a2.getTimeslot().getLengthHours();
        int spanStart, spanEnd;
        if (end1 <= start2) {
            spanStart = end1;
            spanEnd = start2;
        } else if (end2 <= start1) {
            spanStart = end2;
            spanEnd = start1;
        } else {
            return false;
        }
        int midStart = mid.getTimeslot().getStartHour();
        return midStart >= spanStart && midStart < spanEnd;
    }

    /** The earliest start hour among these blocks. */
    public static int earliestStartHour(List<CourseBlockAssignment> blocks) {
        int earliest = Integer.MAX_VALUE;
        for (CourseBlockAssignment a : blocks) {
            earliest = Math.min(earliest, a.getTimeslot().getStartHour());
        }
        return earliest;
    }

    /**
     * How many of these blocks' start hours differ from the most common
     * ("mode") start hour in the set - 0 when they all agree, or already a
     * singleton. Counting the deviation from the mode (not a flat 1-point
     * penalty for "not perfectly consistent") gives local search a smooth
     * gradient: moving one outlying block back toward the group's dominant
     * hour measurably improves the score, rather than being all-or-nothing.
     */
    public static int blocksNotAtModeHour(List<CourseBlockAssignment> blocks) {
        Map<Integer, Integer> hourCounts = new HashMap<>();
        for (CourseBlockAssignment a : blocks) {
            hourCounts.merge(a.getTimeslot().getStartHour(), 1, Integer::sum);
        }
        return blocks.size() - Collections.max(hourCounts.values());
    }

    /**
     * True when this block's course has a HARD-severity semester_hour_limit
     * configured (see Course.getLatestEndHourSeverity()) and its timeslot
     * ends after that limit. In practice this can only ever fire for a
     * pinned row whose timeslot predates the limit (or was pinned before its
     * course's semester was configured with one): a non-pinned block's
     * timeslot is already structurally guaranteed correct by
     * CourseBlockAssignment.getMatchingBlockTimeslots(), which excludes any
     * such timeslot from a HARD-limited course's entity-scoped value range
     * entirely - same "structural guarantee + pinned-row backstop" shape as
     * blockLengthMustMatchTimeslotLength. A SOFT-severity limit never
     * reaches this check - see softSemesterHourLimitExcess() below instead.
     */
    public static boolean violatesHardSemesterHourLimit(CourseBlockAssignment a) {
        Course course = a.getCourse();
        if (course == null || a.getTimeslot() == null || !"HARD".equals(course.getLatestEndHourSeverity())) {
            return false;
        }
        Integer limit = course.getLatestEndHour();
        return limit != null
                && a.getTimeslot().getStartHour() + a.getTimeslot().getLengthHours() > limit;
    }

    /**
     * How many hours past a SOFT-severity semester_hour_limit this block's
     * timeslot ends (0 if its course has no limit, the limit isn't SOFT, or
     * it doesn't exceed it). Unlike the HARD case, a SOFT-limited course's
     * blocks are NOT excluded from the value range - the solver is free to
     * place them past the limit, just penalized proportionally to the
     * overrun when it does (a deviation gradient, like
     * preferSemesterOneBlocksStartEarly's, not a flat penalty).
     */
    public static int softSemesterHourLimitExcess(CourseBlockAssignment a) {
        Course course = a.getCourse();
        if (course == null || a.getTimeslot() == null || !"SOFT".equals(course.getLatestEndHourSeverity())) {
            return 0;
        }
        Integer limit = course.getLatestEndHour();
        if (limit == null) {
            return 0;
        }
        int endHour = a.getTimeslot().getStartHour() + a.getTimeslot().getLengthHours();
        return Math.max(0, endHour - limit);
    }
}
