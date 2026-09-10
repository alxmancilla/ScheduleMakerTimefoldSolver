-- ============================================================================
-- Migration Script: Add component_block_rule (configurable block sizing +
-- max blocks per day)
-- ============================================================================
-- BlockGenerationService ("Generate Blocks") used to hardcode how a course's
-- weekly hours split into blocks: BASICAS courses with exactly 2 hours got
-- two 1-hour blocks, everything else greedily packed 2-hour blocks with a
-- trailing 1-hour block for an odd remainder. This table makes that
-- "preferred block size" configurable per course component (e.g. BASICAS,
-- TEM, TCS, ...) instead of hardcoded Java, editable from Settings.
--
-- A component with no row here falls back to the size-2 default in code
-- (unchanged from today's behavior for anything not explicitly configured).
-- The seeded BASICAS=1 row preserves this system's existing intent (BASICAS
-- courses favor small, flexible blocks) as an editable default rather than
-- a special case that only fired for exactly-2-hour courses.
--
-- max_blocks_per_day generalizes the solver's HARD "no more than N blocks of
-- the same course/group on the same day" constraint the same way: it used to
-- hardcode BASICAS to 1 and everything else to 2 (but only enforced past 4
-- total hours). A component with no row falls back to the code default of 2.
--
-- Purpose: Let admins tune block sizing and daily concentration per
-- component without a code change.
-- Date: 2026-08-21
-- ============================================================================

CREATE TABLE IF NOT EXISTS component_block_rule (
    component VARCHAR(20) PRIMARY KEY,
    preferred_block_size INTEGER NOT NULL,
    max_blocks_per_day INTEGER NOT NULL DEFAULT 2,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT check_preferred_block_size CHECK (preferred_block_size BETWEEN 1 AND 4),
    CONSTRAINT check_max_blocks_per_day CHECK (max_blocks_per_day BETWEEN 1 AND 4)
);

-- Adds the column when this migration runs against a database where the
-- table already exists from an earlier version of this same script.
ALTER TABLE component_block_rule ADD COLUMN IF NOT EXISTS max_blocks_per_day INTEGER NOT NULL DEFAULT 2;
ALTER TABLE component_block_rule DROP CONSTRAINT IF EXISTS check_max_blocks_per_day;
ALTER TABLE component_block_rule ADD CONSTRAINT check_max_blocks_per_day CHECK (max_blocks_per_day BETWEEN 1 AND 4);

INSERT INTO component_block_rule (component, preferred_block_size, max_blocks_per_day)
VALUES ('BASICAS', 1, 1)
ON CONFLICT (component) DO UPDATE SET max_blocks_per_day = EXCLUDED.max_blocks_per_day;

COMMENT ON TABLE component_block_rule IS 'Preferred block size (1-4h) and max blocks per day (1-4) per course component, used by BlockGenerationService and the solver respectively. A component with no row here defaults to size 2 / max 2 per day in code.';
COMMENT ON COLUMN component_block_rule.component IS 'Matches course.component (e.g. BASICAS, TEM, TCS).';
COMMENT ON COLUMN component_block_rule.preferred_block_size IS 'Blocks are packed greedily at this size, with a trailing remainder block if hours don''t divide evenly.';
COMMENT ON COLUMN component_block_rule.max_blocks_per_day IS 'HARD limit on how many blocks of this component''s courses may land on the same day for the same group.';
