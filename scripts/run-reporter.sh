#!/usr/bin/env bash
#
# run-reporter.sh - Generate the block-based PDF reports as a one-shot worker.
#
# The reporter reconstructs the solved schedule from PostgreSQL (the same rows
# the engine persists), recomputes constraint violations, and writes the three
# PDF reports into the output directory. It is a batch job, not a daemon, so it
# is well suited to running on demand or right after an engine run.
#
# Configuration (environment variables, all optional):
#   DB_URL               JDBC URL      (default: jdbc:postgresql://localhost:5432/school_schedule)
#   DB_USER              DB username   (default: mancilla)
#   DB_PASSWORD          DB password   (default: empty)
#   REPORTER_JAR         Path to the shaded reporter jar (default: newest under reporter/target)
#   REPORTER_OUTPUT_DIR  Where PDF reports are written  (default: current directory)
#   REPORT_TARGET         "all" (default) writes all three PDFs; "violations" writes
#                         only calendario-incumplimientos.pdf; "schedules" writes only
#                         the by-teacher/by-group PDFs (no violations report)
#   REPORT_RUN_ID         Generate from a specific past schedule_run id instead of the
#                         current schedule (default: current)
#   REPORT_LOCALE         Report chrome language: "es" or "en" (default: en). Covers
#                         PdfReporter's fixed text only (titles/labels/day names), not
#                         the violation detail sentences from BlockScheduleAnalyzer.
#   JAVA_OPTS            Extra JVM flags (e.g. -Xmx1g)
#
# Exit code is that of the JVM: non-zero on failure, so cron/orchestrators can
# detect a failed run.
#
set -euo pipefail

# Resolve repository root (this script lives in <repo>/scripts).
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"

# Locate the reporter fat jar (exclude the shade "original-" artifact).
if [[ -z "${REPORTER_JAR:-}" ]]; then
  REPORTER_JAR="$(ls -t "${REPO_ROOT}"/reporter/target/scheduler-reporter-*.jar 2>/dev/null \
    | grep -v '/original-' | head -n 1 || true)"
fi

if [[ -z "${REPORTER_JAR}" || ! -f "${REPORTER_JAR}" ]]; then
  echo "ERROR: reporter jar not found." >&2
  echo "Build it first from the repo root:" >&2
  echo "  mvn -pl reporter -am -DskipTests package" >&2
  echo "Or set REPORTER_JAR to an explicit path." >&2
  exit 1
fi

# Export DB settings with the same defaults the app uses, so they are visible to
# the JVM regardless of whether the caller set them.
export DB_URL="${DB_URL:-jdbc:postgresql://localhost:5432/school_schedule}"
export DB_USER="${DB_USER:-mancilla}"
export DB_PASSWORD="${DB_PASSWORD:-}"

# PDF reports are written to the process working directory; honour REPORTER_OUTPUT_DIR.
OUTPUT_DIR="${REPORTER_OUTPUT_DIR:-$(pwd)}"
mkdir -p "${OUTPUT_DIR}"

echo "=== Schedule reporter worker ==="
echo "  jar        : ${REPORTER_JAR}"
echo "  db url     : ${DB_URL}"
echo "  output dir : ${OUTPUT_DIR}"
echo

cd "${OUTPUT_DIR}"
# shellcheck disable=SC2086
exec java ${JAVA_OPTS:-} -jar "${REPORTER_JAR}"
