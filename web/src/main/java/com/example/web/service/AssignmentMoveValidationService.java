package com.example.web.service;

import com.example.common.BlockTimingMath;
import com.example.web.dto.AssignmentMoveValidationResponse;
import com.example.web.entity.BlockTimeslotEntity;
import com.example.web.entity.ComponentBlockRuleEntity;
import com.example.web.entity.CourseBlockAssignmentEntity;
import com.example.web.entity.CourseEntity;
import com.example.web.entity.SemesterHourLimitEntity;
import com.example.web.entity.TeacherAvailabilityEntity;
import com.example.web.entity.TeacherEntity;
import com.example.web.exception.ResourceNotFoundException;
import com.example.web.repository.ComponentBlockRuleRepository;
import com.example.web.repository.ConstraintConfigRepository;
import com.example.web.entity.CourseBlockAssignmentCurrentEntity;
import com.example.web.repository.CourseBlockAssignmentCurrentRepository;
import com.example.web.repository.CourseBlockAssignmentRepository;
import com.example.web.repository.CourseRepository;
import com.example.web.repository.BlockTimeslotRepository;
import com.example.web.repository.SemesterHourLimitRepository;
import com.example.web.repository.TeacherRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Checks whether moving/pinning one assignment to a candidate timeslot would
 * violate a hard constraint, WITHOUT actually saving anything - the
 * authoritative backstop behind Schedule.jsx's move/pin editor, called on
 * every day/hour change before Save is allowed. {@code PUT /api/assignments/
 * {id}} itself performs no constraint checking of its own (double-booking
 * etc. are solver constraints, evaluated at solve time or by
 * PreSolveValidator, not by plain CRUD) - this service exists specifically
 * to give a human editing the live grid the same guarantee, for a single
 * proposed change, without running a full PreSolveValidator pass or the
 * solver itself.
 *
 * <p>engine's SchoolConstraintProvider/BlockScheduleMath - the authoritative
 * implementations - live in a module web deliberately does not depend on
 * (see CLAUDE.md's module boundaries), so the checks below are a
 * web-native re-derivation from data web already owns, not a call into
 * engine. The overlap/consecutiveness math itself (the genuinely
 * error-prone-to-reimplement part) is NOT re-derived - it's shared via
 * scheduler-common's BlockTimingMath, the same implementation engine's
 * BlockScheduleMath delegates to.
 *
 * <p>Four of the five checks below correspond 1:1 to a named
 * SchoolConstraintProvider constraint whose severity can be admin-toggled:
 * teacher/group/room double-booking is always HARD (see
 * scheduler-common's ConfigurableHardConstraints javadoc - those three are
 * deliberately excluded from severity-toggling as physically-impossible
 * outcomes), but teacher availability, the per-day block cap, and
 * same-day consecutiveness can each be switched to SOFT via a
 * constraint_config row (Settings > Constraint Weights), and the
 * semester-hour-limit check has its own independent per-semester severity
 * (semester_hour_limit.severity). A violation of a currently-HARD check is
 * reported as a blocking {@code violation}; a violation of a
 * currently-SOFT one is reported as a non-blocking {@code warning} instead
 * - see AssignmentMoveValidationResponse's own doc.
 */
@Service
public class AssignmentMoveValidationService {

    // Mirrors BlockGenerationService's own local constant (web has no
    // dependency on engine's BlockScheduleMath, where the canonical default
    // actually lives) - see that class for why this one-line default isn't
    // itself worth a scheduler-common extraction.
    private static final int DEFAULT_MAX_BLOCKS_PER_DAY = 2;

    private static final String CONSTRAINT_TEACHER_AVAILABLE = "Teacher must be available for entire block";
    private static final String CONSTRAINT_MAX_BLOCKS_PER_DAY = "Maximum blocks per course per group per day";
    private static final String CONSTRAINT_CONSECUTIVE = "Course blocks must be consecutive";

    @Autowired
    private CourseBlockAssignmentRepository assignmentRepository;

    /**
     * Deliberately the RESOLVED current schedule, not the raw input table
     * (fixed 2026-09-08). course_block_assignment.block_timeslot_id is only
     * ever meaningful for a pinned row - every unpinned row carries null there
     * and takes its actual placement from the latest schedule_run - so reading
     * the raw table made both list-based checks below skip every
     * solver-placed block. Measured on the live dataset at the time: 34 of 551
     * blocks visible, so 94% of the schedule the user is looking at was
     * invisible to the very validation gating their Save button. Reading
     * through course_block_assignment_current makes "what we validate against"
     * and "what the grid displays" the same thing by construction.
     */
    @Autowired
    private CourseBlockAssignmentCurrentRepository assignmentCurrentRepository;

    @Autowired
    private BlockTimeslotRepository timeslotRepository;

    @Autowired
    private TeacherRepository teacherRepository;

    @Autowired
    private CourseRepository courseRepository;

    @Autowired
    private ComponentBlockRuleRepository componentBlockRuleRepository;

    @Autowired
    private SemesterHourLimitRepository semesterHourLimitRepository;

    @Autowired
    private ConstraintConfigRepository constraintConfigRepository;

    public AssignmentMoveValidationResponse validate(String assignmentId, String targetTimeslotId, boolean pinned) {
        CourseBlockAssignmentEntity assignment = assignmentRepository.findById(assignmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Assignment", assignmentId));
        BlockTimeslotEntity target = timeslotRepository.findById(targetTimeslotId)
                .orElseThrow(() -> new ResourceNotFoundException("Timeslot", targetTimeslotId));

        List<String> violations = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        if (!target.getLengthHours().equals(assignment.getBlockLength())) {
            // Structurally shouldn't happen - the editor only offers
            // length-matching timeslots - but a stale client payload could
            // still reach here, so this is checked like any other fact.
            violations.add("Target timeslot length (" + target.getLengthHours()
                    + "h) does not match this block's length (" + assignment.getBlockLength() + "h)");
            return new AssignmentMoveValidationResponse(violations, warnings);
        }
        if (pinned && assignment.getRoomName() == null) {
            violations.add("Cannot pin: this block has no room assigned");
        }

        int newStart = target.getStartHour();
        int newLength = target.getLengthHours();
        int newEnd = newStart + newLength;
        Integer day = target.getDayOfWeek();

        List<CourseBlockAssignmentCurrentEntity> all = assignmentCurrentRepository.findAll();
        Map<String, BlockTimeslotEntity> timeslotsById = timeslotRepository.findAll().stream()
                .collect(Collectors.toMap(BlockTimeslotEntity::getId, t -> t));

        checkDoubleBooking(assignment, newStart, newLength, day, all, timeslotsById, violations);
        checkTeacherAvailability(assignment, newStart, newEnd, day, violations, warnings);
        checkSemesterHourLimit(assignment, newEnd, violations, warnings);
        checkDayShape(assignment, assignmentId, newStart, newLength, day, all, timeslotsById, violations, warnings);

        return new AssignmentMoveValidationResponse(violations, warnings);
    }

    /** Teacher/group/room double-booking - ALWAYS hard, never configurable (see class doc). */
    private void checkDoubleBooking(CourseBlockAssignmentEntity assignment, int newStart, int newLength, Integer day,
            List<CourseBlockAssignmentCurrentEntity> all, Map<String, BlockTimeslotEntity> timeslotsById,
            List<String> violations) {
        for (CourseBlockAssignmentCurrentEntity other : all) {
            // A null timeslot here means genuinely unplaced (no run has ever
            // placed it), not merely unpinned - see the repository's javadoc.
            if (other.getId().equals(assignment.getId()) || other.getBlockTimeslotId() == null) {
                continue;
            }
            BlockTimeslotEntity otherSlot = timeslotsById.get(other.getBlockTimeslotId());
            if (otherSlot == null || !otherSlot.getDayOfWeek().equals(day)) {
                continue;
            }
            if (!BlockTimingMath.overlaps(newStart, newLength, otherSlot.getStartHour(), otherSlot.getLengthHours())) {
                continue;
            }
            if (assignment.getTeacherId() != null && assignment.getTeacherId().equals(other.getTeacherId())) {
                violations.add("Teacher double-booking with " + other.getId() + " at that time");
            }
            if (assignment.getGroupId() != null && assignment.getGroupId().equals(other.getGroupId())) {
                violations.add("Group double-booking with " + other.getId() + " at that time");
            }
            if (assignment.getRoomName() != null && assignment.getRoomName().equals(other.getRoomName())) {
                violations.add("Room double-booking with " + other.getId() + " at that time");
            }
        }
    }

    private void checkTeacherAvailability(CourseBlockAssignmentEntity assignment, int newStart, int newEnd,
            Integer day, List<String> violations, List<String> warnings) {
        if (assignment.getTeacherId() == null) {
            return;
        }
        TeacherEntity teacher = teacherRepository.findById(assignment.getTeacherId()).orElse(null);
        if (teacher == null) {
            return;
        }
        for (int hour = newStart; hour < newEnd; hour++) {
            boolean available = false;
            for (TeacherAvailabilityEntity a : teacher.getAvailability()) {
                if (a.getDayOfWeek().equals(day) && a.getHour() == hour) {
                    available = true;
                    break;
                }
            }
            if (!available) {
                addBySeverity(isCurrentlyHard(CONSTRAINT_TEACHER_AVAILABLE),
                        "Teacher is not available at " + hour + ":00 on the target day", violations, warnings);
                return;
            }
        }
    }

    private void checkSemesterHourLimit(CourseBlockAssignmentEntity assignment, int newEnd,
            List<String> violations, List<String> warnings) {
        CourseEntity course = courseRepository.findById(assignment.getCourseId()).orElse(null);
        if (course == null || course.getSemester() == null) {
            return;
        }
        SemesterHourLimitEntity limit = semesterHourLimitRepository.findById(course.getSemester()).orElse(null);
        if (limit == null || newEnd <= limit.getLatestEndHour()) {
            return;
        }
        String message = "Ends at " + newEnd + ":00, past semester " + course.getSemester()
                + "'s limit of " + limit.getLatestEndHour() + ":00";
        addBySeverity("HARD".equals(limit.getSeverity()), message, violations, warnings);
    }

    /** Per-day block cap and same-day consecutiveness for this (group, course) pair, after the hypothetical move. */
    private void checkDayShape(CourseBlockAssignmentEntity assignment, String assignmentId, int newStart,
            int newLength, Integer day, List<CourseBlockAssignmentCurrentEntity> all,
            Map<String, BlockTimeslotEntity> timeslotsById, List<String> violations, List<String> warnings) {
        if (assignment.getGroupId() == null || assignment.getCourseId() == null) {
            return;
        }
        List<int[]> sameDayBlocks = new ArrayList<>();
        sameDayBlocks.add(new int[] { newStart, newLength });
        for (CourseBlockAssignmentCurrentEntity other : all) {
            if (other.getId().equals(assignmentId) || other.getBlockTimeslotId() == null) {
                continue;
            }
            if (!assignment.getGroupId().equals(other.getGroupId())
                    || !assignment.getCourseId().equals(other.getCourseId())) {
                continue;
            }
            BlockTimeslotEntity slot = timeslotsById.get(other.getBlockTimeslotId());
            if (slot == null || !slot.getDayOfWeek().equals(day)) {
                continue;
            }
            sameDayBlocks.add(new int[] { slot.getStartHour(), slot.getLengthHours() });
        }
        if (sameDayBlocks.size() <= 1) {
            return;
        }

        CourseEntity course = courseRepository.findById(assignment.getCourseId()).orElse(null);
        int maxPerDay = maxBlocksPerDayFor(course);
        if (sameDayBlocks.size() > maxPerDay) {
            addBySeverity(isCurrentlyHard(CONSTRAINT_MAX_BLOCKS_PER_DAY),
                    "This would put " + sameDayBlocks.size() + " blocks of this course for this group on that day"
                            + " (max " + maxPerDay + ")",
                    violations, warnings);
        }

        int breaks = BlockTimingMath.countChainBreaks(sameDayBlocks);
        if (breaks > 0) {
            addBySeverity(isCurrentlyHard(CONSTRAINT_CONSECUTIVE),
                    "This would break same-day consecutiveness for this course/group (" + breaks + " gap(s))",
                    violations, warnings);
        }
    }

    private int maxBlocksPerDayFor(CourseEntity course) {
        if (course == null || course.getDesignation() == null) {
            return DEFAULT_MAX_BLOCKS_PER_DAY;
        }
        return componentBlockRuleRepository.findById(course.getDesignation())
                .map(ComponentBlockRuleEntity::getMaxBlocksPerDay)
                .orElse(DEFAULT_MAX_BLOCKS_PER_DAY);
    }

    /** A constraint with no constraint_config row is still HARD - see ConstraintConfigEntity's own doc. */
    private boolean isCurrentlyHard(String constraintName) {
        return !constraintConfigRepository.existsById(constraintName);
    }

    private void addBySeverity(boolean hard, String message, List<String> violations, List<String> warnings) {
        if (hard) {
            violations.add(message);
        } else {
            warnings.add(message);
        }
    }
}
