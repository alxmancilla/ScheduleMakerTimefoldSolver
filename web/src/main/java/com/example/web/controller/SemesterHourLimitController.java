package com.example.web.controller;

import com.example.common.SchoolCalendarConstants;
import com.example.web.dto.SemesterHourLimitDTO;
import com.example.web.dto.SemesterHourLimitUpsertResponse;
import com.example.web.entity.SemesterHourLimitEntity;
import com.example.web.repository.CourseBlockAssignmentRepository;
import com.example.web.repository.CourseBlockAssignmentRepository.GroupSemesterDemand;
import com.example.web.repository.CourseBlockAssignmentRepository.PinnedHourLimitViolation;
import com.example.web.repository.SemesterHourLimitRepository;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;

/**
 * Admin-only management of per-semester "blocks must/should finish by this
 * hour" limits, read by DataLoader onto Course.latestEndHour/
 * latestEndHourSeverity at load time (see
 * CourseBlockAssignment.getMatchingBlockTimeslots() and
 * SchoolConstraintProvider.semesterHourLimitsMustBeRespected/
 * preferSemesterHourLimits for how the engine uses it). Mounted under
 * /api/admin/**, which SecurityConfig already restricts to the ADMIN role -
 * same convention as ComponentBlockRuleController/ConstraintConfigController.
 * Keyed by semester (a natural key), so PUT upserts rather than requiring a
 * separate create/update distinction.
 *
 * <p>Three guardrails against an infeasible config, in increasing order of
 * how much they actually know about the school's real data:
 * <ol>
 * <li>#1, in SemesterHourLimitDTO: latestEndHour is bounds-checked against
 * the school's real operating hours, not an arbitrary range - blocking,
 * static, no query needed.</li>
 * <li>#2, here, HARD only: rejects the save if any PINNED block of this
 * semester already ends after the proposed limit - exact, not a heuristic,
 * since pinned data is real committed fact, not a projection. Blocking,
 * because a HARD limit's whole point is a structural guarantee; saving one
 * that's already contradicted by real data would be silently broken from
 * the moment it's saved.</li>
 * <li>#3, here, both severities: warns (never blocks) when a group's actual
 * weekly demand among this semester's courses exceeds the window this limit
 * would allow (days x (hour - earliest start)) - the same capacity math
 * this project's semester-1-vs-semester-5 analysis did by hand. Advisory
 * only: unlike #2, exceeding this doesn't necessarily mean broken data (a
 * SOFT limit tolerates it entirely), so the save still succeeds.</li>
 * </ol>
 */
@RestController
@RequestMapping("/api/admin/semester-hour-limits")
public class SemesterHourLimitController {

    @Autowired
    private SemesterHourLimitRepository limitRepository;

    @Autowired
    private CourseBlockAssignmentRepository assignmentRepository;

    @GetMapping
    public List<SemesterHourLimitEntity> getAllLimits() {
        return limitRepository.findAll();
    }

    @PutMapping("/{semester}")
    public SemesterHourLimitUpsertResponse upsertLimit(@PathVariable Integer semester,
            @Valid @RequestBody SemesterHourLimitDTO request) {
        if ("HARD".equals(request.getSeverity())) {
            List<PinnedHourLimitViolation> violations =
                    assignmentRepository.findPinnedHourLimitViolations(semester, request.getLatestEndHour());
            if (!violations.isEmpty()) {
                throw new IllegalArgumentException(buildViolationMessage(semester, request.getLatestEndHour(), violations));
            }
        }

        List<String> warnings = buildCapacityWarnings(semester, request.getLatestEndHour());

        SemesterHourLimitEntity entity = limitRepository.findById(semester)
                .orElseGet(() -> new SemesterHourLimitEntity(semester, null, null));
        entity.setLatestEndHour(request.getLatestEndHour());
        entity.setSeverity(request.getSeverity());
        SemesterHourLimitEntity saved = limitRepository.save(entity);

        return new SemesterHourLimitUpsertResponse(saved, warnings);
    }

    @DeleteMapping("/{semester}")
    public ResponseEntity<Void> deleteLimit(@PathVariable Integer semester) {
        // Idempotent: reverting to "unrestricted" is the goal either way,
        // whether or not a limit was actually configured.
        if (limitRepository.existsById(semester)) {
            limitRepository.deleteById(semester);
        }
        return ResponseEntity.noContent().build();
    }

    /** Guardrail #2: a clear, specific message listing what's already broken - not just "rejected". */
    private String buildViolationMessage(int semester, int latestEndHour, List<PinnedHourLimitViolation> violations) {
        StringBuilder message = new StringBuilder()
                .append(violations.size())
                .append(" pinned block(s) in semester ").append(semester)
                .append(" already end after ").append(latestEndHour)
                .append(":00 and would break under a HARD limit: ");
        int shown = 0;
        for (PinnedHourLimitViolation v : violations) {
            if (shown >= 5) {
                message.append("... and ").append(violations.size() - shown).append(" more");
                break;
            }
            if (shown > 0) {
                message.append(", ");
            }
            message.append(v.getGroupName()).append(" [").append(v.getCourseName()).append("] ")
                    .append(dayAbbreviation(v.getDayOfWeek())).append(' ')
                    .append(v.getStartHour()).append('-').append(v.getStartHour() + v.getLengthHours());
            shown++;
        }
        return message.toString();
    }

    /** Guardrail #3: a warning per group whose real weekly demand can't fit the proposed window - exceeding it is a mathematical fact, not a judgment call. */
    private List<String> buildCapacityWarnings(int semester, int latestEndHour) {
        int windowHoursPerDay = latestEndHour - SchoolCalendarConstants.EARLIEST_START_HOUR;
        int windowHoursPerWeek = windowHoursPerDay * SchoolCalendarConstants.SCHOOL_DAYS_PER_WEEK;

        List<String> warnings = new ArrayList<>();
        for (GroupSemesterDemand demand : assignmentRepository.findGroupWeeklyDemandForSemester(semester)) {
            if (demand.getTotalHours() != null && demand.getTotalHours() > windowHoursPerWeek) {
                warnings.add(String.format(
                        "Group %s needs %dh/week among semester %d courses, but this limit only allows %dh/week "
                                + "(%d days x %dh) - it cannot fit even if every hour were used.",
                        demand.getGroupId(), demand.getTotalHours(), semester, windowHoursPerWeek,
                        SchoolCalendarConstants.SCHOOL_DAYS_PER_WEEK, windowHoursPerDay));
            }
        }
        return warnings;
    }

    private String dayAbbreviation(int dayOfWeek) {
        String[] days = { "Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun" };
        return dayOfWeek >= 1 && dayOfWeek <= 7 ? days[dayOfWeek - 1] : String.valueOf(dayOfWeek);
    }
}
