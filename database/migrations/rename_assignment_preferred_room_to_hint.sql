-- ============================================================================
-- Migration Script: Rename course_block_assignment.preferred_room_name ->
-- preferred_room_hint
-- ============================================================================
-- Scoped to course_block_assignment ONLY. student_group.preferred_room_name
-- (the group's own preference) and course_block_template.preferred_room_name
-- (a template's explicit override) are unrelated columns on different
-- tables and are NOT touched by this migration.
--
-- Rationale: this column and course_block_assignment.room_name were easy to
-- conflate - room_name is the actual, fixed-input room; this column is only
-- a soft-constraint hint (preferBlockSpecifiedRoom, weight 3) that often
-- happens to match room_name but isn't enforced. Renaming it to
-- preferred_room_hint makes that distinction explicit in the name itself.
--
-- Purpose: Reduce confusion between the Assignment's roomName (actual,
-- hard) and its room preference (soft, advisory).
-- Date: 2026-08-22
-- ============================================================================

ALTER TABLE course_block_assignment RENAME COLUMN preferred_room_name TO preferred_room_hint;
ALTER TABLE course_block_assignment RENAME CONSTRAINT fk_cba_preferred_room TO fk_cba_preferred_room_hint;
ALTER INDEX IF EXISTS idx_cba_preferred_room RENAME TO idx_cba_preferred_room_hint;

COMMENT ON COLUMN course_block_assignment.preferred_room_hint IS 'Preferred room for this assignment (soft constraint hint, not enforced)';
