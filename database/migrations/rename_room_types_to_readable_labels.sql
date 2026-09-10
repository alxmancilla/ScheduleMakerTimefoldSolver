-- ============================================================================
-- Migration Script: Rename room_type values to human-readable labels
-- ============================================================================
-- estándar -> Standard, mixto -> Mixed, taller -> "Specialized - Workshop",
-- centro de cómputo -> "Specialized - Computer Lab". taller and centro de
-- cómputo deliberately stay two DISTINCT values (not merged into one
-- "Specialized" value) - they remain non-interchangeable for scheduling
-- purposes, just as before this rename. All 5 columns FK'd into room_type
-- (room.type, course.room_requirement, course_room_requirement.room_type,
-- course_block_template.room_type, course_block_assignment.satisfies_room_type)
-- follow automatically via ON UPDATE CASCADE.
--
-- NOTE: unlike the BASICAS->Core course-designation rename, this one is NOT
-- purely data-driven - common/RoomTypeCompatibility.java hardcodes the
-- "mixto satisfies estándar and taller" special case by literal string, and
-- a few other places (engine's SchoolConstraintProvider/BlockScheduleAnalyzer,
-- web's ExcelImportService.VALID_ROOM_TYPES, web-ui's ROOM_TYPES/Assignments.jsx)
-- also hardcode these literals. All of those are updated in the same change
-- as this migration - see the accompanying code changes.
--
-- Purpose: requested rename to human-readable room-type labels in the UI.
-- Date: 2026-08-22
-- ============================================================================

BEGIN;

UPDATE room_type SET name = 'Standard' WHERE name = 'estándar';
UPDATE room_type SET name = 'Mixed' WHERE name = 'mixto';
UPDATE room_type SET name = 'Specialized - Workshop' WHERE name = 'taller';
UPDATE room_type SET name = 'Specialized - Computer Lab' WHERE name = 'centro de cómputo';

COMMENT ON TABLE room_type IS 'Valid room/room-requirement classification values (Standard, Mixed, Specialized - Workshop, Specialized - Computer Lab).';

COMMIT;
