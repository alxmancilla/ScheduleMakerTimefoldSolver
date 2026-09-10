-- ============================================================================
-- Migration Script: Rename room type 'laboratorio' -> 'dual'
-- ============================================================================
-- 'laboratorio' was the one room type with special satisfiesRequirement()
-- behavior: it could also satisfy an 'estándar' requirement (a lab has
-- desks/a board too). This migration renames it to 'dual' and extends that
-- same convention-seeded capability set to also satisfy 'taller' - so a
-- dual room can host a plain lecture, a workshop, or its own type. 'taller'
-- itself remains a separate, narrower type that still satisfies only itself
-- (e.g. AULA 4, which has no such dual capability).
--
-- The Java-side behavior change lives in Room.capabilitiesFor() (engine) and
-- RoomTypeCompatibility.satisfies() (web) - this migration only renames the
-- data and the two CHECK constraints; it ships together with that code
-- change, not as a standalone data fix.
--
-- Purpose: Let admins mark a room as usable for both regular classes and
-- workshop-style classes, using one clearer type name instead of overloading
-- 'laboratorio' for rooms that were never really wet/chemistry labs (e.g.
-- TEM1-3, TE1, Taller Alimentos).
-- Date: 2026-08-22
-- ============================================================================

-- Wrapped in a transaction, and constraints are dropped before the data is
-- renamed: renaming the data first would fail against the *old* CHECK
-- constraint (which doesn't allow 'dual' yet), and adding the *new*
-- constraint first would fail against the still-'laboratorio' rows.
BEGIN;

ALTER TABLE room DROP CONSTRAINT IF EXISTS check_room_type;
ALTER TABLE course DROP CONSTRAINT IF EXISTS check_course_room_requirement;

UPDATE room SET type = 'dual' WHERE type = 'laboratorio';
UPDATE course SET room_requirement = 'dual' WHERE room_requirement = 'laboratorio';
UPDATE course_room_requirement SET room_type = 'dual' WHERE room_type = 'laboratorio';
UPDATE course_block_template SET room_type = 'dual' WHERE room_type = 'laboratorio';
UPDATE course_block_assignment SET satisfies_room_type = 'dual' WHERE satisfies_room_type = 'laboratorio';

ALTER TABLE room ADD CONSTRAINT check_room_type
    CHECK (type IN ('estándar', 'taller', 'centro de cómputo', 'dual'));
ALTER TABLE course ADD CONSTRAINT check_course_room_requirement
    CHECK (room_requirement IN ('estándar', 'taller', 'centro de cómputo', 'dual'));

COMMENT ON COLUMN room.type IS 'Room type: estándar, taller, centro de cómputo, or dual (satisfies estándar and taller too - see Room.satisfiesRequirement)';

COMMIT;
