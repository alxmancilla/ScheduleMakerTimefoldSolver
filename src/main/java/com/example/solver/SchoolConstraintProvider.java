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
                teacherMustBeAvailable(constraintFactory), // #1: Most likely to fail (~30% rejection rate)
                teacherMustBeQualified(constraintFactory), // #2: Second most likely (~20% rejection rate)
                roomTypeMustSatisfyRequirement(constraintFactory), // #3: Cheap, medium selectivity (~10% rejection)
                nonBasicasCourseMustFinishBy2pm(constraintFactory), // #4: Non-BASICAS courses must end by 14:00
                nonStandardRoomsMustFinishBy2pm(constraintFactory), // #5: Non-standard rooms must end by 14:00
                maxOneBlockPerCoursePerGroupPerDay(constraintFactory), // #6: Max 1 block per course per group per day
                                                                       // (non-BASICAS only)

                // ========== TIER 2: High-Selectivity HARD Pair Constraints ==========
                // These detect the most common conflicts:
                // - Optimized with Joiners (very few pairs evaluated)
                // - High failure rate (detect double-bookings early)
                groupCannotHaveTwoCoursesAtSameTime(constraintFactory), // #4: ~70 pairs, very high selectivity
                noTeacherDoubleBooking(constraintFactory), // #5: ~25 pairs, very high selectivity
                noRoomDoubleBooking(constraintFactory), // #6: ~45 pairs, very high selectivity

                // ========== TIER 3: Medium-Selectivity HARD Pair Constraints ==========
                // REMOVED FOR BLOCK-BASED SCHEDULING:
                // - groupCourseMustBeConsecutiveOnSameDay: OBSOLETE (blocks are inherently
                // consecutive)
                // - sameTeacherForAllCourseHours: OBSOLETE (only one block per course per
                // group)

                // ========== TIER 3: Medium-Selectivity HARD Pair Constraints ==========
                basicasBlocksShouldBeConsecutive(constraintFactory), // #7: BASICAS blocks must be consecutive

                // ========== TIER 4: SOFT Constraints - Quality Optimization ==========
                // These optimize quality, evaluated last:
                // - Don't affect feasibility (can be violated)
                // - Evaluated only if all HARD constraints pass
                // - Order by computational cost (most expensive first)
                minimizeTeacherIdleGaps(constraintFactory), // #9: ~1,000 pairs (most expensive SOFT)
                // limitNonBasicasCoursesToTwoDaysPerGroup(constraintFactory), // #11: groupBy
                // aggregation (weight 15,
                // concentrate non-BASICAS)
                teacherMaxHoursPerWeek(constraintFactory), // #12: groupBy aggregation (workload balance)

                // ========== COMMENTED OUT CONSTRAINTS ==========
                // Uncomment these if needed:
                // groupCoursesInSameRoomByType(constraintFactory), // HARD: same room type
                // consistency
                // groupPreferredRoomConstraint(constraintFactory), // SOFT: prefer group's
                // pre-assigned room
                // minimizeTeacherBuildingChanges(constraintFactory), // SOFT: minimize teacher
                // travel
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

    private Constraint teacherMustBeQualified(ConstraintFactory constraintFactory) {
        return constraintFactory
                .forEach(CourseBlockAssignment.class)
                .filter(assignment -> assignment.getTeacher() != null
                        && !assignment.getTeacher().isQualifiedFor(assignment.getCourse().getName()))
                .penalize(HardSoftScore.ONE_HARD)
                .asConstraint("Teacher must be qualified");
    }

    private Constraint teacherMustBeAvailable(ConstraintFactory constraintFactory) {
        // UPDATED: Check teacher availability for entire block duration
        return constraintFactory
                .forEach(CourseBlockAssignment.class)
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
                .filter((a1, a2) -> a1.getTeacher() != null
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
                .filter((a1, a2) -> a1.getRoom() != null
                        && a1.getTimeslot() != null
                        && a2.getTimeslot() != null
                        && blocksOverlap(a1.getTimeslot(), a2.getTimeslot()))
                .penalize(HardSoftScore.ONE_HARD)
                .asConstraint("No room double-booking");
    }

    private Constraint roomTypeMustSatisfyRequirement(ConstraintFactory constraintFactory) {
        return constraintFactory
                .forEach(CourseBlockAssignment.class)
                .filter(assignment -> assignment.getRoom() != null
                        && !assignment.getRoom().satisfiesRequirement(assignment.getCourse().getRoomRequirement()))
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
                .filter((a1, a2) -> a1.getTimeslot() != null
                        && a2.getTimeslot() != null
                        && blocksOverlap(a1.getTimeslot(), a2.getTimeslot()))
                .penalize(HardSoftScore.ONE_HARD)
                .asConstraint("Group cannot have two courses at same time");
    }

    private Constraint nonBasicasCourseMustFinishBy2pm(ConstraintFactory constraintFactory) {
        // Non-BASICAS courses (TADHR, TEM, TPIAL, etc.) must finish by 2pm (14:00)
        // This ensures specialized/technical courses don't run into late afternoon
        // hours
        // BASICAS courses can run until 3pm (15:00) for more flexibility
        // Pinned assignments are exempt (they are fixed from the database)
        return constraintFactory
                .forEach(CourseBlockAssignment.class)
                .filter(assignment -> {
                    // Check if course is non-BASICAS
                    if (assignment.getCourse() == null || assignment.getTimeslot() == null)
                        return false;

                    // Exempt pinned assignments (they are fixed from database)
                    if (assignment.isPinned())
                        return false;

                    String component = assignment.getCourse().getComponent();
                    if (component == null || component.equalsIgnoreCase("BASICAS"))
                        return false; // BASICAS courses are exempt

                    // Check if block ends after 2pm (14:00)
                    int endHour = assignment.getTimeslot().getStartHour() + assignment.getTimeslot().getLengthHours();
                    return endHour > 14; // Violates if ends after 14:00
                })
                .penalize(HardSoftScore.ONE_HARD)
                .asConstraint("Non-BASICAS courses must finish by 2pm");
    }

    private Constraint nonStandardRoomsMustFinishBy2pm(ConstraintFactory constraintFactory) {
        // Courses using non-standard rooms (labs, workshops, computer centers, etc.)
        // must finish by 2pm (14:00)
        // This ensures specialized facilities are available for maintenance and cleanup
        // Standard rooms can be used until 3pm (15:00)
        // Pinned assignments are exempt (they are fixed from the database)
        return constraintFactory
                .forEach(CourseBlockAssignment.class)
                .filter(assignment -> {
                    // Check if assignment has a room and timeslot
                    if (assignment.getRoom() == null || assignment.getTimeslot() == null)
                        return false;

                    // Exempt pinned assignments (they are fixed from database)
                    if (assignment.isPinned())
                        return false;

                    // Check if room type is non-standard
                    String roomType = assignment.getRoom().getType();
                    if (roomType == null || roomType.equalsIgnoreCase("estándar"))
                        return false; // Standard rooms are exempt

                    // Check if block ends at or after 2pm (14:00)
                    // "Finish by 2pm" means the last hour should be 13:00 (1pm-2pm)
                    int endHour = assignment.getTimeslot().getStartHour() + assignment.getTimeslot().getLengthHours();
                    return endHour > 13; // Violates if ends at 14:00 or later
                })
                .penalize(HardSoftScore.ONE_HARD)
                .asConstraint("Non-standard rooms must finish by 2pm");
    }

    private Constraint maxOneBlockPerCoursePerGroupPerDay(ConstraintFactory constraintFactory) {
        // Each group can have at most 1 block per non-BASICAS course per day
        // This prevents specialized courses from having multiple blocks on the same day
        // BASICAS courses are exempt and can have multiple blocks per day for
        // flexibility
        return constraintFactory
                .forEach(CourseBlockAssignment.class)
                .filter(a -> a.getTimeslot() != null
                        && a.getCourse() != null
                        && a.getCourse().getComponent() != null
                        && !a.getCourse().getComponent().equalsIgnoreCase("BASICAS"))
                .groupBy(CourseBlockAssignment::getGroup,
                        CourseBlockAssignment::getCourse,
                        a -> a.getTimeslot().getDayOfWeek(),
                        ConstraintCollectors.count())
                .filter((group, course, day, count) -> count > 1)
                .penalize(HardSoftScore.ONE_HARD,
                        (group, course, day, count) -> count - 1)
                .asConstraint("Maximum 1 block per non-BASICAS course per group per day");
    }

    private Constraint basicasBlocksShouldBeConsecutive(ConstraintFactory constraintFactory) {
        // When BASICAS courses have multiple blocks on the same day for the same group,
        // they MUST be consecutive to minimize fragmentation and improve student
        // experience
        // This is a HARD constraint - it requires consecutive blocks
        return constraintFactory
                .forEachUniquePair(CourseBlockAssignment.class,
                        Joiners.equal(CourseBlockAssignment::getGroup),
                        Joiners.equal(CourseBlockAssignment::getCourse),
                        Joiners.equal(a -> a.getTimeslot() != null ? a.getTimeslot().getDayOfWeek() : null))
                .filter((a1, a2) -> {
                    // Only apply to BASICAS courses
                    if (a1.getCourse() == null || a1.getTimeslot() == null || a2.getTimeslot() == null)
                        return false;

                    String component = a1.getCourse().getComponent();
                    if (component == null || !component.equalsIgnoreCase("BASICAS"))
                        return false;

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
                .penalize(HardSoftScore.ONE_HARD)
                .asConstraint("BASICAS blocks must be consecutive on same day");
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

    @Deprecated
    private Constraint groupPreferredRoomConstraint(ConstraintFactory constraintFactory) {
        throw new UnsupportedOperationException("Hour-based scheduling is no longer supported");
    }

    @Deprecated
    private Constraint minimizeTeacherBuildingChanges(ConstraintFactory constraintFactory) {
        throw new UnsupportedOperationException("Hour-based scheduling is no longer supported");
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
        return constraintFactory
                .forEach(CourseBlockAssignment.class)
                .filter(a -> a.getTeacher() != null && a.getTimeslot() != null)
                .groupBy(CourseBlockAssignment::getTeacher,
                        ConstraintCollectors.sum(CourseBlockAssignment::getBlockLength))
                .filter((teacher, totalHours) -> totalHours > teacher.getMaxHoursPerWeek())
                .penalize(HardSoftScore.ONE_SOFT,
                        (teacher, totalHours) -> totalHours - teacher.getMaxHoursPerWeek())
                .asConstraint("Teacher exceeds max hours per week");
    }

    private Constraint minimizeTeacherIdleGaps(ConstraintFactory constraintFactory) {
        // UPDATED: Minimizes teacher idle gaps between blocks on the same day
        // SMART LOGIC:
        // 1. Only penalizes gaps when teacher IS available during gap hours
        // 2. No penalty if teacher is unavailable (gap is unavoidable)
        // 3. Uses Joiners for better performance (pre-filters pairs)
        // 4. Works with block end times and start times
        return constraintFactory
                .forEachUniquePair(CourseBlockAssignment.class,
                        // Performance optimization: pre-filter pairs with Joiners
                        Joiners.equal(CourseBlockAssignment::getTeacher),
                        Joiners.equal(a -> a.getTimeslot() != null ? a.getTimeslot().getDayOfWeek() : null))
                .filter((a1, a2) -> {
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

                    // Standard penalty: 1x per gap hour
                    return gapSize;
                })
                .asConstraint("Minimize teacher idle gaps (availability-aware)");
    }

}
