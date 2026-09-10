package com.example.web.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * One successful (2xx) write request (POST/PUT/DELETE) to /api/**, recorded
 * automatically by {@link com.example.web.security.AuditLogInterceptor} - not
 * written by individual controllers.
 */
@Entity
@Table(name = "schedule_audit_log")
public class AuditLogEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "username", length = 100, nullable = false)
    private String username;

    @Column(name = "http_method", length = 10, nullable = false)
    private String httpMethod;

    @Column(name = "path", length = 500, nullable = false)
    private String path;

    @Column(name = "status_code", nullable = false)
    private int statusCode;

    @Column(name = "occurred_at", nullable = false)
    private LocalDateTime occurredAt;

    public AuditLogEntity() {
    }

    public AuditLogEntity(String username, String httpMethod, String path, int statusCode) {
        this.username = username;
        this.httpMethod = httpMethod;
        this.path = path;
        this.statusCode = statusCode;
    }

    @PrePersist
    void onCreate() {
        occurredAt = LocalDateTime.now();
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
