-- ============================================================================
-- SCHEDULE RUN INPUT SNAPSHOT + SOLVER PARAMETERS
-- ============================================================================
-- Widens the run history (add_schedule_run_history.sql) so a past run's
-- exact conditions stay inspectable, not just its output timeslot:
--   - schedule_run gains the *effective* solver time budget used for that
--     run (whether it came from an override or solverConfig.xml's own
--     defaults - DataSaver always resolves and stores the real value, never
--     leaves this null for new rows).
--   - schedule_run_result gains a frozen copy of each assignment's input
--     fields (group/course/blockLength/pinned/teacher/room/satisfiesRoomType/
--     preferredRoomHint) as they were at solve time, alongside the timeslot
--     it already stored.
--
-- These snapshot columns are deliberately NOT foreign keys into
-- teacher/room/course/student_group: they're a frozen copy, not a live
-- reference, so a later rename or deletion of a teacher/room/course/group
-- must not alter or break a historical run's snapshot.
--
-- Existing rows (runs saved before this migration) can't be backfilled with
-- real historical detail - the DEFAULTs below backfill schedule_run's two
-- new columns with solverConfig.xml's own known defaults (5 / 2) as the best
-- available guess, while schedule_run_result's new columns are left NULL for
-- pre-migration rows, honestly reflecting that the detail was never captured.
BEGIN;

ALTER TABLE schedule_run
    ADD COLUMN IF NOT EXISTS minutes_spent_limit INTEGER NOT NULL DEFAULT 5,
    ADD COLUMN IF NOT EXISTS unimproved_minutes_spent_limit INTEGER NOT NULL DEFAULT 2;

ALTER TABLE schedule_run_result
    ADD COLUMN IF NOT EXISTS group_id VARCHAR(100),
    ADD COLUMN IF NOT EXISTS course_id VARCHAR(100),
    ADD COLUMN IF NOT EXISTS block_length INTEGER,
    ADD COLUMN IF NOT EXISTS pinned BOOLEAN,
    ADD COLUMN IF NOT EXISTS teacher_id VARCHAR(100),
    ADD COLUMN IF NOT EXISTS room_name VARCHAR(100),
    ADD COLUMN IF NOT EXISTS satisfies_room_type VARCHAR(100),
    ADD COLUMN IF NOT EXISTS preferred_room_hint VARCHAR(100);

COMMENT ON COLUMN schedule_run.minutes_spent_limit IS 'Effective local search total time budget (minutes) used for this run - solverConfig.xml default unless overridden.';
COMMENT ON COLUMN schedule_run.unimproved_minutes_spent_limit IS 'Effective give-up-if-stuck time budget (minutes) used for this run - solverConfig.xml default unless overridden.';
COMMENT ON COLUMN schedule_run_result.group_id IS 'Frozen copy of the assignment''s group_id at solve time - not a live FK.';
COMMENT ON COLUMN schedule_run_result.course_id IS 'Frozen copy of the assignment''s course_id at solve time - not a live FK.';
COMMENT ON COLUMN schedule_run_result.teacher_id IS 'Frozen copy of the assignment''s pre-assigned teacher_id at solve time - not a live FK.';
COMMENT ON COLUMN schedule_run_result.room_name IS 'Frozen copy of the assignment''s pre-assigned room_name at solve time - not a live FK.';

COMMIT;
