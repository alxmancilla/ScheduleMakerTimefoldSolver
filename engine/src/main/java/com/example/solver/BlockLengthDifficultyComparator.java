package com.example.solver;

import java.util.Comparator;

import com.example.domain.CourseBlockAssignment;
import com.example.domain.Teacher;

/**
 * Comparator for sorting CourseBlockAssignment entities by difficulty.
 *
 * Difficulty is determined by multiple criteria in priority order:
 *
 * 1. TEACHER AVAILABILITY (ASCENDING): Teachers with fewer available hours are
 * scheduled first
 * - This ensures constrained teachers (e.g., MARIO with 20h, RODRIGO with 20h)
 * get priority
 * - Reduces teacher double-booking violations by scheduling constrained
 * teachers early
 * - Teachers with no assigned teacher are scheduled last (treated as infinite
 * availability)
 *
 * 2. BLOCK LENGTH (DESCENDING): Longer blocks are scheduled first within same
 * teacher availability
 * - Follows "largest first" bin packing heuristic
 * - Reduces fragmentation by placing large blocks before small blocks
 * - Leaves smaller, more flexible blocks to fill gaps
 *
 * 3. DETERMINISTIC ORDERING: Group ID → Course ID → Assignment ID
 * - Ensures reproducible results across solver runs
 * - Prevents non-deterministic behavior
 *
 * Example scheduling order:
 * - YAMMEL ANILU (3h available) - all blocks scheduled first
 * - MARCO ANTONIO (8h available) - all blocks scheduled second
 * - FRANCISCO NARCES (13h available) - all blocks scheduled third
 * - MARIO VERDIGUEL (20h available) - all blocks scheduled fourth
 * - RODRIGO (20h available) - all blocks scheduled fifth
 * - ... (teachers with 40h available scheduled last)
 *
 * Within each teacher, longer blocks (4h, 3h) are scheduled before shorter
 * blocks (2h, 1h).
 */
public class BlockLengthDifficultyComparator implements Comparator<CourseBlockAssignment> {

    @Override
    public int compare(CourseBlockAssignment a1, CourseBlockAssignment a2) {
        // Primary: Sort by teacher availability ASCENDING (fewer hours first)
        // Teachers with low availability are harder to schedule → scheduled first
        int availCompare = compareTeacherAvailability(a1, a2);
        if (availCompare != 0) {
            return availCompare;
        }

        // Secondary: Sort by block length DESCENDING (longer blocks first)
        // Within same teacher availability, longer blocks are harder to place
        int lengthCompare = Integer.compare(a2.getBlockLength(), a1.getBlockLength());
        if (lengthCompare != 0) {
            return lengthCompare;
        }

        // Tertiary: Sort by group ID (deterministic)
        if (a1.getGroup() != null && a2.getGroup() != null) {
            int groupCompare = a1.getGroup().getId().compareTo(a2.getGroup().getId());
            if (groupCompare != 0) {
                return groupCompare;
            }
        }

        // Quaternary: Sort by course ID (deterministic)
        if (a1.getCourse() != null && a2.getCourse() != null) {
            int courseCompare = a1.getCourse().getId().compareTo(a2.getCourse().getId());
            if (courseCompare != 0) {
                return courseCompare;
            }
        }

        // Final: Sort by assignment ID (fully deterministic)
        return a1.getId().compareTo(a2.getId());
    }

    /**
     * Compare two assignments by their teacher's total available hours.
     *
     * @param a1 first assignment
     * @param a2 second assignment
     * @return negative if a1's teacher has fewer available hours (scheduled first),
     *         positive if a2's teacher has fewer available hours (scheduled first),
     *         zero if both have same availability
     */
    private int compareTeacherAvailability(CourseBlockAssignment a1, CourseBlockAssignment a2) {
        Teacher t1 = a1.getTeacher();
        Teacher t2 = a2.getTeacher();

        // If either has no teacher, treat as infinite availability (schedule last)
        if (t1 == null && t2 == null) {
            return 0; // Both have no teacher, equal priority
        }
        if (t1 == null) {
            return 1; // a1 has no teacher → schedule later
        }
        if (t2 == null) {
            return -1; // a2 has no teacher → schedule later
        }

        // Count total available hours for each teacher
        int avail1 = t1.getTotalAvailableHours();
        int avail2 = t2.getTotalAvailableHours();

        // ASCENDING order: fewer hours = scheduled first
        // Example: MARIO (20h) scheduled before LETICIA (40h)
        return Integer.compare(avail1, avail2);
    }
}
