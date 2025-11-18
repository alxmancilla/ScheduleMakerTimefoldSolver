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
                // Hard constraints
                teacherMustBeQualified(constraintFactory),
                teacherMustBeAvailable(constraintFactory),
                noTeacherDoubleBooking(constraintFactory),
                noRoomDoubleBooking(constraintFactory),
                roomTypeMustSatisfyRequirement(constraintFactory),
                groupCannotHaveTwoCoursesAtSameTime(constraintFactory),
                // noLunchTimeAssignments(constraintFactory),
                sameTeacherForAllCourseHours(constraintFactory),
                groupNonLabCoursesInSameRoom(constraintFactory),

                // Soft constraints
                minimizeTeacherBuildingChanges(constraintFactory),
                minimizeTeacherIdleGaps(constraintFactory),
                // preferEarlierTimeslots(constraintFactory)
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

    private Constraint noLunchTimeAssignments(ConstraintFactory constraintFactory) {
        return constraintFactory
                .forEach(CourseAssignment.class)
                .filter(assignment -> assignment.getTimeslot() != null && assignment.getTimeslot().isLunchTime())
                .penalize(HardSoftScore.ONE_HARD)
                .asConstraint("No class during lunch (12-1)");
    }

    private Constraint sameTeacherForAllCourseHours(ConstraintFactory constraintFactory) {
        return constraintFactory
                .forEachUniquePair(CourseAssignment.class)
                .filter((a1, a2) -> a1.getGroup().equals(a2.getGroup())
                        && a1.getCourse().equals(a2.getCourse())
                        && a1.getTeacher() != null && a2.getTeacher() != null
                        && !a1.getTeacher().equals(a2.getTeacher()))
                .penalize(HardSoftScore.ONE_HARD)
                .asConstraint("Same teacher for all course hours");
    }

    private Constraint groupNonLabCoursesInSameRoom(ConstraintFactory constraintFactory) {
        return constraintFactory
                .forEachUniquePair(CourseAssignment.class)
                .filter((a1, a2) -> {
                    if (!a1.getGroup().equals(a2.getGroup()))
                        return false;
                    if (a1.getRoom() == null || a2.getRoom() == null)
                        return false;

                    boolean a1IsLab = "science_lab".equals(a1.getCourse().getRoomRequirement());
                    boolean a2IsLab = "science_lab".equals(a2.getCourse().getRoomRequirement());

                    // Both non-lab courses must be in same room
                    if (!a1IsLab && !a2IsLab && !a1.getRoom().equals(a2.getRoom())) {
                        return true;
                    }
                    return false;
                })
                .penalize(HardSoftScore.ONE_HARD)
                .asConstraint("Group non-lab courses must use same room");
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
                .asConstraint("Minimize teacher building changes");
    }

    private Constraint minimizeTeacherIdleGaps(ConstraintFactory constraintFactory) {
        return constraintFactory
                .forEachUniquePair(CourseAssignment.class)
                .filter((a1, a2) -> {
                    if (a1.getTeacher() == null || !a1.getTeacher().equals(a2.getTeacher()))
                        return false;
                    if (a1.getTimeslot() == null || a2.getTimeslot() == null)
                        return false;

                    // Same day with gap
                    return a1.getTimeslot().getDayOfWeek().equals(a2.getTimeslot().getDayOfWeek())
                            && Math.abs(a1.getTimeslot().getHour() - a2.getTimeslot().getHour()) > 1;
                })
                .penalize(HardSoftScore.ofSoft(1))
                .asConstraint("Minimize teacher idle gaps");
    }

    private Constraint preferEarlierTimeslots(ConstraintFactory constraintFactory) {
        return constraintFactory
                .forEach(CourseAssignment.class)
                .filter(assignment -> assignment.getTimeslot() != null)
                .penalize(HardSoftScore.ofSoft(1),
                        assignment -> assignment.getTimeslot().getHour() - 8)
                .asConstraint("Prefer earlier timeslots");
    }
}
