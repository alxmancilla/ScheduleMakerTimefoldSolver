#!/usr/bin/env bash
#
# db-import.sh - Restore the whole PostgreSQL database from a .sql file
# produced by db-export.sh (or any plain `pg_dump` of this database).
#
# ALWAYS takes a fresh safety backup (via db-export.sh, BACKUP_LABEL=pre_restore)
# immediately before touching anything, so a bad restore is itself recoverable -
# this cannot be skipped.
#
# Restoring runs the dump's own DROP/CREATE statements against the live
# database while the running app's connection pool may still be using it;
# this is intended for local/dev use, not a zero-downtime production
# migration path.
#
# Usage: db-import.sh <path-to-sql-file>
#   <path-to-sql-file> may be absolute, or relative to BACKUP_DIR.
#
# Configuration (environment variables, all optional):
#   DB_URL       JDBC URL      (default: jdbc:postgresql://localhost:5432/school_schedule)
#   DB_USER      DB username   (default: mancilla)
#   DB_PASSWORD  DB password   (default: empty)
#   BACKUP_DIR   Where the pre-restore safety backup is written, and where a
#                relative <path-to-sql-file> is resolved from
#                (default: <repo root>/database/backups)
#
# Exit code is that of psql: non-zero on failure. A failed restore may leave
# the database partially applied - restore the pre_restore_*.sql safety
# backup this script just took to get back to the pre-restore state.
#
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"

if [[ $# -lt 1 || -z "$1" ]]; then
  echo "ERROR: usage: db-import.sh <path-to-sql-file>" >&2
  exit 1
fi

DB_URL="${DB_URL:-jdbc:postgresql://localhost:5432/school_schedule}"
DB_USER="${DB_USER:-mancilla}"
DB_PASSWORD="${DB_PASSWORD:-}"
BACKUP_DIR="${BACKUP_DIR:-${REPO_ROOT}/database/backups}"

RESTORE_FILE="$1"
if [[ "${RESTORE_FILE}" != /* ]]; then
  RESTORE_FILE="${BACKUP_DIR}/${RESTORE_FILE}"
fi
if [[ ! -f "${RESTORE_FILE}" ]]; then
  echo "ERROR: restore file not found: ${RESTORE_FILE}" >&2
  exit 1
fi

without_prefix="${DB_URL#jdbc:postgresql://}"
host_port="${without_prefix%%/*}"
DB_NAME="${without_prefix#*/}"
DB_HOST="${host_port%%:*}"
DB_PORT="${host_port#*:}"
if [[ "${DB_PORT}" == "${host_port}" ]]; then
  DB_PORT=5432
fi

echo "=== Database restore ==="
echo "  database    : ${DB_NAME} @ ${DB_HOST}:${DB_PORT}"
echo "  restore from: ${RESTORE_FILE}"
echo

echo "--- Taking mandatory pre-restore safety backup ---"
DB_URL="${DB_URL}" DB_USER="${DB_USER}" DB_PASSWORD="${DB_PASSWORD}" \
  BACKUP_DIR="${BACKUP_DIR}" BACKUP_LABEL="pre_restore" \
  bash "${SCRIPT_DIR}/db-export.sh"
echo

echo "--- Restoring ---"
PGPASSWORD="${DB_PASSWORD}" psql -h "${DB_HOST}" -p "${DB_PORT}" -U "${DB_USER}" -d "${DB_NAME}" \
  -v ON_ERROR_STOP=1 -f "${RESTORE_FILE}"

echo
echo "Done: restored from $(basename "${RESTORE_FILE}")"
