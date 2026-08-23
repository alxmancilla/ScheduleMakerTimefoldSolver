package com.example.web.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/**
 * Optional per-run overrides for the solver's local search time budget.
 * Either field may be omitted (null), in which case solverConfig.xml's own
 * value is used unchanged - see SchoolSolverConfig.buildSolverFactory.
 */
public class EngineRunRequest {

    @Min(1)
    @Max(30)
    private Integer minutesSpentLimit;

    @Min(1)
    @Max(15)
    private Integer unimprovedMinutesSpentLimit;

    public Integer getMinutesSpentLimit() {
        return minutesSpentLimit;
    }

    public void setMinutesSpentLimit(Integer minutesSpentLimit) {
        this.minutesSpentLimit = minutesSpentLimit;
    }

    public Integer getUnimprovedMinutesSpentLimit() {
        return unimprovedMinutesSpentLimit;
    }

    public void setUnimprovedMinutesSpentLimit(Integer unimprovedMinutesSpentLimit) {
        this.unimprovedMinutesSpentLimit = unimprovedMinutesSpentLimit;
    }
}
