-- ============================================================================
-- Migration Script: Rename room type 'dual' -> 'mixto'
-- ============================================================================
-- 'dual' turned out to collide with the pre-existing "dual room requirements"
-- feature name (course_room_requirement: a course split across multiple room
-- types) - same word, unrelated concepts. Renaming the room type to 'mixto'
-- removes that ambiguity. Behavior is unchanged: a 'mixto' room still also
-- satisfies 'estándar' and 'taller' requirements (Room.capabilitiesFor()).
--
-- Wrapped in a transaction, constraints dropped before the data is renamed -
-- same ordering as rename_laboratorio_to_dual.sql, for the same reason.
-- Date: 2026-08-22
-- ============================================================================

BEGIN;

ALTER TABLE room DROP CONSTRAINT IF EXISTS check_room_type;
ALTER TABLE course DROP CONSTRAINT IF EXISTS check_course_room_requirement;

UPDATE room SET type = 'mixto' WHERE type = 'dual';
UPDATE course SET room_requirement = 'mixto' WHERE room_requirement = 'dual';
UPDATE course_room_requirement SET room_type = 'mixto' WHERE room_type = 'dual';
UPDATE course_block_template SET room_type = 'mixto' WHERE room_type = 'dual';
UPDATE course_block_assignment SET satisfies_room_type = 'mixto' WHERE satisfies_room_type = 'dual';

ALTER TABLE room ADD CONSTRAINT check_room_type
    CHECK (type IN ('estándar', 'taller', 'centro de cómputo', 'mixto'));
ALTER TABLE course ADD CONSTRAINT check_course_room_requirement
    CHECK (room_requirement IN ('estándar', 'taller', 'centro de cómputo', 'mixto'));

COMMENT ON COLUMN room.type IS 'Room type: estándar, taller, centro de cómputo, or mixto (satisfies estándar and taller too - see Room.satisfiesRequirement)';

COMMIT;
