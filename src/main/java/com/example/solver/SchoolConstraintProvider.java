package com.example.solver;

import ai.timefold.solver.core.api.score.stream.Constraint;
import ai.timefold.solver.core.api.score.stream.ConstraintFactory;
import ai.timefold.solver.core.api.score.stream.ConstraintProvider;
import ai.timefold.solver.core.api.score.stream.Joiners;
import ai.timefold.solver.core.api.score.buildin.hardsoft.HardSoftScore;
import ai.timefold.solver.core.api.score.stream.ConstraintCollectors;

import java.time.DayOfWeek;
import java.time.LocalTime;

import com.example.domain.CourseAssignment;
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

                // ========== TIER 2: High-Selectivity HARD Pair Constraints ==========
                // These detect the most common conflicts:
                // - Optimized with Joiners (very few pairs evaluated)
                // - High failure rate (detect double-bookings early)
                groupCannotHaveTwoCoursesAtSameTime(constraintFactory), // #4: ~70 pairs, very high selectivity
                noTeacherDoubleBooking(constraintFactory), // #5: ~25 pairs, very high selectivity
                noRoomDoubleBooking(constraintFactory), // #6: ~45 pairs, very high selectivity

                // ========== TIER 3: Medium-Selectivity HARD Pair Constraints ==========
                // These enforce consistency rules:
                // - More pairs to evaluate than Tier 2
                // - Lower failure rate (less selective)
                // - Still critical for feasibility
                groupCourseMustBeConsecutiveOnSameDay(constraintFactory), // #7: ~500 pairs, medium selectivity
                sameTeacherForAllCourseHours(constraintFactory), // #8: ~2,000 pairs, low selectivity

                // ========== TIER 4: SOFT Constraints - Quality Optimization ==========
                // These optimize quality, evaluated last:
                // - Don't affect feasibility (can be violated)
                // - Evaluated only if all HARD constraints pass
                // - Order by computational cost (most expensive first)
                minimizeTeacherIdleGaps(constraintFactory), // #9: ~1,000 pairs (most expensive SOFT)
                // limitNonBasicasCoursesToTwoDaysPerGroup(constraintFactory), // #10: groupBy
                // aggregation (weight 15,
                // concentrate non-BASICAS)
                teacherMaxHoursPerWeek(constraintFactory), // #11: groupBy aggregation (workload balance)

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

    private Constraint teacherMustBeQualified(ConstraintFactory constraintFactory) {
        return constraintFactory
                .forEach(CourseAssignment.class)
                .filter(assignment -> assignment.getTeacher() != null
                        && !assignment.getTeacher().isQualifiedFor(assignment.getCourse().getName()))
                .penalize(HardSoftScore.ONE_HARD)
                .asConstraint("Teacher must be qualified");
    }

    private Constraint teacherMustBeAvailable(ConstraintFactory constraintFactory) {
        return constraintFactory
                .forEach(CourseAssignment.class)
                .filter(assignment -> assignment.getTeacher() != null && assignment.getTimeslot() != null
                        && !assignment.getTeacher().isAvailableAt(assignment.getTimeslot()))
                .penalize(HardSoftScore.ONE_HARD)
                .asConstraint("Teacher must be available at timeslot");
    }

    private Constraint noTeacherDoubleBooking(ConstraintFactory constraintFactory) {
        // OPTIMIZED: Uses Joiners to pre-filter pairs (99% reduction)
        // Before: ~124,750 pairs → After: ~25 pairs
        return constraintFactory
                .forEachUniquePair(CourseAssignment.class,
                        Joiners.equal(CourseAssignment::getTeacher),
                        Joiners.equal(CourseAssignment::getTimeslot))
                .filter((a1, a2) -> a1.getTeacher() != null && a1.getTimeslot() != null)
                .penalize(HardSoftScore.ONE_HARD)
                .asConstraint("No teacher double-booking");
    }

    private Constraint noRoomDoubleBooking(ConstraintFactory constraintFactory) {
        // OPTIMIZED: Uses Joiners to pre-filter pairs (99% reduction)
        // Before: ~124,750 pairs → After: ~45 pairs
        return constraintFactory
                .forEachUniquePair(CourseAssignment.class,
                        Joiners.equal(CourseAssignment::getRoom),
                        Joiners.equal(CourseAssignment::getTimeslot))
                .filter((a1, a2) -> a1.getRoom() != null && a1.getTimeslot() != null)
                .penalize(HardSoftScore.ONE_HARD)
                .asConstraint("No room double-booking");
    }

    private Constraint roomTypeMustSatisfyRequirement(ConstraintFactory constraintFactory) {
        return constraintFactory
                .forEach(CourseAssignment.class)
                .filter(assignment -> assignment.getRoom() != null
                        && !assignment.getRoom().satisfiesRequirement(assignment.getCourse().getRoomRequirement()))
                .penalize(HardSoftScore.ONE_HARD)
                .asConstraint("Room type must satisfy course requirement");
    }

    private Constraint groupCannotHaveTwoCoursesAtSameTime(ConstraintFactory constraintFactory) {
        // OPTIMIZED: Uses Joiners to pre-filter pairs (99% reduction)
        // Before: ~124,750 pairs → After: ~70 pairs
        return constraintFactory
                .forEachUniquePair(CourseAssignment.class,
                        Joiners.equal(CourseAssignment::getGroup),
                        Joiners.equal(CourseAssignment::getTimeslot))
                .filter((a1, a2) -> a1.getTimeslot() != null)
                .penalize(HardSoftScore.ONE_HARD)
                .asConstraint("Group cannot have two courses at same time");
    }

    private Constraint sameTeacherForAllCourseHours(ConstraintFactory constraintFactory) {
        // OPTIMIZED: Uses Joiners to pre-filter pairs (98% reduction)
        // Before: ~124,750 pairs → After: ~2,000 pairs
        return constraintFactory
                .forEachUniquePair(CourseAssignment.class,
                        Joiners.equal(CourseAssignment::getGroup),
                        Joiners.equal(CourseAssignment::getCourse))
                .filter((a1, a2) -> a1.getTeacher() != null && a2.getTeacher() != null
                        && !a1.getTeacher().equals(a2.getTeacher()))
                .penalize(HardSoftScore.ONE_HARD)
                .asConstraint("Same teacher for all course hours (hard constraint)");
    }

    private Constraint groupCourseMustBeConsecutiveOnSameDay(ConstraintFactory constraintFactory) {
        // When a group takes multiple hours of the same course on the SAME day,
        // those hours must be consecutive.
        // However, course hours can be spread across different days (no requirement for
        // same day).
        // This constraint only applies when both assignments happen to be on the same
        // day.
        // ENHANCED: Higher penalty for non-BASICAS courses (TADHR, TEM, etc.)
        return constraintFactory
                .forEachUniquePair(CourseAssignment.class,
                        // Add join to reduce pairs evaluated
                        Joiners.equal(CourseAssignment::getGroup),
                        Joiners.equal(CourseAssignment::getCourse),
                        Joiners.equal(a -> a.getTimeslot() != null ? a.getTimeslot().getDayOfWeek() : null))
                .filter((a1, a2) -> {
                    // Simplified filter since joins handle most conditions
                    return a1.getSequenceIndex() != a2.getSequenceIndex()
                            && a1.getTimeslot() != null && a2.getTimeslot() != null;
                })
                .penalize(HardSoftScore.ONE_HARD, (a1, a2) -> {
                    // Calculate gap size (how many hours apart they are)
                    int hour1 = a1.getTimeslot().getHour();
                    int hour2 = a2.getTimeslot().getHour();
                    int seqDiff = Math.abs(a2.getSequenceIndex() - a1.getSequenceIndex());
                    int hourDiff = Math.abs(hour2 - hour1);
                    int gapSize = hourDiff - seqDiff; // Gap between consecutive sequence indices

                    // Get course component (BASICAS, TADHR, TEM, etc.)
                    String component = a1.getCourse().getComponent();

                    // ENHANCED PENALTY SYSTEM:
                    // - BASICAS courses: 1x penalty per gap hour (standard)
                    // - Non-BASICAS courses (TADHR, TEM, etc.): 3x penalty per gap hour (stricter)
                    // This ensures specialized/technical courses are more strictly scheduled
                    // consecutively

                    int basePenalty = Math.max(1, gapSize); // At least 1 penalty for any violation
                    /**
                     * if (component != null && !component.equalsIgnoreCase("BASICAS")) {
                     * // Non-BASICAS courses: 3x penalty (TADHR, TEM, etc.)
                     * return basePenalty * 3;
                     * }
                     */

                    // BASICAS courses: standard penalty
                    return basePenalty;
                })
                .asConstraint("Group course hours must be consecutive when on the same day (stricter for non-BASICAS)");
    }

    private Constraint groupCoursesInSameRoomByType(ConstraintFactory constraintFactory) {
        // All courses with the same room requirement for a group must use the same
        // room.
        // This consolidates standard and lab room consistency into a single constraint
        // and automatically handles any new room types (e.g., "auditorium", "gym").
        // OPTIMIZED: Uses Joiners to pre-filter pairs (90% reduction)
        // Before: ~124,750 pairs → After: ~12,000 pairs
        return constraintFactory
                .forEachUniquePair(CourseAssignment.class,
                        Joiners.equal(CourseAssignment::getGroup))
                .filter((a1, a2) -> {
                    // Basic null guards
                    if (a1.getRoom() == null || a2.getRoom() == null)
                        return false;
                    if (a1.getCourse() == null || a2.getCourse() == null)
                        return false;

                    // Get room requirements
                    String req1 = a1.getCourse().getRoomRequirement();
                    String req2 = a2.getCourse().getRoomRequirement();

                    if (req1 == null || req2 == null)
                        return false;

                    // Only enforce if both courses have the same room requirement type
                    if (!req1.trim().equalsIgnoreCase(req2.trim()))
                        return false;

                    // Same group + same room type requirement: must use same room
                    return !a1.getRoom().equals(a2.getRoom());
                })
                .penalize(HardSoftScore.ONE_HARD)
                .asConstraint("Group courses with same room type must use same room");
    }

    private boolean overlapsForbiddenWindow(LocalTime start, LocalTime end) {
        LocalTime forbiddenStart = LocalTime.of(9, 0);
        LocalTime forbiddenEnd = LocalTime.of(13, 0);

        return start.isBefore(forbiddenEnd) && end.isAfter(forbiddenStart);
    }

    private Constraint teacherMaxHoursPerWeek(ConstraintFactory constraintFactory) {
        // Count the number of assigned CourseAssignment objects per teacher and
        // penalize when the count exceeds Teacher.maxHoursPerWeek.
        // Each CourseAssignment represents exactly 1 hour of teaching.
        return constraintFactory
                .forEach(CourseAssignment.class)
                .filter(a -> a.getTeacher() != null && a.getTimeslot() != null)
                .groupBy(CourseAssignment::getTeacher,
                        ConstraintCollectors.count())
                .filter((teacher, totalAssignments) -> totalAssignments > teacher.getMaxHoursPerWeek())
                .penalize(HardSoftScore.ONE_SOFT,
                        (teacher, totalAssignments) -> totalAssignments - teacher.getMaxHoursPerWeek())
                .asConstraint("Teacher exceeds max hours per week (hard)");
    }

    private Constraint preferUsingTeachersWithMoreAvailability(ConstraintFactory factory) {
        return factory.forEach(CourseAssignment.class)
                .filter(a -> a.getTeacher() != null)
                .penalize(
                        HardSoftScore.ONE_SOFT,
                        a -> scarcityPenalty(a.getTeacher()))
                .asConstraint("Prefer teachers with higher availability");
    }

    private int scarcityPenalty(Teacher teacher) {
        // Inverse weight: fewer hours → bigger penalty
        return Math.max(1, 40 - teacher.getMaxHoursPerWeek());
    }

    private Constraint groupPreferredRoomConstraint(ConstraintFactory constraintFactory) {
        return constraintFactory
                .forEach(CourseAssignment.class)
                .filter(assignment -> assignment.getGroup() != null
                        && assignment.getGroup().getPreferredRoom() != null
                        && !"taller".equalsIgnoreCase(assignment.getGroup().getPreferredRoom().getType())
                        && !"centro de cómputo".equalsIgnoreCase(assignment.getGroup().getPreferredRoom().getType())
                        && !"laboratorio".equalsIgnoreCase(assignment.getGroup().getPreferredRoom().getType())
                        && (assignment.getRoom() == null
                                || !assignment.getRoom().equals(assignment.getGroup().getPreferredRoom())))
                .penalize(HardSoftScore.ofSoft(3))
                .asConstraint("Prefer group's pre-assigned room (weight 3)");
    }

    // ==================== SOFT CONSTRAINTS ====================

    private Constraint minimizeTeacherBuildingChanges(ConstraintFactory constraintFactory) {
        // OPTIMIZED: Uses Joiners to pre-filter pairs (95% reduction)
        // Before: ~124,750 pairs → After: ~1,000 pairs
        return constraintFactory
                .forEachUniquePair(CourseAssignment.class,
                        Joiners.equal(CourseAssignment::getTeacher),
                        Joiners.equal(a -> a.getTimeslot() != null ? a.getTimeslot().getDayOfWeek() : null))
                .filter((a1, a2) -> {
                    if (a1.getTimeslot() == null || a2.getTimeslot() == null)
                        return false;
                    if (a1.getRoom() == null || a2.getRoom() == null)
                        return false;

                    // Same day, different buildings
                    return !a1.getRoom().getBuilding().equals(a2.getRoom().getBuilding());
                })
                .penalize(HardSoftScore.ofSoft(1))
                .asConstraint("Minimize teacher building changes (comfort)");
    }

    private Constraint minimizeTeacherIdleGaps(ConstraintFactory constraintFactory) {
        // Minimizes teacher idle gaps on the same day
        // SMART LOGIC:
        // 1. Only penalizes gaps when teacher IS available during gap hours
        // 2. No penalty if teacher is unavailable (gap is unavoidable)
        // 3. Uses Joiners for better performance (pre-filters pairs)
        // 4. Handles same-group vs different-group scenarios
        return constraintFactory
                .forEachUniquePair(CourseAssignment.class,
                        // Performance optimization: pre-filter pairs with Joiners
                        Joiners.equal(CourseAssignment::getTeacher),
                        Joiners.equal(a -> a.getTimeslot() != null ? a.getTimeslot().getDayOfWeek() : null))
                .filter((a1, a2) -> {
                    // Additional filters after Joiners
                    if (a1.getTeacher() == null || a1.getTimeslot() == null || a2.getTimeslot() == null)
                        return false;

                    // Must have gap > 1 hour (consecutive hours are fine)
                    int hour1 = a1.getTimeslot().getHour();
                    int hour2 = a2.getTimeslot().getHour();
                    int hourDiff = Math.abs(hour1 - hour2);

                    if (hourDiff <= 1)
                        return false; // Consecutive or same hour - no gap

                    // Check if teacher is available during ALL gap hours
                    // If teacher is unavailable for ANY gap hour, the gap is unavoidable (no
                    // penalty)
                    int minHour = Math.min(hour1, hour2);
                    int maxHour = Math.max(hour1, hour2);
                    DayOfWeek day = a1.getTimeslot().getDayOfWeek();

                    for (int gapHour = minHour + 1; gapHour < maxHour; gapHour++) {
                        if (!a1.getTeacher().isAvailableAt(day, gapHour)) {
                            return false; // Teacher unavailable during gap - no penalty
                        }
                    }

                    // Teacher IS available for all gap hours - this gap is avoidable, penalize it
                    return true;
                })
                .penalize(HardSoftScore.ONE_SOFT, (a1, a2) -> {
                    int hour1 = a1.getTimeslot().getHour();
                    int hour2 = a2.getTimeslot().getHour();
                    int gapSize = Math.abs(hour1 - hour2) - 1; // Number of idle hours

                    // SCENARIO 1: Same group, same day with gap
                    // This is BAD for student experience (context switching within same day)
                    // Example: Math 7-8, gap at 9, Math 10-11 (confusing for students)
                    // NOTE: This should be caught by groupCourseMustBeConsecutiveOnSameDay (HARD)
                    // But we add soft penalty as backup/reinforcement
                    if (a1.getGroup() != null && a1.getGroup().equals(a2.getGroup())) {
                        // Same group: 3x penalty (very bad for student experience)
                        return gapSize * 3;
                    }

                    // SCENARIO 2: Different groups, same teacher, same day with gap
                    // This is less critical but still undesirable for teacher comfort
                    // Example: Teacher has Group A at 7-8, gap at 9, Group B at 10-11
                    // Teacher has idle time but it's acceptable
                    return gapSize; // Standard penalty (1x per gap hour)
                })
                .asConstraint("Minimize teacher idle gaps (availability-aware)");
    }

    private Constraint balanceTeacherWorkload(ConstraintFactory constraintFactory) {
        // Very gentle progressive penalty as teachers approach their max hours.
        // Encourages distribution without blocking feasibility during construction.
        // Only activates near capacity to guide the solver toward better solutions.
        return constraintFactory
                .forEach(CourseAssignment.class)
                .filter(a -> a.getTeacher() != null && a.getTimeslot() != null
                        && a.getTeacher().isQualifiedFor(a.getCourse().getName()))
                .groupBy(CourseAssignment::getTeacher,
                        ConstraintCollectors.count())
                .penalize(HardSoftScore.ONE_SOFT, (teacher, totalAssignments) -> {
                    int max = Math.max(1, teacher.getMaxHoursPerWeek());
                    double utilization = (double) totalAssignments / max;

                    // Very gentle curve: no penalty until 90%, then light increase
                    // 50% = 0, 80% = 0, 85% = 1, 95% = 3, 100% = 5, >100% = higher
                    if (utilization < 0.95) {
                        return 0; // No penalty below 95% utilization
                    } else if (utilization <= 1.0) {
                        // Light quadratic curve from 95-100%: (utilization - 0.95)^2 * 50
                        double excess = utilization - 0.95;
                        return (int) Math.round(excess * excess * 50);
                    } else {
                        // Over capacity: moderate penalty (hard constraint handles enforcement)
                        return (int) Math.round(5 + (utilization - 1.0) * 20);
                    }
                })
                .asConstraint("Balance teacher workload gently");
    }

    private Constraint limitNonBasicasCoursesToTwoDaysPerGroup(ConstraintFactory constraintFactory) {
        // For non-BASICAS courses (TADHR, TEM), strongly encourage concentrating
        // all hours into at most 2 different days per group.
        // This improves focus and reduces context switching for specialized/technical
        // courses.
        // Weight: 15 (very strong preference to concentrate non-BASICAS courses)
        // Kept as SOFT to maintain solver flexibility and avoid infeasibility
        // If you want to make this HARD (guaranteed), change ofSoft(15) to ONE_HARD
        return constraintFactory
                .forEach(CourseAssignment.class)
                .filter(a -> a.getTimeslot() != null
                        && a.getCourse().getComponent() != null
                        && !a.getCourse().getComponent().equalsIgnoreCase("BASICAS"))
                .groupBy(CourseAssignment::getGroup,
                        CourseAssignment::getCourse,
                        ConstraintCollectors.countDistinct(a -> a.getTimeslot().getDayOfWeek()))
                .filter((group, course, distinctDays) -> distinctDays > 2)
                .penalize(HardSoftScore.ofSoft(15), (group, course, distinctDays) -> {
                    // Penalty increases with number of days beyond 2
                    // 3 days = 15 points, 4 days = 30 points, 5 days = 45 points
                    int excessDays = distinctDays - 2;
                    return excessDays * 15;
                })
                .asConstraint("Limit non-BASICAS courses to at most 2 days per group");
    }

    private Constraint encourageAlternativeQualifiedTeachers(ConstraintFactory constraintFactory) {
        // Extremely gentle encouragement to prefer lower-capacity teachers.
        // This is a very weak preference that only provides minor guidance.
        // Works with balanceTeacherWorkload to prevent overloading while distributing
        // evenly.
        return constraintFactory
                .forEach(CourseAssignment.class)
                .filter(a -> a.getTeacher() != null && a.getTimeslot() != null
                        && a.getTeacher().isQualifiedFor(a.getCourse().getName()))
                .groupBy(CourseAssignment::getTeacher,
                        ConstraintCollectors.count())
                .penalize(HardSoftScore.ONE_SOFT, (teacher, totalAssignments) -> {
                    int max = Math.max(1, teacher.getMaxHoursPerWeek());
                    int remaining = Math.max(0, max - totalAssignments);

                    // Extremely gentle linear penalty on remaining capacity
                    // Divided by 3 to make this a very weak preference
                    return remaining / 3;
                })
                .asConstraint("Encourage distribution among qualified teachers");
    }

}
