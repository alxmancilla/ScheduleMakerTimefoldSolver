-- ============================================================================
-- Migration Script: Add TEACHER role and app_user.teacher_id link
-- ============================================================================
-- Adds a fourth role, TEACHER, scoped to viewing only their own schedule
-- (see SecurityConfig's dedicated matcher for GET /api/schedule/view/me).
-- teacher_id optionally links a login account to a teacher record; it is
-- only meaningful for TEACHER-role accounts, but is left nullable/unrestricted
-- by role so existing ADMIN/WRITER/READER accounts are unaffected.
--
-- Purpose: Let a teacher log in and see just their own schedule, without
-- granting them the broad READER access every other role gets.
-- Date: 2026-08-15
-- ============================================================================

-- Drop whichever name the existing constraint happens to have (an older,
-- unnamed inline CHECK defaults to Postgres's "app_user_role_check"; this
-- database's current one is explicitly named "check_app_user_role") before
-- re-adding it under one consistent name.
ALTER TABLE app_user DROP CONSTRAINT IF EXISTS check_app_user_role;
ALTER TABLE app_user DROP CONSTRAINT IF EXISTS app_user_role_check;
ALTER TABLE app_user ADD CONSTRAINT check_app_user_role
    CHECK (role IN ('ADMIN', 'WRITER', 'READER', 'TEACHER'));

ALTER TABLE app_user ADD COLUMN IF NOT EXISTS teacher_id VARCHAR(100);

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'fk_app_user_teacher'
    ) THEN
        ALTER TABLE app_user ADD CONSTRAINT fk_app_user_teacher FOREIGN KEY (teacher_id)
            REFERENCES teacher(id) ON DELETE SET NULL;
    END IF;
END $$;

COMMENT ON COLUMN app_user.teacher_id IS 'Optional link to a teacher record. Meaningful only for TEACHER-role accounts, which can only view this teacher''s own schedule (GET /api/schedule/view/me).';
