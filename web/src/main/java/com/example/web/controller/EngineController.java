package com.example.web.controller;

import com.example.web.dto.EngineRunRequest;
import com.example.web.dto.EngineStatusResponse;
import com.example.web.entity.AppUserEntity;
import com.example.web.repository.AppUserRepository;
import com.example.web.service.EngineRunnerService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Admin-only trigger for the engine (solver) subprocess. Mounted under
 * /api/admin/**, which SecurityConfig already restricts to the ADMIN role.
 */
@RestController
@RequestMapping("/api/admin/engine")
public class EngineController {

    @Autowired
    private EngineRunnerService engineRunnerService;

    @Autowired
    private AppUserRepository appUserRepository;

    @PostMapping("/run")
    public EngineStatusResponse runEngine(@Valid @RequestBody(required = false) EngineRunRequest request,
            Authentication authentication) {
        Integer minutesSpentLimit = request != null ? request.getMinutesSpentLimit() : null;
        Integer unimprovedMinutesSpentLimit = request != null ? request.getUnimprovedMinutesSpentLimit() : null;
        Boolean skipValidation = request != null ? request.getSkipValidation() : null;
        String randomSeed = request != null ? request.getRandomSeed() : null;
        String locale = appUserRepository.findById(authentication.getName())
                .map(AppUserEntity::getPreferredLanguage)
                .orElse(null);
        if (!engineRunnerService.tryStart(minutesSpentLimit, unimprovedMinutesSpentLimit, locale, skipValidation,
                randomSeed)) {
            throw new IllegalArgumentException("The engine is already running");
        }
        return new EngineStatusResponse(engineRunnerService.getSnapshot());
    }

    @GetMapping("/status")
    public EngineStatusResponse getStatus() {
        return new EngineStatusResponse(engineRunnerService.getSnapshot());
    }
}
