-- ============================================================================
-- Migration Script: Add group_room_range, replacing
-- student_group.preferred_room_name
-- ============================================================================
-- student_group.preferred_room_name pinned a group to exactly one room. Some
-- groups don't have one specific home room but can reasonably move between a
-- small set of equivalent rooms for their Core (Standard-type) classes - and,
-- separately, may want a different curated set for their specialized
-- (Mixed/Computer-Lab/Workshop-type) courses. group_room_range generalizes
-- the single preferred room into a per-room-type set of acceptable rooms: one
-- row per (group, room type, room) that's acceptable.
--
-- A room type a group has no rows for simply falls through to the solver's
-- existing full type-filtered room list (unrestricted, today's behavior for
-- a group with no preference). A room type with exactly one row behaves
-- exactly like today's single preferred_room_name (structurally fixed - the
-- solver has nothing else to pick). A room type with 2+ rows is genuinely
-- movable, just narrowed to that curated set instead of every room of that
-- type in the school. Enforced by CourseBlockAssignment.getMatchingRooms()/
-- isRoomFixed() (engine module) and BlockGenerationService.defaultRoomFor()
-- (web module, block-generation time).
--
-- This REPLACES preferred_room_name entirely rather than having both
-- mechanisms coexist: every group's existing single preference is migrated
-- below into a range-of-1 for that room's own type, then the old column is
-- dropped.
--
-- Purpose: Let a group move between several equivalent rooms instead of
-- being pinned to exactly one, with a separate curated set per room type.
-- Date: 2026-08-24
-- ============================================================================

CREATE TABLE IF NOT EXISTS group_room_range (
    id SERIAL PRIMARY KEY,
    group_id VARCHAR(100) NOT NULL,
    room_type VARCHAR(50) NOT NULL,
    room_name VARCHAR(100) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_group_room_range_group FOREIGN KEY (group_id)
        REFERENCES student_group(id) ON DELETE CASCADE,
    CONSTRAINT fk_group_room_range_type FOREIGN KEY (room_type)
        REFERENCES room_type(name) ON DELETE RESTRICT ON UPDATE CASCADE,
    CONSTRAINT fk_group_room_range_room FOREIGN KEY (room_name)
        REFERENCES room(name) ON DELETE CASCADE ON UPDATE CASCADE,
    CONSTRAINT uq_group_room_range UNIQUE (group_id, room_type, room_name)
);

CREATE INDEX IF NOT EXISTS idx_group_room_range_group ON group_room_range(group_id);
CREATE INDEX IF NOT EXISTS idx_group_room_range_type ON group_room_range(room_type);

-- Migrate every group's existing single preference into a range-of-1 for
-- that room's own current type, then drop the old column - one source of
-- truth going forward.
INSERT INTO group_room_range (group_id, room_type, room_name)
SELECT sg.id, r.type, sg.preferred_room_name
FROM student_group sg
JOIN room r ON r.name = sg.preferred_room_name
WHERE sg.preferred_room_name IS NOT NULL
ON CONFLICT (group_id, room_type, room_name) DO NOTHING;

DROP INDEX IF EXISTS idx_student_group_preferred_room;
ALTER TABLE student_group DROP CONSTRAINT IF EXISTS fk_student_group_room;
ALTER TABLE student_group DROP COLUMN IF EXISTS preferred_room_name;

COMMENT ON TABLE group_room_range IS 'A group''s curated set of acceptable rooms per room type, replacing the old single student_group.preferred_room_name. A room type with no rows for a group is unrestricted (falls through to the full type-filtered list); one row is structurally fixed like the old single preference; 2+ rows is a narrowed but movable set.';
COMMENT ON COLUMN group_room_range.group_id IS 'The student group this row applies to.';
COMMENT ON COLUMN group_room_range.room_type IS 'Which room type this acceptable room applies to - matches course_block_assignment.satisfies_room_type.';
COMMENT ON COLUMN group_room_range.room_name IS 'One room this group may use for blocks requiring room_type.';
