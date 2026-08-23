-- ============================================================================
-- Migration Script: Rename course component BASICAS -> Core, add Elective
-- ============================================================================
-- Renames the "BASICAS" course component to "Core" and adds a new "Elective"
-- component to the course_component lookup table (see
-- add_room_type_and_course_component_lookup_tables.sql). Both course.component
-- and component_block_rule.component FK into course_component(name) with
-- ON UPDATE CASCADE, so a single UPDATE renames it everywhere - exactly the
-- benefit that migration was written to provide.
--
-- Purpose: Requested rename to a human-readable label, plus a new elective
-- category, both surfaced automatically in the Courses UI's component
-- dropdown since it's now fully DB-driven (course_component is the single
-- source of truth, no hardcoded list anywhere in the app).
-- Date: 2026-08-22
-- ============================================================================

BEGIN;

UPDATE course_component SET name = 'Core' WHERE name = 'BASICAS';

INSERT INTO course_component (name) VALUES ('Elective');

COMMIT;
