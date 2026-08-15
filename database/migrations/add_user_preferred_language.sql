-- ============================================================================
-- Migration Script: Add preferred_language to app_user
-- ============================================================================
-- Adds a per-user UI language preference (en/es), defaulting existing users
-- to 'en'. Fresh installs get this column directly from add_app_users.sql;
-- this script is for databases where add_app_users.sql already ran.
-- ============================================================================

ALTER TABLE app_user
    ADD COLUMN IF NOT EXISTS preferred_language VARCHAR(5) NOT NULL DEFAULT 'en';

ALTER TABLE app_user
    ADD CONSTRAINT check_app_user_preferred_language CHECK (preferred_language IN ('en', 'es'));

COMMENT ON COLUMN app_user.preferred_language IS 'UI language preference: en or es. Defaults to en.';
