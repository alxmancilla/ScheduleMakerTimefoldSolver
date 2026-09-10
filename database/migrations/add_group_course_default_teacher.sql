-- ============================================================================
-- Migration Script: Add group_course.default_teacher_id
-- ============================================================================
-- A group's courses could be given a default room automatically (via
-- student_group.preferred_room_name applied at "Generate Blocks" time), but
-- there was no equivalent way to pre-assign a teacher to a (group, course)
-- pairing before blocks exist - the only place to set a block's teacher was
-- the course_block_assignment rows themselves, which don't exist until
-- "Generate Blocks" has run.
--
-- default_teacher_id closes that gap: an admin can pick a teacher for a
-- group's course right away, and BlockGenerationService applies it to every
-- block it creates for that pairing, the same way it already applies the
-- group's preferred room. It has no effect once blocks already exist (that
-- pairing is then skipped by "Generate Blocks" and its blocks carry their
-- own teacher_id going forward), so this is a "pending assignment" field,
-- not a live source of truth once blocks exist.
--
-- Purpose: Let admins pre-assign a qualified teacher to a group's course
-- before running "Generate Blocks".
-- Date: 2026-08-22
-- ============================================================================

ALTER TABLE group_course ADD COLUMN IF NOT EXISTS default_teacher_id VARCHAR(100);

ALTER TABLE group_course DROP CONSTRAINT IF EXISTS fk_group_course_default_teacher;
ALTER TABLE group_course ADD CONSTRAINT fk_group_course_default_teacher
    FOREIGN KEY (default_teacher_id) REFERENCES teacher(id) ON DELETE SET NULL;

CREATE INDEX IF NOT EXISTS idx_group_course_default_teacher ON group_course(default_teacher_id);

COMMENT ON COLUMN group_course.default_teacher_id IS 'Optional teacher pre-assigned to this group+course pairing before blocks exist; applied automatically by BlockGenerationService to every block it creates for this pairing (same pattern as the group''s preferred room). No effect once blocks already exist for the pairing.';
