-- ============================================================================
-- Migration Script: Add semester_hour_limit (dynamic, per-semester,
-- HARD-or-SOFT "must/should finish by hour X" limits - Tier 1.5)
-- ============================================================================
-- The old "First-semester blocks must finish by 2pm" rule was hardcoded in
-- two places: CourseBlockAssignment.getMatchingBlockTimeslots() (excluding
-- any past-14:00 timeslot from a semester-1 block's value range) and the
-- HARD constraint semesterOneBlocksMustFinishBy2pm (a pinned-row backstop).
-- Both only ever recognized semester == 1 and only ever as HARD - extending
-- the same guarantee to another semester (e.g. semester 5) meant a code
-- change and redeploy, and semester 5 doesn't have the same generous
-- capacity slack semester 1 does, so a HARD guarantee there risks
-- infeasibility rather than just being a preference.
--
-- This table generalizes both dimensions: which semester, and how strict.
-- A semester with no row here is entirely unrestricted (same "absent row =
-- unrestricted" convention as component_block_rule). severity = 'HARD'
-- reproduces the old structural guarantee (the solver can never place a
-- non-pinned block of that semester past the limit in the first place);
-- severity = 'SOFT' allows it, penalized instead in proportion to the
-- overrun (see SchoolConstraintProvider.preferSemesterHourLimits, whose own
-- weight is independently configurable via constraint_config).
--
-- The seed row below reproduces today's existing semester-1 HARD guarantee
-- exactly (semester 1, 14:00, HARD) so this migration is a pure refactor -
-- it changes nothing about current behavior on its own. Configuring a
-- second semester (e.g. 5, SOFT) is then a Settings edit, not a code change.
--
-- Purpose: Let admins configure per-semester finish-by-hour limits, HARD or
-- SOFT, without a code change.
-- Date: 2026-09-01
-- ============================================================================

CREATE TABLE IF NOT EXISTS semester_hour_limit (
    semester INTEGER PRIMARY KEY,
    latest_end_hour INTEGER NOT NULL CHECK (latest_end_hour BETWEEN 1 AND 24),
    severity VARCHAR(10) NOT NULL CHECK (severity IN ('HARD', 'SOFT')),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Preserves today's existing hardcoded semester-1-must-finish-by-2pm HARD
-- guarantee exactly - removing or changing this row is a deliberate,
-- separate decision, not a side effect of running this migration.
INSERT INTO semester_hour_limit (semester, latest_end_hour, severity)
VALUES (1, 14, 'HARD')
ON CONFLICT (semester) DO NOTHING;

COMMENT ON TABLE semester_hour_limit IS 'Per-semester "blocks must/should finish by this hour" limit, read onto Course.latestEndHour/latestEndHourSeverity by DataLoader. A semester with no row here is unrestricted.';
COMMENT ON COLUMN semester_hour_limit.semester IS 'Matches course.semester.';
COMMENT ON COLUMN semester_hour_limit.latest_end_hour IS 'A block of this semester may never (HARD) or should not (SOFT) be assigned a timeslot ending after this hour.';
COMMENT ON COLUMN semester_hour_limit.severity IS 'HARD: structurally excluded from the value range, enforced as a genuine guarantee. SOFT: allowed, but penalized by SchoolConstraintProvider.preferSemesterHourLimits in proportion to the overrun.';
