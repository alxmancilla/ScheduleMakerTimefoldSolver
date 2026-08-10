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
#   DB_URL              JDBC URL      (default: jdbc:postgresql://localhost:5432/school_schedule)
#   DB_USER             DB username   (default: mancilla)
#   DB_PASSWORD         DB password   (default: empty)
#   ENGINE_JAR          Path to the shaded engine jar (default: newest under engine/target)
#   ENGINE_OUTPUT_DIR   Where PDF reports are written  (default: current directory)
#   JAVA_OPTS           Extra JVM flags (e.g. -Xmx1g)
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

# PDF reports are written to the process working directory; honour ENGINE_OUTPUT_DIR.
OUTPUT_DIR="${ENGINE_OUTPUT_DIR:-$(pwd)}"
mkdir -p "${OUTPUT_DIR}"

echo "=== Schedule engine worker ==="
echo "  jar        : ${ENGINE_JAR}"
echo "  db url     : ${DB_URL}"
echo "  output dir : ${OUTPUT_DIR}"
echo

cd "${OUTPUT_DIR}"
# shellcheck disable=SC2086
exec java ${JAVA_OPTS:-} -jar "${ENGINE_JAR}"
