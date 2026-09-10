-- ============================================================================
-- Migration Script: Add room.capacity and student_group.student_count
-- ============================================================================
-- Both columns are optional (nullable). When both are set for a given room
-- and group, a new SOFT constraint (engine) warns if the group's headcount
-- exceeds the room's seating capacity; when either is unset, the check is
-- skipped entirely, so this is safe to apply without backfilling existing
-- rows.
--
-- Purpose: Let capacity/size be tracked and checked without requiring it.
-- Date: 2026-08-15
-- ============================================================================

ALTER TABLE room ADD COLUMN IF NOT EXISTS capacity INTEGER;
ALTER TABLE room ADD CONSTRAINT check_room_capacity CHECK (capacity IS NULL OR capacity > 0);
COMMENT ON COLUMN room.capacity IS 'Optional seating capacity. When set alongside student_group.student_count, a soft constraint warns if an assigned group exceeds it.';

ALTER TABLE student_group ADD COLUMN IF NOT EXISTS student_count INTEGER;
ALTER TABLE student_group ADD CONSTRAINT check_student_group_student_count CHECK (student_count IS NULL OR student_count > 0);
COMMENT ON COLUMN student_group.student_count IS 'Optional headcount. When set alongside room.capacity, a soft constraint warns if this group is placed in a room too small for it.';
