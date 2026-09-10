#!/usr/bin/env bash
#
# run-engine.sh - Run the scheduling engine as a one-shot batch worker.
#
# The engine loads the problem from PostgreSQL, runs the Timefold solver, writes
# the solved timeslot assignments back to the database (which the web API then
# serves), and emits PDF reports into the output directory. It is a batch job,
# not a daemon, so it is well suited to on-demand or cron invocation.
#
# Configuration (environment variables, all optional):
#   DB_URL                              JDBC URL      (default: jdbc:postgresql://localhost:5432/school_schedule)
#   DB_USER                             DB username   (default: mancilla)
#   DB_PASSWORD                         DB password   (default: empty)
#   ENGINE_JAR                          Path to the shaded engine jar (default: newest under engine/target)
#   ENGINE_OUTPUT_DIR                   Where PDF reports are written  (default: current directory)
#   JAVA_OPTS                           Extra JVM flags (e.g. -Xmx1g)
#   SOLVER_MINUTES_LIMIT                Overrides solverConfig.xml's local search total time budget (default: 5)
#   SOLVER_UNIMPROVED_MINUTES_LIMIT     Overrides solverConfig.xml's give-up-if-stuck limit (default: 2)
#   SOLVER_RANDOM_SEED                  Overrides solverConfig.xml's own fixed (reproducible) random seed
#                                        (default: unset - use solverConfig.xml's seed unchanged). A specific
#                                        numeric value replays that exact seed (e.g. to reproduce a past run
#                                        found in schedule_run); "random" generates and logs a fresh seed each
#                                        run, for genuine exploration that stays reproducible on demand later.
#   SKIP_PRESOLVE_VALIDATION            Proceed to solve even if PreSolveValidator finds blocking
#                                        problems (default: false - aborts on any problem). Validation
#                                        still runs and prints everything it finds either way.
#   VALIDATE_ONLY                       Run PreSolveValidator and exit - never solves, regardless of
#                                        SKIP_PRESOLVE_VALIDATION (default: false). Exit code is 0 if
#                                        no blocking problems were found, 1 otherwise.
#
# Exit code is that of the JVM: non-zero on failure, so cron/orchestrators can
# detect a failed run.
#
set -euo pipefail

# Resolve repository root (this script lives in <repo>/scripts).
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"

# Locate the engine fat jar (exclude the shade "original-" artifact).
if [[ -z "${ENGINE_JAR:-}" ]]; then
  ENGINE_JAR="$(ls -t "${REPO_ROOT}"/engine/target/scheduler-engine-*.jar 2>/dev/null \
    | grep -v '/original-' | head -n 1 || true)"
fi

if [[ -z "${ENGINE_JAR}" || ! -f "${ENGINE_JAR}" ]]; then
  echo "ERROR: engine jar not found." >&2
  echo "Build it first from the repo root:" >&2
  echo "  mvn -pl engine -am -DskipTests package" >&2
  echo "Or set ENGINE_JAR to an explicit path." >&2
  exit 1
fi

# Export DB settings with the same defaults the app uses, so they are visible to
# the JVM regardless of whether the caller set them.
export DB_URL="${DB_URL:-jdbc:postgresql://localhost:5432/school_schedule}"
export DB_USER="${DB_USER:-mancilla}"
export DB_PASSWORD="${DB_PASSWORD:-}"

# Record which engine commit produced this run, so a schedule_run score change
# can be told apart from a genuine solver-logic change later. Best-effort: an
# ENGINE_JAR built from a source tree that isn't a git checkout (or is dirty)
# still runs fine, just without this provenance.
if [[ -z "${ENGINE_GIT_COMMIT:-}" ]]; then
  ENGINE_GIT_COMMIT="$(git -C "${REPO_ROOT}" rev-parse HEAD 2>/dev/null || true)"
fi
export ENGINE_GIT_COMMIT

# PDF reports are written to the process working directory; honour ENGINE_OUTPUT_DIR.
OUTPUT_DIR="${ENGINE_OUTPUT_DIR:-$(pwd)}"
mkdir -p "${OUTPUT_DIR}"

# Skipped for VALIDATE_ONLY runs: PreSolveValidationRunnerService captures this
# script's full stdout as the report shown in the web UI's "Run Validation"
# panel, and this banner is just noise there - a real solve run still wants it
# (which jar/db it hit is useful context for a multi-minute background job).
if [[ "${VALIDATE_ONLY:-}" != "true" ]]; then
  echo "=== Schedule engine worker ==="
  echo "  jar        : ${ENGINE_JAR}"
  echo "  db url     : ${DB_URL}"
  echo "  output dir : ${OUTPUT_DIR}"
  echo
fi

cd "${OUTPUT_DIR}"
# shellcheck disable=SC2086
exec java ${JAVA_OPTS:-} -jar "${ENGINE_JAR}"
