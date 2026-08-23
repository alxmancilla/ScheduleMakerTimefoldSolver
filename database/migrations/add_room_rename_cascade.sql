-- ============================================================================
-- Migration Script: Add ON UPDATE CASCADE to all FKs referencing room(name)
-- ============================================================================
-- room.name is a natural-key primary key (no separate surrogate id), and
-- until now every FK pointing at it defaulted to ON UPDATE NO ACTION -
-- meaning renaming a room (UPDATE room SET name = ...) would fail outright
-- if anything referenced it, forcing a manual multi-step workaround (null
-- out every referencing row, rename, restore them) every single time.
--
-- Adds ON UPDATE CASCADE to all 6 FKs pointing at room(name), so a plain
-- `UPDATE room SET name = 'NewName' WHERE name = 'OldName'` now propagates
-- automatically to every table that references it:
--   student_group.preferred_room_name, course_block_assignment.room_name,
--   course_block_assignment.preferred_room_hint,
--   course_room_requirement.default_preferred_room,
--   course_block_template.preferred_room_name, teacher.required_room_name
--
-- ON DELETE behavior (SET NULL throughout) is unchanged - only the ON
-- UPDATE action is added.
--
-- Purpose: Let a room be renamed with a single UPDATE instead of a manual,
-- error-prone multi-table workaround.
-- Date: 2026-08-22
-- ============================================================================

BEGIN;

ALTER TABLE student_group DROP CONSTRAINT fk_student_group_room;
ALTER TABLE student_group ADD CONSTRAINT fk_student_group_room
    FOREIGN KEY (preferred_room_name) REFERENCES room(name) ON DELETE SET NULL ON UPDATE CASCADE;

ALTER TABLE course_block_assignment DROP CONSTRAINT fk_block_assignment_room;
ALTER TABLE course_block_assignment ADD CONSTRAINT fk_block_assignment_room
    FOREIGN KEY (room_name) REFERENCES room(name) ON DELETE SET NULL ON UPDATE CASCADE;

ALTER TABLE course_block_assignment DROP CONSTRAINT fk_cba_preferred_room_hint;
ALTER TABLE course_block_assignment ADD CONSTRAINT fk_cba_preferred_room_hint
    FOREIGN KEY (preferred_room_hint) REFERENCES room(name) ON DELETE SET NULL ON UPDATE CASCADE;

ALTER TABLE course_room_requirement DROP CONSTRAINT fk_default_room;
ALTER TABLE course_room_requirement ADD CONSTRAINT fk_default_room
    FOREIGN KEY (default_preferred_room) REFERENCES room(name) ON DELETE SET NULL ON UPDATE CASCADE;

ALTER TABLE course_block_template DROP CONSTRAINT fk_preferred_room;
ALTER TABLE course_block_template ADD CONSTRAINT fk_preferred_room
    FOREIGN KEY (preferred_room_name) REFERENCES room(name) ON DELETE SET NULL ON UPDATE CASCADE;

ALTER TABLE teacher DROP CONSTRAINT fk_teacher_required_room;
ALTER TABLE teacher ADD CONSTRAINT fk_teacher_required_room
    FOREIGN KEY (required_room_name) REFERENCES room(name) ON DELETE SET NULL ON UPDATE CASCADE;

COMMIT;
