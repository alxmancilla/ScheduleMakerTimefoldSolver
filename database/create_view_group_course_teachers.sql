-- Drop existing view if it exists
DROP VIEW IF EXISTS v_group_course_teachers CASCADE;

-- Create view: Courses and assigned teachers per group
CREATE VIEW v_group_course_teachers AS
SELECT
    sg.id AS group_id,
    sg.name AS group_name,
    c.id AS course_id,
    c.name AS course_name,
    c.abbreviation AS course_abbreviation,
    c.required_hours_per_week,
    c.semester,
    c.component,
    c.room_requirement,
    t.id AS teacher_id,
    CONCAT(t.name, ' ', t.last_name) AS teacher_name,
    COUNT(DISTINCT ca.id) AS total_assignments,
    COUNT(DISTINCT ca.timeslot_id) AS scheduled_hours,
    STRING_AGG(DISTINCT ts.display_name, ', ' ORDER BY ts.display_name) AS scheduled_timeslots,
    STRING_AGG(DISTINCT ca.room_name, ', ' ORDER BY ca.room_name) AS assigned_rooms,
    CASE
        WHEN COUNT(DISTINCT ca.timeslot_id) = c.required_hours_per_week THEN 'Complete'
        WHEN COUNT(DISTINCT ca.timeslot_id) > 0 THEN 'Partial'
        ELSE 'Not Scheduled'
    END AS scheduling_status
FROM student_group sg
INNER JOIN group_course gc ON sg.id = gc.group_id
INNER JOIN course c ON gc.course_name = c.name
LEFT JOIN course_assignment ca ON sg.id = ca.group_id AND c.id = ca.course_id
LEFT JOIN teacher t ON ca.teacher_id = t.id
LEFT JOIN timeslot ts ON ca.timeslot_id = ts.id
GROUP BY sg.id, sg.name, c.id, c.name, c.abbreviation, c.required_hours_per_week, 
         c.semester, c.component, c.room_requirement, t.id, t.name, t.last_name
ORDER BY sg.name, c.name, teacher_name;

COMMENT ON VIEW v_group_course_teachers IS 'Shows courses and their assigned teachers for each student group with scheduling status';

