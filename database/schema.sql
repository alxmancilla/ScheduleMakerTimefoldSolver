-- ============================================================================
-- School Scheduling System - PostgreSQL Database Schema
-- ============================================================================
-- This schema represents the domain model for the Timefold-based school
-- scheduling constraint optimization system.
-- ============================================================================

-- ============================================================================
-- DATABASE CREATION (Optional - run as superuser if database doesn't exist)
-- ============================================================================
-- Uncomment these lines if you want to create the database from this script:
--
-- DROP DATABASE IF EXISTS school_schedule;
-- CREATE DATABASE school_schedule
--      WITH ENCODING='UTF8'
--      LC_COLLATE='en_US.UTF-8'
--      LC_CTYPE='en_US.UTF-8'
--      OWNER=mancilla;
--
--  \c school_schedule

-- Set client encoding to UTF-8
SET client_encoding = 'UTF8';

-- Drop tables if they exist (for clean reinstalls)
DROP TABLE IF EXISTS course_assignment CASCADE;
DROP TABLE IF EXISTS group_course CASCADE;
DROP TABLE IF EXISTS teacher_qualification CASCADE;
DROP TABLE IF EXISTS teacher_availability CASCADE;
DROP TABLE IF EXISTS student_group CASCADE;
DROP TABLE IF EXISTS timeslot CASCADE;
DROP TABLE IF EXISTS room CASCADE;
DROP TABLE IF EXISTS course CASCADE;
DROP TABLE IF EXISTS teacher CASCADE;
DROP VIEW IF EXISTS v_teacher_availability_by_day CASCADE;
DROP VIEW IF EXISTS v_teacher_availability_grid CASCADE;
DROP VIEW IF EXISTS v_teacher_day_availability CASCADE;

-- ============================================================================
-- TEACHERS TABLE
-- ============================================================================
-- Stores teacher information including workload capacity
CREATE TABLE teacher (
    id VARCHAR(100) PRIMARY KEY,
    name VARCHAR(200) NOT NULL,
    last_name VARCHAR(200) NOT NULL,
    max_hours_per_week INTEGER NOT NULL DEFAULT 40,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT check_max_hours_positive CHECK (max_hours_per_week > 0)
);

CREATE INDEX idx_teacher_name ON teacher(name);
CREATE INDEX idx_teacher_last_name ON teacher(last_name);
CREATE INDEX idx_teacher_full_name ON teacher(name, last_name);

COMMENT ON TABLE teacher IS 'Teachers who can be assigned to teach courses';
COMMENT ON COLUMN teacher.id IS 'Unique identifier for teacher (sanitized from last name)';
COMMENT ON COLUMN teacher.name IS 'First name(s) of the teacher';
COMMENT ON COLUMN teacher.last_name IS 'Last name(s) of the teacher';
COMMENT ON COLUMN teacher.max_hours_per_week IS 'Maximum teaching hours per week allowed for this teacher';

-- ============================================================================
-- TEACHER QUALIFICATIONS TABLE
-- ============================================================================
-- Many-to-many relationship: teachers can have multiple qualifications
CREATE TABLE teacher_qualification (
    teacher_id VARCHAR(100) NOT NULL,
    qualification VARCHAR(200) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (teacher_id, qualification),
    CONSTRAINT fk_qualification_teacher FOREIGN KEY (teacher_id) REFERENCES teacher(id) ON DELETE CASCADE
);

CREATE INDEX idx_qualification_teacher ON teacher_qualification(teacher_id);
CREATE INDEX idx_qualification_name ON teacher_qualification(qualification);

COMMENT ON TABLE teacher_qualification IS 'Course qualifications held by teachers';
COMMENT ON COLUMN teacher_qualification.teacher_id IS 'Reference to teacher';
COMMENT ON COLUMN teacher_qualification.qualification IS 'Course name that teacher is qualified to teach';

-- ============================================================================
-- TEACHER AVAILABILITY TABLE
-- ============================================================================
-- Stores per-day, per-hour availability for each teacher
CREATE TABLE teacher_availability (
    id SERIAL PRIMARY KEY,
    teacher_id VARCHAR(100) NOT NULL,
    day_of_week INTEGER NOT NULL,
    hour INTEGER NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_availability_teacher FOREIGN KEY (teacher_id) REFERENCES teacher(id) ON DELETE CASCADE,
    CONSTRAINT check_availability_day_of_week CHECK (day_of_week BETWEEN 1 AND 7),
    CONSTRAINT check_availability_hour_range CHECK (hour BETWEEN 0 AND 23),
    CONSTRAINT uq_availability_teacher_day_hour UNIQUE (teacher_id, day_of_week, hour)
);

CREATE INDEX idx_availability_teacher ON teacher_availability(teacher_id);
CREATE INDEX idx_availability_day_hour ON teacher_availability(day_of_week, hour);

COMMENT ON TABLE teacher_availability IS 'Defines when each teacher is available to teach';
COMMENT ON COLUMN teacher_availability.id IS 'Auto-incrementing primary key';
COMMENT ON COLUMN teacher_availability.teacher_id IS 'Reference to teacher';
COMMENT ON COLUMN teacher_availability.day_of_week IS 'Day of week: 1=Monday, 2=Tuesday, 3=Wednesday, 4=Thursday, 5=Friday, 6=Saturday, 7=Sunday';
COMMENT ON COLUMN teacher_availability.hour IS 'Hour of day when teacher is available (typically 7-15 for school hours)';

-- ============================================================================
-- COURSES TABLE
-- ============================================================================
-- Courses that can be taught
CREATE TABLE course (
    id VARCHAR(5) PRIMARY KEY,
    name VARCHAR(200) NOT NULL UNIQUE,
    abbreviation VARCHAR(100) NOT NULL,
    room_requirement VARCHAR(50) NOT NULL,
    required_hours_per_week INTEGER NOT NULL,
    semester VARCHAR(2) NOT NULL,
    component VARCHAR(20) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT check_course_room_requirement CHECK (room_requirement IN ('estándar', 'taller', 'taller electromecánica', 'taller electrónica', 'centro de cómputo', 'laboratorio')),
    CONSTRAINT check_course_required_hours CHECK (required_hours_per_week > 0)
);

CREATE INDEX idx_course_name ON course(name);
CREATE INDEX idx_course_room_requirement ON course(room_requirement);
CREATE INDEX idx_course_semester ON course(semester);
CREATE INDEX idx_course_component ON course(component);
CREATE INDEX idx_course_active ON course(active) WHERE active = TRUE;

COMMENT ON TABLE course IS 'Courses offered in the curriculum';
COMMENT ON COLUMN course.id IS 'Unique course identifier (short code)';
COMMENT ON COLUMN course.name IS 'Full course name';
COMMENT ON COLUMN course.abbreviation IS 'Short abbreviation for display';
COMMENT ON COLUMN course.room_requirement IS 'Type of room required: estándar, taller, centro de cómputo, or laboratorio';
COMMENT ON COLUMN course.required_hours_per_week IS 'Number of hours per week this course needs';
COMMENT ON COLUMN course.semester IS 'Semester level (II, IV, VI)';
COMMENT ON COLUMN course.component IS 'Course component category (BASICAS, TADRH, TEM, etc.)';
COMMENT ON COLUMN course.active IS 'Whether this course is currently active in the curriculum';

-- ============================================================================
-- ROOMS TABLE
-- ============================================================================
-- Physical classrooms and labs
CREATE TABLE room (
    name VARCHAR(100) PRIMARY KEY,
    building VARCHAR(50) NOT NULL,
    type VARCHAR(50) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT check_room_type CHECK (type IN ('estándar', 'taller', 'taller electromecánica', 'taller electrónica', 'centro de cómputo', 'laboratorio'))
);

CREATE INDEX idx_room_building ON room(building);
CREATE INDEX idx_room_type ON room(type);

COMMENT ON TABLE room IS 'Physical classrooms and laboratory spaces';
COMMENT ON COLUMN room.name IS 'Unique room identifier/name';
COMMENT ON COLUMN room.building IS 'Building identifier where room is located';
COMMENT ON COLUMN room.type IS 'Room type: estándar, taller, taller electromecánica, taller electrónica, centro de cómputo, or laboratorio';

-- ============================================================================
-- TIMESLOTS TABLE
-- ============================================================================
-- Available time slots for scheduling
CREATE TABLE timeslot (
    id VARCHAR(50) PRIMARY KEY,
    day_of_week INTEGER NOT NULL,  -- 1=Monday through 7=Sunday
    hour INTEGER NOT NULL,          -- Hour of day
    display_name VARCHAR(100) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT check_timeslot_day CHECK (day_of_week BETWEEN 1 AND 7),
    CONSTRAINT check_timeslot_hour CHECK (hour BETWEEN 0 AND 23),
    UNIQUE (day_of_week, hour)
);

CREATE INDEX idx_timeslot_day_hour ON timeslot(day_of_week, hour);

COMMENT ON TABLE timeslot IS 'Available time slots for class scheduling';
COMMENT ON COLUMN timeslot.day_of_week IS 'Day of week: 1=Monday through 7=Sunday';
COMMENT ON COLUMN timeslot.hour IS 'Hour of day when slot begins';
COMMENT ON COLUMN timeslot.display_name IS 'Human-readable label (e.g., "Lun 7-8")';

-- ============================================================================
-- STUDENT GROUPS TABLE
-- ============================================================================
-- Student groups that take courses together
CREATE TABLE student_group (
    id VARCHAR(100) PRIMARY KEY,
    name VARCHAR(200) NOT NULL UNIQUE,
    preferred_room_name VARCHAR(100),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_student_group_room FOREIGN KEY (preferred_room_name) REFERENCES room(name) ON DELETE SET NULL
);

CREATE INDEX idx_student_group_name ON student_group(name);
CREATE INDEX idx_student_group_preferred_room ON student_group(preferred_room_name);

COMMENT ON TABLE student_group IS 'Student groups that attend courses together';
COMMENT ON COLUMN student_group.id IS 'Unique group identifier (e.g., 2AARH, 4APIA)';
COMMENT ON COLUMN student_group.name IS 'Full group name';
COMMENT ON COLUMN student_group.preferred_room_name IS 'Optional pre-assigned room for this group (soft preference)';

-- ============================================================================
-- GROUP COURSES TABLE
-- ============================================================================
-- Many-to-many: which courses each group takes
CREATE TABLE group_course (
    group_id VARCHAR(100) NOT NULL,
    course_name VARCHAR(200) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (group_id, course_name),
    CONSTRAINT fk_group_course_group FOREIGN KEY (group_id) REFERENCES student_group(id) ON DELETE CASCADE,
    CONSTRAINT fk_group_course_course FOREIGN KEY (course_name) REFERENCES course(name) ON DELETE CASCADE
);

CREATE INDEX idx_group_course_group ON group_course(group_id);
CREATE INDEX idx_group_course_course ON group_course(course_name);

COMMENT ON TABLE group_course IS 'Defines which courses each student group must take';
COMMENT ON COLUMN group_course.group_id IS 'Reference to student group';
COMMENT ON COLUMN group_course.course_name IS 'Reference to course name';

-- ============================================================================
-- COURSE ASSIGNMENTS TABLE
-- ============================================================================
-- The planning entity: assignments of teacher/timeslot/room to group+course
CREATE TABLE course_assignment (
    id VARCHAR(100) PRIMARY KEY,
    group_id VARCHAR(100) NOT NULL,
    course_id VARCHAR(100) NOT NULL,
    sequence_index INTEGER NOT NULL DEFAULT 0,
    teacher_id VARCHAR(100),
    timeslot_id VARCHAR(50),
    room_name VARCHAR(100),
    pinned BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_assignment_group FOREIGN KEY (group_id) REFERENCES student_group(id) ON DELETE CASCADE,
    CONSTRAINT fk_assignment_course FOREIGN KEY (course_id) REFERENCES course(id) ON DELETE CASCADE,
    CONSTRAINT fk_assignment_teacher FOREIGN KEY (teacher_id) REFERENCES teacher(id) ON DELETE SET NULL,
    CONSTRAINT fk_assignment_timeslot FOREIGN KEY (timeslot_id) REFERENCES timeslot(id) ON DELETE SET NULL,
    CONSTRAINT fk_assignment_room FOREIGN KEY (room_name) REFERENCES room(name) ON DELETE SET NULL,
    CONSTRAINT check_assignment_sequence_index CHECK (sequence_index >= 0),
    CONSTRAINT check_assignment_pinned_requires_timeslot CHECK (pinned = FALSE OR timeslot_id IS NOT NULL),
    CONSTRAINT uq_assignment_group_course_sequence UNIQUE (group_id, course_id, sequence_index)
);

CREATE INDEX idx_assignment_group ON course_assignment(group_id);
CREATE INDEX idx_assignment_course ON course_assignment(course_id);
CREATE INDEX idx_assignment_teacher ON course_assignment(teacher_id);
CREATE INDEX idx_assignment_timeslot ON course_assignment(timeslot_id);
CREATE INDEX idx_assignment_room ON course_assignment(room_name);
CREATE INDEX idx_assignment_pinned ON course_assignment(pinned) WHERE pinned = TRUE;
CREATE INDEX idx_assignment_teacher_timeslot ON course_assignment(teacher_id, timeslot_id) WHERE teacher_id IS NOT NULL AND timeslot_id IS NOT NULL;
CREATE INDEX idx_assignment_room_timeslot ON course_assignment(room_name, timeslot_id) WHERE room_name IS NOT NULL AND timeslot_id IS NOT NULL;

COMMENT ON TABLE course_assignment IS 'Course assignment planning entities (teacher, timeslot, room assigned by Timefold solver)';
COMMENT ON COLUMN course_assignment.id IS 'Unique assignment identifier';
COMMENT ON COLUMN course_assignment.group_id IS 'Student group taking this course';
COMMENT ON COLUMN course_assignment.course_id IS 'Course being taught';
COMMENT ON COLUMN course_assignment.sequence_index IS 'Which hour of the course (0-indexed) for multi-hour courses';
COMMENT ON COLUMN course_assignment.teacher_id IS 'Assigned teacher (null until solver assigns)';
COMMENT ON COLUMN course_assignment.timeslot_id IS 'Assigned timeslot (null until solver assigns)';
COMMENT ON COLUMN course_assignment.room_name IS 'Assigned room (null until solver assigns)';
COMMENT ON COLUMN course_assignment.pinned IS 'If TRUE, Timefold solver must not modify this assignment';

-- ============================================================================
-- UTILITY VIEWS
-- ============================================================================

-- View: Complete teacher information with aggregated qualifications
CREATE VIEW v_teacher_summary AS
SELECT
    t.id,
    t.name,
    t.last_name,
    CONCAT(t.name, ' ', t.last_name) AS full_name,
    t.max_hours_per_week,
    STRING_AGG(DISTINCT tq.qualification, ', ' ORDER BY tq.qualification) AS qualifications,
    COUNT(DISTINCT CONCAT(ta.day_of_week, '-', ta.hour)) AS availability_hours
FROM teacher t
LEFT JOIN teacher_qualification tq ON t.id = tq.teacher_id
LEFT JOIN teacher_availability ta ON t.id = ta.teacher_id
GROUP BY t.id, t.name, t.last_name, t.max_hours_per_week;

COMMENT ON VIEW v_teacher_summary IS 'Summarized teacher information with qualifications and availability count';

-- View: Complete schedule with all assignment details
CREATE VIEW v_schedule AS
SELECT
    ca.id AS assignment_id,
    sg.name AS group_name,
    c.name AS course_name,
    c.required_hours_per_week AS course_hours,
    ca.sequence_index,
    CONCAT(t.name, ' ', t.last_name) AS teacher_name,
    ts.display_name AS timeslot,
    ts.day_of_week,
    ts.hour,
    r.name AS room_name,
    r.building,
    r.type AS room_type
FROM course_assignment ca
JOIN student_group sg ON ca.group_id = sg.id
JOIN course c ON ca.course_id = c.id
LEFT JOIN teacher t ON ca.teacher_id = t.id
LEFT JOIN timeslot ts ON ca.timeslot_id = ts.id
LEFT JOIN room r ON ca.room_name = r.name
ORDER BY sg.name, c.name, ca.sequence_index;

COMMENT ON VIEW v_schedule IS 'Complete schedule view with all assignment details';

-- View: Teacher workload analysis
CREATE VIEW v_teacher_workload AS
SELECT
    t.id,
    t.name,
    t.last_name,
    CONCAT(t.name, ' ', t.last_name) AS full_name,
    t.max_hours_per_week,
    COUNT(DISTINCT ca.id) AS assigned_hours,
    t.max_hours_per_week - COUNT(DISTINCT ca.id) AS remaining_capacity,
    ROUND(100.0 * COUNT(DISTINCT ca.id) / t.max_hours_per_week, 2) AS utilization_percent
FROM teacher t
LEFT JOIN course_assignment ca ON t.id = ca.teacher_id
GROUP BY t.id, t.name, t.last_name, t.max_hours_per_week
ORDER BY utilization_percent DESC;

COMMENT ON VIEW v_teacher_workload IS 'Teacher workload and capacity utilization';

-- View: Room utilization
CREATE VIEW v_room_utilization AS
SELECT
    r.name,
    r.building,
    r.type,
    COUNT(DISTINCT ca.id) AS assignments_count,
    COUNT(DISTINCT ca.timeslot_id) AS unique_timeslots_used
FROM room r
LEFT JOIN course_assignment ca ON r.name = ca.room_name
GROUP BY r.name, r.building, r.type
ORDER BY assignments_count DESC;

COMMENT ON VIEW v_room_utilization IS 'Room usage statistics';

-- View: Courses and assigned teachers per group
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



-- ============================================================================
-- Teacher Availability View - Easy to Read Format
-- ============================================================================
-- This view provides a comprehensive, easy-to-read representation of teacher
-- availability organized by teacher and day of the week.
-- ============================================================================

-- Drop view if exists

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
-- VIEW: v_teacher_availability_grid
-- ============================================================================
-- Pivot-style view showing availability as a grid (teacher x day x hour)

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


-- ============================================================================
-- END OF SCHEMA
-- ============================================================================
