package com.example.web.dto;

import com.example.web.entity.SemesterHourLimitEntity;

import java.util.List;

/**
 * Response for PUT /api/admin/semester-hour-limits/{semester}: the saved
 * limit, plus any non-blocking capacity warnings (guardrail #3 - see
 * SemesterHourLimitController). The save always succeeds when this is
 * returned; a blocking guardrail violation (#2: pinned data already
 * conflicts) throws instead and never reaches this response at all.
 */
public class SemesterHourLimitUpsertResponse {

    private final SemesterHourLimitEntity limit;
    private final List<String> warnings;

    public SemesterHourLimitUpsertResponse(SemesterHourLimitEntity limit, List<String> warnings) {
        this.limit = limit;
        this.warnings = warnings;
    }

    public SemesterHourLimitEntity getLimit() {
        return limit;
    }

    public List<String> getWarnings() {
        return warnings;
    }
}
