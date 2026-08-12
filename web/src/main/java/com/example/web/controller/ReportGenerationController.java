package com.example.web.controller;

import com.example.web.dto.ReportStatusResponse;
import com.example.web.service.ReportRunnerService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
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

    @PostMapping("/generate")
    public ReportStatusResponse generateReports() {
        if (!reportRunnerService.tryStart()) {
            throw new IllegalArgumentException("Report generation is already running");
        }
        return new ReportStatusResponse(reportRunnerService.getSnapshot());
    }
}
