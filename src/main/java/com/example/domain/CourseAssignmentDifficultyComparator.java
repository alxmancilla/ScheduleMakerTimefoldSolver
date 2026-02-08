package com.example.domain;

import java.util.Comparator;

public class CourseAssignmentDifficultyComparator
        implements Comparator<CourseAssignment> {

    /**
     * @Override
     *           public int compare(CourseAssignment a, CourseAssignment b) {
     * 
     *           int byDuration = Integer.compare(
     *           b.getCourse().getRequiredHoursPerWeek(),
     *           a.getCourse().getRequiredHoursPerWeek());
     *           if (byDuration != 0)
     *           return byDuration;
     * 
     *           return Integer.compare(
     *           (int) b.getGroup().getName().charAt(0),
     *           (int) a.getGroup().getName().charAt(0));
     *           }
     */
    @Override
    public int compare(CourseAssignment a, CourseAssignment b) {

        // 1. Primary: Component type (non-BASICAS = harder due to 3x penalty)
        String compA = a.getCourse().getComponent();
        String compB = b.getCourse().getComponent();
        boolean aIsBasicas = "BASICAS".equalsIgnoreCase(compA);
        boolean bIsBasicas = "BASICAS".equalsIgnoreCase(compB);
        if (aIsBasicas != bIsBasicas) {
            return aIsBasicas ? 1 : -1; // Non-BASICAS first (CORRECTED)
        }

        // 2. Secondary: Required hours (descending - more hours = harder)
        int byDuration = Integer.compare(
                b.getCourse().getRequiredHoursPerWeek(),
                a.getCourse().getRequiredHoursPerWeek());
        if (byDuration != 0)
            return byDuration;

        // 3. Ternary: Semester - first character of group name (for consistency)
        return Integer.compare(
                (int) b.getGroup().getName().charAt(0),
                (int) a.getGroup().getName().charAt(0));
    }

}