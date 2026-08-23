-- ============================================================================
-- Migration Script: Remove 'taller electromecánica' / 'taller electrónica'
-- room types
-- ============================================================================
-- These two specialized workshop types are retired: their rooms (TEM1, TEM2,
-- TEM3, TE1) were retyped to 'laboratorio' instead, which already doubles as
-- a plain 'estándar' classroom (Room.satisfiesRequirement's convention-seeded
-- capability set) - so those rooms can now also host general-education
-- blocks, not just their original specialized use. No course,
-- course_room_requirement, or course_block_assignment row referenced either
-- retired value at the time this migration was written; ADD CONSTRAINT below
-- will fail loudly (not silently) if that's no longer true when you run it.
--
-- Purpose: Narrow room.type / course.room_requirement to the 4 types
-- actually in use: estándar, taller, centro de cómputo, laboratorio.
-- Date: 2026-08-22
-- ============================================================================

ALTER TABLE room DROP CONSTRAINT IF EXISTS check_room_type;
ALTER TABLE room ADD CONSTRAINT check_room_type
    CHECK (type IN ('estándar', 'taller', 'centro de cómputo', 'laboratorio'));

ALTER TABLE course DROP CONSTRAINT IF EXISTS check_course_room_requirement;
ALTER TABLE course ADD CONSTRAINT check_course_room_requirement
    CHECK (room_requirement IN ('estándar', 'taller', 'centro de cómputo', 'laboratorio'));

COMMENT ON COLUMN room.type IS 'Room type: estándar, taller, centro de cómputo, or laboratorio';
