package com.example.web.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * A single explicit soft-constraint weight override (constraint_config
 * table). A constraint with no row here isn't represented by an instance of
 * this class at all - see ConstraintConfigController, which merges this
 * with scheduler-common's SoftConstraintDefaults to show every known
 * constraint (overridden or not) in one list.
 */
@Entity
@Table(name = "constraint_config")
public class ConstraintConfigEntity {

    @Id
    @Column(name = "constraint_name", length = 150)
    private String constraintName;

    @Column(name = "weight_soft", nullable = false)
    private Integer weightSoft;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public ConstraintConfigEntity() {
    }

    public ConstraintConfigEntity(String constraintName, Integer weightSoft) {
        this.constraintName = constraintName;
        this.weightSoft = weightSoft;
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

    public String getConstraintName() {
        return constraintName;
    }

    public void setConstraintName(String constraintName) {
        this.constraintName = constraintName;
    }

    public Integer getWeightSoft() {
        return weightSoft;
    }

    public void setWeightSoft(Integer weightSoft) {
        this.weightSoft = weightSoft;
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
