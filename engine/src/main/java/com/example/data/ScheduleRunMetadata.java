package com.example.data;

import java.time.LocalDateTime;

/**
 * Run-level metadata beyond score and time budget, persisted onto
 * schedule_run for the same reason schedule_run_constraint exists: letting a
 * later comparison across runs tell a genuine change (which random seed,
 * which engine code, whether validation was bypassed) apart from ordinary
 * solver variance, instead of having to re-run a live experiment to find out
 * (as this project's own investigation into runs 57-60 had to, before this
 * existed).
 *
 * @param randomSeed        the Timefold randomSeed actually used, or null if
 *                          solverConfig.xml's own fixed default was used
 *                          unchanged (see SchoolSolverConfig.Built)
 * @param environmentMode   the Timefold EnvironmentMode actually active
 *                          (solverConfig.xml's unconfigured default today)
 * @param skipValidation    true if SKIP_PRESOLVE_VALIDATION let this run
 *                          proceed despite PreSolveValidator finding blocking
 *                          problems - this run's schedule is known to contain
 *                          at least one structurally-provable violation
 * @param finishedAt        when the solve actually completed
 * @param engineGitCommit   the engine module's git commit hash at run time,
 *                          or null if it couldn't be determined (e.g. not
 *                          running from within a git checkout)
 * @param terminationReason best-effort inferred reason this run stopped
 *                          (BEST_SCORE_LIMIT / TIME_SPENT_LIMIT /
 *                          UNIMPROVED_TIME_SPENT_LIMIT) - Timefold doesn't
 *                          report which OR-combined termination condition
 *                          actually fired via its public API, so this is
 *                          computed from the final score and actual duration
 *                          against the configured limits, not a value
 *                          Timefold itself returns
 */
public record ScheduleRunMetadata(Long randomSeed, String environmentMode, boolean skipValidation,
        LocalDateTime finishedAt, String engineGitCommit, String terminationReason) {
}
