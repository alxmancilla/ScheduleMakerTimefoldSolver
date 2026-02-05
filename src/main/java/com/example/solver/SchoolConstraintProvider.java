package com.example.solver;

import ai.timefold.solver.core.api.score.stream.Constraint;
import ai.timefold.solver.core.api.score.stream.ConstraintFactory;
import ai.timefold.solver.core.api.score.stream.ConstraintProvider;
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
                // Hard constraints (infeasibility: must be satisfied)
                teacherMustBeQualified(constraintFactory),
                teacherMustBeAvailable(constraintFactory),
                noTeacherDoubleBooking(constraintFactory),
                sameTeacherForAllCourseHours(constraintFactory),
                roomTypeMustSatisfyRequirement(constraintFactory),
                noRoomDoubleBooking(constraintFactory),
                groupCannotHaveTwoCoursesAtSameTime(constraintFactory),
                groupCourseMustBeConsecutiveOnSameDay(constraintFactory),
                sixthSemesterGroupsMustFinishBefore2pm(constraintFactory),
                // noCoursesOnMondayMorningForADHR(constraintFactory),
                // groupCoursesInSameRoomByType(constraintFactory),

                // Soft constraints (quality optimization: weighted preferences)
                // Prefer group's pre-assigned room is a soft preference (weight 3)
                // groupPreferredRoomConstraint(constraintFactory),
                minimizeTeacherIdleGaps(constraintFactory), // weight 1 (comfort: efficiency)
                teacherMaxHoursPerWeek(constraintFactory),
                // preferUsingTeachersWithMoreAvailability(constraintFactory),
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
        // When a group takes multiple hours of the same course on the SAME day,
        // those hours must be consecutive.
        // However, course hours can be spread across different days (no requirement for
        // same day).
        // This constraint only applies when both assignments happen to be on the same
        // day.
        return constraintFactory
                .forEachUniquePair(CourseAssignment.class)
                .filter((a1, a2) -> {
                    // Must be same group and same course
                    if (!a1.getGroup().equals(a2.getGroup()) || !a1.getCourse().equals(a2.getCourse())) {
                        return false;
                    }

                    // Different sequence indices (different hours of the same course)
                    if (a1.getSequenceIndex() == a2.getSequenceIndex()) {
                        return false;
                    }

                    if (a1.getTimeslot() == null || a2.getTimeslot() == null) {
                        return false;
                    }

                    // Only enforce consecutiveness if both assignments are on the same day
                    if (!a1.getTimeslot().getDayOfWeek().equals(a2.getTimeslot().getDayOfWeek())) {
                        return false; // Different days: no violation
                    }

                    // Both on same day: validate consecutive hours based on sequence indices
                    int hour1 = a1.getTimeslot().getHour();
                    int hour2 = a2.getTimeslot().getHour();
                    int seqDiff = a2.getSequenceIndex() - a1.getSequenceIndex();
                    int hourDiff = hour2 - hour1;

                    // Hours should match the sequence difference
                    // If seqDiff is +1, then hour should also be +1 (consecutive)
                    // If seqDiff is -1, then hour should be -1 (consecutive in reverse)
                    if (hourDiff != seqDiff) {
                        return true; // Violation: not consecutive on the same day
                    }

                    return false;
                })
                .penalize(HardSoftScore.ONE_HARD)
                .asConstraint("Group course hours must be consecutive when on the same day");
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

    private Constraint noCoursesOnMondayMorningForADHR(ConstraintFactory factory) {
        return factory.forEach(CourseAssignment.class)
                // Timeslot must exist
                .filter(assignment -> assignment.getTimeslot() != null)

                // ✅ Scope: ONLY 6th semester groups
                .filter(assignment -> assignment.getGroup() != null && (assignment.getGroup().getId().equals("4AARH") ||
                        assignment.getGroup().getId().equals("6AARH")))

                // Monday 09:00–13:00 overlap
                .filter(assignment -> assignment.getTimeslot().getDayOfWeek() == DayOfWeek.MONDAY &&
                        overlapsForbiddenWindow(
                                LocalTime.of(assignment.getTimeslot().getHour(), 0),
                                LocalTime.of(assignment.getTimeslot().getHour() + 1, 0)))

                .penalize(HardSoftScore.ONE_HARD)
                .asConstraint("6th semester groups cannot have courses on Monday 9am–1pm");
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
                        // Same group split across hours: penalize 2x
                        // This keeps course hours consecutive and improves teaching quality
                        return gap * 2;
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
