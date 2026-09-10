-- ============================================================================
-- Migration Script: Rename course designation DIGITAL -> Digital, add
-- Specialized
-- ============================================================================
-- course.designation and component_block_rule.component FK into
-- course_designation(name) with ON UPDATE CASCADE, so the rename propagates
-- automatically. No component_block_rule row exists for DIGITAL, so nothing
-- to cascade there. No hardcoded "DIGITAL" literal exists in application
-- code (confirmed via repo-wide grep) - this is purely a data-driven lookup
-- value, unlike the room-type rename.
--
-- Purpose: requested rename + new designation option, both surfaced
-- automatically in the Courses UI's designation dropdown since it's fully
-- DB-driven.
-- Date: 2026-08-22
-- ============================================================================

BEGIN;

UPDATE course_designation SET name = 'Digital' WHERE name = 'DIGITAL';

INSERT INTO course_designation (name) VALUES ('Specialized');

COMMIT;
