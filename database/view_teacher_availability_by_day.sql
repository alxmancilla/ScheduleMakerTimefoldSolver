-- ============================================================================
-- Teacher Availability View - Easy to Read Format
-- ============================================================================
-- This view provides a comprehensive, easy-to-read representation of teacher
-- availability organized by teacher and day of the week.
-- ============================================================================

-- Drop view if exists
DROP VIEW IF EXISTS v_teacher_availability_by_day CASCADE;

-- ============================================================================
-- VIEW: v_teacher_availability_by_day
-- ============================================================================
-- Shows teacher availability in a readable format with day names and hour ranges
CREATE VIEW v_teacher_availability_by_day AS
SELECT
    t.id AS teacher_id,
    t.name AS teacher_first_name,
    t.last_name AS teacher_last_name,
    CONCAT(t.name, ' ', t.last_name) AS teacher_full_name,
    ta.day_of_week,
    CASE ta.day_of_week
        WHEN 1 THEN 'Lunes'
        WHEN 2 THEN 'Martes'
        WHEN 3 THEN 'Miércoles'
        WHEN 4 THEN 'Jueves'
        WHEN 5 THEN 'Viernes'
        WHEN 6 THEN 'Sábado'
        WHEN 7 THEN 'Domingo'
    END AS day_name,
    ta.hour,
    CONCAT(ta.hour, ':00-', ta.hour + 1, ':00') AS hour_range,
    ts.id AS timeslot_id,
    ts.display_name AS timeslot_display
FROM teacher t
INNER JOIN teacher_availability ta ON t.id = ta.teacher_id
LEFT JOIN timeslot ts ON ts.day_of_week = ta.day_of_week AND ts.hour = ta.hour
ORDER BY t.last_name, t.name, ta.day_of_week, ta.hour;

COMMENT ON VIEW v_teacher_availability_by_day IS 'Teacher availability with readable day names and hour ranges';

-- ============================================================================
-- VIEW: v_teacher_availability_summary
-- ============================================================================
-- Aggregated view showing availability hours per day for each teacher
DROP VIEW IF EXISTS v_teacher_availability_summary CASCADE;

CREATE VIEW v_teacher_availability_summary AS
SELECT
    t.id AS teacher_id,
    CONCAT(t.name, ' ', t.last_name) AS teacher_full_name,
    t.max_hours_per_week,
    COUNT(DISTINCT CONCAT(ta.day_of_week, '-', ta.hour)) AS total_available_hours,
    COUNT(DISTINCT CASE WHEN ta.day_of_week = 1 THEN ta.hour END) AS monday_hours,
    COUNT(DISTINCT CASE WHEN ta.day_of_week = 2 THEN ta.hour END) AS tuesday_hours,
    COUNT(DISTINCT CASE WHEN ta.day_of_week = 3 THEN ta.hour END) AS wednesday_hours,
    COUNT(DISTINCT CASE WHEN ta.day_of_week = 4 THEN ta.hour END) AS thursday_hours,
    COUNT(DISTINCT CASE WHEN ta.day_of_week = 5 THEN ta.hour END) AS friday_hours,
    COUNT(DISTINCT CASE WHEN ta.day_of_week = 6 THEN ta.hour END) AS saturday_hours,
    COUNT(DISTINCT CASE WHEN ta.day_of_week = 7 THEN ta.hour END) AS sunday_hours,
    STRING_AGG(DISTINCT 
        CASE ta.day_of_week
            WHEN 1 THEN 'Lun'
            WHEN 2 THEN 'Mar'
            WHEN 3 THEN 'Mié'
            WHEN 4 THEN 'Jue'
            WHEN 5 THEN 'Vie'
            WHEN 6 THEN 'Sáb'
            WHEN 7 THEN 'Dom'
        END, ', ' ORDER BY ta.day_of_week) AS available_days
FROM teacher t
LEFT JOIN teacher_availability ta ON t.id = ta.teacher_id
GROUP BY t.id, t.name, t.last_name, t.max_hours_per_week
ORDER BY t.last_name, t.name;

COMMENT ON VIEW v_teacher_availability_summary IS 'Aggregated teacher availability showing hours per day';

-- ============================================================================
-- VIEW: v_teacher_availability_grid
-- ============================================================================
-- Pivot-style view showing availability as a grid (teacher x day x hour)
DROP VIEW IF EXISTS v_teacher_availability_grid CASCADE;

CREATE VIEW v_teacher_availability_grid AS
SELECT
    t.id AS teacher_id,
    CONCAT(t.name, ' ', t.last_name) AS teacher_full_name,
    STRING_AGG(
        CASE WHEN ta.day_of_week = 1 THEN CAST(ta.hour AS TEXT) END, 
        ', ' ORDER BY ta.hour
    ) AS monday_hours,
    STRING_AGG(
        CASE WHEN ta.day_of_week = 2 THEN CAST(ta.hour AS TEXT) END, 
        ', ' ORDER BY ta.hour
    ) AS tuesday_hours,
    STRING_AGG(
        CASE WHEN ta.day_of_week = 3 THEN CAST(ta.hour AS TEXT) END, 
        ', ' ORDER BY ta.hour
    ) AS wednesday_hours,
    STRING_AGG(
        CASE WHEN ta.day_of_week = 4 THEN CAST(ta.hour AS TEXT) END, 
        ', ' ORDER BY ta.hour
    ) AS thursday_hours,
    STRING_AGG(
        CASE WHEN ta.day_of_week = 5 THEN CAST(ta.hour AS TEXT) END, 
        ', ' ORDER BY ta.hour
    ) AS friday_hours,
    STRING_AGG(
        CASE WHEN ta.day_of_week = 6 THEN CAST(ta.hour AS TEXT) END, 
        ', ' ORDER BY ta.hour
    ) AS saturday_hours,
    STRING_AGG(
        CASE WHEN ta.day_of_week = 7 THEN CAST(ta.hour AS TEXT) END, 
        ', ' ORDER BY ta.hour
    ) AS sunday_hours
FROM teacher t
LEFT JOIN teacher_availability ta ON t.id = ta.teacher_id
GROUP BY t.id, t.name, t.last_name
ORDER BY t.last_name, t.name;

COMMENT ON VIEW v_teacher_availability_grid IS 'Teacher availability in grid format showing hours per day';

-- ============================================================================
-- VIEW: v_teacher_day_availability
-- ============================================================================
-- Detailed view showing each teacher-day combination with all available hours
DROP VIEW IF EXISTS v_teacher_day_availability CASCADE;

CREATE VIEW v_teacher_day_availability AS
SELECT
    t.id AS teacher_id,
    CONCAT(t.name, ' ', t.last_name) AS teacher_full_name,
    ta.day_of_week,
    CASE ta.day_of_week
        WHEN 1 THEN 'Lunes'
        WHEN 2 THEN 'Martes'
        WHEN 3 THEN 'Miércoles'
        WHEN 4 THEN 'Jueves'
        WHEN 5 THEN 'Viernes'
        WHEN 6 THEN 'Sábado'
        WHEN 7 THEN 'Domingo'
    END AS day_name,
    COUNT(ta.hour) AS available_hours_count,
    STRING_AGG(CAST(ta.hour AS TEXT), ', ' ORDER BY ta.hour) AS available_hours,
    STRING_AGG(CONCAT(ta.hour, ':00-', ta.hour + 1, ':00'), ', ' ORDER BY ta.hour) AS hour_ranges,
    MIN(ta.hour) AS first_hour,
    MAX(ta.hour) AS last_hour,
    CONCAT(MIN(ta.hour), ':00-', MAX(ta.hour) + 1, ':00') AS time_span
FROM teacher t
INNER JOIN teacher_availability ta ON t.id = ta.teacher_id
GROUP BY t.id, t.name, t.last_name, ta.day_of_week
ORDER BY t.last_name, t.name, ta.day_of_week;

COMMENT ON VIEW v_teacher_day_availability IS 'Teacher availability grouped by day with hour ranges';

