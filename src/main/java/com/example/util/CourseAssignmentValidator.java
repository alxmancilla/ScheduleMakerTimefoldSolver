package com.example.util;

import java.util.List;

import com.example.domain.Course;
import com.example.domain.CourseAssignment;
import com.example.domain.Teacher;

/**
 * Utility class for validating CourseAssignment constraints before creation.
 * This validator checks business rules such as teacher availability and hour
 * limits
 * prior to instantiating CourseAssignment objects.
 */
public class CourseAssignmentValidator {

    /**
     * Checks if a teacher can be assigned to a course without violating
     * maxHoursPerWeek.
     * 
     * @param teacher             the teacher to check
     * @param course              the course to assign
     * @param existingAssignments list of already-created assignments
     * @return true if the assignment is feasible, false otherwise
     */
    public static boolean canAssignCourse(Teacher teacher, Course course,
            List<CourseAssignment> existingAssignments) {
        if (teacher == null || course == null) {
            return false;
        }

        long hoursAssigned = existingAssignments.stream()
                .filter(a -> teacher.equals(a.getTeacher()))
                .filter(a -> a.getSequenceIndex() >= 0
                        && a.getSequenceIndex() < a.getCourse().getRequiredHoursPerWeek())
                .count();

        return (hoursAssigned + course.getRequiredHoursPerWeek()) <= teacher.getMaxHoursPerWeek();
    }

    /**
     * Returns the number of remaining hours available for a teacher.
     * 
     * @param teacher             the teacher to check
     * @param existingAssignments list of already-created assignments
     * @return the number of remaining hours (0 if teacher is at capacity)
     */
    public static int getTeacherRemainingHours(Teacher teacher,
            List<CourseAssignment> existingAssignments) {
        if (teacher == null) {
            return 0;
        }

        long hoursUsed = existingAssignments.stream()
                .filter(a -> teacher.equals(a.getTeacher()))
                .filter(a -> a.getSequenceIndex() >= 0
                        && a.getSequenceIndex() < a.getCourse().getRequiredHoursPerWeek())
                .count();

        return Math.max(0, teacher.getMaxHoursPerWeek() - (int) hoursUsed);
    }

    /**
     * Gets the current hours assigned to a teacher.
     * 
     * @param teacher             the teacher to check
     * @param existingAssignments list of already-created assignments
     * @return the number of hours currently assigned
     */
    public static int getTeacherAssignedHours(Teacher teacher,
            List<CourseAssignment> existingAssignments) {
        if (teacher == null) {
            return 0;
        }

        return (int) existingAssignments.stream()
                .filter(a -> teacher.equals(a.getTeacher()))
                .filter(a -> a.getSequenceIndex() >= 0
                        && a.getSequenceIndex() < a.getCourse().getRequiredHoursPerWeek())
                .count();
    }
}
