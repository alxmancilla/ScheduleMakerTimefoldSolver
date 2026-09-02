package com.example.validation;

import com.example.domain.BlockTimeslot;
import com.example.domain.CourseBlockAssignment;
import com.example.domain.SchoolSchedule;
import com.example.domain.Teacher;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Validates pinned {@link CourseBlockAssignment}s, plus whole-schedule
 * capacity facts, before solving.
 *
 * <p>
 * Pinned blocks are fixed inputs (their timeslot/teacher/room come from the
 * database and never change). The solver's hard constraints deliberately
 * <em>exclude</em> pinned assignments, so an invalid pinned row loaded from the
 * database is silently accepted and never surfaces in the score. This validator
 * closes that gap by re-applying the same checks the solver skips, plus
 * conflict detection among pinned blocks themselves, and fails fast with a
 * clear report.
 * </p>
 * <p>
 * It also warns (never blocks) about a teacher whose total assigned hours
 * exceed their total availability - see {@link #validateCapacity}. Unlike the
 * pinned checks above, this isn't about invalid data; it's a structural fact
 * about the whole schedule (pinned and movable assignments together) that no
 * amount of solving can fix, so it's surfaced up front instead of only
 * showing up indirectly as double-booking violations in the solved result.
 * </p>
 */
public final class PreSolveValidator {

    private PreSolveValidator() {
    }

    /**
     * Validate all pinned assignments in the schedule, plus whole-schedule
     * capacity warnings.
     *
     * @param schedule the problem to validate (before solving)
     * @return a {@link ValidationResult} listing every problem and warning found
     */
    public static ValidationResult validate(SchoolSchedule schedule) {
        List<String> problems = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        if (schedule == null || schedule.getCourseBlockAssignments() == null) {
            return new ValidationResult(problems, warnings);
        }

        List<CourseBlockAssignment> pinned = new ArrayList<>();
        for (CourseBlockAssignment a : schedule.getCourseBlockAssignments()) {
            if (a.isPinned()) {
                pinned.add(a);
            }
        }

        for (CourseBlockAssignment a : pinned) {
            validateSingle(a, problems);
        }
        validateConflicts(pinned, problems);
        validateCapacity(schedule.getCourseBlockAssignments(), warnings);

        return new ValidationResult(problems, warnings);
    }

    /**
     * Warns (never blocks) when a teacher's total assigned hours - summed
     * across every assignment for them, pinned or not, the same basis
     * TeacherController.buildCapacityWarning() uses - exceed their total
     * weekly availability ({@link Teacher#getTotalAvailableHours()}). Unlike
     * that web-layer check, this runs for every solve regardless of how the
     * data got there (hand-edited via the API, bulk Excel import, or a
     * direct SQL fix) and regardless of how the solve was triggered (CLI or
     * the web "Run Solver" button, since both funnel through
     * MainBlockSchedulingApp). Exceeding this is a mathematical fact - there
     * are more required hours than there are hours in the week the teacher
     * can teach - so at least one double-booking is unavoidable no matter
     * how good the solve is.
     */
    private static void validateCapacity(List<CourseBlockAssignment> assignments, List<String> warnings) {
        Map<Teacher, Integer> assignedHoursByTeacher = new LinkedHashMap<>();
        for (CourseBlockAssignment a : assignments) {
            Teacher teacher = a.getTeacher();
            if (teacher == null) {
                continue;
            }
            assignedHoursByTeacher.merge(teacher, a.getBlockLength(), Integer::sum);
        }
        for (Map.Entry<Teacher, Integer> entry : assignedHoursByTeacher.entrySet()) {
            Teacher teacher = entry.getKey();
            int assignedHours = entry.getValue();
            int availableHours = teacher.getTotalAvailableHours();
            if (assignedHours > availableHours) {
                warnings.add(String.format(
                        "Teacher '%s' is assigned %dh/week of courses but only has %dh/week of availability - "
                                + "short by %dh. At least one double-booking is unavoidable.",
                        teacher.getId(), assignedHours, availableHours, assignedHours - availableHours));
            }
        }
    }

    /** Per-block checks that mirror the solver's (pinned-excluded) hard rules. */
    private static void validateSingle(CourseBlockAssignment a, List<String> problems) {
        BlockTimeslot slot = a.getTimeslot();
        if (slot == null) {
            problems.add(describe(a) + " is pinned but has no timeslot assigned.");
            return;
        }

        // Room must be assigned (data-integrity rule, mirrors the DB's
        // check_block_assignment_pinned_requires_room constraint - a pinned
        // row without a room can still be loaded from a database that
        // predates that constraint, so this check stays even though the
        // constraint should normally prevent it at the source).
        if (a.getRoom() == null) {
            problems.add(describe(a) + " is pinned but has no room assigned.");
        }

        // Block length must match timeslot length (data-integrity rule).
        if (a.getBlockLength() != slot.getLengthHours()) {
            problems.add(String.format("%s block length %d does not match timeslot length %d (%s).",
                    describe(a), a.getBlockLength(), slot.getLengthHours(), slot.getDisplayName()));
        }

        // Room type must satisfy the block's required room type.
        if (a.getRoom() != null && a.getSatisfiesRoomType() != null
                && !a.getRoom().satisfiesRequirement(a.getSatisfiesRoomType())) {
            problems.add(String.format("%s room '%s' (type '%s') does not satisfy required room type '%s'.",
                    describe(a), a.getRoom().getName(), a.getRoom().getType(), a.getSatisfiesRoomType()));
        }

        // Teacher must be qualified and available for the whole block.
        if (a.getTeacher() != null) {
            String courseName = a.getCourse() != null ? a.getCourse().getName() : null;
            if (courseName != null && !a.getTeacher().isQualifiedFor(courseName)) {
                problems.add(String.format("%s teacher '%s' is not qualified for course '%s'.",
                        describe(a), a.getTeacher().getId(), courseName));
            }
            if (!a.getTeacher().isAvailableForBlock(slot)) {
                problems.add(String.format("%s teacher '%s' is not available for the entire block (%s).",
                        describe(a), a.getTeacher().getId(), slot.getDisplayName()));
            }
        }
    }

    /** Pairwise conflict checks among pinned blocks that share teacher/group/room. */
    private static void validateConflicts(List<CourseBlockAssignment> pinned, List<String> problems) {
        for (int i = 0; i < pinned.size(); i++) {
            for (int j = i + 1; j < pinned.size(); j++) {
                CourseBlockAssignment a1 = pinned.get(i);
                CourseBlockAssignment a2 = pinned.get(j);
                if (!blocksOverlap(a1.getTimeslot(), a2.getTimeslot())) {
                    continue;
                }
                if (a1.getTeacher() != null && a1.getTeacher().equals(a2.getTeacher())) {
                    problems.add(String.format("Pinned teacher double-booking: %s and %s share teacher '%s'.",
                            describe(a1), describe(a2), a1.getTeacher().getId()));
                }
                if (a1.getGroup() != null && a1.getGroup().equals(a2.getGroup())) {
                    problems.add(String.format("Pinned group clash: %s and %s share group '%s' at the same time.",
                            describe(a1), describe(a2), a1.getGroup().getId()));
                }
                if (a1.getRoom() != null && a1.getRoom().equals(a2.getRoom())) {
                    problems.add(String.format("Pinned room double-booking: %s and %s share room '%s'.",
                            describe(a1), describe(a2), a1.getRoom().getName()));
                }
            }
        }
    }

    /** Same overlap semantics as {@code SchoolConstraintProvider.blocksOverlap}. */
    private static boolean blocksOverlap(BlockTimeslot b1, BlockTimeslot b2) {
        if (b1 == null || b2 == null) {
            return false;
        }
        if (!b1.getDayOfWeek().equals(b2.getDayOfWeek())) {
            return false;
        }
        int start1 = b1.getStartHour();
        int end1 = start1 + b1.getLengthHours();
        int start2 = b2.getStartHour();
        int end2 = start2 + b2.getLengthHours();
        return start1 < end2 && start2 < end1;
    }

    private static String describe(CourseBlockAssignment a) {
        String group = a.getGroup() != null ? a.getGroup().getId() : "?";
        String course = a.getCourse() != null ? a.getCourse().getId() : "?";
        return String.format("Assignment '%s' (group %s, course %s)", a.getId(), group, course);
    }
}
