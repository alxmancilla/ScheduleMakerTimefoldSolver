package com.example.web.entity;

import jakarta.persistence.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * A school calendar exception (holiday, half-day, or exam day) for a
 * specific date - record-keeping only (v1). block_timeslot is a pure
 * recurring weekly template with no date concept at all, so this table does
 * not yet gate block generation or the solver; see the migration comment in
 * database/migrations/add_calendar_exception.sql for why.
 */
@Entity
@Table(name = "calendar_exception")
public class CalendarExceptionEntity {

    @Id
    @Column(name = "exception_date")
    private LocalDate exceptionDate;

    @Column(name = "type", length = 20, nullable = false)
    private String type;

    @Column(name = "label", length = 200)
    private String label;

    @Column(name = "end_hour")
    private Integer endHour;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public CalendarExceptionEntity() {
    }

    public CalendarExceptionEntity(LocalDate exceptionDate, String type, String label, Integer endHour) {
        this.exceptionDate = exceptionDate;
        this.type = type;
        this.label = label;
        this.endHour = endHour;
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

    public LocalDate getExceptionDate() {
        return exceptionDate;
    }

    public void setExceptionDate(LocalDate exceptionDate) {
        this.exceptionDate = exceptionDate;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public Integer getEndHour() {
        return endHour;
    }

    public void setEndHour(Integer endHour) {
        this.endHour = endHour;
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
