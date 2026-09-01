package com.example.web.dto;

/**
 * One row of the GET /api/admin/constraint-config response: a known soft
 * constraint (from scheduler-common's SoftConstraintDefaults), its
 * hardcoded default weight, its current override if one exists in
 * constraint_config (null otherwise), and the effective weight the solver
 * will actually use (override, falling back to default).
 */
public class ConstraintWeightResponse {

    private final String constraintName;
    private final int defaultWeight;
    private final Integer overrideWeight;

    public ConstraintWeightResponse(String constraintName, int defaultWeight, Integer overrideWeight) {
        this.constraintName = constraintName;
        this.defaultWeight = defaultWeight;
        this.overrideWeight = overrideWeight;
    }

    public String getConstraintName() {
        return constraintName;
    }

    public int getDefaultWeight() {
        return defaultWeight;
    }

    public Integer getOverrideWeight() {
        return overrideWeight;
    }

    public int getEffectiveWeight() {
        return overrideWeight != null ? overrideWeight : defaultWeight;
    }
}
