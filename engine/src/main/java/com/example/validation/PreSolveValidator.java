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
 * It also blocks on a teacher whose total assigned hours exceed their total
 * availability - see {@link #validateCapacity}. This isn't invalid data the
 * way the pinned checks above are, but it's just as fatal to the solve: it's
 * a proven mathematical fact (pigeonhole - every teaching hour occupies
 * exactly one of the teacher's available hour-slots, and two blocks can never
 * share one) that at least one double-booking is unavoidable, so a solve
 * attempt can only ever end in a hard violation. Blocking here - rather than
 * warning and burning the full solve budget to confirm what's already known
 * - mirrors this project's other "exact, not a heuristic" guardrail
 * (SemesterHourLimitController's guardrail #2).
 * </p>
 */
public final class PreSolveValidator {

    private PreSolveValidator() {
    }

    /**
     * Validate all pinned assignments in the schedule, plus whole-schedule
     * capacity facts.
     *
     * @param schedule the problem to validate (before solving)
     * @return a {@link ValidationResult} listing every problem found
     */
    public static ValidationResult validate(SchoolSchedule schedule) {
        List<String> problems = new ArrayList<>();
        if (schedule == null || schedule.getCourseBlockAssignments() == null) {
            return new ValidationResult(problems);
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
        validateCapacity(schedule.getCourseBlockAssignments(), problems);

        return new ValidationResult(problems);
    }

    /**
     * Blocks when a teacher's total assigned hours - summed across every
     * assignment for them, pinned or not, the same basis
     * TeacherController.buildCapacityWarning() uses - exceed their total
     * weekly availability ({@link Teacher#getTotalAvailableHours()}). Unlike
     * that web-layer check (which only warns, since an admin might be mid-fix
     * when saving), this runs right before every solve regardless of how the
     * data got there (hand-edited via the API, bulk Excel import, or a direct
     * SQL fix) and regardless of how the solve was triggered (CLI or the web
     * "Run Solver" button, since both funnel through
     * MainBlockSchedulingApp) - so there's no good reason left to let the
     * solve run anyway: the outcome (at least one double-booking) is already
     * certain.
     */
    private static void validateCapacity(List<CourseBlockAssignment> assignments, List<String> problems) {
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
                problems.add(String.format(
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
