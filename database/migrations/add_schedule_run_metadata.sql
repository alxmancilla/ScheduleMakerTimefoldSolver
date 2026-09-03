-- ============================================================================
-- SCHEDULE RUN METADATA
-- ============================================================================
-- Adds run-level context schedule_run couldn't previously answer without
-- re-running a live experiment: which random seed produced this arrangement
-- (needed for Option B - a per-run-opt-in non-fixed seed, so an exploratory
-- run stays reproducible on demand instead of being a one-shot gamble),
-- whether PreSolveValidator's blocking checks were bypassed for this run
-- (SKIP_PRESOLVE_VALIDATION), how long it actually took vs. its configured
-- budget, which git commit produced it (so a score change can be told apart
-- from a genuine engine-logic change, like the pinned-conflict-exclusion fix,
-- the same way schedule_run_constraint already separates a constraint-set
-- change from ordinary solver variance), and which termination condition
-- actually fired (inferred from the final score and actual duration vs. the
-- configured limits - solverConfig.xml OR-combines bestScoreLimit,
-- minutesSpentLimit, and unimprovedMinutesSpentLimit, and Timefold doesn't
-- expose which one fired via its public event API, so this is a computed
-- best-effort label, not a value Timefold itself reports).
BEGIN;

ALTER TABLE schedule_run ADD COLUMN IF NOT EXISTS random_seed BIGINT;
ALTER TABLE schedule_run ADD COLUMN IF NOT EXISTS environment_mode VARCHAR(50);
ALTER TABLE schedule_run ADD COLUMN IF NOT EXISTS skip_validation BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE schedule_run ADD COLUMN IF NOT EXISTS finished_at TIMESTAMP;
ALTER TABLE schedule_run ADD COLUMN IF NOT EXISTS engine_git_commit VARCHAR(100);
ALTER TABLE schedule_run ADD COLUMN IF NOT EXISTS termination_reason VARCHAR(50);

COMMENT ON COLUMN schedule_run.random_seed IS 'The Timefold randomSeed actually used for this run - null means solverConfig.xml''s own fixed default (reproducible) was used unchanged. Set only when SOLVER_RANDOM_SEED was supplied (a specific value to replay a past exploratory run, or a freshly generated one), so an exploratory run can still be reproduced on demand later.';
COMMENT ON COLUMN schedule_run.environment_mode IS 'The Timefold EnvironmentMode actually active for this run (e.g. PHASE_ASSERT, solverConfig.xml''s unconfigured default) - logged for visibility, not currently overridable per run.';
COMMENT ON COLUMN schedule_run.skip_validation IS 'True when SKIP_PRESOLVE_VALIDATION let this run proceed despite PreSolveValidator finding blocking problems - this run''s schedule is known to contain at least one structurally-provable violation, not just an ordinary unconverged one.';
COMMENT ON COLUMN schedule_run.finished_at IS 'When the solve actually completed - lets actual wall-clock duration be compared against the configured minutes_spent_limit/unimproved_minutes_spent_limit budget, distinct from what was merely allowed.';
COMMENT ON COLUMN schedule_run.engine_git_commit IS 'The engine module''s git commit hash at run time (from scripts/run-engine.sh via git rev-parse HEAD), so a score change can be told apart from a genuine solver-logic change - the same purpose schedule_run_constraint serves for constraint-set changes.';
COMMENT ON COLUMN schedule_run.termination_reason IS 'Best-effort inferred reason this run stopped (BEST_SCORE_LIMIT / TIME_SPENT_LIMIT / UNIMPROVED_TIME_SPENT_LIMIT) - Timefold''s OR-combined termination (solverConfig.xml) doesn''t report which condition actually fired via its public API, so this is computed from the final score and actual duration vs. the configured limits, not a value Timefold itself returns.';

COMMIT;
