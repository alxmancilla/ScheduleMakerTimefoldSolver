-- ============================================================================
-- FIX: course_block_assignment_current must resolve room_name for non-pinned
-- rows too, not just block_timeslot_id
-- ============================================================================
-- course_block_assignment_current (added by add_schedule_run_history.sql) was
-- meant to resolve "the current effective schedule": pinned rows use their
-- own input columns, every other row uses the most recent schedule_run's
-- result. Its CASE logic only ever did that for block_timeslot_id - room_name
-- fell straight through to the raw course_block_assignment column regardless
-- of pinned status, with no "latest run" overlay at all.
--
-- room is a genuine @PlanningVariable (like timeslot), so its value can move
-- across solves for a non-pinned row exactly the way the timeslot can. Since
-- the raw table's room_name is otherwise never rewritten in place for a
-- non-pinned row (DataSaver only ever inserts a new schedule_run_result row;
-- DataLoader's room-fixed force-correction is in-memory only, applied right
-- before a solve, and isn't persisted back either), the raw column can go
-- stale relative to what the solver actually decided - the app/PDF reports
-- were silently displaying that stale room forever, even after later solves
-- moved the block elsewhere. Discovered 2026-09-01 while investigating why a
-- group's blocks appeared to still use a room absent from its curated
-- group_room_range: the room was correct in schedule_run_result the whole
-- time, only the view was showing the old value.
--
-- satisfies_room_type and preferred_room_hint are deliberately NOT given the
-- same overlay: they aren't planning variables, DataSaver only snapshots
-- them into schedule_run_result as a convenience, and overlaying them from a
-- past run would introduce the opposite bug - masking a legitimate edit to
-- the block's input data behind an older run's snapshot.
BEGIN;

CREATE OR REPLACE VIEW course_block_assignment_current AS
SELECT
    cba.id,
    cba.group_id,
    cba.course_id,
    cba.block_length,
    cba.pinned,
    cba.teacher_id,
    CASE WHEN cba.pinned THEN cba.block_timeslot_id ELSE latest.block_timeslot_id END AS block_timeslot_id,
    CASE WHEN cba.pinned THEN cba.room_name ELSE latest.room_name END AS room_name,
    cba.satisfies_room_type,
    cba.preferred_room_hint
FROM course_block_assignment cba
LEFT JOIN (
    SELECT srr.assignment_id, srr.block_timeslot_id, srr.room_name
    FROM schedule_run_result srr
    WHERE srr.schedule_run_id = (SELECT MAX(id) FROM schedule_run)
) latest ON latest.assignment_id = cba.id;

COMMENT ON VIEW course_block_assignment_current IS 'Resolved "current schedule": pinned rows use their own input timeslot/room, everything else uses the most recent schedule_run''s result for both.';

COMMIT;
