package com.example.web.controller;

import com.example.common.SoftConstraintDefaults;
import com.example.web.dto.ConstraintConfigDTO;
import com.example.web.dto.ConstraintWeightResponse;
import com.example.web.entity.ConstraintConfigEntity;
import com.example.web.repository.ConstraintConfigRepository;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Admin-only management of soft-constraint weight overrides, read by
 * DataLoader into a Timefold ConstraintWeightOverrides at solve time (see
 * SchoolSchedule.getConstraintWeightOverrides()). Mounted under
 * /api/admin/**, which SecurityConfig already restricts to the ADMIN role -
 * same convention as ComponentBlockRuleController. Keyed by constraint name
 * (a natural key), so PUT upserts rather than requiring a separate
 * create/update distinction.
 */
@RestController
@RequestMapping("/api/admin/constraint-config")
public class ConstraintConfigController {

    @Autowired
    private ConstraintConfigRepository configRepository;

    /**
     * Every known soft constraint (from SoftConstraintDefaults, in its
     * highest-weight-first order) with its default, current override (null
     * if none), and effective weight - not just the rows that happen to
     * have an override, so Settings can show the full, stable list.
     */
    @GetMapping
    public List<ConstraintWeightResponse> getAllWeights() {
        Map<String, ConstraintConfigEntity> overridesByName = configRepository.findAll().stream()
                .collect(java.util.stream.Collectors.toMap(ConstraintConfigEntity::getConstraintName, e -> e));
        return SoftConstraintDefaults.DEFAULTS.entrySet().stream()
                .map(entry -> {
                    ConstraintConfigEntity override = overridesByName.get(entry.getKey());
                    return new ConstraintWeightResponse(entry.getKey(), entry.getValue(),
                            override != null ? override.getWeightSoft() : null);
                })
                .toList();
    }

    @PutMapping("/{constraintName}")
    public ConstraintConfigEntity upsertWeight(@PathVariable String constraintName,
            @Valid @RequestBody ConstraintConfigDTO request) {
        if (SoftConstraintDefaults.getDefault(constraintName) == null) {
            throw new IllegalArgumentException(
                    "'" + constraintName + "' is not a known soft constraint");
        }
        ConstraintConfigEntity entity = configRepository.findById(constraintName)
                .orElseGet(() -> new ConstraintConfigEntity(constraintName, null));
        entity.setWeightSoft(request.getWeightSoft());
        return configRepository.save(entity);
    }

    @DeleteMapping("/{constraintName}")
    public ResponseEntity<Void> deleteWeight(@PathVariable String constraintName) {
        // Idempotent: reverting to "default weight" is the goal either way,
        // whether or not an override was actually configured.
        if (configRepository.existsById(constraintName)) {
            configRepository.deleteById(constraintName);
        }
        return ResponseEntity.noContent().build();
    }
}
