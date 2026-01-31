package com.example.solver;

import ai.timefold.solver.core.api.score.stream.Constraint;
import ai.timefold.solver.core.api.score.stream.ConstraintFactory;
import ai.timefold.solver.core.api.score.stream.ConstraintProvider;
import ai.timefold.solver.core.api.score.buildin.hardsoft.HardSoftScore;
import ai.timefold.solver.core.api.score.stream.ConstraintCollectors;
import com.example.domain.CourseAssignment;

public class SchoolConstraintProvider implements ConstraintProvider {

    @Override
    public Constraint[] defineConstraints(ConstraintFactory constraintFactory) {
        return new Constraint[] {
                // Hard constraints (infeasibility: must be satisfied)
                teacherMustBeQualified(constraintFactory),
                teacherMustBeAvailable(constraintFactory),
                minimizeTeacherIdleGaps(constraintFactory), // weight 1 (comfort: efficiency)
                noTeacherDoubleBooking(constraintFactory),
                noRoomDoubleBooking(constraintFactory),
                roomTypeMustSatisfyRequirement(constraintFactory),
                groupCannotHaveTwoCoursesAtSameTime(constraintFactory),
                sameTeacherForAllCourseHours(constraintFactory),
                sixthSemesterGroupsMustFinishBefore2pm(constraintFactory),
                groupCourseMustBeConsecutiveOnSameDay(constraintFactory),
                // groupCoursesInSameRoomByType(constraintFactory),

                // Soft constraints (quality optimization: weighted preferences)
                // Prefer group's pre-assigned room is a soft preference (weight 3)
                // groupPreferredRoomConstraint(constraintFactory),
                teacherMaxHoursPerWeek(constraintFactory),
                // minimizeTeacherBuildingChanges(constraintFactory), // weight 1 (comfort:
                // minimize travel)
                balanceTeacherWorkload(constraintFactory),
                // encourageAlternativeQualifiedTeachers(constraintFactory),
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
        return constraintFactory
                .forEachUniquePair(CourseAssignment.class)
                .filter((a1, a2) -> a1.getTeacher() != null && a1.getTeacher().equals(a2.getTeacher())
                        && a1.getTimeslot() != null && a1.getTimeslot().equals(a2.getTimeslot()))
                .penalize(HardSoftScore.ONE_HARD)
                .asConstraint("No teacher double-booking");
    }

    private Constraint noRoomDoubleBooking(ConstraintFactory constraintFactory) {
        return constraintFactory
                .forEachUniquePair(CourseAssignment.class)
                .filter((a1, a2) -> a1.getRoom() != null && a1.getRoom().equals(a2.getRoom())
                        && a1.getTimeslot() != null && a1.getTimeslot().equals(a2.getTimeslot()))
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
        return constraintFactory
                .forEachUniquePair(CourseAssignment.class)
                .filter((a1, a2) -> a1.getGroup().equals(a2.getGroup())
                        && a1.getTimeslot() != null && a1.getTimeslot().equals(a2.getTimeslot()))
                .penalize(HardSoftScore.ONE_HARD)
                .asConstraint("Group cannot have two courses at same time");
    }

    private Constraint sameTeacherForAllCourseHours(ConstraintFactory constraintFactory) {
        return constraintFactory
                .forEachUniquePair(CourseAssignment.class)
                .filter((a1, a2) -> a1.getGroup().equals(a2.getGroup())
                        && a1.getCourse().equals(a2.getCourse())
                        && a1.getTeacher() != null && a2.getTeacher() != null
                        && !a1.getTeacher().equals(a2.getTeacher()))
                .penalize(HardSoftScore.ONE_HARD)
                .asConstraint("Same teacher for all course hours (hard constraint)");
    }

    private Constraint sixthSemesterGroupsMustFinishBefore2pm(ConstraintFactory constraintFactory) {
        return constraintFactory
                .forEach(CourseAssignment.class)
                .filter(assignment -> assignment.getCourse() != null
                        && "VI".equals(assignment.getCourse().getSemester())
                        && assignment.getTimeslot() != null
                        && assignment.getTimeslot().getHour() >= 14) // 14:00 is 2pm
                .penalize(HardSoftScore.ONE_HARD)
                .asConstraint("6th semester groups must finish before 2pm");
    }

    private Constraint groupCourseMustBeConsecutiveOnSameDay(ConstraintFactory constraintFactory) {
        // Each group taking a course should have all its hours consecutive on the same day.
        // This constraint penalizes pairs of assignments where:
        // 1. Same group takes the same course
        // 2. They are on the same day but NOT consecutive (gap exists)
        // 3. OR they are on different days (violating the "same day" requirement)
        // 4. OR there would be 3+ consecutive hours (max 2 hours allowed)
        return constraintFactory
                .forEachUniquePair(CourseAssignment.class)
                .filter((a1, a2) -> {
                    // Must be same group and same course
                    if (!a1.getGroup().equals(a2.getGroup()) || !a1.getCourse().equals(a2.getCourse())) {
                        return false;
                    }
                    
                    if (a1.getTimeslot() == null || a2.getTimeslot() == null) {
                        return false;
                    }
                    
                    // If on different days, they must both be scheduled on the same day
                    if (!a1.getTimeslot().getDayOfWeek().equals(a2.getTimeslot().getDayOfWeek())) {
                        return true; // Violation: same course for same group should be on same day
                    }
                    
                    // Both on same day: check if consecutive
                    int hour1 = a1.getTimeslot().getHour();
                    int hour2 = a2.getTimeslot().getHour();
                    int hourDiff = Math.abs(hour1 - hour2);
                    
                    // Not consecutive (gap > 1 hour on same day) = violation
                    if (hourDiff > 1) {
                        return true;
                    }
                    
                    return false;
                })
                .penalize(HardSoftScore.ONE_HARD)
                .asConstraint("Group course hours must be consecutive on the same day");
    }

    private Constraint groupCoursesInSameRoomByType(ConstraintFactory constraintFactory) {
        // All courses with the same room requirement for a group must use the same
        // room.
        // This consolidates standard and lab room consistency into a single constraint
        // and automatically handles any new room types (e.g., "auditorium", "gym").
        return constraintFactory
                .forEachUniquePair(CourseAssignment.class)
                .filter((a1, a2) -> {
                    // Basic null/group guards
                    if (a1.getGroup() == null || !a1.getGroup().equals(a2.getGroup()))
                        return false;
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
        return constraintFactory
                .forEachUniquePair(CourseAssignment.class)
                .filter((a1, a2) -> {
                    if (a1.getTeacher() == null || !a1.getTeacher().equals(a2.getTeacher()))
                        return false;
                    if (a1.getTimeslot() == null || a2.getTimeslot() == null)
                        return false;
                    if (a1.getRoom() == null || a2.getRoom() == null)
                        return false;

                    // Same day, different buildings
                    return a1.getTimeslot().getDayOfWeek().equals(a2.getTimeslot().getDayOfWeek())
                            && !a1.getRoom().getBuilding().equals(a2.getRoom().getBuilding());
                })
                .penalize(HardSoftScore.ofSoft(1))
                .asConstraint("Minimize teacher building changes (comfort)");
    }

    private Constraint minimizeTeacherIdleGaps(ConstraintFactory constraintFactory) {
        return constraintFactory
                .forEachUniquePair(CourseAssignment.class)
                .filter((a1, a2) -> {
                    if (a1.getTeacher() == null || !a1.getTeacher().equals(a2.getTeacher()))
                        return false;
                    if (a1.getTimeslot() == null || a2.getTimeslot() == null)
                        return false;

                    // Same day with gap > 1 hour
                    return a1.getTimeslot().getDayOfWeek().equals(a2.getTimeslot().getDayOfWeek())
                            && Math.abs(a1.getTimeslot().getHour() - a2.getTimeslot().getHour()) > 1;
                })
                .penalize(HardSoftScore.ONE_SOFT, (a1, a2) -> {
                    int gap = Math.abs(a1.getTimeslot().getHour() - a2.getTimeslot().getHour()) - 1;

                    // ENHANCED: Check if teaching SAME group = higher penalty
                    // Same group and same day with gap should be more strictly penalized
                    if (a1.getGroup() != null && a1.getGroup().equals(a2.getGroup())
                            && a1.getTimeslot().getDayOfWeek().equals(a2.getTimeslot().getDayOfWeek())) {
                        // Same group split across hours: penalize 4x
                        // This keeps course hours consecutive and improves teaching quality
                        return gap * 3;
                    }

                    // Different courses: standard penalty (less strict)
                    return gap;
                })
                .asConstraint("Minimize teacher idle gaps (efficiency)");
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
                    if (utilization < 0.85) {
                        return 0; // No penalty below 85% utilization
                    } else if (utilization <= 1.0) {
                        // Light quadratic curve from 85-100%: (utilization - 0.85)^2 * 50
                        double excess = utilization - 0.85;
                        return (int) Math.round(excess * excess * 50);
                    } else {
                        // Over capacity: moderate penalty (hard constraint handles enforcement)
                        return (int) Math.round(5 + (utilization - 1.0) * 20);
                    }
                })
                .asConstraint("Balance teacher workload gently");
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
