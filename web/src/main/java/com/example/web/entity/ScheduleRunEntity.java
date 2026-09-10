package com.example.web.entity;

import jakarta.persistence.*;
import org.hibernate.annotations.Immutable;
import java.time.LocalDateTime;

/**
 * One row per solver run - see DataSaver (engine module) for how these are
 * written and pruned to the most recent 10. Read-only from the web side:
 * nothing here ever inserts/updates a schedule_run row.
 */
@Entity
@Immutable
@Table(name = "schedule_run")
public class ScheduleRunEntity {

    public ScheduleRunEntity() {
    }

    /** For tests only - @Immutable already prevents Hibernate from ever persisting through this entity. */
    public ScheduleRunEntity(Integer id, LocalDateTime createdAt, Integer hardScore, Integer softScore,
            Integer minutesSpentLimit, Integer unimprovedMinutesSpentLimit) {
        this.id = id;
        this.createdAt = createdAt;
        this.hardScore = hardScore;
        this.softScore = softScore;
        this.minutesSpentLimit = minutesSpentLimit;
        this.unimprovedMinutesSpentLimit = unimprovedMinutesSpentLimit;
    }

    @Id
    private Integer id;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "hard_score", nullable = false)
    private Integer hardScore;

    @Column(name = "soft_score", nullable = false)
    private Integer softScore;

    /** Effective time budget used for this run - solverConfig.xml's default unless overridden. */
    @Column(name = "minutes_spent_limit", nullable = false)
    private Integer minutesSpentLimit;

    @Column(name = "unimproved_minutes_spent_limit", nullable = false)
    private Integer unimprovedMinutesSpentLimit;

    public Integer getId() {
        return id;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public Integer getHardScore() {
        return hardScore;
    }

    public Integer getSoftScore() {
        return softScore;
    }

    public Integer getMinutesSpentLimit() {
        return minutesSpentLimit;
    }

    public Integer getUnimprovedMinutesSpentLimit() {
        return unimprovedMinutesSpentLimit;
    }
}
