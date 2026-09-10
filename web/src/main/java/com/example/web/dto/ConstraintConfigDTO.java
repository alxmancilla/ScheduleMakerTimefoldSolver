package com.example.web.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * Request body for upserting a constraint's soft-weight override. The
 * constraint name itself is the path variable, not part of the body - same
 * convention as ComponentBlockRuleDTO (no meaningful create/update
 * distinction for a config value keyed by a natural key, so a single PUT
 * handles both).
 */
public class ConstraintConfigDTO {

    @NotNull(message = "Weight is required")
    @Min(value = 0, message = "Weight must be between 0 and 1000")
    @Max(value = 1000, message = "Weight must be between 0 and 1000")
    private Integer weightSoft;

    public ConstraintConfigDTO() {
    }

    public Integer getWeightSoft() {
        return weightSoft;
    }

    public void setWeightSoft(Integer weightSoft) {
        this.weightSoft = weightSoft;
    }
}
