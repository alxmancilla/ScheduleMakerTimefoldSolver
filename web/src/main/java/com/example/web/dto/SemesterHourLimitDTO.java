package com.example.web.dto;

import com.example.common.SchoolCalendarConstants;
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
 *
 * <p>Guardrail #1 (of 3 - see SemesterHourLimitController for #2/#3): bounds
 * are the school's REAL operating hours (SchoolCalendarConstants), not an
 * arbitrary 1-24 range - the lower bound is the earliest hour a 1h block
 * could possibly end at (EARLIEST_START_HOUR + 1), so a value that leaves
 * literally zero valid timeslots is rejected before it ever reaches the
 * database, let alone a solve.
 */
public class SemesterHourLimitDTO {

    @NotNull(message = "Latest end hour is required")
    @Min(value = SchoolCalendarConstants.EARLIEST_START_HOUR + 1,
            message = "Latest end hour must be between " + (SchoolCalendarConstants.EARLIEST_START_HOUR + 1)
                    + " and " + SchoolCalendarConstants.LATEST_HOUR
                    + " (the school's actual operating hours - a shorter block couldn't fit before it anyway)")
    @Max(value = SchoolCalendarConstants.LATEST_HOUR,
            message = "Latest end hour must be between " + (SchoolCalendarConstants.EARLIEST_START_HOUR + 1)
                    + " and " + SchoolCalendarConstants.LATEST_HOUR
                    + " (the school's actual operating hours - a shorter block couldn't fit before it anyway)")
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
