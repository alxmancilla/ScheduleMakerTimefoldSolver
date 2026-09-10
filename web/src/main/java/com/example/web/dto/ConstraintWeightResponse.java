package com.example.web.dto;

/**
 * One row of the GET /api/admin/constraint-config response - either:
 * <ul>
 * <li>a constraint whose DEFAULT is already SOFT (scheduler-common's
 * SoftConstraintDefaults) - defaultSeverity is always "SOFT", defaultWeight
 * is the real weight in effect until overridden; or</li>
 * <li>a HARD constraint whose severity is admin-configurable
 * (scheduler-common's ConfigurableHardConstraints) - defaultSeverity is
 * always "HARD", and defaultWeight is only a SUGGESTED starting weight to
 * pre-fill the UI with when first switched to SOFT (there's no real
 * "default weight" for a constraint that's never been soft).</li>
 * </ul>
 * overrideWeight is the current constraint_config row's weight if one
 * exists (null otherwise); effectiveSeverity/effectiveWeight are what the
 * solver will actually use (an override always means SOFT, regardless of
 * defaultSeverity - see ConfigurableHardConstraints' javadoc for why a
 * constraint_config row transparently flips a HARD constraint to SOFT).
 */
public class ConstraintWeightResponse {

    private final String constraintName;
    private final String defaultSeverity;
    private final int defaultWeight;
    private final Integer overrideWeight;

    public ConstraintWeightResponse(String constraintName, String defaultSeverity, int defaultWeight,
            Integer overrideWeight) {
        this.constraintName = constraintName;
        this.defaultSeverity = defaultSeverity;
        this.defaultWeight = defaultWeight;
        this.overrideWeight = overrideWeight;
    }

    public String getConstraintName() {
        return constraintName;
    }

    public String getDefaultSeverity() {
        return defaultSeverity;
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

    /** SOFT whenever an override row exists (even for a HARD-default constraint) or the default is SOFT; otherwise HARD. */
    public String getEffectiveSeverity() {
        return overrideWeight != null ? "SOFT" : defaultSeverity;
    }
}
