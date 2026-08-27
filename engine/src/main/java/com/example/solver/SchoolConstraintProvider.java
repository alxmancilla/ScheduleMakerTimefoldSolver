package com.example.solver;

import ai.timefold.solver.core.api.score.stream.Constraint;
import ai.timefold.solver.core.api.score.stream.ConstraintFactory;
import ai.timefold.solver.core.api.score.stream.ConstraintProvider;
import ai.timefold.solver.core.api.score.stream.Joiners;
import ai.timefold.solver.core.api.score.buildin.hardsoft.HardSoftScore;
import ai.timefold.solver.core.api.score.stream.ConstraintCollectors;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;

import com.example.domain.CourseBlockAssignment;
import com.example.domain.BlockTimeslot;
import com.example.domain.Room;
import com.example.domain.Teacher;

/**
 * Every constraint here uses forEachIncludingUnassigned/ifNotExistsIncludingUnassigned
 * (never the plain forEach/forEachUniquePair/ifNotExists) for CourseBlockAssignment.
 * room is @PlanningVariable(allowsUnassigned = true) (some entities' room is
 * legitimately still undecided - see CourseBlockAssignment.getMatchingRooms()),
 * and Timefold's plain forEach()-family methods silently exclude any entity
 * with a null value for ANY genuine planning variable, not just the ones a
 * given constraint actually reads. Confirmed empirically: with plain forEach,
 * a roomless entity vanished even from constraints that never reference room
 * at all (teacherMustBeQualified, groupCannotHaveTwoCoursesAtSameTime,
 * groupMustHaveBreakAfterConsecutiveHours), silently under-counting real
 * violations. The *IncludingUnassigned variants restore normal visibility;
 * forEachUniquePair's "unique pair, no self-pairs" behavior is replicated
 * manually via forEachIncludingUnassigned(...).join(forEachIncludingUnassigned(...),
 * Joiners.lessThan(getId), ...the constraint's own joiners...).
 */
public class SchoolConstraintProvider implements ConstraintProvider {

    @Override
    public Constraint[] defineConstraints(ConstraintFactory constraintFactory) {
        return new Constraint[] {
                // ========== Cheap HARD Constraints (forEach, single-entity) ==========
                // Grouped here for readability (cheapest checks, most likely to be
                // violated in a fresh/unsolved schedule). This is NOT a short-circuit
                // order: Constraint Streams maintains every constraint as its own
                // continuously-updated incremental network (closer to Drools/RETE
                // forward-chaining) and recalculates only the tuples a changed
                // variable actually affects, across ALL constraints - reordering this
                // array doesn't skip or defer evaluation of the ones after it.
                blockLengthMustMatchTimeslotLength(constraintFactory), // #0: CRITICAL - Block length must match
                                                                       // timeslot
                teacherMustBeAvailable(constraintFactory), // #1: Most likely to fail (~30% rejection rate)
                teacherMustBeQualified(constraintFactory), // #2: Second most likely (~20% rejection rate)
                roomTypeMustSatisfyRequirement(constraintFactory), // #3: Cheap, medium selectivity (~10% rejection)
                teacherRequiredRoomMustBeUsed(constraintFactory), // #3b: Pinned blocks only - see method doc

                // ========== High-Selectivity HARD Pair Constraints ==========
                // Optimized with Joiners (very few pairs evaluated) - the pruning here
                // is real (see Joiners.equal below), unlike the array-order comment above.
                groupCannotHaveTwoCoursesAtSameTime(constraintFactory), // #4: ~70 pairs, very high selectivity
                noTeacherDoubleBooking(constraintFactory), // #5: ~25 pairs, very high selectivity
                noRoomDoubleBooking(constraintFactory), // #6: ~45 pairs, very high selectivity

                // ========== Whole-Day Aggregate HARD Constraints ==========
                maxTwoBlocksPerCoursePerGroupPerDay(constraintFactory), // HARD: Max blocks per course per group per
                                                                        // day (per-component via component_block_rule)
                courseBlocksMustBeConsecutive(constraintFactory), // HARD: ALL course blocks MUST be consecutive
                // TEMP DISABLED 2026-08-24 (per request) - re-enable by uncommenting.
                // Also re-enable its BlockScheduleAnalyzer mirror and bump
                // ConstraintConsistencyTest's expected counts back to 12 hard / 20 total.
                // teacherMustHaveBreakAfterConsecutiveHours(constraintFactory), // HARD: break after N straight hours
                // TEMP DISABLED 2026-08-24 (per request - groups are limited, don't need a
                // break) - re-enable by uncommenting, along with its BlockScheduleAnalyzer
                // mirror and ConstraintConsistencyTest's expected counts.
                // groupMustHaveBreakAfterConsecutiveHours(constraintFactory), // HARD: break after N straight hours

                // ========== SOFT Constraints - Quality Optimization ==========
                // Don't affect feasibility; ordered by weight (highest first) for
                // readability only.
                nonStandardRoomsShouldFinishBy2pm(constraintFactory), // SOFT (weight 10): Prefer non-standard rooms to
                                                                      // finish by 2pm
                preferSemesterOneBlocksStartEarly(constraintFactory), // SOFT (weight 6): first-semester groups start
                                                                       // their day as early as possible
                minimizeSemesterOneGroupIdleGaps(constraintFactory), // SOFT (weight 6): first-semester groups run
                                                                      // gap-free
                teacherMaxHoursPerWeek(constraintFactory), // SOFT (weight 5): workload balance
                roomCapacityShouldFitGroupSize(constraintFactory), // SOFT (weight 4): group shouldn't exceed room capacity
                // TEMP DISABLED 2026-08-24 (per request - replaced for first-semester groups
                // by minimizeSemesterOneGroupIdleGaps above; other groups' idle gaps are no
                // longer minimized at all) - re-enable by uncommenting, along with its
                // BlockScheduleAnalyzer mirror, GroupIdleGapAnalyzerTest's assertions, and
                // ConstraintConsistencyTest's expected soft constraints/counts.
                // minimizeGroupIdleGaps(constraintFactory), // SOFT (weight 3): student schedule quality
                preferBlockSpecifiedRoom(constraintFactory), // SOFT (weight 3): prefer block's specified room (CC
                                                             // distribution)
                minimizeTeacherIdleGaps(constraintFactory), // SOFT (weight 2): teacher satisfaction
                groupPreferredRoomConstraint(constraintFactory), // SOFT (weight 2): prefer group's pre-assigned room
                // TEMP DISABLED 2026-08-26 (per request) - re-enable by uncommenting, along
                // with its BlockScheduleAnalyzer mirror and ConstraintConsistencyTest's
                // expected soft constraints/counts.
                // preferCoreOneHourBlocksAtSameTimeAcrossDays(constraintFactory), // SOFT
                // (weight 2): predictable "Math is always at 8am" schedule
                // TEMP DISABLED 2026-08-24 (per request - not required anymore) - re-enable
                // by uncommenting, along with its BlockScheduleAnalyzer mirror and
                // ConstraintConsistencyTest's expected soft constraints/counts.
                // minimizeTeacherBuildingChanges(constraintFactory), // SOFT (weight 1): minimize teacher travel

                // ========== COMMENTED OUT CONSTRAINTS ==========
                // Uncomment these if needed:
                // mustFinishBy2pm(constraintFactory), // SOFT: Prefer non-Core courses in
                // non-standard rooms to finish by 2pm
                // limitNonBasicasCoursesToTwoDaysPerGroup(constraintFactory), // SOFT:
                // concentrate non-Core
                // groupCoursesInSameRoomByType(constraintFactory), // HARD: same room type
                // consistency
                // balanceTeacherWorkload(constraintFactory), // SOFT: balance workload
                // distribution
                // preferUsingTeachersWithMoreAvailability(constraintFactory), // SOFT: prefer
                // high-availability teachers
                // encourageAlternativeQualifiedTeachers(constraintFactory), // SOFT: distribute
                // among qualified teachers
        };
    }

    // ==================== HARD CONSTRAINTS ====================

    /** A block's day of week, or null if it has no timeslot yet - shared join key for every day-scoped pair constraint below. */
    private static DayOfWeek dayOfWeekOrNull(CourseBlockAssignment a) {
        return a.getTimeslot() != null ? a.getTimeslot().getDayOfWeek() : null;
    }

    /**
     * Helper method to check if two block timeslots overlap.
     * Blocks overlap if they are on the same day and their time ranges intersect.
     *
     * @param block1 first block timeslot
     * @param block2 second block timeslot
     * @return true if blocks overlap, false otherwise
     */
    private boolean blocksOverlap(BlockTimeslot block1, BlockTimeslot block2) {
        if (block1 == null || block2 == null) {
            return false;
        }

        // Different days = no overlap
        if (!block1.getDayOfWeek().equals(block2.getDayOfWeek())) {
            return false;
        }

        // Same day: check if time ranges overlap
        int start1 = block1.getStartHour();
        int end1 = block1.getStartHour() + block1.getLengthHours();
        int start2 = block2.getStartHour();
        int end2 = block2.getStartHour() + block2.getLengthHours();

        // Blocks overlap if one starts before the other ends
        return start1 < end2 && start2 < end1;
    }

    /**
     * CRITICAL CONSTRAINT: Block length must match timeslot length.
     * A 1-hour block can only be assigned to a 1-hour timeslot.
     * A 2-hour block can only be assigned to a 2-hour timeslot, etc.
     *
     * This prevents data corruption where a 1-hour block is assigned to a 3-hour
     * timeslot,
     * which causes overlapping assignments and double-booking violations.
     */
    private Constraint blockLengthMustMatchTimeslotLength(ConstraintFactory constraintFactory) {
        return constraintFactory
                .forEachIncludingUnassigned(CourseBlockAssignment.class)
                .filter(assignment -> {
                    if (assignment.getTimeslot() == null) {
                        return false; // Unassigned timeslots are handled elsewhere
                    }
                    // Penalize if block length doesn't match timeslot length
                    // NOTE: This constraint is NOT excluded for pinned assignments because it's a
                    // data integrity constraint, not a business rule. If a pinned assignment
                    // violates this, it indicates a database error that must be fixed.
                    return assignment.getBlockLength() != assignment.getTimeslot().getLengthHours();
                })
                .penalize(HardSoftScore.ONE_HARD)
                .asConstraint("Block length must match timeslot length");
    }

    private Constraint teacherMustBeQualified(ConstraintFactory constraintFactory) {
        return constraintFactory
                .forEachIncludingUnassigned(CourseBlockAssignment.class)
                .filter(assignment -> !assignment.isPinned() // Exclude pinned assignments
                        && assignment.getTeacher() != null
                        && !assignment.getTeacher().isQualifiedFor(assignment.getCourse().getName()))
                .penalize(HardSoftScore.ONE_HARD)
                .asConstraint("Teacher must be qualified");
    }

    private Constraint teacherMustBeAvailable(ConstraintFactory constraintFactory) {
        // UPDATED: Check teacher availability for entire block duration
        return constraintFactory
                .forEachIncludingUnassigned(CourseBlockAssignment.class)
                .filter(assignment -> !assignment.isPinned()) // Exclude pinned assignments
                .filter(assignment -> assignment.getTeacher() != null && assignment.getTimeslot() != null
                        && !assignment.getTeacher().isAvailableForBlock(assignment.getTimeslot()))
                .penalize(HardSoftScore.ONE_HARD)
                .asConstraint("Teacher must be available for entire block");
    }

    private Constraint noTeacherDoubleBooking(ConstraintFactory constraintFactory) {
        // UPDATED: Uses block overlap detection instead of exact timeslot matching
        // OPTIMIZED: Uses Joiners to pre-filter pairs by teacher and day
        return constraintFactory
                .forEachIncludingUnassigned(CourseBlockAssignment.class)
                .join(constraintFactory.forEachIncludingUnassigned(CourseBlockAssignment.class),
                        Joiners.lessThan(CourseBlockAssignment::getId),
                        Joiners.equal(CourseBlockAssignment::getTeacher),
                        Joiners.equal(SchoolConstraintProvider::dayOfWeekOrNull))
                .filter((a1, a2) -> (!a1.isPinned() || !a2.isPinned()) // Penalize if at least one is unpinned
                        && a1.getTeacher() != null // Guard: Joiners.equal joins null==null; two unassigned
                                                   // blocks are not a double-booking (a2 is non-null via the join)
                        && a1.getTimeslot() != null
                        && a2.getTimeslot() != null
                        && blocksOverlap(a1.getTimeslot(), a2.getTimeslot()))
                .penalize(HardSoftScore.ONE_HARD)
                .asConstraint("No teacher double-booking");
    }

    private Constraint noRoomDoubleBooking(ConstraintFactory constraintFactory) {
        // UPDATED: Uses block overlap detection instead of exact timeslot matching
        // OPTIMIZED: Uses Joiners to pre-filter pairs by room and day
        return constraintFactory
                .forEachIncludingUnassigned(CourseBlockAssignment.class)
                .join(constraintFactory.forEachIncludingUnassigned(CourseBlockAssignment.class),
                        Joiners.lessThan(CourseBlockAssignment::getId),
                        Joiners.equal(CourseBlockAssignment::getRoom),
                        Joiners.equal(SchoolConstraintProvider::dayOfWeekOrNull))
                .filter((a1, a2) -> (!a1.isPinned() || !a2.isPinned()) // Penalize if at least one is unpinned
                        && a1.getRoom() != null // Guard: Joiners.equal joins null==null; two unassigned
                                                // blocks are not a double-booking (a2 is non-null via the join)
                        && a1.getTimeslot() != null
                        && a2.getTimeslot() != null
                        && blocksOverlap(a1.getTimeslot(), a2.getTimeslot()))
                .penalize(HardSoftScore.ONE_HARD)
                .asConstraint("No room double-booking");
    }

    private Constraint roomTypeMustSatisfyRequirement(ConstraintFactory constraintFactory) {
        // HARD: Room type must match the block's satisfiesRoomType field
        // This supports dual room requirements where different blocks of the same
        // course
        // can require different room types (e.g., 4h in Specialized - Computer Lab + 1h in Standard)
        return constraintFactory
                .forEachIncludingUnassigned(CourseBlockAssignment.class)
                .filter(assignment -> {
                    // Exclude pinned assignments (they are fixed from database)
                    if (assignment.isPinned()) {
                        return false;
                    }
                    // Skip if no room assigned or no room type requirement
                    if (assignment.getRoom() == null || assignment.getSatisfiesRoomType() == null) {
                        return false;
                    }
                    // Check if assigned room satisfies the block's required room type
                    // Uses satisfiesRoomType field (from course_block_assignment table)
                    // instead of course.room_requirement (old single-requirement system)
                    return !assignment.getRoom().satisfiesRequirement(assignment.getSatisfiesRoomType());
                })
                .penalize(HardSoftScore.ONE_HARD)
                .asConstraint("Room type must satisfy course requirement");
    }

    /**
     * HARD, data-integrity constraint (like blockLengthMustMatchTimeslotLength):
     * NOT excluded for pinned assignments, because pinned rows are exactly the
     * case this exists to catch. A non-pinned block's room is already
     * structurally guaranteed correct by CourseBlockAssignment.getMatchingRooms()
     * (its room value range collapses to the teacher's required room whenever
     * one applies), so this can only ever fire for a pinned row in practice.
     * TeacherController.backfillRequiredRoom() explicitly skips pinned blocks
     * when a teacher's required room changes, so a pinned row can silently
     * drift out of sync with its teacher's current requirement - with nothing
     * else to report that.
     *
     * Uses isTeacherRequiredRoomApplicable() rather than a blind name
     * comparison: a teacher's required room only governs blocks whose
     * satisfiesRoomType it actually satisfies (see CourseBlockAssignment's
     * compatibility fallback) - a multi-subject teacher's other blocks
     * correctly land in the group's room instead, and shouldn't be flagged
     * as if they'd disobeyed a requirement that was never applicable to them.
     */
    private Constraint teacherRequiredRoomMustBeUsed(ConstraintFactory constraintFactory) {
        return constraintFactory
                .forEachIncludingUnassigned(CourseBlockAssignment.class)
                .filter(a -> a.isTeacherRequiredRoomApplicable()
                        && a.getRoom() != null
                        && !a.getTeacher().getRequiredRoomName().equals(a.getRoom().getName()))
                .penalize(HardSoftScore.ONE_HARD)
                .asConstraint("Teacher's required room must be used");
    }

    private Constraint groupCannotHaveTwoCoursesAtSameTime(ConstraintFactory constraintFactory) {
        // UPDATED: Uses block overlap detection instead of exact timeslot matching
        // OPTIMIZED: Uses Joiners to pre-filter pairs by group and day
        return constraintFactory
                .forEachIncludingUnassigned(CourseBlockAssignment.class)
                .join(constraintFactory.forEachIncludingUnassigned(CourseBlockAssignment.class),
                        Joiners.lessThan(CourseBlockAssignment::getId),
                        Joiners.equal(CourseBlockAssignment::getGroup),
                        Joiners.equal(SchoolConstraintProvider::dayOfWeekOrNull))
                .filter((a1, a2) -> (!a1.isPinned() || !a2.isPinned()) // Penalize if at least one is unpinned
                        && a1.getTimeslot() != null
                        && a2.getTimeslot() != null
                        && blocksOverlap(a1.getTimeslot(), a2.getTimeslot()))
                .penalize(HardSoftScore.ONE_HARD)
                .asConstraint("Group cannot have two courses at same time");
    }

    private Constraint nonStandardRoomsShouldFinishBy2pm(ConstraintFactory constraintFactory) {
        // SOFT (weight 10): Non-standard rooms SHOULD finish by 2pm (14:00)
        //
        // This soft constraint strongly encourages courses using non-standard rooms
        // (computer centers, workshops, labs) to finish by 2pm to allow for:
        // - Equipment maintenance and cleanup
        // - Facility preparation for next day
        // - Reduced operational costs (utilities, supervision)
        //
        // Non-standard room types:
        // - Specialized - Computer Lab (Computer Centers: CC 1, CC 2, CC 3)
        // - Specialized - Workshop (General Workshop: AULA 4)
        // - Mixed (LQ1, LMICRO, TEM1-3, TE1, TPIAL - also satisfy Standard/Specialized - Workshop)
        //
        // Standard rooms can run until 3pm (15:00) for flexibility.
        //
        // Exemptions:
        // - Pinned assignments are exempt (they are fixed from the database)
        //
        // Block-based logic:
        // - Uses BlockTimeslot.startHour + BlockTimeslot.lengthHours for end time
        // - Works with multi-hour blocks (1-4 hours)
        // - Example: Block starting at 13:00 with length 2 hours ends at 15:00
        // (VIOLATION)
        //
        // WEIGHT: 10 (Very high priority - strong preference but not absolute
        // requirement)
        return constraintFactory
                .forEachIncludingUnassigned(CourseBlockAssignment.class)
                .filter(assignment -> {
                    // Skip if no timeslot or room assigned
                    if (assignment.getTimeslot() == null || assignment.getRoom() == null)
                        return false;

                    // Skip pinned assignments (fixed from database)
                    if (assignment.isPinned())
                        return false;

                    // Calculate block end hour (BLOCK-BASED APPROACH)
                    int endHour = assignment.getTimeslot().getStartHour()
                            + assignment.getTimeslot().getLengthHours();

                    // Check if ends after 2pm (14:00)
                    // "Finish by 2pm" means the last hour should be 13:00 (1pm-2pm)
                    // So endHour > 14 is a violation (e.g., 15:00 is after 2pm)
                    if (endHour <= 14)
                        return false; // Ends by 2pm, no violation

                    // Check if room is non-standard
                    String roomType = assignment.getRoom().getType();
                    boolean isNonStandard = (roomType != null
                            && !roomType.equalsIgnoreCase("Standard"));

                    return isNonStandard; // Penalize if non-standard room after 2pm
                })
                .penalize(HardSoftScore.ofSoft(10)) // SOFT constraint (weight 10) - strong preference
                .asConstraint("Non-standard rooms should finish by 2pm");
    }

    // A component with no row in component_block_rule falls back to this,
    // matching the old default cap for non-Core courses.
    private static final int DEFAULT_MAX_BLOCKS_PER_DAY = 2;

    private Constraint maxTwoBlocksPerCoursePerGroupPerDay(ConstraintFactory constraintFactory) {
        // HARD: No more than course.getMaxBlocksPerDay() blocks of the same
        // course/group may land on the same day. This prevents excessive
        // concentration of the same course on one day. The limit is configurable
        // per course component via component_block_rule (Settings > Block Rules);
        // a component with no configured rule falls back to DEFAULT_MAX_BLOCKS_PER_DAY.
        return constraintFactory
                .forEachIncludingUnassigned(CourseBlockAssignment.class)
                .filter(a -> !a.isPinned() && a.getTimeslot() != null)
                .groupBy(
                        CourseBlockAssignment::getGroup,
                        CourseBlockAssignment::getCourse,
                        a -> a.getTimeslot().getDayOfWeek(),
                        ConstraintCollectors.toList())
                .filter((group, course, day, assignments) -> {
                    int limit = course.getMaxBlocksPerDay() != null
                            ? course.getMaxBlocksPerDay()
                            : DEFAULT_MAX_BLOCKS_PER_DAY;
                    return assignments.size() > limit;
                })
                .penalize(HardSoftScore.ONE_HARD,
                        (group, course, day, assignments) -> {
                            int limit = course.getMaxBlocksPerDay() != null
                                    ? course.getMaxBlocksPerDay()
                                    : DEFAULT_MAX_BLOCKS_PER_DAY;
                            return assignments.size() - limit;
                        })
                .asConstraint("Maximum blocks per course per group per day");
    }

    private Constraint courseBlocksMustBeConsecutive(ConstraintFactory constraintFactory) {
        // HARD: When ANY course has multiple blocks on the same day for the same group,
        // they MUST form a single contiguous chain (no gaps between blocks).
        // This is critical for maintaining course continuity and student focus.
        //
        // The blocks for a (group, course, day) are grouped together and sorted by
        // start hour; the number of breaks in the chain (gaps or overlaps between
        // adjacent blocks) is the penalty. Grouping avoids the false positives of a
        // pairwise check, where a valid 7-8 / 8-9 / 9-10 sequence would still yield a
        // non-consecutive pair between 7-8 and 9-10.
        return constraintFactory
                .forEachIncludingUnassigned(CourseBlockAssignment.class)
                .filter(a -> !a.isPinned() && a.getTimeslot() != null)
                .groupBy(
                        CourseBlockAssignment::getGroup,
                        CourseBlockAssignment::getCourse,
                        a -> a.getTimeslot().getDayOfWeek(),
                        ConstraintCollectors.toList())
                .filter((group, course, day, blocks) -> blocks.size() > 1)
                .penalize(HardSoftScore.ONE_HARD,
                        (group, course, day, blocks) -> countChainBreaks(blocks))
                .asConstraint("Course blocks must be consecutive");
    }

    /**
     * Count the number of breaks (gaps or overlaps between adjacent blocks) in a
     * set
     * of blocks once sorted by start hour. Zero means the blocks form a single
     * contiguous chain; each break contributes one hard penalty.
     */
    private static int countChainBreaks(java.util.List<CourseBlockAssignment> blocks) {
        java.util.List<CourseBlockAssignment> sorted = new java.util.ArrayList<>(blocks);
        sorted.sort(java.util.Comparator.comparingInt(a -> a.getTimeslot().getStartHour()));
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

    // Half the 8h (7:00-15:00) school day - a teacher/group scheduled this many
    // consecutive hours with zero idle time between blocks must get a break
    // before continuing. Pinned blocks are excluded from the run (same
    // convention as nonStandardRoomsShouldFinishBy2pm/groupPreferredRoomConstraint)
    // so legacy pinned data can't block solver convergence; only movable blocks
    // are enforced.
    private static final int MAX_CONSECUTIVE_HOURS_WITHOUT_BREAK = 4;

    private Constraint teacherMustHaveBreakAfterConsecutiveHours(ConstraintFactory constraintFactory) {
        // HARD: A teacher scheduled MAX_CONSECUTIVE_HOURS_WITHOUT_BREAK straight
        // hours (blocks with zero idle time between them, same day) must get a
        // break before continuing - minimizeTeacherIdleGaps only minimizes gaps
        // toward zero, it never required one to exist in the first place.
        return constraintFactory
                .forEachIncludingUnassigned(CourseBlockAssignment.class)
                .filter(a -> !a.isPinned() && a.getTeacher() != null && a.getTimeslot() != null)
                .groupBy(
                        CourseBlockAssignment::getTeacher,
                        a -> a.getTimeslot().getDayOfWeek(),
                        ConstraintCollectors.toList())
                .filter((teacher, day, blocks) -> longestConsecutiveRunHours(blocks) > MAX_CONSECUTIVE_HOURS_WITHOUT_BREAK)
                .penalize(HardSoftScore.ONE_HARD,
                        (teacher, day, blocks) -> longestConsecutiveRunHours(blocks) - MAX_CONSECUTIVE_HOURS_WITHOUT_BREAK)
                .asConstraint("Teacher must have a break after consecutive hours");
    }

    private Constraint groupMustHaveBreakAfterConsecutiveHours(ConstraintFactory constraintFactory) {
        // HARD: Same rule as teacherMustHaveBreakAfterConsecutiveHours, for
        // student groups instead of teachers.
        return constraintFactory
                .forEachIncludingUnassigned(CourseBlockAssignment.class)
                .filter(a -> !a.isPinned() && a.getGroup() != null && a.getTimeslot() != null)
                .groupBy(
                        CourseBlockAssignment::getGroup,
                        a -> a.getTimeslot().getDayOfWeek(),
                        ConstraintCollectors.toList())
                .filter((group, day, blocks) -> longestConsecutiveRunHours(blocks) > MAX_CONSECUTIVE_HOURS_WITHOUT_BREAK)
                .penalize(HardSoftScore.ONE_HARD,
                        (group, day, blocks) -> longestConsecutiveRunHours(blocks) - MAX_CONSECUTIVE_HOURS_WITHOUT_BREAK)
                .asConstraint("Group must have a break after consecutive hours");
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
     * result depend on which of two equal-start-hour blocks the (otherwise
     * unstable-for-ties) sort visited first, silently violating Timefold's
     * requirement that a constraint be a pure, order-independent function of
     * the group's contents. The explicit id tie-break plus interval-merge
     * fixes both the correctness and the determinism.
     */
    static int longestConsecutiveRunHours(java.util.List<CourseBlockAssignment> blocks) {
        java.util.List<CourseBlockAssignment> sorted = new java.util.ArrayList<>(blocks);
        sorted.sort(java.util.Comparator
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

    // ==================== DEPRECATED HOUR-BASED CONSTRAINTS ====================
    // The following constraints are for hour-based scheduling and are no longer
    // used.
    // They are kept here for reference but should not be called.

    @Deprecated
    private Constraint sameTeacherForAllCourseHours(ConstraintFactory constraintFactory) {
        throw new UnsupportedOperationException("Hour-based scheduling is no longer supported");
    }

    @Deprecated
    private Constraint groupCourseMustBeConsecutiveOnSameDay(ConstraintFactory constraintFactory) {
        throw new UnsupportedOperationException("Hour-based scheduling is no longer supported");
    }

    @Deprecated
    private Constraint groupCoursesInSameRoomByType(ConstraintFactory constraintFactory) {
        throw new UnsupportedOperationException("Hour-based scheduling is no longer supported");
    }

    @Deprecated
    private boolean overlapsForbiddenWindow(LocalTime start, LocalTime end) {
        throw new UnsupportedOperationException("Hour-based scheduling is no longer supported");
    }

    @Deprecated
    private Constraint preferUsingTeachersWithMoreAvailability(ConstraintFactory factory) {
        throw new UnsupportedOperationException("Hour-based scheduling is no longer supported");
    }

    @Deprecated
    private int scarcityPenalty(Teacher teacher) {
        throw new UnsupportedOperationException("Hour-based scheduling is no longer supported");
    }

    private Constraint groupPreferredRoomConstraint(ConstraintFactory constraintFactory) {
        // SOFT: Prefer assigning a group's blocks to one of its curated
        // acceptable rooms for that block's own satisfiesRoomType (see
        // Group.getAcceptableRooms()/CourseBlockAssignment.getMatchingRooms()).
        // Being keyed by room type means this naturally applies (or doesn't)
        // per type - a group with no curated range for a given type is
        // skipped entirely below, so a Mixed-required block is only
        // penalized if the group has actually curated a Mixed range for it,
        // never against a range curated for a different type.
        // WEIGHT: 2 (Medium priority - operational efficiency)
        return constraintFactory
                .forEachIncludingUnassigned(CourseBlockAssignment.class)
                .filter(assignment -> {
                    // Exclude pinned assignments
                    if (assignment.isPinned()) {
                        return false;
                    }
                    if (assignment.getGroup() == null || assignment.getRoom() == null) {
                        return false;
                    }
                    List<Room> acceptableRooms = assignment.getGroup().getAcceptableRooms(assignment.getSatisfiesRoomType());
                    if (acceptableRooms == null) {
                        return false;
                    }
                    // Penalize if NOT using one of the acceptable rooms
                    return !acceptableRooms.contains(assignment.getRoom());
                })
                .penalize(HardSoftScore.ofSoft(2))
                .asConstraint("Prefer group's preferred room");
    }

    /**
     * SOFT: For a Core course's 1-hour blocks (one per day it meets, for the
     * same group), prefer they all land on the same start hour - a predictable
     * "Math is always at 8am" schedule. Doesn't apply to multi-hour blocks
     * (different lengths can't trivially "share an hour" the same way).
     * WEIGHT: 2 (same tier as the other schedule-predictability preferences).
     */
    private Constraint preferCoreOneHourBlocksAtSameTimeAcrossDays(ConstraintFactory constraintFactory) {
        return constraintFactory
                .forEachIncludingUnassigned(CourseBlockAssignment.class)
                .filter(a -> !a.isPinned() && a.getBlockLength() == 1
                        && a.getGroup() != null && a.getCourse() != null && a.getTimeslot() != null
                        && "Core".equals(a.getCourse().getDesignation()))
                .groupBy(CourseBlockAssignment::getGroup, CourseBlockAssignment::getCourse,
                        ConstraintCollectors.toList())
                .filter((group, course, blocks) -> blocksNotAtModeHour(blocks) > 0)
                .penalize(HardSoftScore.ofSoft(2), (group, course, blocks) -> blocksNotAtModeHour(blocks))
                .asConstraint("Prefer Core 1h blocks at the same time across days");
    }

    /**
     * How many of these blocks' start hours differ from the most common
     * ("mode") start hour in the set - 0 when they all agree, or already a
     * singleton. Counting the deviation from the mode (not a flat 1-point
     * penalty for "not perfectly consistent") gives local search a smooth
     * gradient: moving one outlying block back toward the group's dominant
     * hour measurably improves the score, rather than being all-or-nothing.
     */
    private static int blocksNotAtModeHour(java.util.List<CourseBlockAssignment> blocks) {
        java.util.Map<Integer, Integer> hourCounts = new java.util.HashMap<>();
        for (CourseBlockAssignment a : blocks) {
            hourCounts.merge(a.getTimeslot().getStartHour(), 1, Integer::sum);
        }
        return blocks.size() - java.util.Collections.max(hourCounts.values());
    }

    private Constraint roomCapacityShouldFitGroupSize(ConstraintFactory constraintFactory) {
        // SOFT: Warn when a group's headcount exceeds its assigned room's capacity.
        // Only applies when BOTH room.capacity and group.studentCount are known;
        // either being unset means "not tracked", so the check is skipped rather
        // than assumed to pass or fail.
        // WEIGHT: 4 (meaningful operational concern, but must never block
        // feasibility since this data may be incomplete for existing rooms/groups)
        return constraintFactory
                .forEachIncludingUnassigned(CourseBlockAssignment.class)
                .filter(assignment -> {
                    if (assignment.getRoom() == null || assignment.getGroup() == null) {
                        return false;
                    }
                    Integer capacity = assignment.getRoom().getCapacity();
                    Integer studentCount = assignment.getGroup().getStudentCount();
                    if (capacity == null || studentCount == null) {
                        return false;
                    }
                    return studentCount > capacity;
                })
                .penalize(HardSoftScore.ofSoft(4))
                .asConstraint("Room capacity should fit group size");
    }

    private Constraint minimizeTeacherBuildingChanges(ConstraintFactory constraintFactory) {
        // SOFT: Minimize teacher building changes on same day
        // Reduces teacher travel time and improves satisfaction
        // WEIGHT: 1 (Low priority - nice to have)
        return constraintFactory
                .forEachIncludingUnassigned(CourseBlockAssignment.class)
                .join(constraintFactory.forEachIncludingUnassigned(CourseBlockAssignment.class),
                        Joiners.lessThan(CourseBlockAssignment::getId),
                        Joiners.equal(CourseBlockAssignment::getTeacher),
                        Joiners.equal(SchoolConstraintProvider::dayOfWeekOrNull))
                .filter((a1, a2) -> {
                    // Exclude pinned assignments
                    if (a1.isPinned() || a2.isPinned()) {
                        return false;
                    }
                    if (a1.getRoom() == null || a2.getRoom() == null) {
                        return false;
                    }
                    // Penalize if different buildings
                    String building1 = a1.getRoom().getBuilding();
                    String building2 = a2.getRoom().getBuilding();
                    return building1 != null && building2 != null && !building1.equals(building2);
                })
                .penalize(HardSoftScore.ofSoft(1))
                .asConstraint("Minimize teacher building changes");
    }

    @Deprecated
    private Constraint balanceTeacherWorkload(ConstraintFactory constraintFactory) {
        throw new UnsupportedOperationException("Hour-based scheduling is no longer supported");
    }

    @Deprecated
    private Constraint limitNonBasicasCoursesToTwoDaysPerGroup(ConstraintFactory constraintFactory) {
        throw new UnsupportedOperationException("Hour-based scheduling is no longer supported");
    }

    @Deprecated
    private Constraint encourageAlternativeQualifiedTeachers(ConstraintFactory constraintFactory) {
        throw new UnsupportedOperationException("Hour-based scheduling is no longer supported");
    }

    // ==================== BLOCK-BASED CONSTRAINTS ====================

    private Constraint teacherMaxHoursPerWeek(ConstraintFactory constraintFactory) {
        // UPDATED: Sum blockLength instead of counting assignments
        // Each CourseBlockAssignment has a blockLength (number of hours)
        // IMPORTANT: Includes BOTH pinned and unpinned assignments because pinned
        // assignments
        // represent real teaching hours that count toward the teacher's workload limit.
        // WEIGHT: 5 (Highest priority - legal/union requirement)
        return constraintFactory
                .forEachIncludingUnassigned(CourseBlockAssignment.class)
                .filter(a -> a.getTeacher() != null && a.getTimeslot() != null) // Include ALL assignments
                .groupBy(CourseBlockAssignment::getTeacher,
                        ConstraintCollectors.sum(CourseBlockAssignment::getBlockLength))
                .filter((teacher, totalHours) -> totalHours > teacher.getMaxHoursPerWeek())
                .penalize(HardSoftScore.ONE_SOFT,
                        (teacher, totalHours) -> (totalHours - teacher.getMaxHoursPerWeek()) * 5)
                .asConstraint("Teacher exceeds max hours per week");
    }

    private Constraint minimizeTeacherIdleGaps(ConstraintFactory constraintFactory) {
        // SOFT: Minimizes teacher idle gaps between blocks on the same day.
        // Only ADJACENT block pairs are penalized: a pair (a1, a2) with a1 ending
        // before a2 starts is counted only when no third block of the same teacher
        // sits (fully or partly) between them (ifNotExists). This avoids the pairwise
        // over-penalization where a non-adjacent pair (blocks 1 and 3) would re-count
        // idle hours already accounted for by the adjacent pairs (1-2 and 2-3).
        // Availability-aware: an idle hour is only penalized if the teacher is
        // actually available during it (otherwise the gap is unavoidable).
        // WEIGHT: 2 (Medium priority - teacher satisfaction)
        return constraintFactory
                .forEachIncludingUnassigned(CourseBlockAssignment.class)
                .join(constraintFactory.forEachIncludingUnassigned(CourseBlockAssignment.class),
                        Joiners.lessThan(CourseBlockAssignment::getId),
                        Joiners.equal(CourseBlockAssignment::getTeacher),
                        Joiners.equal(SchoolConstraintProvider::dayOfWeekOrNull))
                .filter((a1, a2) -> !a1.isPinned() && !a2.isPinned()
                        && a1.getTeacher() != null && a1.getTimeslot() != null && a2.getTimeslot() != null
                        && availableGapHours(a1, a2) > 0)
                // Keep only adjacent pairs: no third block of the same teacher lies in
                // the [earlierEnd, laterStart] span between a1 and a2.
                .ifNotExistsIncludingUnassigned(CourseBlockAssignment.class,
                        Joiners.equal((a1, a2) -> a1.getTeacher(), CourseBlockAssignment::getTeacher),
                        Joiners.filtering((a1, a2, mid) -> !mid.isPinned() && mid.getTimeslot() != null
                                && mid != a1 && mid != a2 && liesBetween(a1, a2, mid)))
                .penalize(HardSoftScore.ofSoft(2), (a1, a2) -> availableGapHours(a1, a2))
                .asConstraint("Minimize teacher idle gaps (availability-aware)");
    }

    // The school day's earliest possible start hour (matches the earliest
    // BlockTimeslot start hour, 7:00) - the target preferSemesterOneBlocksStartEarly
    // penalizes deviation from.
    private static final int EARLIEST_START_HOUR = 7;

    /** True when this block belongs to a first-semester (semester == 1) course. */
    private static boolean isSemesterOne(CourseBlockAssignment a) {
        return a.getCourse() != null && Integer.valueOf(1).equals(a.getCourse().getSemester());
    }

    /**
     * SOFT: First-semester groups should start their school day as early as
     * possible (7:00). Groups the group's unpinned semester-1 blocks per day,
     * penalizing by how far the EARLIEST one's start hour is from
     * EARLIEST_START_HOUR - a deviation-from-ideal gradient (like
     * preferCoreOneHourBlocksAtSameTimeAcrossDays' mode-deviation), not a flat
     * all-or-nothing penalty, so local search can improve incrementally by
     * moving the earliest block closer to 7:00.
     * WEIGHT: 4 (middle priority, slightly above the general-purpose 3 tier).
     */
    private Constraint preferSemesterOneBlocksStartEarly(ConstraintFactory constraintFactory) {
        return constraintFactory
                .forEachIncludingUnassigned(CourseBlockAssignment.class)
                .filter(a -> !a.isPinned() && a.getGroup() != null && a.getTimeslot() != null && isSemesterOne(a))
                .groupBy(CourseBlockAssignment::getGroup, a -> a.getTimeslot().getDayOfWeek(),
                        ConstraintCollectors.toList())
                .filter((group, day, blocks) -> earliestStartHour(blocks) > EARLIEST_START_HOUR)
                .penalize(HardSoftScore.ofSoft(6),
                        (group, day, blocks) -> earliestStartHour(blocks) - EARLIEST_START_HOUR)
                .asConstraint("Prefer first-semester blocks to start early");
    }

    /** The earliest start hour among these blocks. */
    private static int earliestStartHour(java.util.List<CourseBlockAssignment> blocks) {
        int earliest = Integer.MAX_VALUE;
        for (CourseBlockAssignment a : blocks) {
            earliest = Math.min(earliest, a.getTimeslot().getStartHour());
        }
        return earliest;
    }

    /**
     * SOFT: First-semester groups should run their day gap-free. Same
     * adjacent-pair-only idle-gap logic as minimizeGroupIdleGaps (now disabled
     * in favor of this scoped, higher-weighted version), but the *pair* being
     * penalized is restricted to two semester-1 blocks - both joined streams
     * are filtered to isSemesterOne() up front, so every penalized pair is
     * guaranteed semester-1-to-semester-1, with no overlap/double-counting
     * against any other gap constraint. Adjacency itself, however, still
     * considers a "mid" block of ANY semester: if a higher-semester block sits
     * between two semester-1 blocks with no idle time on either side, the
     * student isn't actually idle then (they're in that other class), so it
     * must still break adjacency - scoping the mid-check to semester-1 only
     * would have mistaken "occupied by another course" for "idle" and
     * inflated the gap across the intervening block's own occupied hours.
     * WEIGHT: 4 (middle priority, slightly above the general-purpose 3 tier).
     */
    private Constraint minimizeSemesterOneGroupIdleGaps(ConstraintFactory constraintFactory) {
        return constraintFactory
                .forEachIncludingUnassigned(CourseBlockAssignment.class)
                .filter(SchoolConstraintProvider::isSemesterOne)
                .join(constraintFactory.forEachIncludingUnassigned(CourseBlockAssignment.class)
                                .filter(SchoolConstraintProvider::isSemesterOne),
                        Joiners.lessThan(CourseBlockAssignment::getId),
                        Joiners.equal(CourseBlockAssignment::getGroup),
                        Joiners.equal(SchoolConstraintProvider::dayOfWeekOrNull))
                .filter((a1, a2) -> !a1.isPinned() && !a2.isPinned()
                        && a1.getGroup() != null && a1.getTimeslot() != null && a2.getTimeslot() != null
                        && gapHours(a1, a2) > 0)
                // Keep only adjacent pairs: no third block of the same group (any
                // semester) lies in the [earlierEnd, laterStart] span between a1/a2.
                .ifNotExistsIncludingUnassigned(CourseBlockAssignment.class,
                        Joiners.equal((a1, a2) -> a1.getGroup(), CourseBlockAssignment::getGroup),
                        Joiners.filtering((a1, a2, mid) -> !mid.isPinned() && mid.getTimeslot() != null
                                && mid != a1 && mid != a2 && liesBetween(a1, a2, mid)))
                .penalize(HardSoftScore.ofSoft(6), (a1, a2) -> gapHours(a1, a2))
                .asConstraint("Minimize first-semester group idle gaps");
    }

    private Constraint minimizeGroupIdleGaps(ConstraintFactory constraintFactory) {
        // SOFT: Minimizes student group idle gaps between blocks on the same day.
        // Only ADJACENT block pairs are penalized: a pair (a1, a2) with a gap between
        // them is counted only when no third block of the same group sits between them
        // (ifNotExists). This avoids the pairwise over-penalization where a
        // non-adjacent pair (blocks 1 and 3) would re-count idle hours already
        // accounted for by the adjacent pairs (1-2 and 2-3).
        // WEIGHT: 3 (High priority - student schedule quality, higher than teacher
        // gaps)
        return constraintFactory
                .forEachIncludingUnassigned(CourseBlockAssignment.class)
                .join(constraintFactory.forEachIncludingUnassigned(CourseBlockAssignment.class),
                        Joiners.lessThan(CourseBlockAssignment::getId),
                        Joiners.equal(CourseBlockAssignment::getGroup),
                        Joiners.equal(SchoolConstraintProvider::dayOfWeekOrNull))
                .filter((a1, a2) -> !a1.isPinned() && !a2.isPinned()
                        && a1.getGroup() != null && a1.getTimeslot() != null && a2.getTimeslot() != null
                        && gapHours(a1, a2) > 0)
                // Keep only adjacent pairs: no third block of the same group lies in
                // the [earlierEnd, laterStart] span between a1 and a2.
                .ifNotExistsIncludingUnassigned(CourseBlockAssignment.class,
                        Joiners.equal((a1, a2) -> a1.getGroup(), CourseBlockAssignment::getGroup),
                        Joiners.filtering((a1, a2, mid) -> !mid.isPinned() && mid.getTimeslot() != null
                                && mid != a1 && mid != a2 && liesBetween(a1, a2, mid)))
                .penalize(HardSoftScore.ofSoft(3), (a1, a2) -> gapHours(a1, a2))
                .asConstraint("Minimize group idle gaps");
    }

    /**
     * The idle-hour count in the gap between two same-day blocks (0 if they overlap
     * or are consecutive).
     */
    private static int gapHours(CourseBlockAssignment a1, CourseBlockAssignment a2) {
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
     * The idle-hour count in the gap between two same-day teacher blocks, counting
     * only hours during which the teacher is actually available (unavailable gap
     * hours are unavoidable and therefore not penalized).
     */
    private static int availableGapHours(CourseBlockAssignment a1, CourseBlockAssignment a2) {
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
     * True if {@code mid} starts within the open span between the earlier block's
     * end
     * and the later block's start of the {@code (a1, a2)} pair. Used to detect that
     * the pair is NOT adjacent (a third block lies between them), so its gap must
     * not
     * be penalized directly.
     */
    private static boolean liesBetween(CourseBlockAssignment a1, CourseBlockAssignment a2,
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

    private Constraint preferBlockSpecifiedRoom(ConstraintFactory constraintFactory) {
        // SOFT: Prefer using the block's specified preferred room
        // This supports Computer Center distribution strategy and custom room
        // assignments
        // WEIGHT: 3 (High priority - operational strategy for specialized rooms)
        return constraintFactory
                .forEachIncludingUnassigned(CourseBlockAssignment.class)
                .filter(assignment -> {
                    // Exclude pinned assignments (they already have fixed rooms)
                    if (assignment.isPinned()) {
                        return false;
                    }
                    if (assignment.getRoom() == null) {
                        return false;
                    }
                    // Check if block has a preferred room specified
                    String preferredRoomHint = assignment.getPreferredRoomHint();
                    if (preferredRoomHint == null || preferredRoomHint.isEmpty()) {
                        return false; // No preference specified
                    }
                    // Penalize if NOT using the preferred room
                    return !preferredRoomHint.equals(assignment.getRoom().getName());
                })
                .penalize(HardSoftScore.ofSoft(3))
                .asConstraint("Prefer block's specified room");
    }

}
