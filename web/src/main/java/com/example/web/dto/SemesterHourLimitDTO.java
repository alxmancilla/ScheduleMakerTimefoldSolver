package com.example.web.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/**
 * Request body for upserting a semester's finish-by-hour limit. The
 * semester itself is the path variable, not part of the body - same
 * convention as ComponentBlockRuleDTO/ConstraintConfigDTO (no meaningful
 * create/update distinction for a config value keyed by a natural key, so a
 * single PUT handles both).
 */
public class SemesterHourLimitDTO {

    @NotNull(message = "Latest end hour is required")
    @Min(value = 1, message = "Latest end hour must be between 1 and 24")
    @Max(value = 24, message = "Latest end hour must be between 1 and 24")
    private Integer latestEndHour;

    @NotNull(message = "Severity is required")
    @Pattern(regexp = "HARD|SOFT", message = "Severity must be HARD or SOFT")
    private String severity;

    public SemesterHourLimitDTO() {
    }

    public Integer getLatestEndHour() {
        return latestEndHour;
    }

    public void setLatestEndHour(Integer latestEndHour) {
        this.latestEndHour = latestEndHour;
    }

    public String getSeverity() {
        return severity;
    }

    public void setSeverity(String severity) {
        this.severity = severity;
    }
}
