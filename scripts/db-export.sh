#!/usr/bin/env bash
#
# db-export.sh - Dump the whole PostgreSQL database to a timestamped .sql file
# under database/backups/, so it can be recreated on any other Postgres
# instance with `psql -f <file>`.
#
# Configuration (environment variables, all optional):
#   DB_URL          JDBC URL      (default: jdbc:postgresql://localhost:5432/school_schedule)
#   DB_USER         DB username   (default: mancilla)
#   DB_PASSWORD     DB password   (default: empty)
#   BACKUP_DIR      Where the .sql file is written (default: <repo root>/database/backups)
#   BACKUP_LABEL    Filename prefix (default: school_schedule) - db-import.sh uses
#                   "pre_restore" for its automatic safety backup so the two are
#                   easy to tell apart in a directory listing.
#   BACKUP_FILENAME Exact output filename, overriding BACKUP_LABEL/the generated
#                   timestamp - lets a caller that already knows the name it wants
#                   (e.g. DatabaseBackupService) avoid parsing this script's output.
#
# Exit code is that of pg_dump: non-zero on failure.
#
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"

DB_URL="${DB_URL:-jdbc:postgresql://localhost:5432/school_schedule}"
DB_USER="${DB_USER:-mancilla}"
DB_PASSWORD="${DB_PASSWORD:-}"
BACKUP_DIR="${BACKUP_DIR:-${REPO_ROOT}/database/backups}"
BACKUP_LABEL="${BACKUP_LABEL:-school_schedule}"

# Parse "jdbc:postgresql://host:port/dbname" into pg_dump's native flags -
# pg_dump/psql don't understand JDBC URLs, but every other script in this
# repo is configured via DB_URL, so parse it here instead of asking the
# caller for yet another set of env vars.
without_prefix="${DB_URL#jdbc:postgresql://}"
host_port="${without_prefix%%/*}"
DB_NAME="${without_prefix#*/}"
DB_HOST="${host_port%%:*}"
DB_PORT="${host_port#*:}"
if [[ "${DB_PORT}" == "${host_port}" ]]; then
  DB_PORT=5432
fi

mkdir -p "${BACKUP_DIR}"
if [[ -n "${BACKUP_FILENAME:-}" ]]; then
  OUT_FILE="${BACKUP_DIR}/${BACKUP_FILENAME}"
else
  OUT_FILE="${BACKUP_DIR}/${BACKUP_LABEL}_$(date +%Y%m%d_%H%M%S).sql"
fi

echo "=== Database export ==="
echo "  database   : ${DB_NAME} @ ${DB_HOST}:${DB_PORT}"
echo "  output file: ${OUT_FILE}"
echo


# --clean --if-exists: prepend DROP ... IF EXISTS before each CREATE, so the
# same file works both to seed an empty database and to restore over an
# already-populated one (db-import.sh's whole purpose) - a plain pg_dump
# would fail immediately with "already exists" errors on the latter.
PGPASSWORD="${DB_PASSWORD}" pg_dump --clean --if-exists \
  -h "${DB_HOST}" -p "${DB_PORT}" -U "${DB_USER}" "${DB_NAME}" > "${OUT_FILE}"

echo "Done: $(basename "${OUT_FILE}") ($(wc -c < "${OUT_FILE}" | tr -d ' ') bytes)"
