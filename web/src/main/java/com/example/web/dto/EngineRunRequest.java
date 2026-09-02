package com.example.web.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/**
 * Optional per-run overrides for the solver's local search time budget, plus
 * a blanket escape hatch past PreSolveValidator's blocking checks.
 * Either time-budget field may be omitted (null), in which case
 * solverConfig.xml's own value is used unchanged - see
 * SchoolSolverConfig.buildSolverFactory.
 */
public class EngineRunRequest {

    @Min(1)
    @Max(120)
    private Integer minutesSpentLimit;

    @Min(1)
    @Max(60)
    private Integer unimprovedMinutesSpentLimit;

    /**
     * true proceeds to solve even if PreSolveValidator finds blocking
     * problems (defaults to false/null - abort on any problem, same as the
     * CLI's SKIP_PRESOLVE_VALIDATION env var, which this maps onto for the
     * engine subprocess). Validation still runs and its findings still print
     * to the run log either way - this only changes whether a non-empty
     * result stops the run. Every check PreSolveValidator runs is a proven
     * mathematical fact, not a heuristic, so this is deliberately all-or-
     * nothing rather than per-check: a legitimate need to force a run
     * through known-broken data (testing, debugging, urgent work-in-progress)
     * applies uniformly, not to one check at a time.
     */
    private Boolean skipValidation;

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

    public Boolean getSkipValidation() {
        return skipValidation;
    }

    public void setSkipValidation(Boolean skipValidation) {
        this.skipValidation = skipValidation;
    }
}
