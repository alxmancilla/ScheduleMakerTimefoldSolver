-- ============================================================================
-- Migration Script: Rename course_component/course.component to
-- course_designation/course.designation
-- ============================================================================
-- Renames the "component" concept on Course to "designation" throughout the
-- DB layer: the lookup table itself, the FK column on course, its constraint
-- and index, and the reporting view that surfaces it. Deliberately leaves
-- component_block_rule (table + column) untouched - a different feature
-- (block-generation rules keyed by the same values) that wasn't part of this
-- request; its FK into the renamed table keeps working automatically since
-- Postgres tracks the referenced table by OID, not by name.
--
-- Purpose: requested rename to match the "Designation" label now used in the
-- Courses UI.
-- Date: 2026-08-22
-- ============================================================================

BEGIN;

ALTER TABLE course_component RENAME TO course_designation;
COMMENT ON TABLE course_designation IS 'Valid course designation category values (Core, Elective, TEM, TCOM, etc.).';

ALTER TABLE course RENAME COLUMN component TO designation;
ALTER TABLE course RENAME CONSTRAINT fk_course_component TO fk_course_designation;
ALTER INDEX idx_course_component RENAME TO idx_course_designation;
COMMENT ON COLUMN course.designation IS 'Course designation category. References course_designation(name).';

COMMENT ON COLUMN component_block_rule.component IS 'Matches course.designation (e.g. Core, TEM, TCS).';

DROP VIEW IF EXISTS v_group_course_teachers;
CREATE VIEW v_group_course_teachers AS
SELECT sg.id AS group_id,
    sg.name AS group_name,
    c.id AS course_id,
    c.name AS course_name,
    c.abbreviation AS course_abbreviation,
    c.required_hours_per_week,
    c.semester,
    c.designation,
    c.room_requirement,
    t.id AS teacher_id,
    concat(t.name, ' ', t.last_name) AS teacher_name,
    count(DISTINCT cba.id) AS total_block_assignments,
    COALESCE(sum(cba.block_length), 0::bigint) AS scheduled_hours,
    string_agg((((
        CASE bt.day_of_week
            WHEN 1 THEN 'Lun'::text
            WHEN 2 THEN 'Mar'::text
            WHEN 3 THEN 'Mie'::text
            WHEN 4 THEN 'Jue'::text
            WHEN 5 THEN 'Vie'::text
            WHEN 6 THEN 'Sáb'::text
            WHEN 7 THEN 'Dom'::text
            ELSE NULL::text
        END || ' '::text) || bt.start_hour) || '-'::text) || (bt.start_hour + bt.length_hours), ', '::text ORDER BY bt.day_of_week, bt.start_hour) AS scheduled_timeslots,
    array_agg(bt.length_hours ORDER BY bt.day_of_week, bt.start_hour) FILTER (WHERE bt.length_hours IS NOT NULL) AS block_lengths,
    string_agg(DISTINCT cba.room_name::text, ', '::text ORDER BY (cba.room_name::text)) AS assigned_rooms,
        CASE
            WHEN COALESCE(sum(cba.block_length), 0::bigint) >= c.required_hours_per_week THEN 'Complete'::text
            WHEN COALESCE(sum(cba.block_length), 0::bigint) > 0 THEN 'Partial'::text
            ELSE 'Not Scheduled'::text
        END AS scheduling_status
   FROM student_group sg
     JOIN group_course gc ON sg.id::text = gc.group_id::text
     JOIN course c ON gc.course_name::text = c.name::text
     LEFT JOIN course_block_assignment cba ON sg.id::text = cba.group_id::text AND c.id::text = cba.course_id::text
     LEFT JOIN teacher t ON cba.teacher_id::text = t.id::text
     LEFT JOIN block_timeslot bt ON cba.block_timeslot_id::text = bt.id::text
  GROUP BY sg.id, sg.name, c.id, c.name, c.abbreviation, c.required_hours_per_week, c.semester, c.designation, c.room_requirement, t.id, t.name, t.last_name
  ORDER BY sg.name, c.name, (concat(t.name, ' ', t.last_name));

COMMENT ON VIEW v_group_course_teachers IS 'Shows courses and their assigned teachers for each student group with scheduling status and block lengths array (block-based)';

COMMIT;
