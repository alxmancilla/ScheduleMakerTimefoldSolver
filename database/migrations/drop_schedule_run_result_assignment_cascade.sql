-- ============================================================================
-- DROP schedule_run_result's CASCADE FK to course_block_assignment
-- ============================================================================
-- schedule_run_result was explicitly designed as a frozen, denormalized
-- snapshot: group_id, course_id, teacher_id, room_name, block_length, pinned,
-- satisfies_room_type, and preferred_room_hint are all copied onto the row
-- directly, precisely so "a later rename or deletion of that teacher/room/
-- course/group must not alter or break a historical run's snapshot" (see the
-- table's own comment in schema_block_scheduling.sql). assignment_id was the
-- one exception: it kept a live FK with ON DELETE CASCADE back to
-- course_block_assignment, which directly undermines that same design intent
-- - the moment the live assignment row is deleted, its "frozen" historical
-- copies vanish too, in every retained run, not just the most recent one.
--
-- Confirmed the hard way: deleting course_block_assignment rows to force a
-- clean block regeneration (a legitimate, expected operation, not a rare
-- edge case) silently cascaded and stripped every one of the 10 retained
-- schedule_run rows down to only their pinned-row results.
--
-- A non-cascading FK isn't a viable middle ground here: assignment_id has no
-- valid ON DELETE action that both (a) allows deleting an assignment with
-- history and (b) doesn't lose that history - NO ACTION/RESTRICT would block
-- the delete outright (virtually every assignment has some run history),
-- and SET NULL isn't legal since assignment_id is part of this table's own
-- primary key (schedule_run_id, assignment_id). Dropping the FK entirely
-- makes assignment_id a plain denormalized label, exactly like this table's
-- other columns already are.
BEGIN;

ALTER TABLE schedule_run_result DROP CONSTRAINT IF EXISTS schedule_run_result_assignment_id_fkey;

COMMENT ON COLUMN schedule_run_result.assignment_id IS 'The assignment this snapshot was for, at save time - a plain label, not a live FK (deliberately, like every other column here): deleting or regenerating that course_block_assignment row later must not delete this historical snapshot.';

COMMIT;
