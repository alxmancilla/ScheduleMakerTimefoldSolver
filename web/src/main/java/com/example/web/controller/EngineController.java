package com.example.web.controller;

import com.example.web.dto.EngineStatusResponse;
import com.example.web.service.EngineRunnerService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
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

    @PostMapping("/run")
    public EngineStatusResponse runEngine() {
        if (!engineRunnerService.tryStart()) {
            throw new IllegalArgumentException("The engine is already running");
        }
        return new EngineStatusResponse(engineRunnerService.getSnapshot());
    }

    @GetMapping("/status")
    public EngineStatusResponse getStatus() {
        return new EngineStatusResponse(engineRunnerService.getSnapshot());
    }
}
