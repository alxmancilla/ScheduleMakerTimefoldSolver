package com.example.web.dto;

import com.example.web.entity.AuditLogEntity;
import java.time.LocalDateTime;

/** One recent write-request entry, returned by GET /api/admin/audit-log. */
public class AuditLogResponse {

    private final Long id;
    private final String username;
    private final String httpMethod;
    private final String path;
    private final int statusCode;
    private final LocalDateTime occurredAt;

    public AuditLogResponse(AuditLogEntity entity) {
        this.id = entity.getId();
        this.username = entity.getUsername();
        this.httpMethod = entity.getHttpMethod();
        this.path = entity.getPath();
        this.statusCode = entity.getStatusCode();
        this.occurredAt = entity.getOccurredAt();
    }

    public Long getId() {
        return id;
    }

    public String getUsername() {
        return username;
    }

    public String getHttpMethod() {
        return httpMethod;
    }

    public String getPath() {
        return path;
    }

    public int getStatusCode() {
        return statusCode;
    }

    public LocalDateTime getOccurredAt() {
        return occurredAt;
    }
}
