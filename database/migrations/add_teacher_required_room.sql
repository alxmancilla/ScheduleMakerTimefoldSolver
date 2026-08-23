-- ============================================================================
-- Migration Script: Add teacher.required_room_name
-- ============================================================================
-- Some teachers must always teach in one specific room regardless of the
-- group's preferred room (e.g. mobility needs, equipment tied to a room).
-- required_room_name is a HARD, teacher-specific override: it takes
-- precedence over the group's preferred room (but not over a more specific
-- block-template preferredRoomName or a course_room_requirement's own
-- defaultPreferredRoom, which remain the most specific, deliberate
-- overrides). It's enforced wherever the teacher gets assigned to a block:
--
-- 1. BlockGenerationService: when generating a block whose (group, course)
--    pairing already has a default_teacher_id set, the teacher's required
--    room is used ahead of the group's preferred room (subject to the same
--    estándar/mixto type-compatibility check as the group's room). (this
--    type was named 'laboratorio' when this migration was written, then
--    'dual', before settling on 'mixto' - see rename_laboratorio_to_dual.sql
--    and rename_dual_to_mixto.sql.)
-- 2. CourseBlockAssignmentController: whenever a block is created or updated
--    with a teacherId, if that teacher has a compatible required room, the
--    block's room is forced to it - regardless of what room was submitted.
-- 3. TeacherController: setting/changing a teacher's required room backfills
--    every existing (non-pinned) block already assigned to that teacher
--    whose room type is compatible.
--
-- Purpose: Let a teacher's fixed-room requirement apply everywhere that
-- teacher gets assigned, without needing to fix it block by block.
-- Date: 2026-08-22
-- ============================================================================

ALTER TABLE teacher ADD COLUMN IF NOT EXISTS required_room_name VARCHAR(100);

ALTER TABLE teacher DROP CONSTRAINT IF EXISTS fk_teacher_required_room;
ALTER TABLE teacher ADD CONSTRAINT fk_teacher_required_room
    FOREIGN KEY (required_room_name) REFERENCES room(name) ON DELETE SET NULL;

CREATE INDEX IF NOT EXISTS idx_teacher_required_room ON teacher(required_room_name);

COMMENT ON COLUMN teacher.required_room_name IS 'Optional room this teacher must always use, overriding the group''s preferred room. Enforced by BlockGenerationService, CourseBlockAssignmentController, and TeacherController (backfill) wherever this teacher gets assigned - subject to room-type compatibility with the block being assigned.';
