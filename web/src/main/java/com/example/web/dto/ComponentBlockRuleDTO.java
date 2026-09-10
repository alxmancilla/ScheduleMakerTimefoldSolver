package com.example.web.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * Request body for upserting a component's preferred block size and max
 * blocks per day. The component itself is the path variable, not part of
 * the body - there's no meaningful distinction between "create" and
 * "update" for a config value keyed by a natural key, so a single PUT
 * handles both.
 */
public class ComponentBlockRuleDTO {

    @NotNull(message = "Preferred block size is required")
    @Min(value = 1, message = "Preferred block size must be between 1 and 4")
    @Max(value = 4, message = "Preferred block size must be between 1 and 4")
    private Integer preferredBlockSize;

    @NotNull(message = "Max blocks per day is required")
    @Min(value = 1, message = "Max blocks per day must be between 1 and 4")
    @Max(value = 4, message = "Max blocks per day must be between 1 and 4")
    private Integer maxBlocksPerDay;

    /**
     * Optional override for how many spare distinct days a generated shape
     * must leave beyond the bare minimum before it's accepted as safe (see
     * AvailabilityAwareBlockShaper.DEFAULT_MARGIN_DAYS). Unlike the two
     * fields above, this is genuinely optional - null means "use the
     * hardcoded default," not "invalid request."
     */
    @Min(value = 0, message = "Margin days must be between 0 and 4")
    @Max(value = 4, message = "Margin days must be between 0 and 4")
    private Integer marginDays;

    public ComponentBlockRuleDTO() {
    }

    public Integer getPreferredBlockSize() {
        return preferredBlockSize;
    }

    public void setPreferredBlockSize(Integer preferredBlockSize) {
        this.preferredBlockSize = preferredBlockSize;
    }

    public Integer getMaxBlocksPerDay() {
        return maxBlocksPerDay;
    }

    public void setMaxBlocksPerDay(Integer maxBlocksPerDay) {
        this.maxBlocksPerDay = maxBlocksPerDay;
    }

    public Integer getMarginDays() {
        return marginDays;
    }

    public void setMarginDays(Integer marginDays) {
        this.marginDays = marginDays;
    }
}
