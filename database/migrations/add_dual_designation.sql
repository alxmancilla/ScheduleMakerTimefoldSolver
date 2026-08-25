-- ============================================================================
-- Migration Script: Add course designation "Dual"
-- ============================================================================
-- course.designation references course_designation(name) with ON UPDATE
-- CASCADE, so this is purely a data-driven addition - no hardcoded literal
-- for it exists anywhere in application code (confirmed via repo-wide grep;
-- only "Core" is ever compared by name, for
-- preferCoreOneHourBlocksAtSameTimeAcrossDays). No component_block_rule row
-- is seeded for it either, so a Dual course falls back to the code defaults
-- (2h preferred block size, 2 max blocks/day) - same as Digital/Specialized
-- today.
--
-- "Dual" identifies a course that's part theory (Standard room) and part
-- workshop/lab (e.g. Specialized - Computer Lab), via a
-- course_room_requirement dual room requirement - distinct from "Digital"
-- (single room type, no split) and "Specialized" (purely workshop/lab).
--
-- Purpose: requested new designation option, surfaced automatically in the
-- Courses UI's designation dropdown since it's fully DB-driven.
-- Date: 2026-08-24
-- ============================================================================

INSERT INTO course_designation (name) VALUES ('Dual')
ON CONFLICT (name) DO NOTHING;
