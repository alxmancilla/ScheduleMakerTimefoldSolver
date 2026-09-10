-- ============================================================================
-- Migration Script: Add calendar_exception table
-- ============================================================================
-- Record-keeping only (v1): tracks the school's holiday/exam/half-day dates,
-- surfaced in Settings > Calendar. block_timeslot is a pure recurring weekly
-- template with no date concept at all (day-of-week + hour, not tied to any
-- actual calendar date), so this table does NOT yet gate block generation or
-- the solver - it exists so the term's calendar is tracked in the system for
-- the first time. Wiring it into generation/solving is a separate, larger
-- change (turning the single recurring week into a dated, multi-week
-- calendar) deferred until that architectural question is settled.
--
-- Purpose: first phase of the "calendar/holiday awareness" gap identified in
-- this session's product-scope assessment.
-- Date: 2026-08-22
-- ============================================================================

CREATE TABLE IF NOT EXISTS calendar_exception (
    exception_date DATE PRIMARY KEY,
    type VARCHAR(20) NOT NULL CHECK (type IN ('HOLIDAY', 'HALF_DAY', 'EXAM_DAY')),
    label VARCHAR(200),
    end_hour INTEGER CHECK (end_hour IS NULL OR end_hour BETWEEN 7 AND 15),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON TABLE calendar_exception IS 'School calendar exceptions (holidays, exam days, half-days) - record-keeping only, not yet read by block generation or the solver.';
COMMENT ON COLUMN calendar_exception.exception_date IS 'The specific calendar date this exception applies to.';
COMMENT ON COLUMN calendar_exception.type IS 'HOLIDAY (no classes), HALF_DAY (classes end early, see end_hour), or EXAM_DAY (informational only).';
COMMENT ON COLUMN calendar_exception.label IS 'Optional human-readable description (e.g. "Día de la Independencia").';
COMMENT ON COLUMN calendar_exception.end_hour IS 'Only meaningful for HALF_DAY: the hour classes end that day (7-15).';
