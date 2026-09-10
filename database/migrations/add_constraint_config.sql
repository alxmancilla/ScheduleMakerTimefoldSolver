-- ============================================================================
-- Migration Script: Add constraint_config (dynamic soft-constraint weights)
-- ============================================================================
-- Every soft constraint's weight (HardSoftScore.ofSoft(N)) used to be a
-- hardcoded literal inside SchoolConstraintProvider - changing it meant
-- editing Java, recompiling, and redeploying (this project's own history:
-- preferSemesterOneBlocksStartEarly raised from weight 4 to 6,
-- minimizeSemesterOneGroupIdleGaps likewise). This table makes those
-- weights configurable per constraint, editable from Settings, and read by
-- DataLoader into a Timefold ConstraintWeightOverrides at solve time - no
-- code change or redeploy needed to retune one.
--
-- A constraint with no row here keeps its hardcoded default (see
-- scheduler-common's SoftConstraintDefaults, the single canonical list of
-- known soft constraint names and their defaults, shared by engine and
-- web) - this table only ever holds explicit overrides, not a full copy of
-- every known constraint's weight.
--
-- Purpose: Let admins tune soft-constraint weights without a code change.
-- Date: 2026-09-01
-- ============================================================================

CREATE TABLE IF NOT EXISTS constraint_config (
    constraint_name VARCHAR(150) PRIMARY KEY,
    weight_soft INTEGER NOT NULL CHECK (weight_soft >= 0),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON TABLE constraint_config IS 'Per-constraint soft-weight overrides read into a Timefold ConstraintWeightOverrides at solve time. A constraint with no row here keeps its hardcoded default from scheduler-common''s SoftConstraintDefaults.';
COMMENT ON COLUMN constraint_config.constraint_name IS 'Must exactly match the constraint''s own asConstraint(name) string in SchoolConstraintProvider - see SoftConstraintDefaults for the canonical list.';
COMMENT ON COLUMN constraint_config.weight_soft IS 'The HardSoftScore soft-weight to use instead of the constraint''s hardcoded default.';
