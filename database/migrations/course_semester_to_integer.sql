-- Migrate course.semester from Roman-numeral VARCHAR(2) to a mandatory
-- INTEGER in [1, 12]. Existing data only uses II/IV/VI, but the mapping
-- covers I-XII for completeness in case older data is present.
--
-- v_group_course_teachers selects c.semester, so it must be dropped and
-- recreated around the column type change.

BEGIN;

DROP VIEW IF EXISTS v_group_course_teachers;

ALTER TABLE course
    ALTER COLUMN semester TYPE INTEGER
    USING (
        CASE semester
            WHEN 'I' THEN 1
            WHEN 'II' THEN 2
            WHEN 'III' THEN 3
            WHEN 'IV' THEN 4
            WHEN 'V' THEN 5
            WHEN 'VI' THEN 6
            WHEN 'VII' THEN 7
            WHEN 'VIII' THEN 8
            WHEN 'IX' THEN 9
            WHEN 'X' THEN 10
            WHEN 'XI' THEN 11
            WHEN 'XII' THEN 12
            ELSE NULL
        END
    );

ALTER TABLE course
    ADD CONSTRAINT check_course_semester CHECK (semester BETWEEN 1 AND 12);

COMMENT ON COLUMN course.semester IS 'Semester number (1-12)';

CREATE VIEW v_group_course_teachers AS
 SELECT sg.id AS group_id,
    sg.name AS group_name,
    c.id AS course_id,
    c.name AS course_name,
    c.abbreviation AS course_abbreviation,
    c.required_hours_per_week,
    c.semester,
    c.component,
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
  GROUP BY sg.id, sg.name, c.id, c.name, c.abbreviation, c.required_hours_per_week, c.semester, c.component, c.room_requirement, t.id, t.name, t.last_name
  ORDER BY sg.name, c.name, (concat(t.name, ' ', t.last_name));

COMMIT;
