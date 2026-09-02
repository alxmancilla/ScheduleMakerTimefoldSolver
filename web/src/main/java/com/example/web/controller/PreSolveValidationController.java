package com.example.web.controller;

import com.example.web.dto.PreSolveValidationStatusResponse;
import com.example.web.service.PreSolveValidationRunnerService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Trigger for PreSolveValidator on its own, independent of actually solving.
 * Deliberately NOT under /api/admin/** (which SecurityConfig restricts to
 * ADMIN) - this falls under the general POST /api/** rule instead
 * (WRITER or ADMIN), since checking the data's validity is a much lower-risk
 * action than triggering a real solve (EngineController, ADMIN-only) and the
 * request was explicitly for both roles to be able to run it.
 */
@RestController
@RequestMapping("/api/validation")
public class PreSolveValidationController {

    @Autowired
    private PreSolveValidationRunnerService validationRunnerService;

    @PostMapping("/run")
    public PreSolveValidationStatusResponse runValidation() {
        if (!validationRunnerService.tryStart()) {
            throw new IllegalArgumentException("A validation run is already in progress");
        }
        return new PreSolveValidationStatusResponse(validationRunnerService.getSnapshot());
    }

    @GetMapping("/status")
    public PreSolveValidationStatusResponse getStatus() {
        return new PreSolveValidationStatusResponse(validationRunnerService.getSnapshot());
    }
}
