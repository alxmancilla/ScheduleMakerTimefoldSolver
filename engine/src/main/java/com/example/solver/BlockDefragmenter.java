package com.example.solver;

import ai.timefold.solver.core.api.score.director.ScoreDirector;
import ai.timefold.solver.core.api.solver.phase.PhaseCommand;
import com.example.domain.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.DayOfWeek;
import java.util.*;
import java.util.function.BooleanSupplier;
import java.util.stream.Collectors;

/**
 * Custom phase that runs AFTER Construction Heuristic to detect and fix
 * schedule fragmentation.
 * 
 * Fragmentation occurs when a day's schedule is filled with small blocks (e.g.,
 * 1-hour BASICAS)
 * that prevent larger blocks (e.g., 3-4 hour non-BASICAS) from being assigned
 * to contiguous timeslots.
 * 
 * This phase:
 * 1. Detects blocks with mismatched lengths (violations)
 * 2. Finds suitable contiguous timeslots for these blocks
 * 3. Moves conflicting smaller blocks to create space
 * 4. Assigns the larger block to the cleared contiguous slot
 */
public class BlockDefragmenter implements PhaseCommand<SchoolSchedule> {

    private static final Logger logger = LoggerFactory.getLogger(BlockDefragmenter.class);

    @Override
    public void changeWorkingSolution(ScoreDirector<SchoolSchedule> scoreDirector, BooleanSupplier isPhaseTerminated) {
        SchoolSchedule schedule = scoreDirector.getWorkingSolution();

        logger.info("=== Starting Defragmentation Phase ===");

        // Step 1: Find all violations (blocks with mismatched lengths)
        List<CourseBlockAssignment> violations = findViolations(schedule);

        logger.info("Found {} violations to fix", violations.size());

        if (violations.isEmpty()) {
            logger.info("No violations found. Defragmentation phase complete.");
            return;
        }

        // Step 2: For each violation, try to fix it
        int fixedCount = 0;
        for (CourseBlockAssignment violation : violations) {
            if (isPhaseTerminated.getAsBoolean()) {
                logger.info("Phase terminated early. Fixed {}/{} violations.", fixedCount, violations.size());
                break;
            }

            logger.info("Attempting to fix: {} {} {} (block_length={}, timeslot_length={})",
                    violation.getGroup().getId(),
                    violation.getCourse().getComponent(),
                    violation.getCourse().getName(),
                    violation.getBlockLength(),
                    violation.getTimeslot() != null ? violation.getTimeslot().getLengthHours() : "null");

            // Step 3: Find a suitable contiguous timeslot
            BlockTimeslot suitableSlot = findSuitableSlot(schedule, violation, scoreDirector);

            if (suitableSlot != null) {
                logger.info("Found suitable slot: {} {}-{} ({}h)",
                        getDayName(suitableSlot.getDayOfWeek()),
                        suitableSlot.getStartHour(),
                        suitableSlot.getStartHour() + suitableSlot.getLengthHours(),
                        suitableSlot.getLengthHours());

                // Step 4: Clear the space by moving conflicting blocks
                boolean cleared = clearSpace(schedule, violation, suitableSlot, scoreDirector);

                if (cleared) {
                    // Step 5: Assign the violation to the cleared slot
                    scoreDirector.beforeVariableChanged(violation, "timeslot");
                    violation.setTimeslot(suitableSlot);
                    scoreDirector.afterVariableChanged(violation, "timeslot");
                    scoreDirector.triggerVariableListeners();

                    fixedCount++;
                    logger.info("✓ Successfully fixed violation {}/{}", fixedCount, violations.size());
                } else {
                    logger.warn("✗ Could not clear space for suitable slot");
                }
            } else {
                logger.warn("✗ No suitable slot found for this violation");
            }
        }

        logger.info("=== Defragmentation Phase Complete: Fixed {}/{} violations ===", fixedCount, violations.size());
    }

    /**
     * Find all blocks with mismatched lengths, sorted by block length (largest
     * first).
     */
    private List<CourseBlockAssignment> findViolations(SchoolSchedule schedule) {
        return schedule.getCourseBlockAssignments().stream()
                .filter(a -> !a.isPinned()) // Skip pinned assignments
                .filter(a -> a.getTimeslot() != null) // Must have a timeslot
                .filter(a -> a.getBlockLength() != a.getTimeslot().getLengthHours()) // Mismatch
                .sorted(Comparator.comparingInt(CourseBlockAssignment::getBlockLength).reversed()) // Largest first
                .collect(Collectors.toList());
    }

    /**
     * Find a suitable contiguous timeslot for the violation.
     * A slot is suitable if:
     * 1. It has the correct length
     * 2. Teacher is available
     * 3. Conflicts can be resolved
     */
    private BlockTimeslot findSuitableSlot(SchoolSchedule schedule,
            CourseBlockAssignment violation,
            ScoreDirector<SchoolSchedule> scoreDirector) {
        int requiredLength = violation.getBlockLength();

        // Get all timeslots with matching length
        List<BlockTimeslot> candidates = schedule.getBlockTimeslots().stream()
                .filter(ts -> ts.getLengthHours() == requiredLength)
                .collect(Collectors.toList());

        logger.debug("Checking {} candidate timeslots with length {}", candidates.size(), requiredLength);

        // For each candidate, check if it's feasible
        for (BlockTimeslot candidate : candidates) {
            if (isFeasible(schedule, violation, candidate, scoreDirector)) {
                return candidate;
            }
        }

        return null; // No suitable slot found
    }

    /**
     * Check if a timeslot is feasible for the violation.
     */
    private boolean isFeasible(SchoolSchedule schedule,
            CourseBlockAssignment violation,
            BlockTimeslot candidate,
            ScoreDirector<SchoolSchedule> scoreDirector) {
        Teacher teacher = violation.getTeacher();

        // Check 1: Teacher availability
        if (teacher != null && !isTeacherAvailable(teacher, candidate)) {
            logger.debug("  ✗ Teacher {} not available at {}", teacher.getId(), formatTimeslot(candidate));
            return false;
        }

        // Check 2: Can we resolve all conflicts?
        List<CourseBlockAssignment> conflicts = findAllConflicts(schedule, violation, candidate);

        if (!canResolveConflicts(schedule, conflicts, scoreDirector)) {
            logger.debug("  ✗ Cannot resolve {} conflicts at {}", conflicts.size(), formatTimeslot(candidate));
            return false;
        }

        logger.debug("  ✓ Feasible: {} conflicts can be resolved", conflicts.size());
        return true;
    }

    /**
     * Check if teacher is available for all hours in the timeslot.
     */
    private boolean isTeacherAvailable(Teacher teacher, BlockTimeslot timeslot) {
        DayOfWeek day = timeslot.getDayOfWeek();
        int startHour = timeslot.getStartHour();
        int endHour = startHour + timeslot.getLengthHours();

        for (int hour = startHour; hour < endHour; hour++) {
            if (!teacher.isAvailableAt(day, hour)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Find all conflicts (group, room, teacher) for assigning violation to
     * candidate timeslot.
     */
    private List<CourseBlockAssignment> findAllConflicts(SchoolSchedule schedule,
            CourseBlockAssignment violation,
            BlockTimeslot candidate) {
        Set<CourseBlockAssignment> conflicts = new HashSet<>();

        // Group conflicts
        conflicts.addAll(findGroupConflicts(schedule, violation.getGroup(), candidate));

        // Room conflicts
        if (violation.getRoom() != null) {
            conflicts.addAll(findRoomConflicts(schedule, violation.getRoom(), candidate));
        }

        // Teacher conflicts
        if (violation.getTeacher() != null) {
            conflicts.addAll(findTeacherConflicts(schedule, violation.getTeacher(), candidate));
        }

        // Remove the violation itself if it's in the list
        conflicts.remove(violation);

        return new ArrayList<>(conflicts);
    }

    /**
     * Find all assignments for a group that overlap with the timeslot.
     */
    private List<CourseBlockAssignment> findGroupConflicts(SchoolSchedule schedule,
            Group group,
            BlockTimeslot timeslot) {
        return schedule.getCourseBlockAssignments().stream()
                .filter(a -> a.getGroup().equals(group))
                .filter(a -> a.getTimeslot() != null)
                .filter(a -> blocksOverlap(a.getTimeslot(), timeslot))
                .collect(Collectors.toList());
    }

    /**
     * Find all assignments for a room that overlap with the timeslot.
     */
    private List<CourseBlockAssignment> findRoomConflicts(SchoolSchedule schedule,
            Room room,
            BlockTimeslot timeslot) {
        return schedule.getCourseBlockAssignments().stream()
                .filter(a -> room.equals(a.getRoom()))
                .filter(a -> a.getTimeslot() != null)
                .filter(a -> blocksOverlap(a.getTimeslot(), timeslot))
                .collect(Collectors.toList());
    }

    /**
     * Find all assignments for a teacher that overlap with the timeslot.
     */
    private List<CourseBlockAssignment> findTeacherConflicts(SchoolSchedule schedule,
            Teacher teacher,
            BlockTimeslot timeslot) {
        return schedule.getCourseBlockAssignments().stream()
                .filter(a -> teacher.equals(a.getTeacher()))
                .filter(a -> a.getTimeslot() != null)
                .filter(a -> blocksOverlap(a.getTimeslot(), timeslot))
                .collect(Collectors.toList());
    }

    /**
     * Check if two timeslots overlap.
     */
    private boolean blocksOverlap(BlockTimeslot ts1, BlockTimeslot ts2) {
        if (!ts1.getDayOfWeek().equals(ts2.getDayOfWeek())) {
            return false;
        }

        int start1 = ts1.getStartHour();
        int end1 = start1 + ts1.getLengthHours();
        int start2 = ts2.getStartHour();
        int end2 = start2 + ts2.getLengthHours();

        return start1 < end2 && start2 < end1;
    }

    /**
     * Check if all conflicts can be resolved by moving them to alternative slots.
     */
    private boolean canResolveConflicts(SchoolSchedule schedule,
            List<CourseBlockAssignment> conflicts,
            ScoreDirector<SchoolSchedule> scoreDirector) {
        // For each conflict, check if we can find an alternative slot
        for (CourseBlockAssignment conflict : conflicts) {
            // Skip pinned assignments - we can't move them
            if (conflict.isPinned()) {
                return false;
            }

            BlockTimeslot alternativeSlot = findAlternativeSlot(schedule, conflict, scoreDirector);
            if (alternativeSlot == null) {
                return false; // Can't move this conflict
            }
        }

        return true; // All conflicts can be moved
    }

    /**
     * Clear space by moving all conflicting blocks to alternative slots.
     */
    private boolean clearSpace(SchoolSchedule schedule,
            CourseBlockAssignment violation,
            BlockTimeslot targetSlot,
            ScoreDirector<SchoolSchedule> scoreDirector) {
        List<CourseBlockAssignment> conflicts = findAllConflicts(schedule, violation, targetSlot);

        logger.debug("  Clearing {} conflicts", conflicts.size());

        // Move each conflicting block to an alternative slot
        for (CourseBlockAssignment conflict : conflicts) {
            BlockTimeslot alternativeSlot = findAlternativeSlot(schedule, conflict, scoreDirector);

            if (alternativeSlot == null) {
                logger.warn("  ✗ Could not find alternative slot for conflict: {} {}",
                        conflict.getGroup().getId(), conflict.getCourse().getName());
                return false;
            }

            // Move the conflict
            logger.debug("    Moving {} {} from {} to {}",
                    conflict.getGroup().getId(),
                    conflict.getCourse().getName(),
                    formatTimeslot(conflict.getTimeslot()),
                    formatTimeslot(alternativeSlot));

            scoreDirector.beforeVariableChanged(conflict, "timeslot");
            conflict.setTimeslot(alternativeSlot);
            scoreDirector.afterVariableChanged(conflict, "timeslot");
            scoreDirector.triggerVariableListeners();
        }

        return true;
    }

    /**
     * Find an alternative timeslot for a block that doesn't create hard constraint
     * violations.
     */
    private BlockTimeslot findAlternativeSlot(SchoolSchedule schedule,
            CourseBlockAssignment block,
            ScoreDirector<SchoolSchedule> scoreDirector) {
        int requiredLength = block.getBlockLength();
        BlockTimeslot originalSlot = block.getTimeslot();

        // Get all timeslots with matching length
        List<BlockTimeslot> candidates = schedule.getBlockTimeslots().stream()
                .filter(ts -> ts.getLengthHours() == requiredLength)
                .filter(ts -> !ts.equals(originalSlot)) // Different from current
                .collect(Collectors.toList());

        // Find the first candidate that doesn't create hard constraint violations
        for (BlockTimeslot candidate : candidates) {
            // Check teacher availability
            if (block.getTeacher() != null && !isTeacherAvailable(block.getTeacher(), candidate)) {
                continue;
            }

            // Temporarily assign to check for conflicts
            scoreDirector.beforeVariableChanged(block, "timeslot");
            block.setTimeslot(candidate);
            scoreDirector.afterVariableChanged(block, "timeslot");
            scoreDirector.triggerVariableListeners();

            // Check if this creates any hard constraint violations
            boolean hasConflicts = hasHardConflicts(schedule, block, candidate);

            // Revert to original
            scoreDirector.beforeVariableChanged(block, "timeslot");
            block.setTimeslot(originalSlot);
            scoreDirector.afterVariableChanged(block, "timeslot");
            scoreDirector.triggerVariableListeners();

            // If no conflicts, use this slot
            if (!hasConflicts) {
                return candidate;
            }
        }

        return null; // No alternative found
    }

    /**
     * Check if assigning a block to a timeslot creates hard constraint violations.
     */
    private boolean hasHardConflicts(SchoolSchedule schedule,
            CourseBlockAssignment block,
            BlockTimeslot timeslot) {
        // Check for group conflicts
        List<CourseBlockAssignment> groupConflicts = findGroupConflicts(schedule, block.getGroup(), timeslot);
        if (groupConflicts.stream().anyMatch(a -> !a.equals(block))) {
            return true;
        }

        // Check for room conflicts
        if (block.getRoom() != null) {
            List<CourseBlockAssignment> roomConflicts = findRoomConflicts(schedule, block.getRoom(), timeslot);
            if (roomConflicts.stream().anyMatch(a -> !a.equals(block))) {
                return true;
            }
        }

        // Check for teacher conflicts
        if (block.getTeacher() != null) {
            List<CourseBlockAssignment> teacherConflicts = findTeacherConflicts(schedule, block.getTeacher(), timeslot);
            if (teacherConflicts.stream().anyMatch(a -> !a.equals(block))) {
                return true;
            }
        }

        return false;
    }

    /**
     * Format a timeslot for logging.
     */
    private String formatTimeslot(BlockTimeslot timeslot) {
        if (timeslot == null) {
            return "null";
        }
        return String.format("%s %d-%d (%dh)",
                getDayName(timeslot.getDayOfWeek()),
                timeslot.getStartHour(),
                timeslot.getStartHour() + timeslot.getLengthHours(),
                timeslot.getLengthHours());
    }

    /**
     * Get day name in Spanish.
     */
    private String getDayName(DayOfWeek day) {
        switch (day) {
            case MONDAY:
                return "Lun";
            case TUESDAY:
                return "Mar";
            case WEDNESDAY:
                return "Mie";
            case THURSDAY:
                return "Jue";
            case FRIDAY:
                return "Vie";
            case SATURDAY:
                return "Sab";
            case SUNDAY:
                return "Dom";
            default:
                return day.toString();
        }
    }
}
