package com.example.solver;

import ai.timefold.solver.core.api.score.stream.Constraint;
import ai.timefold.solver.core.api.score.stream.ConstraintFactory;
import ai.timefold.solver.core.api.score.stream.ConstraintProvider;
import ai.timefold.solver.core.api.score.buildin.hardsoft.HardSoftScore;
import com.example.domain.CourseAssignment;

public class SchoolConstraintProvider implements ConstraintProvider {

    @Override
    public Constraint[] defineConstraints(ConstraintFactory constraintFactory) {
        return new Constraint[] {
                // Hard constraints (infeasibility: must be satisfied)
                teacherMustBeQualified(constraintFactory),
                teacherMustBeAvailable(constraintFactory),
                noTeacherDoubleBooking(constraintFactory),
                noRoomDoubleBooking(constraintFactory),
                roomTypeMustSatisfyRequirement(constraintFactory),
                groupCannotHaveTwoCoursesAtSameTime(constraintFactory),
                groupPreferredRoomConstraint(constraintFactory),
                // groupNonLabCoursesInSameRoom(constraintFactory),

                // Soft constraints (quality optimization: weighted preferences)
                sameTeacherForAllCourseHours(constraintFactory), // weight 3 (high priority: continuity)
                minimizeTeacherIdleGaps(constraintFactory), // weight 1 (comfort: efficiency)
                minimizeTeacherBuildingChanges(constraintFactory), // weight 1 (comfort: minimize travel)
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
                .penalize(HardSoftScore.ofSoft(3))
                .asConstraint("Prefer same teacher for all course hours (continuity)");
    }

    private Constraint groupNonLabCoursesInSameRoom(ConstraintFactory constraintFactory) {
        return constraintFactory
                .forEachUniquePair(CourseAssignment.class)
                .filter((a1, a2) -> {
                    // Basic null/group guards
                    if (a1.getGroup() == null || a2.getGroup() == null)
                        return false;
                    if (!a1.getGroup().equals(a2.getGroup()))
                        return false;
                    if (a1.getRoom() == null || a2.getRoom() == null)
                        return false;

                    // Treat roomRequirement defensively (case/whitespace tolerant)
                    String req1 = a1.getCourse() == null ? null : a1.getCourse().getRoomRequirement();
                    String req2 = a2.getCourse() == null ? null : a2.getCourse().getRoomRequirement();
                    boolean a1IsLab = req1 != null && "lab".equalsIgnoreCase(req1.trim());
                    boolean a2IsLab = req2 != null && "lab".equalsIgnoreCase(req2.trim());

                    // Allow lab exception: if either course is a lab, do not enforce same-room rule
                    if (a1IsLab || a2IsLab) {
                        return false;
                    }

                    // Both non-lab courses for the same group: enforce same room (hard)
                    return !a1.getRoom().equals(a2.getRoom());
                })
                .penalize(HardSoftScore.ONE_HARD)
                .asConstraint("Group non-lab courses must use same room (except labs)");
    }

    private Constraint groupPreferredRoomConstraint(ConstraintFactory constraintFactory) {
        return constraintFactory
                .forEach(CourseAssignment.class)
                .filter(assignment -> assignment.getGroup() != null
                        && assignment.getGroup().getPreferredRoom() != null
                        && !"lab".equalsIgnoreCase(assignment.getGroup().getPreferredRoom().getType())
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
                .penalize(HardSoftScore.ofSoft(1))
                .asConstraint("Minimize teacher idle gaps (efficiency)");
    }

}
