package com.example.web.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Singleton row (id pinned to 1) holding the current term/period label shown
 * in the UI (e.g. "Fall 2026"). Purely organizational metadata - never read
 * by the solver or any constraint.
 */
@Entity
@Table(name = "school_term")
public class SchoolTermEntity {

    @Id
    private Integer id;

    @Column(name = "label", length = 100, nullable = false)
    private String label = "";

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public SchoolTermEntity() {
    }

    public SchoolTermEntity(Integer id, String label) {
        this.id = id;
        this.label = label;
    }

    @PrePersist
    @PreUpdate
    void onSave() {
        updatedAt = LocalDateTime.now();
    }

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}
