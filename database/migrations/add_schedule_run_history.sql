-- ============================================================================
-- SCHEDULE RUN HISTORY
-- ============================================================================
-- Separates "the problem" from "the last N solutions to it". Previously the
-- solver overwrote course_block_assignment.block_timeslot_id in place on
-- every run, so there was no history and course_block_assignment was both
-- input (teacher/room/pinned/blockLength) and output (the solved timeslot)
-- at once. From this migration on, course_block_assignment is pure input -
-- block_timeslot_id there is only ever meaningful for pinned = true rows.
--
-- schedule_run: one row per solver run (score + timestamp).
-- schedule_run_result: one row per assignment per run - the solved (or still
-- unassigned) timeslot for that run. DataSaver prunes schedule_run down to
-- the most recent 10 rows after every insert; ON DELETE CASCADE cleans up
-- the corresponding schedule_run_result rows automatically.
BEGIN;

CREATE TABLE IF NOT EXISTS schedule_run (
    id SERIAL PRIMARY KEY,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    hard_score INTEGER NOT NULL,
    soft_score INTEGER NOT NULL
);

CREATE TABLE IF NOT EXISTS schedule_run_result (
    schedule_run_id INTEGER NOT NULL REFERENCES schedule_run(id) ON DELETE CASCADE,
    assignment_id VARCHAR(100) NOT NULL REFERENCES course_block_assignment(id) ON DELETE CASCADE,
    block_timeslot_id VARCHAR(50) REFERENCES block_timeslot(id),
    PRIMARY KEY (schedule_run_id, assignment_id)
);

CREATE INDEX IF NOT EXISTS idx_schedule_run_result_assignment ON schedule_run_result(assignment_id);
CREATE INDEX IF NOT EXISTS idx_schedule_run_created_at ON schedule_run(created_at);

-- Resolves "the current effective schedule" in one place: pinned rows use
-- their own (input) block_timeslot_id; every other row uses the most recent
-- schedule_run's result for that assignment. Everything that displays or
-- loads "the schedule" (web Schedule View, PDF reports, and the engine's own
-- DataLoader, which gets warm-starting for free this way) reads through this
-- view instead of re-deriving the pinned/latest-run rule itself.
CREATE OR REPLACE VIEW course_block_assignment_current AS
SELECT
    cba.id,
    cba.group_id,
    cba.course_id,
    cba.block_length,
    cba.pinned,
    cba.teacher_id,
    CASE WHEN cba.pinned THEN cba.block_timeslot_id ELSE latest.block_timeslot_id END AS block_timeslot_id,
    cba.room_name,
    cba.satisfies_room_type,
    cba.preferred_room_hint
FROM course_block_assignment cba
LEFT JOIN (
    SELECT srr.assignment_id, srr.block_timeslot_id
    FROM schedule_run_result srr
    WHERE srr.schedule_run_id = (SELECT MAX(id) FROM schedule_run)
) latest ON latest.assignment_id = cba.id;

COMMENT ON TABLE schedule_run IS 'One row per solver run. DataSaver prunes to the most recent 10 after every insert.';
COMMENT ON TABLE schedule_run_result IS 'One row per assignment per run: the solved (or still-unassigned) timeslot for that run.';
COMMENT ON VIEW course_block_assignment_current IS 'Resolved "current schedule": pinned rows use their own input timeslot, everything else uses the most recent schedule_run.';

COMMIT;
