package com.example.web.controller;

import com.example.web.dto.AuditLogResponse;
import com.example.web.repository.AuditLogRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Admin-only view of recent write activity (POST/PUT/DELETE to /api/**),
 * populated automatically by {@link com.example.web.security.AuditLogInterceptor}.
 * Mounted under /api/admin/**, which SecurityConfig already restricts to ADMIN.
 */
@RestController
@RequestMapping("/api/admin/audit-log")
public class AuditLogController {

    @Autowired
    private AuditLogRepository auditLogRepository;

    @GetMapping
    public List<AuditLogResponse> getRecentAuditLog() {
        return auditLogRepository.findTop200ByOrderByOccurredAtDesc().stream()
                .map(AuditLogResponse::new)
                .toList();
    }
}
