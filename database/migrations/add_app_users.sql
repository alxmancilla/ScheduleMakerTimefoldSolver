-- ============================================================================
-- Migration Script: Add application users and roles (web access control)
-- ============================================================================
-- Creates the app_user table backing the web module's authentication and
-- role-based authorization. Each user has exactly one role:
--   ADMIN   - full access, including user management (/api/admin/**)
--   WRITER  - read + create/update/delete of scheduling data
--   READER  - read-only access
--   TEACHER - can only view their own schedule (GET /api/schedule/view/me),
--             via the optional teacher_id link below
--
-- Passwords are stored as BCrypt hashes (never plaintext); the web app hashes
-- them with BCryptPasswordEncoder. The first admin is bootstrapped by the
-- application on startup (ADMIN_BOOTSTRAP_PASSWORD env var) when no users exist.
--
-- Purpose: Add a security access layer to the REST API and web UI.
-- Date: 2026-08-10
-- ============================================================================

SET client_encoding = 'UTF8';

CREATE TABLE IF NOT EXISTS app_user (
    username           VARCHAR(100) PRIMARY KEY,
    password_hash      VARCHAR(100) NOT NULL,
    role               VARCHAR(20)  NOT NULL
                         CONSTRAINT check_app_user_role CHECK (role IN ('ADMIN', 'WRITER', 'READER', 'TEACHER')),
    enabled            BOOLEAN      NOT NULL DEFAULT TRUE,
    preferred_language VARCHAR(5)   NOT NULL DEFAULT 'en'
                         CHECK (preferred_language IN ('en', 'es')),
    teacher_id         VARCHAR(100) REFERENCES teacher(id) ON DELETE SET NULL,
    created_at         TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at         TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON TABLE app_user IS 'Web application users for authentication and RBAC.';
COMMENT ON COLUMN app_user.password_hash IS 'BCrypt hash of the user password.';
COMMENT ON COLUMN app_user.role IS 'One of ADMIN, WRITER, READER, TEACHER.';
COMMENT ON COLUMN app_user.preferred_language IS 'UI language preference: en or es. Defaults to en.';
COMMENT ON COLUMN app_user.teacher_id IS 'Optional link to a teacher record. Meaningful only for TEACHER-role accounts.';
