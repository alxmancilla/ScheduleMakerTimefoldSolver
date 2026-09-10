package com.example.web.controller;

import com.example.common.ConfigurableHardConstraints;
import com.example.common.SoftConstraintDefaults;
import com.example.web.dto.ConstraintConfigDTO;
import com.example.web.dto.ConstraintWeightResponse;
import com.example.web.entity.ConstraintConfigEntity;
import com.example.web.repository.ConstraintConfigRepository;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Admin-only management of constraint weight/severity overrides, read by
 * DataLoader into a Timefold ConstraintWeightOverrides at solve time (see
 * SchoolSchedule.getConstraintWeightOverrides()). Mounted under
 * /api/admin/**, which SecurityConfig already restricts to the ADMIN role -
 * same convention as ComponentBlockRuleController. Keyed by constraint name
 * (a natural key), so PUT upserts rather than requiring a separate
 * create/update distinction.
 *
 * <p>Covers two different kinds of constraint, both stored in the SAME
 * constraint_config table and both resolved through the SAME
 * ConstraintWeightOverrides mechanism: SoftConstraintDefaults (constraints
 * that are SOFT by default - a row here just retunes the weight) and
 * ConfigurableHardConstraints (constraints that are HARD by default - a row
 * here flips them to SOFT entirely, at the given weight; deleting the row
 * reverts them to HARD). See ConfigurableHardConstraints' javadoc for why
 * this works with no SchoolConstraintProvider code change.
 */
@RestController
@RequestMapping("/api/admin/constraint-config")
public class ConstraintConfigController {

    @Autowired
    private ConstraintConfigRepository configRepository;

    /**
     * Every known constraint - both the SOFT-by-default ones
     * (SoftConstraintDefaults) and the severity-configurable HARD ones
     * (ConfigurableHardConstraints) - with its default severity/weight,
     * current override (null if none), and effective severity/weight - not
     * just the rows that happen to have an override, so Settings can show
     * the full, stable list.
     */
    @GetMapping
    public List<ConstraintWeightResponse> getAllWeights() {
        Map<String, ConstraintConfigEntity> overridesByName = configRepository.findAll().stream()
                .collect(java.util.stream.Collectors.toMap(ConstraintConfigEntity::getConstraintName, e -> e));

        List<ConstraintWeightResponse> result = new ArrayList<>();
        SoftConstraintDefaults.DEFAULTS.forEach((name, defaultWeight) -> {
            ConstraintConfigEntity override = overridesByName.get(name);
            result.add(new ConstraintWeightResponse(name, "SOFT", defaultWeight,
                    override != null ? override.getWeightSoft() : null));
        });
        ConfigurableHardConstraints.SUGGESTED_SOFT_WEIGHT.forEach((name, suggestedWeight) -> {
            ConstraintConfigEntity override = overridesByName.get(name);
            result.add(new ConstraintWeightResponse(name, "HARD", suggestedWeight,
                    override != null ? override.getWeightSoft() : null));
        });
        return result;
    }

    @PutMapping("/{constraintName}")
    public ConstraintConfigEntity upsertWeight(@PathVariable String constraintName,
            @Valid @RequestBody ConstraintConfigDTO request) {
        if (SoftConstraintDefaults.getDefault(constraintName) == null
                && !ConfigurableHardConstraints.isConfigurable(constraintName)) {
            throw new IllegalArgumentException(
                    "'" + constraintName + "' is not a known constraint");
        }
        ConstraintConfigEntity entity = configRepository.findById(constraintName)
                .orElseGet(() -> new ConstraintConfigEntity(constraintName, null));
        entity.setWeightSoft(request.getWeightSoft());
        return configRepository.save(entity);
    }

    @DeleteMapping("/{constraintName}")
    public ResponseEntity<Void> deleteWeight(@PathVariable String constraintName) {
        // Idempotent: reverting to "default" (weight, or - for a
        // ConfigurableHardConstraints entry - severity back to HARD) is the
        // goal either way, whether or not an override was actually configured.
        if (configRepository.existsById(constraintName)) {
            configRepository.deleteById(constraintName);
        }
        return ResponseEntity.noContent().build();
    }
}
