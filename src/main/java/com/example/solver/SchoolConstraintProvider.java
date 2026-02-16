package com.example.solver;

import ai.timefold.solver.core.api.score.stream.Constraint;
import ai.timefold.solver.core.api.score.stream.ConstraintFactory;
import ai.timefold.solver.core.api.score.stream.ConstraintProvider;
import ai.timefold.solver.core.api.score.stream.Joiners;
import ai.timefold.solver.core.api.score.buildin.hardsoft.HardSoftScore;
import ai.timefold.solver.core.api.score.stream.ConstraintCollectors;

import java.time.DayOfWeek;
import java.time.LocalTime;

import com.example.domain.CourseBlockAssignment;
import com.example.domain.BlockTimeslot;
import com.example.domain.Teacher;

public class SchoolConstraintProvider implements ConstraintProvider {

    @Override
    public Constraint[] defineConstraints(ConstraintFactory constraintFactory) {
        return new Constraint[] {
                // ========== TIER 1: Cheap HARD Constraints (forEach) - Fail Fast ==========
                // These are evaluated first because they're:
                // - Cheapest to evaluate (O(n) single-entity checks)
                // - Most likely to fail (high selectivity)
                // - Enable early exit (skip remaining constraints if violated)
                blockLengthMustMatchTimeslotLength(constraintFactory), // #0: CRITICAL - Block length must match
                                                                       // timeslot
                teacherMustBeAvailable(constraintFactory), // #1: Most likely to fail (~30% rejection rate)
                teacherMustBeQualified(constraintFactory), // #2: Second most likely (~20% rejection rate)
                roomTypeMustSatisfyRequirement(constraintFactory), // #3: Cheap, medium selectivity (~10% rejection)

                // ========== TIER 2: High-Selectivity HARD Pair Constraints ==========
                // These detect the most common conflicts:
                // - Optimized with Joiners (very few pairs evaluated)
                // - High failure rate (detect double-bookings early)
                groupCannotHaveTwoCoursesAtSameTime(constraintFactory), // #4: ~70 pairs, very high selectivity
                noTeacherDoubleBooking(constraintFactory), // #5: ~25 pairs, very high selectivity
                noRoomDoubleBooking(constraintFactory), // #6: ~45 pairs, very high selectivity

                // ========== TIER 3: Medium-Selectivity HARD Pair Constraints ==========
                maxTwoBlocksPerCoursePerGroupPerDay(constraintFactory), // HARD: Max 2 blocks per course per group per
                                                                        // day

                // ========== TIER 4: SOFT Constraints - Quality Optimization ==========
                // These optimize quality, evaluated last:
                // - Don't affect feasibility (can be violated)
                // - Evaluated only if all HARD constraints pass
                // - Order by weight (highest priority first)
                nonStandardRoomsShouldFinishBy2pm(constraintFactory), // SOFT (weight 10): Prefer non-standard rooms to
                                                                      // finish by 2pm
                teacherMaxHoursPerWeek(constraintFactory), // SOFT (weight 5): workload balance
                courseBlocksShouldBeConsecutive(constraintFactory), // SOFT (weight 3): Prefer course blocks to be
                                                                    // consecutive
                minimizeGroupIdleGaps(constraintFactory), // SOFT (weight 3): student schedule quality
                preferBlockSpecifiedRoom(constraintFactory), // SOFT (weight 3): prefer block's specified room (CC
                                                             // distribution)
                minimizeTeacherIdleGaps(constraintFactory), // SOFT (weight 2): teacher satisfaction
                groupPreferredRoomConstraint(constraintFactory), // SOFT (weight 2): prefer group's pre-assigned room
                minimizeTeacherBuildingChanges(constraintFactory), // SOFT (weight 1): minimize teacher travel

                // ========== COMMENTED OUT CONSTRAINTS ==========
                // Uncomment these if needed:
                // mustFinishBy2pm(constraintFactory), // SOFT: Prefer non-BASICAS courses in
                // non-standard rooms to finish by 2pm
                // limitNonBasicasCoursesToTwoDaysPerGroup(constraintFactory), // SOFT:
                // concentrate non-BASICAS
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
                .forEach(CourseBlockAssignment.class)
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
                .forEach(CourseBlockAssignment.class)
                .filter(assignment -> !assignment.isPinned() // Exclude pinned assignments
                        && assignment.getTeacher() != null
                        && !assignment.getTeacher().isQualifiedFor(assignment.getCourse().getName()))
                .penalize(HardSoftScore.ONE_HARD)
                .asConstraint("Teacher must be qualified");
    }

    private Constraint teacherMustBeAvailable(ConstraintFactory constraintFactory) {
        // UPDATED: Check teacher availability for entire block duration
        return constraintFactory
                .forEach(CourseBlockAssignment.class)
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
                .forEachUniquePair(CourseBlockAssignment.class,
                        Joiners.equal(CourseBlockAssignment::getTeacher),
                        Joiners.equal(a -> a.getTimeslot() != null ? a.getTimeslot().getDayOfWeek() : null))
                .filter((a1, a2) -> (!a1.isPinned() || !a2.isPinned()) // Penalize if at least one is unpinned
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
                .forEachUniquePair(CourseBlockAssignment.class,
                        Joiners.equal(CourseBlockAssignment::getRoom),
                        Joiners.equal(a -> a.getTimeslot() != null ? a.getTimeslot().getDayOfWeek() : null))
                .filter((a1, a2) -> (!a1.isPinned() || !a2.isPinned()) // Penalize if at least one is unpinned
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
        // can require different room types (e.g., 4h in CC + 1h in estándar)
        return constraintFactory
                .forEach(CourseBlockAssignment.class)
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

    private Constraint groupCannotHaveTwoCoursesAtSameTime(ConstraintFactory constraintFactory) {
        // UPDATED: Uses block overlap detection instead of exact timeslot matching
        // OPTIMIZED: Uses Joiners to pre-filter pairs by group and day
        return constraintFactory
                .forEachUniquePair(CourseBlockAssignment.class,
                        Joiners.equal(CourseBlockAssignment::getGroup),
                        Joiners.equal(a -> a.getTimeslot() != null ? a.getTimeslot().getDayOfWeek() : null))
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
        // - centro de cómputo (Computer Centers: CC 1, CC 2, CC 3)
        // - taller electromecánica (Electromechanical Workshops: TEM 1, TEM 2, TEM 3)
        // - taller electrónica (Electronics Workshop: TE 1)
        // - taller (General Workshop: AULA 4)
        // - laboratorio (Labs: LQ 1, LMICRO)
        //
        // Standard rooms (estándar) can run until 3pm (15:00) for flexibility.
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
                .forEach(CourseBlockAssignment.class)
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
                            && !roomType.equalsIgnoreCase("estándar"));

                    return isNonStandard; // Penalize if non-standard room after 2pm
                })
                .penalize(HardSoftScore.ofSoft(10)) // SOFT constraint (weight 10) - strong preference
                .asConstraint("Non-standard rooms should finish by 2pm");
    }

    private Constraint maxTwoBlocksPerCoursePerGroupPerDay(ConstraintFactory constraintFactory) {
        // HARD: Maximum 1 block per day for BASICAS courses, 2 for non-BASICAS
        // This prevents excessive concentration of same course on one day
        // BASICAS courses: Maximum 1 block per course per group per day
        // Non-BASICAS courses: Maximum 2 blocks per course per group per day
        return constraintFactory
                .forEach(CourseBlockAssignment.class)
                .filter(a -> !a.isPinned() && a.getTimeslot() != null)
                .groupBy(
                        CourseBlockAssignment::getGroup,
                        CourseBlockAssignment::getCourse,
                        a -> a.getTimeslot().getDayOfWeek(),
                        ConstraintCollectors.count())
                .filter((group, course, day, count) -> {
                    // BASICAS courses: max 1 block per day
                    if ("BASICAS".equals(course.getComponent())) {
                        return count > 1;
                    }
                    // Non-BASICAS courses: max 2 blocks per day
                    return count > 2;
                })
                .penalize(HardSoftScore.ONE_HARD,
                        (group, course, day, count) -> {
                            // BASICAS: penalize each block beyond 1
                            if ("BASICAS".equals(course.getComponent())) {
                                return count - 1;
                            }
                            // Non-BASICAS: penalize each block beyond 2
                            return count - 2;
                        })
                .asConstraint("Maximum blocks per course per group per day (1 for BASICAS, 2 for non-BASICAS)");
    }

    private Constraint courseBlocksShouldBeConsecutive(ConstraintFactory constraintFactory) {
        // SOFT: When ANY course has multiple blocks on the same day for the same group,
        // prefer them to be consecutive to minimize fragmentation and improve student
        // experience
        // This applies to ALL courses (BASICAS and non-BASICAS)
        // This is a SOFT constraint - it encourages but doesn't require consecutive
        // blocks
        // WEIGHT: 3 (High priority - student experience and schedule quality)
        return constraintFactory
                .forEachUniquePair(CourseBlockAssignment.class,
                        Joiners.equal(CourseBlockAssignment::getGroup),
                        Joiners.equal(CourseBlockAssignment::getCourse),
                        Joiners.equal(a -> a.getTimeslot() != null ? a.getTimeslot().getDayOfWeek() : null))
                .filter((a1, a2) -> {
                    // Exclude pinned assignments
                    if (a1.isPinned() || a2.isPinned()) {
                        return false;
                    }
                    // Apply to ALL courses (removed BASICAS-only filter)
                    // Note: Joiners already filter out null groups and courses

                    // Check if blocks are NOT consecutive
                    int end1 = a1.getTimeslot().getStartHour() + a1.getTimeslot().getLengthHours();
                    int start2 = a2.getTimeslot().getStartHour();
                    int end2 = a2.getTimeslot().getStartHour() + a2.getTimeslot().getLengthHours();
                    int start1 = a1.getTimeslot().getStartHour();

                    // They are consecutive if end1 == start2 OR end2 == start1
                    boolean areConsecutive = (end1 == start2 || end2 == start1);

                    // Penalize if NOT consecutive
                    return !areConsecutive;
                })
                .penalize(HardSoftScore.ofSoft(3))
                .asConstraint("Prefer course blocks to be consecutive on same day");
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
        // SOFT: Prefer assigning groups to their preferred room
        // This reduces room changes for students and improves familiarity
        // WEIGHT: 2 (Medium priority - operational efficiency)
        return constraintFactory
                .forEach(CourseBlockAssignment.class)
                .filter(assignment -> {
                    // Exclude pinned assignments
                    if (assignment.isPinned()) {
                        return false;
                    }
                    if (assignment.getGroup() == null || assignment.getRoom() == null) {
                        return false;
                    }
                    // Only apply to non-lab blocks (labs must use lab rooms)
                    // Uses satisfiesRoomType field to support dual room requirements
                    if (assignment.getSatisfiesRoomType() != null &&
                            "laboratorio".equalsIgnoreCase(assignment.getSatisfiesRoomType())) {
                        return false;
                    }
                    // Check if group has a preferred room
                    var preferredRoom = assignment.getGroup().getPreferredRoom();
                    if (preferredRoom == null) {
                        return false;
                    }
                    // Penalize if NOT using preferred room
                    return !preferredRoom.equals(assignment.getRoom());
                })
                .penalize(HardSoftScore.ofSoft(2))
                .asConstraint("Prefer group's preferred room");
    }

    private Constraint minimizeTeacherBuildingChanges(ConstraintFactory constraintFactory) {
        // SOFT: Minimize teacher building changes on same day
        // Reduces teacher travel time and improves satisfaction
        // WEIGHT: 1 (Low priority - nice to have)
        return constraintFactory
                .forEachUniquePair(CourseBlockAssignment.class,
                        Joiners.equal(CourseBlockAssignment::getTeacher),
                        Joiners.equal(a -> a.getTimeslot() != null ? a.getTimeslot().getDayOfWeek() : null))
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
                .forEach(CourseBlockAssignment.class)
                .filter(a -> a.getTeacher() != null && a.getTimeslot() != null) // Include ALL assignments
                .groupBy(CourseBlockAssignment::getTeacher,
                        ConstraintCollectors.sum(CourseBlockAssignment::getBlockLength))
                .filter((teacher, totalHours) -> totalHours > teacher.getMaxHoursPerWeek())
                .penalize(HardSoftScore.ONE_SOFT,
                        (teacher, totalHours) -> (totalHours - teacher.getMaxHoursPerWeek()) * 5)
                .asConstraint("Teacher exceeds max hours per week");
    }

    private Constraint minimizeTeacherIdleGaps(ConstraintFactory constraintFactory) {
        // UPDATED: Minimizes teacher idle gaps between blocks on the same day
        // SMART LOGIC:
        // 1. Only penalizes gaps when teacher IS available during gap hours
        // 2. No penalty if teacher is unavailable (gap is unavoidable)
        // 3. Uses Joiners for better performance (pre-filters pairs)
        // 4. Works with block end times and start times
        // WEIGHT: 2 (Medium priority - teacher satisfaction)
        return constraintFactory
                .forEachUniquePair(CourseBlockAssignment.class,
                        // Performance optimization: pre-filter pairs with Joiners
                        Joiners.equal(CourseBlockAssignment::getTeacher),
                        Joiners.equal(a -> a.getTimeslot() != null ? a.getTimeslot().getDayOfWeek() : null))
                .filter((a1, a2) -> {
                    // Exclude pinned assignments
                    if (a1.isPinned() || a2.isPinned())
                        return false;
                    // Additional filters after Joiners
                    if (a1.getTeacher() == null || a1.getTimeslot() == null || a2.getTimeslot() == null)
                        return false;

                    // Calculate block end times
                    int end1 = a1.getTimeslot().getStartHour() + a1.getTimeslot().getLengthHours();
                    int start2 = a2.getTimeslot().getStartHour();
                    int end2 = a2.getTimeslot().getStartHour() + a2.getTimeslot().getLengthHours();
                    int start1 = a1.getTimeslot().getStartHour();

                    // Determine if there's a gap between blocks
                    int gapStart, gapEnd;
                    if (end1 <= start2) {
                        // Block 1 ends before block 2 starts
                        gapStart = end1;
                        gapEnd = start2;
                    } else if (end2 <= start1) {
                        // Block 2 ends before block 1 starts
                        gapStart = end2;
                        gapEnd = start1;
                    } else {
                        // Blocks overlap or are consecutive - no gap
                        return false;
                    }

                    int gapSize = gapEnd - gapStart;
                    if (gapSize <= 0)
                        return false; // No gap or consecutive blocks

                    // Check if teacher is available during ALL gap hours
                    DayOfWeek day = a1.getTimeslot().getDayOfWeek();
                    for (int gapHour = gapStart; gapHour < gapEnd; gapHour++) {
                        if (!a1.getTeacher().isAvailableAt(day, gapHour)) {
                            return false; // Teacher unavailable during gap - no penalty
                        }
                    }

                    // Teacher IS available for all gap hours - this gap is avoidable, penalize it
                    return true;
                })
                .penalize(HardSoftScore.ONE_SOFT, (a1, a2) -> {
                    // Calculate gap size
                    int end1 = a1.getTimeslot().getStartHour() + a1.getTimeslot().getLengthHours();
                    int start2 = a2.getTimeslot().getStartHour();
                    int end2 = a2.getTimeslot().getStartHour() + a2.getTimeslot().getLengthHours();
                    int start1 = a1.getTimeslot().getStartHour();

                    int gapSize;
                    if (end1 <= start2) {
                        gapSize = start2 - end1;
                    } else {
                        gapSize = start1 - end2;
                    }

                    // Weighted penalty: 2x per gap hour (medium priority)
                    return gapSize * 2;
                })
                .asConstraint("Minimize teacher idle gaps (availability-aware)");
    }

    private Constraint minimizeGroupIdleGaps(ConstraintFactory constraintFactory) {
        // SOFT: Minimizes student group idle gaps between blocks on the same day
        // This improves student experience by reducing waiting time between classes
        // WEIGHT: 3 (High priority - student schedule quality, higher than teacher
        // gaps)
        return constraintFactory
                .forEachUniquePair(CourseBlockAssignment.class,
                        // Performance optimization: pre-filter pairs with Joiners
                        Joiners.equal(CourseBlockAssignment::getGroup),
                        Joiners.equal(a -> a.getTimeslot() != null ? a.getTimeslot().getDayOfWeek() : null))
                .filter((a1, a2) -> {
                    // Exclude pinned assignments
                    if (a1.isPinned() || a2.isPinned())
                        return false;
                    // Additional filters after Joiners
                    if (a1.getGroup() == null || a1.getTimeslot() == null || a2.getTimeslot() == null)
                        return false;

                    // Calculate block end times
                    int end1 = a1.getTimeslot().getStartHour() + a1.getTimeslot().getLengthHours();
                    int start2 = a2.getTimeslot().getStartHour();
                    int end2 = a2.getTimeslot().getStartHour() + a2.getTimeslot().getLengthHours();
                    int start1 = a1.getTimeslot().getStartHour();

                    // Determine if there's a gap between blocks
                    int gapStart, gapEnd;
                    if (end1 <= start2) {
                        // Block 1 ends before block 2 starts
                        gapStart = end1;
                        gapEnd = start2;
                    } else if (end2 <= start1) {
                        // Block 2 ends before block 1 starts
                        gapStart = end2;
                        gapEnd = start1;
                    } else {
                        // Blocks overlap or are consecutive - no gap
                        return false;
                    }

                    int gapSize = gapEnd - gapStart;
                    if (gapSize <= 0)
                        return false; // No gap or consecutive blocks

                    // Penalize all gaps for student groups (no availability check needed)
                    return true;
                })
                .penalize(HardSoftScore.ONE_SOFT, (a1, a2) -> {
                    // Calculate gap size
                    int end1 = a1.getTimeslot().getStartHour() + a1.getTimeslot().getLengthHours();
                    int start2 = a2.getTimeslot().getStartHour();
                    int end2 = a2.getTimeslot().getStartHour() + a2.getTimeslot().getLengthHours();
                    int start1 = a1.getTimeslot().getStartHour();

                    int gapSize;
                    if (end1 <= start2) {
                        gapSize = start2 - end1;
                    } else {
                        gapSize = start1 - end2;
                    }

                    // Weighted penalty: 3x per gap hour (high priority - student experience)
                    return gapSize * 3;
                })
                .asConstraint("Minimize group idle gaps");
    }

    private Constraint preferBlockSpecifiedRoom(ConstraintFactory constraintFactory) {
        // SOFT: Prefer using the block's specified preferred room
        // This supports Computer Center distribution strategy and custom room
        // assignments
        // WEIGHT: 3 (High priority - operational strategy for specialized rooms)
        return constraintFactory
                .forEach(CourseBlockAssignment.class)
                .filter(assignment -> {
                    // Exclude pinned assignments (they already have fixed rooms)
                    if (assignment.isPinned()) {
                        return false;
                    }
                    if (assignment.getRoom() == null) {
                        return false;
                    }
                    // Check if block has a preferred room specified
                    String preferredRoomName = assignment.getPreferredRoomName();
                    if (preferredRoomName == null || preferredRoomName.isEmpty()) {
                        return false; // No preference specified
                    }
                    // Penalize if NOT using the preferred room
                    return !preferredRoomName.equals(assignment.getRoom().getName());
                })
                .penalize(HardSoftScore.ofSoft(3))
                .asConstraint("Prefer block's specified room");
    }

}
