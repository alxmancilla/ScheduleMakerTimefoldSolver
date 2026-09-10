package com.example.web.dto;

import com.example.web.entity.ScheduleRunEntity;
import java.time.LocalDateTime;

/** One entry in the run-history list (GET /api/schedule/runs). */
public class ScheduleRunDTO {

    private final Integer id;
    private final LocalDateTime createdAt;
    private final Integer hardScore;
    private final Integer softScore;
    private final Integer minutesSpentLimit;
    private final Integer unimprovedMinutesSpentLimit;

    public ScheduleRunDTO(ScheduleRunEntity entity) {
        this.id = entity.getId();
        this.createdAt = entity.getCreatedAt();
        this.hardScore = entity.getHardScore();
        this.softScore = entity.getSoftScore();
        this.minutesSpentLimit = entity.getMinutesSpentLimit();
        this.unimprovedMinutesSpentLimit = entity.getUnimprovedMinutesSpentLimit();
    }

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
