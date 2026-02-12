package com.example.solver;

import java.util.Comparator;

import com.example.domain.CourseBlockAssignment;

/**
 * Comparator for sorting CourseBlockAssignment entities by difficulty.
 *
 * Difficulty is determined by block length in DECREASING order:
 * - Longer blocks (4h, 3h) are harder to place → scheduled first
 * - Shorter blocks (1h, 2h) are easier to place → scheduled last
 *
 * This follows the "largest first" bin packing heuristic, which typically
 * produces better solutions by:
 * 1. Placing the most constrained items first
 * 2. Leaving smaller, more flexible items to fill gaps
 * 3. Reducing fragmentation in the schedule
 *
 * Secondary sorting criteria (for blocks of same length):
 * 1. Group ID (for deterministic ordering)
 * 2. Course ID (for deterministic ordering)
 * 3. Assignment ID (for fully deterministic ordering)
 */
public class BlockLengthDifficultyComparator implements Comparator<CourseBlockAssignment> {

    @Override
    public int compare(CourseBlockAssignment a1, CourseBlockAssignment a2) {
        // Primary: Sort by block length DESCENDING (longer blocks first)
        int lengthCompare = Integer.compare(a2.getBlockLength(), a1.getBlockLength());
        if (lengthCompare != 0) {
            return lengthCompare;
        }

        // Secondary: Sort by group ID (deterministic)
        if (a1.getGroup() != null && a2.getGroup() != null) {
            int groupCompare = a1.getGroup().getId().compareTo(a2.getGroup().getId());
            if (groupCompare != 0) {
                return groupCompare;
            }
        }

        // Tertiary: Sort by course ID (deterministic)
        if (a1.getCourse() != null && a2.getCourse() != null) {
            int courseCompare = a1.getCourse().getId().compareTo(a2.getCourse().getId());
            if (courseCompare != 0) {
                return courseCompare;
            }
        }

        // Final: Sort by assignment ID (fully deterministic)
        return a1.getId().compareTo(a2.getId());
    }
}
