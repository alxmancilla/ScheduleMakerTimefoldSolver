package com.example.web.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.Immutable;

/**
 * One individual hard/soft constraint violation instance for a schedule_run
 * - see DataSaver (engine module) for how these are written, from
 * BlockScheduleAnalyzer's own detailed analysis. Read-only from the web
 * side: nothing here ever inserts/updates a schedule_run_violation row.
 * Unlike schedule_run_constraint (one row per constraint NAME that was
 * active), this is one row per actual violation description string, so a
 * constraint can have zero, one, or many rows for a given run.
 */
@Entity
@Immutable
@Table(name = "schedule_run_violation")
public class ScheduleRunViolationEntity {

    public ScheduleRunViolationEntity() {
    }

    /** For tests only - @Immutable already prevents Hibernate from ever persisting through this entity. */
    public ScheduleRunViolationEntity(Long id, Integer scheduleRunId, String constraintName, Boolean isHard,
            String description) {
        this.id = id;
        this.scheduleRunId = scheduleRunId;
        this.constraintName = constraintName;
        this.isHard = isHard;
        this.description = description;
    }

    @Id
    private Long id;

    @Column(name = "schedule_run_id")
    private Integer scheduleRunId;

    @Column(name = "constraint_name", length = 200)
    private String constraintName;

    @Column(name = "is_hard")
    private Boolean isHard;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    public Long getId() {
        return id;
    }

    public Integer getScheduleRunId() {
        return scheduleRunId;
    }

    public String getConstraintName() {
        return constraintName;
    }

    public Boolean getIsHard() {
        return isHard;
    }

    public String getDescription() {
        return description;
    }
}
