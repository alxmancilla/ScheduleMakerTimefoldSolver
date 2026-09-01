package com.example.web.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * A per-semester "blocks must/should finish by this hour" limit
 * (semester_hour_limit table), read onto Course.latestEndHour/
 * latestEndHourSeverity by DataLoader. A semester with no row here is
 * unrestricted.
 */
@Entity
@Table(name = "semester_hour_limit")
public class SemesterHourLimitEntity {

    @Id
    @Column(name = "semester")
    private Integer semester;

    @Column(name = "latest_end_hour", nullable = false)
    private Integer latestEndHour;

    @Column(name = "severity", length = 10, nullable = false)
    private String severity;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public SemesterHourLimitEntity() {
    }

    public SemesterHourLimitEntity(Integer semester, Integer latestEndHour, String severity) {
        this.semester = semester;
        this.latestEndHour = latestEndHour;
        this.severity = severity;
    }

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public Integer getSemester() {
        return semester;
    }

    public void setSemester(Integer semester) {
        this.semester = semester;
    }

    public Integer getLatestEndHour() {
        return latestEndHour;
    }

    public void setLatestEndHour(Integer latestEndHour) {
        this.latestEndHour = latestEndHour;
    }

    public String getSeverity() {
        return severity;
    }

    public void setSeverity(String severity) {
        this.severity = severity;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
