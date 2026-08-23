package com.example.web.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Request body for upserting a calendar exception. The date itself is the
 * path variable, not part of the body - keyed by a natural key (like
 * ComponentBlockRuleDTO), so PUT upserts rather than requiring a separate
 * create/update distinction. endHour's "required when type=HALF_DAY" rule
 * is cross-field, so it's checked in the controller rather than here.
 */
public class CalendarExceptionDTO {

    @NotBlank(message = "Type is required")
    @Pattern(regexp = "HOLIDAY|HALF_DAY|EXAM_DAY", message = "Type must be one of HOLIDAY, HALF_DAY, EXAM_DAY")
    private String type;

    @Size(max = 200, message = "Label must not exceed 200 characters")
    private String label;

    @Min(value = 7, message = "End hour must be between 7 and 15")
    @Max(value = 15, message = "End hour must be between 7 and 15")
    private Integer endHour;

    public CalendarExceptionDTO() {
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public Integer getEndHour() {
        return endHour;
    }

    public void setEndHour(Integer endHour) {
        this.endHour = endHour;
    }
}
