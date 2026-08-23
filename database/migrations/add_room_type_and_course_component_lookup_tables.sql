-- ============================================================================
-- Migration Script: Normalize room type and course component into lookup
-- tables (FK-backed) instead of a hand-maintained CHECK constraint / free
-- VARCHAR duplicated across several columns
-- ============================================================================
-- room.type and course.room_requirement (plus course_room_requirement.room_type,
-- course_block_template.room_type, course_block_assignment.satisfies_room_type)
-- have always stored the same 4-value set (estándar, taller, centro de
-- cómputo, mixto) independently, enforced only by a CHECK constraint on
-- room/course. Renaming or adding a room type meant editing every CHECK by
-- hand (this session's dual->mixto rename touched 2 constraints across 2
-- migrations). course.component (and component_block_rule.component) had no
-- constraint at all - any typo silently created a new, disconnected
-- component with no block rule and no way to catch the mistake.
--
-- This introduces two bare reference tables, following the exact pattern
-- `room` itself already uses correctly (natural-key PK, FKs from every
-- column that stores the value, ON UPDATE CASCADE so a rename is a single
-- UPDATE instead of a repo-wide grep):
--   room_type(name)         <- room.type, course.room_requirement,
--                               course_room_requirement.room_type,
--                               course_block_template.room_type,
--                               course_block_assignment.satisfies_room_type
--   course_component(name)  <- course.component, component_block_rule.component
--
-- course_component is intentionally its own table rather than reusing
-- component_block_rule as the FK target: component_block_rule only has a
-- row for components with a NON-default block rule (today just BASICAS) -
-- a component with no row deliberately falls back to code defaults
-- (DEFAULT_BLOCK_SIZE / DEFAULT_MAX_BLOCKS_PER_DAY = 2). FK'ing
-- course.component straight at component_block_rule would force every
-- component to have a row, destroying that "unconfigured = default"
-- behavior. course_component is the bare list of valid components;
-- component_block_rule remains an optional child of it, exactly like
-- student_group.preferred_room_name is an optional reference to room.
--
-- ON DELETE RESTRICT throughout (except component_block_rule, see below):
-- these are classification columns, not optional soft links like
-- room.name - silently nulling one out on delete would corrupt scheduling
-- data, so deleting a type/component still in use is blocked instead.
-- component_block_rule.component uses ON DELETE CASCADE: a block-rule row
-- is meaningless without its component, unlike a course.
--
-- Purpose: let a room type or course component be renamed with a single
-- UPDATE, and make a typo'd component value fail loudly (FK violation)
-- instead of silently creating an orphaned, unconfigured category.
-- Date: 2026-08-22
-- ============================================================================

BEGIN;

-- --------------------------------------------------------------------------
-- room_type
-- --------------------------------------------------------------------------
CREATE TABLE room_type (
    name VARCHAR(50) PRIMARY KEY
);

COMMENT ON TABLE room_type IS 'Valid room/room-requirement classification values (estándar, taller, centro de cómputo, mixto). Referenced by FK from every column that stores a room type, so a rename is a single UPDATE instead of a repo-wide CHECK-constraint edit.';

INSERT INTO room_type (name) VALUES
    ('estándar'),
    ('taller'),
    ('centro de cómputo'),
    ('mixto');

ALTER TABLE room DROP CONSTRAINT check_room_type;
ALTER TABLE room ADD CONSTRAINT fk_room_type
    FOREIGN KEY (type) REFERENCES room_type(name) ON DELETE RESTRICT ON UPDATE CASCADE;

ALTER TABLE course DROP CONSTRAINT check_course_room_requirement;
ALTER TABLE course ADD CONSTRAINT fk_course_room_requirement
    FOREIGN KEY (room_requirement) REFERENCES room_type(name) ON DELETE RESTRICT ON UPDATE CASCADE;

ALTER TABLE course_room_requirement ADD CONSTRAINT fk_course_room_requirement_type
    FOREIGN KEY (room_type) REFERENCES room_type(name) ON DELETE RESTRICT ON UPDATE CASCADE;

ALTER TABLE course_block_template ADD CONSTRAINT fk_course_block_template_room_type
    FOREIGN KEY (room_type) REFERENCES room_type(name) ON DELETE RESTRICT ON UPDATE CASCADE;

ALTER TABLE course_block_assignment ADD CONSTRAINT fk_cba_satisfies_room_type
    FOREIGN KEY (satisfies_room_type) REFERENCES room_type(name) ON DELETE RESTRICT ON UPDATE CASCADE;

-- --------------------------------------------------------------------------
-- course_component
-- --------------------------------------------------------------------------
CREATE TABLE course_component (
    name VARCHAR(20) PRIMARY KEY
);

COMMENT ON TABLE course_component IS 'Valid course component category values (BASICAS, TEM, TCOM, ...). Referenced by FK from course.component and component_block_rule.component, so a typo fails loudly instead of silently creating an orphaned, unconfigured category.';

INSERT INTO course_component (name) VALUES
    ('BASICAS'), ('DIGITAL'), ('TCOM'), ('TCSEG'), ('TELE'),
    ('TEM'), ('TIA'), ('TPIA'), ('TPROG'), ('TRH');

ALTER TABLE course ADD CONSTRAINT fk_course_component
    FOREIGN KEY (component) REFERENCES course_component(name) ON DELETE RESTRICT ON UPDATE CASCADE;

ALTER TABLE component_block_rule ADD CONSTRAINT fk_component_block_rule_component
    FOREIGN KEY (component) REFERENCES course_component(name) ON DELETE CASCADE ON UPDATE CASCADE;

COMMIT;
