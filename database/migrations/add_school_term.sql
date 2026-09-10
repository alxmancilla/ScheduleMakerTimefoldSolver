-- ============================================================================
-- Migration Script: Add school_term (current term/period label)
-- ============================================================================
-- A single-row table holding a free-text label (e.g. "Fall 2026") shown in
-- the app header and editable by admins in Settings. Purely organizational
-- metadata - never read by the solver or any constraint - so it exists just
-- to tell schedule runs/report exports apart across terms.
--
-- The id is pinned to 1 via CHECK so at most one row can ever exist; the
-- initial row is seeded with an empty label.
--
-- Purpose: Let admins label which term the current schedule represents.
-- Date: 2026-08-15
-- ============================================================================

CREATE TABLE IF NOT EXISTS school_term (
    id INTEGER PRIMARY KEY DEFAULT 1 CHECK (id = 1),
    label VARCHAR(100) NOT NULL DEFAULT '',
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

INSERT INTO school_term (id, label) VALUES (1, '') ON CONFLICT (id) DO NOTHING;

COMMENT ON TABLE school_term IS 'Singleton row holding the current term/period label shown in the UI (e.g. "Fall 2026"). Not used by the solver.';
COMMENT ON COLUMN school_term.label IS 'Free-text term label, editable by ADMIN in Settings.';
