package com.example.web.controller;

import com.example.web.dto.ComponentBlockRuleDTO;
import com.example.web.entity.ComponentBlockRuleEntity;
import com.example.web.repository.ComponentBlockRuleRepository;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Admin-only management of per-component preferred block sizes, read by
 * BlockGenerationService when decomposing a course's weekly hours into
 * blocks. Mounted under /api/admin/**, which SecurityConfig already
 * restricts to the ADMIN role. Keyed by component (a natural key), so PUT
 * upserts rather than requiring a separate create/update distinction.
 */
@RestController
@RequestMapping("/api/admin/component-block-rules")
public class ComponentBlockRuleController {

    @Autowired
    private ComponentBlockRuleRepository ruleRepository;

    @GetMapping
    public List<ComponentBlockRuleEntity> getAllRules() {
        return ruleRepository.findAll();
    }

    @PutMapping("/{component}")
    public ComponentBlockRuleEntity upsertRule(@PathVariable String component,
            @Valid @RequestBody ComponentBlockRuleDTO request) {
        ComponentBlockRuleEntity rule = ruleRepository.findById(component)
                .orElseGet(() -> new ComponentBlockRuleEntity(component, null, null));
        rule.setPreferredBlockSize(request.getPreferredBlockSize());
        rule.setMaxBlocksPerDay(request.getMaxBlocksPerDay());
        rule.setMarginDays(request.getMarginDays());
        return ruleRepository.save(rule);
    }

    @DeleteMapping("/{component}")
    public ResponseEntity<Void> deleteRule(@PathVariable String component) {
        // Idempotent: reverting to "no override" is the goal either way, whether or not
        // one was actually configured.
        if (ruleRepository.existsById(component)) {
            ruleRepository.deleteById(component);
        }
        return ResponseEntity.noContent().build();
    }
}
