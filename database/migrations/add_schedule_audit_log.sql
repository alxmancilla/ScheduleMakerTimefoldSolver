-- ============================================================================
-- Migration Script: Add schedule_audit_log
-- ============================================================================
-- Records who made which write request (POST/PUT/DELETE) and when. Written
-- automatically by a Spring MVC interceptor after a successful (2xx) request
-- to /api/** (excluding login) - no existing controller code was touched to
-- add this.
--
-- Purpose: Basic accountability once more than one planner uses the system.
-- Date: 2026-08-15
-- ============================================================================

CREATE TABLE IF NOT EXISTS schedule_audit_log (
    id BIGSERIAL PRIMARY KEY,
    username VARCHAR(100) NOT NULL,
    http_method VARCHAR(10) NOT NULL,
    path VARCHAR(500) NOT NULL,
    status_code INTEGER NOT NULL,
    occurred_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_schedule_audit_log_occurred_at ON schedule_audit_log(occurred_at DESC);

COMMENT ON TABLE schedule_audit_log IS 'Write requests (POST/PUT/DELETE) to /api/**, logged automatically by a servlet interceptor for basic accountability.';
COMMENT ON COLUMN schedule_audit_log.username IS 'Authenticated username that made the request.';
COMMENT ON COLUMN schedule_audit_log.status_code IS 'HTTP response status; only 2xx (successful) requests are logged.';
