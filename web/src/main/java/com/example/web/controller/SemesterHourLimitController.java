package com.example.web.controller;

import com.example.web.dto.SemesterHourLimitDTO;
import com.example.web.entity.SemesterHourLimitEntity;
import com.example.web.repository.SemesterHourLimitRepository;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

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
 */
@RestController
@RequestMapping("/api/admin/semester-hour-limits")
public class SemesterHourLimitController {

    @Autowired
    private SemesterHourLimitRepository limitRepository;

    @GetMapping
    public List<SemesterHourLimitEntity> getAllLimits() {
        return limitRepository.findAll();
    }

    @PutMapping("/{semester}")
    public SemesterHourLimitEntity upsertLimit(@PathVariable Integer semester,
            @Valid @RequestBody SemesterHourLimitDTO request) {
        SemesterHourLimitEntity entity = limitRepository.findById(semester)
                .orElseGet(() -> new SemesterHourLimitEntity(semester, null, null));
        entity.setLatestEndHour(request.getLatestEndHour());
        entity.setSeverity(request.getSeverity());
        return limitRepository.save(entity);
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
}
