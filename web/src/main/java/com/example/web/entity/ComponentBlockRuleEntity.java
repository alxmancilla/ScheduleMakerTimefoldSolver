package com.example.web.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "component_block_rule")
public class ComponentBlockRuleEntity {

    @Id
    @Column(name = "component", length = 20)
    private String component;

    @Column(name = "preferred_block_size", nullable = false)
    private Integer preferredBlockSize;

    @Column(name = "max_blocks_per_day", nullable = false)
    private Integer maxBlocksPerDay;

    @Column(name = "margin_days")
    private Integer marginDays;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public ComponentBlockRuleEntity() {
    }

    public ComponentBlockRuleEntity(String component, Integer preferredBlockSize, Integer maxBlocksPerDay) {
        this.component = component;
        this.preferredBlockSize = preferredBlockSize;
        this.maxBlocksPerDay = maxBlocksPerDay;
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

    public String getComponent() {
        return component;
    }

    public void setComponent(String component) {
        this.component = component;
    }

    public Integer getPreferredBlockSize() {
        return preferredBlockSize;
    }

    public void setPreferredBlockSize(Integer preferredBlockSize) {
        this.preferredBlockSize = preferredBlockSize;
    }

    public Integer getMaxBlocksPerDay() {
        return maxBlocksPerDay;
    }

    public void setMaxBlocksPerDay(Integer maxBlocksPerDay) {
        this.maxBlocksPerDay = maxBlocksPerDay;
    }

    /** Null means "use AvailabilityAwareBlockShaper.DEFAULT_MARGIN_DAYS" - see BlockGenerationService.decomposeHours. */
    public Integer getMarginDays() {
        return marginDays;
    }

    public void setMarginDays(Integer marginDays) {
        this.marginDays = marginDays;
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
