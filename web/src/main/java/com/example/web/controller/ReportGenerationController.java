package com.example.web.controller;

import com.example.web.dto.ReportStatusResponse;
import com.example.web.entity.AppUserEntity;
import com.example.web.repository.AppUserRepository;
import com.example.web.service.ReportRunnerService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Trigger for the PDF report generation subprocess. Deliberately under
 * /api/reports (not /api/admin/**), so SecurityConfig's general POST rule
 * (WRITER or ADMIN) applies rather than the ADMIN-only admin rule - WRITERs
 * can generate reports, same as they can create/edit other domain data.
 * READERs still cannot (POST requires WRITER/ADMIN). Reading the generated
 * files/status is handled separately by ReportController, open to any role.
 */
@RestController
@RequestMapping("/api/reports")
public class ReportGenerationController {

    @Autowired
    private ReportRunnerService reportRunnerService;

    @Autowired
    private AppUserRepository appUserRepository;

    @PostMapping("/generate")
    public ReportStatusResponse generateReports(@RequestParam(required = false) Integer runId,
            Authentication authentication) {
        String locale = appUserRepository.findById(authentication.getName())
                .map(AppUserEntity::getPreferredLanguage)
                .orElse(null);
        if (!reportRunnerService.tryStart(runId, locale)) {
            throw new IllegalArgumentException("Report generation is already running");
        }
        return new ReportStatusResponse(reportRunnerService.getSnapshot());
    }
}
