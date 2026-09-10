-- ============================================================================
-- Migration Script: Require a room on every pinned course_block_assignment
-- ============================================================================
-- pinned = TRUE is supposed to mean "this placement is a locked-in fact",
-- but nothing enforced that room_name was actually filled in - a pinned row
-- could silently sit with room_name NULL indefinitely (found empirically:
-- 22 such rows existed in production data before this migration). This
-- mirrors the existing check_block_assignment_pinned_requires_timeslot
-- constraint, applying the same rule to room_name.
--
-- A group's preferred_room_name or a teacher's required_room_name is
-- deliberately NOT an acceptable substitute here and this migration does not
-- auto-backfill room_name from either: a group's single preferred room is
-- frequently the wrong type for some of that group's own blocks (e.g. a
-- Standard homeroom can't stand in for a Specialized - Workshop block), so
-- filling it in requires a real decision about which specific room to use,
-- not an automatic default.
--
-- Purpose: make it structurally impossible to pin a block without an actual
-- room, instead of discovering the gap later.
-- Date: 2026-08-23
-- ============================================================================

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'check_block_assignment_pinned_requires_room'
    ) THEN
        ALTER TABLE course_block_assignment
        ADD CONSTRAINT check_block_assignment_pinned_requires_room
            CHECK (pinned = FALSE OR room_name IS NOT NULL);
    END IF;
END $$;
