-- ============================================================================
-- School Scheduling System - Block-Based Scheduling Database Schema
-- ============================================================================
-- This schema supports block-based scheduling with multi-hour time blocks.
--
-- INCLUDES:
-- - Core tables: teacher, course, room, student_group
-- - Block-based scheduling: block_timeslot, course_block_assignment
-- - Utility views for block-based scheduling
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
DROP TABLE IF EXISTS course_block_template CASCADE;
DROP TABLE IF EXISTS course_room_requirement CASCADE;
DROP TABLE IF EXISTS course_block_assignment CASCADE;
DROP TABLE IF EXISTS block_timeslot CASCADE;
DROP TABLE IF EXISTS group_course CASCADE;
DROP TABLE IF EXISTS teacher_qualification CASCADE;
DROP TABLE IF EXISTS teacher_availability CASCADE;
DROP TABLE IF EXISTS student_group CASCADE;
DROP TABLE IF EXISTS room CASCADE;
DROP TABLE IF EXISTS course CASCADE;
DROP TABLE IF EXISTS teacher CASCADE;
DROP VIEW IF EXISTS v_block_schedule CASCADE;
DROP VIEW IF EXISTS v_teacher_availability_by_day CASCADE;
DROP VIEW IF EXISTS v_teacher_availability_grid CASCADE;
DROP VIEW IF EXISTS v_teacher_day_availability CASCADE;
DROP VIEW IF EXISTS v_teacher_summary CASCADE;
DROP VIEW IF EXISTS v_schedule CASCADE;
DROP VIEW IF EXISTS v_teacher_workload CASCADE;
DROP VIEW IF EXISTS v_room_utilization CASCADE;
DROP VIEW IF EXISTS v_group_course_teachers CASCADE;
DROP FUNCTION IF EXISTS generate_block_timeslots() CASCADE;

-- ============================================================================
-- CORE TABLES (Shared by both scheduling modes)
-- ============================================================================

-- ============================================================================
-- TEACHERS TABLE
-- ============================================================================
-- Stores teacher information including workload capacity
-- required_room_name references room(name), but room isn't created until
-- later in this script - its FK constraint is added just after the ROOMS
-- TABLE section below instead of inline here.
CREATE TABLE teacher (
    id VARCHAR(100) PRIMARY KEY,
    name VARCHAR(200) NOT NULL,
    last_name VARCHAR(200) NOT NULL,
    max_hours_per_week INTEGER NOT NULL DEFAULT 40,
    required_room_name VARCHAR(100),
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
COMMENT ON COLUMN teacher.required_room_name IS 'Optional room this teacher must always use, overriding the group''s preferred room. Enforced wherever this teacher gets assigned to a block, subject to room-type compatibility.';

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
-- ROOM TYPE TABLE
-- ============================================================================
-- Bare reference list of valid room/room-requirement classification values.
-- Referenced by FK from every column that stores a room type (room.type,
-- course.room_requirement, course_room_requirement.room_type,
-- course_block_template.room_type, course_block_assignment.satisfies_room_type)
-- so a rename is a single UPDATE instead of editing a CHECK constraint in
-- several places by hand.
CREATE TABLE room_type (
    name VARCHAR(50) PRIMARY KEY
);

COMMENT ON TABLE room_type IS 'Valid room/room-requirement classification values (estándar, taller, centro de cómputo, mixto).';

INSERT INTO room_type (name) VALUES
    ('estándar'),
    ('taller'),
    ('centro de cómputo'),
    ('mixto');

-- ============================================================================
-- COURSE DESIGNATION TABLE
-- ============================================================================
-- Bare reference list of valid course designation values. Referenced by FK
-- from course.designation and component_block_rule.component, so a typo'd
-- designation fails loudly (FK violation) instead of silently creating an
-- orphaned, unconfigured category. Kept separate from component_block_rule
-- itself: that table only holds a row for designations with a NON-default
-- block rule, and a designation with no row deliberately falls back to code
-- defaults - FK'ing course.designation straight at component_block_rule
-- would force a row to exist for every designation and destroy that behavior.
CREATE TABLE course_designation (
    name VARCHAR(20) PRIMARY KEY
);

COMMENT ON TABLE course_designation IS 'Valid course designation category values (Core, Elective, TEM, TCOM, etc.).';

INSERT INTO course_designation (name) VALUES
    ('Core'), ('Elective'), ('Digital'), ('Specialized'), ('TCOM'), ('TCSEG'), ('TELE'),
    ('TEM'), ('TIA'), ('TPIA'), ('TPROG'), ('TRH');

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
    semester INTEGER NOT NULL,
    designation VARCHAR(20) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT fk_course_room_requirement FOREIGN KEY (room_requirement) REFERENCES room_type(name) ON DELETE RESTRICT ON UPDATE CASCADE,
    CONSTRAINT fk_course_designation FOREIGN KEY (designation) REFERENCES course_designation(name) ON DELETE RESTRICT ON UPDATE CASCADE,
    CONSTRAINT check_course_required_hours CHECK (required_hours_per_week > 0),
    CONSTRAINT check_course_semester CHECK (semester BETWEEN 1 AND 12)
);

CREATE INDEX idx_course_name ON course(name);
CREATE INDEX idx_course_room_requirement ON course(room_requirement);
CREATE INDEX idx_course_semester ON course(semester);
CREATE INDEX idx_course_designation ON course(designation);
CREATE INDEX idx_course_active ON course(active) WHERE active = TRUE;

COMMENT ON TABLE course IS 'Courses offered in the curriculum';
COMMENT ON COLUMN course.id IS 'Unique course identifier (short code)';
COMMENT ON COLUMN course.name IS 'Full course name';
COMMENT ON COLUMN course.abbreviation IS 'Short abbreviation for display';
COMMENT ON COLUMN course.room_requirement IS 'Type of room required. References room_type(name).';
COMMENT ON COLUMN course.required_hours_per_week IS 'Number of hours per week this course needs';
COMMENT ON COLUMN course.semester IS 'Semester number (1-12)';
COMMENT ON COLUMN course.designation IS 'Course designation category. References course_designation(name).';
COMMENT ON COLUMN course.active IS 'Whether this course is currently active in the curriculum';

-- ============================================================================
-- ROOMS TABLE
-- ============================================================================
-- Physical classrooms and labs
CREATE TABLE room (
    name VARCHAR(100) PRIMARY KEY,
    building VARCHAR(50) NOT NULL,
    type VARCHAR(50) NOT NULL,
    capacity INTEGER,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_room_type FOREIGN KEY (type) REFERENCES room_type(name) ON DELETE RESTRICT ON UPDATE CASCADE,
    CONSTRAINT check_room_capacity CHECK (capacity IS NULL OR capacity > 0)
);

CREATE INDEX idx_room_building ON room(building);
CREATE INDEX idx_room_type ON room(type);

COMMENT ON TABLE room IS 'Physical classrooms and laboratory spaces';
COMMENT ON COLUMN room.name IS 'Unique room identifier/name';
COMMENT ON COLUMN room.building IS 'Building identifier where room is located';
COMMENT ON COLUMN room.type IS 'Room type. References room_type(name).';
COMMENT ON COLUMN room.capacity IS 'Optional seating capacity. When set alongside student_group.student_count, a soft constraint warns if an assigned group exceeds it.';

ALTER TABLE teacher ADD CONSTRAINT fk_teacher_required_room
    FOREIGN KEY (required_room_name) REFERENCES room(name) ON DELETE SET NULL ON UPDATE CASCADE;
CREATE INDEX idx_teacher_required_room ON teacher(required_room_name);

-- ============================================================================
-- STUDENT GROUPS TABLE
-- ============================================================================
-- Student groups that take courses together
CREATE TABLE student_group (
    id VARCHAR(100) PRIMARY KEY,
    name VARCHAR(200) NOT NULL UNIQUE,
    preferred_room_name VARCHAR(100),
    student_count INTEGER,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_student_group_room FOREIGN KEY (preferred_room_name) REFERENCES room(name) ON DELETE SET NULL ON UPDATE CASCADE,
    CONSTRAINT check_student_group_student_count CHECK (student_count IS NULL OR student_count > 0)
);

CREATE INDEX idx_student_group_name ON student_group(name);
CREATE INDEX idx_student_group_preferred_room ON student_group(preferred_room_name);

COMMENT ON TABLE student_group IS 'Student groups that attend courses together';
COMMENT ON COLUMN student_group.id IS 'Unique group identifier (e.g., 2AARH, 4APIA)';
COMMENT ON COLUMN student_group.name IS 'Full group name';
COMMENT ON COLUMN student_group.preferred_room_name IS 'Optional pre-assigned room for this group (soft preference)';
COMMENT ON COLUMN student_group.student_count IS 'Optional headcount. When set alongside room.capacity, a soft constraint warns if this group is placed in a room too small for it.';

-- ============================================================================
-- GROUP COURSES TABLE
-- ============================================================================
-- Many-to-many: which courses each group takes
CREATE TABLE group_course (
    group_id VARCHAR(100) NOT NULL,
    course_name VARCHAR(200) NOT NULL,
    default_teacher_id VARCHAR(100),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (group_id, course_name),
    CONSTRAINT fk_group_course_group FOREIGN KEY (group_id) REFERENCES student_group(id) ON DELETE CASCADE,
    CONSTRAINT fk_group_course_course FOREIGN KEY (course_name) REFERENCES course(name) ON DELETE CASCADE,
    CONSTRAINT fk_group_course_default_teacher FOREIGN KEY (default_teacher_id) REFERENCES teacher(id) ON DELETE SET NULL
);

CREATE INDEX idx_group_course_group ON group_course(group_id);
CREATE INDEX idx_group_course_course ON group_course(course_name);
CREATE INDEX idx_group_course_default_teacher ON group_course(default_teacher_id);

COMMENT ON TABLE group_course IS 'Defines which courses each student group must take';
COMMENT ON COLUMN group_course.group_id IS 'Reference to student group';
COMMENT ON COLUMN group_course.course_name IS 'Reference to course name';
COMMENT ON COLUMN group_course.default_teacher_id IS 'Optional teacher pre-assigned to this pairing before blocks exist; applied by BlockGenerationService to every block it creates for this pairing. No effect once blocks already exist.';



-- ============================================================================
-- BLOCK-BASED SCHEDULING TABLES
-- ============================================================================

-- ============================================================================
-- BLOCK TIMESLOTS TABLE
-- ============================================================================
-- Represents time blocks with start hour and length (1-4 hours)
CREATE TABLE IF NOT EXISTS block_timeslot (
    id VARCHAR(50) PRIMARY KEY,
    day_of_week INTEGER NOT NULL,  -- 1=Monday through 7=Sunday
    start_hour INTEGER NOT NULL,    -- Starting hour (7-15)
    length_hours INTEGER NOT NULL,  -- Block length in hours (1-4)
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT check_block_day CHECK (day_of_week BETWEEN 1 AND 7),
    CONSTRAINT check_block_start_hour CHECK (start_hour BETWEEN 7 AND 15),
    CONSTRAINT check_block_length CHECK (length_hours BETWEEN 1 AND 4),
    CONSTRAINT check_block_end_hour CHECK (start_hour + length_hours <= 15),
    UNIQUE (day_of_week, start_hour, length_hours)
);

CREATE INDEX idx_block_timeslot_day_hour ON block_timeslot(day_of_week, start_hour);
CREATE INDEX idx_block_timeslot_length ON block_timeslot(length_hours);

COMMENT ON TABLE block_timeslot IS 'Time blocks for block-based scheduling (consecutive hours)';
COMMENT ON COLUMN block_timeslot.id IS 'Unique block identifier (e.g., "block_1")';
COMMENT ON COLUMN block_timeslot.day_of_week IS 'Day of week: 1=Monday through 7=Sunday';
COMMENT ON COLUMN block_timeslot.start_hour IS 'Starting hour of the block (7-15)';
COMMENT ON COLUMN block_timeslot.length_hours IS 'Number of consecutive hours in the block (1-4)';

-- ============================================================================
-- COURSE BLOCK ASSIGNMENTS TABLE
-- ============================================================================
-- The planning entity for block-based scheduling
CREATE TABLE IF NOT EXISTS course_block_assignment (
    id VARCHAR(100) PRIMARY KEY,
    group_id VARCHAR(100) NOT NULL,
    course_id VARCHAR(100) NOT NULL,
    block_length INTEGER NOT NULL,  -- Number of hours for this course block
    teacher_id VARCHAR(100),
    block_timeslot_id VARCHAR(50),
    room_name VARCHAR(100),
    pinned BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_block_assignment_group FOREIGN KEY (group_id) REFERENCES student_group(id) ON DELETE CASCADE,
    CONSTRAINT fk_block_assignment_course FOREIGN KEY (course_id) REFERENCES course(id) ON DELETE CASCADE,
    CONSTRAINT fk_block_assignment_teacher FOREIGN KEY (teacher_id) REFERENCES teacher(id) ON DELETE SET NULL,
    CONSTRAINT fk_block_assignment_timeslot FOREIGN KEY (block_timeslot_id) REFERENCES block_timeslot(id) ON DELETE SET NULL,
    CONSTRAINT fk_block_assignment_room FOREIGN KEY (room_name) REFERENCES room(name) ON DELETE SET NULL ON UPDATE CASCADE,
    CONSTRAINT check_block_assignment_length CHECK (block_length BETWEEN 1 AND 4),
    CONSTRAINT check_block_assignment_pinned_requires_timeslot CHECK (pinned = FALSE OR block_timeslot_id IS NOT NULL),
    CONSTRAINT check_block_assignment_pinned_requires_room CHECK (pinned = FALSE OR room_name IS NOT NULL)
    -- Note: No unique constraint on (group_id, course_id) because a course can have multiple blocks
    -- For example: A 5-hour course might be split into a 3-hour block and a 2-hour block
);

CREATE INDEX idx_block_assignment_group ON course_block_assignment(group_id);
CREATE INDEX idx_block_assignment_course ON course_block_assignment(course_id);
CREATE INDEX idx_block_assignment_teacher ON course_block_assignment(teacher_id);
CREATE INDEX idx_block_assignment_timeslot ON course_block_assignment(block_timeslot_id);
CREATE INDEX idx_block_assignment_room ON course_block_assignment(room_name);
CREATE INDEX idx_block_assignment_pinned ON course_block_assignment(pinned) WHERE pinned = TRUE;
CREATE INDEX idx_block_assignment_teacher_timeslot ON course_block_assignment(teacher_id, block_timeslot_id) 
    WHERE teacher_id IS NOT NULL AND block_timeslot_id IS NOT NULL;
CREATE INDEX idx_block_assignment_room_timeslot ON course_block_assignment(room_name, block_timeslot_id) 
    WHERE room_name IS NOT NULL AND block_timeslot_id IS NOT NULL;

COMMENT ON TABLE course_block_assignment IS 'Course block assignment planning entities for block-based scheduling';
COMMENT ON COLUMN course_block_assignment.id IS 'Unique assignment identifier';
COMMENT ON COLUMN course_block_assignment.group_id IS 'Student group taking this course';
COMMENT ON COLUMN course_block_assignment.course_id IS 'Course being taught';
COMMENT ON COLUMN course_block_assignment.block_length IS 'Number of consecutive hours for this course';
COMMENT ON COLUMN course_block_assignment.teacher_id IS 'Assigned teacher (null until solver assigns)';
COMMENT ON COLUMN course_block_assignment.block_timeslot_id IS 'Assigned block timeslot (null until solver assigns)';
COMMENT ON COLUMN course_block_assignment.room_name IS 'Assigned room (null until solver assigns)';
COMMENT ON COLUMN course_block_assignment.pinned IS 'If TRUE, Timefold solver must not modify this assignment';

-- ============================================================================
-- UTILITY VIEW FOR BLOCK SCHEDULE
-- ============================================================================
CREATE OR REPLACE VIEW v_block_schedule AS
SELECT
    cba.id AS assignment_id,
    sg.name AS group_name,
    c.name AS course_name,
    c.required_hours_per_week AS course_hours,
    cba.block_length,
    CONCAT(t.name, ' ', t.last_name) AS teacher_name,
    CASE bt.day_of_week
        WHEN 1 THEN 'Lun'
        WHEN 2 THEN 'Mar'
        WHEN 3 THEN 'Mie'
        WHEN 4 THEN 'Jue'
        WHEN 5 THEN 'Vie'
        WHEN 6 THEN 'Sáb'
        WHEN 7 THEN 'Dom'
    END || ' ' || bt.start_hour || '-' || (bt.start_hour + bt.length_hours) AS timeslot,
    bt.day_of_week,
    bt.start_hour,
    bt.length_hours,
    r.name AS room_name,
    r.building AS room_building,
    r.type AS room_type,
    cba.pinned
FROM course_block_assignment cba
JOIN student_group sg ON cba.group_id = sg.id
JOIN course c ON cba.course_id = c.id
LEFT JOIN teacher t ON cba.teacher_id = t.id
LEFT JOIN block_timeslot bt ON cba.block_timeslot_id = bt.id
LEFT JOIN room r ON cba.room_name = r.name
ORDER BY sg.name, bt.day_of_week, bt.start_hour;

COMMENT ON VIEW v_block_schedule IS 'Complete block schedule with all assignment details';

-- ============================================================================
-- SAMPLE DATA GENERATION FUNCTION
-- ============================================================================
-- Function to generate block timeslots for Mon-Fri, 7-15, lengths 1-4
CREATE OR REPLACE FUNCTION generate_block_timeslots() RETURNS INTEGER AS $$
DECLARE
    block_count INTEGER := 0;
    day INTEGER;
    start_h INTEGER;
    len INTEGER;
    block_id VARCHAR(50);
BEGIN
    -- Clear existing block timeslots
    DELETE FROM block_timeslot;
    
    -- Generate blocks for each day
    FOR day IN 1..5 LOOP  -- Monday to Friday
        FOR start_h IN 7..14 LOOP  -- Start hours 7-14
            FOR len IN 1..4 LOOP  -- Block lengths 1-4
                -- Only create blocks that don't exceed 15:00
                IF start_h + len <= 15 THEN
                    block_id := 'block_' || block_count + 1;
                    INSERT INTO block_timeslot (id, day_of_week, start_hour, length_hours)
                    VALUES (block_id, day, start_h, len);
                    block_count := block_count + 1;
                END IF;
            END LOOP;
        END LOOP;
    END LOOP;
    
    RETURN block_count;
END;
$$ LANGUAGE plpgsql;

COMMENT ON FUNCTION generate_block_timeslots() IS 'Generate all possible block timeslots for Mon-Fri, 7-15';

-- ============================================================================
-- UTILITY VIEWS FOR BLOCK-BASED SCHEDULING
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

-- View: Complete schedule with all assignment details (block-based)
CREATE VIEW v_schedule AS
SELECT
    cba.id AS assignment_id,
    sg.name AS group_name,
    c.name AS course_name,
    c.required_hours_per_week AS course_hours,
    cba.block_length,
    CONCAT(t.name, ' ', t.last_name) AS teacher_name,
    CASE bt.day_of_week
        WHEN 1 THEN 'Lun'
        WHEN 2 THEN 'Mar'
        WHEN 3 THEN 'Mie'
        WHEN 4 THEN 'Jue'
        WHEN 5 THEN 'Vie'
        WHEN 6 THEN 'Sáb'
        WHEN 7 THEN 'Dom'
    END || ' ' || bt.start_hour || '-' || (bt.start_hour + bt.length_hours) AS timeslot,
    bt.day_of_week,
    bt.start_hour AS hour,
    r.name AS room_name,
    r.building,
    r.type AS room_type,
    cba.pinned
FROM course_block_assignment cba
JOIN student_group sg ON cba.group_id = sg.id
JOIN course c ON cba.course_id = c.id
LEFT JOIN teacher t ON cba.teacher_id = t.id
LEFT JOIN block_timeslot bt ON cba.block_timeslot_id = bt.id
LEFT JOIN room r ON cba.room_name = r.name
ORDER BY sg.name, c.name, bt.day_of_week, bt.start_hour;

COMMENT ON VIEW v_schedule IS 'Complete schedule view with all assignment details for block-based scheduling';

-- View: Teacher workload analysis (block-based)
CREATE VIEW v_teacher_workload AS
SELECT
    t.id,
    t.name,
    t.last_name,
    CONCAT(t.name, ' ', t.last_name) AS full_name,
    t.max_hours_per_week,
    COALESCE(SUM(cba.block_length), 0) AS assigned_hours,
    t.max_hours_per_week - COALESCE(SUM(cba.block_length), 0) AS remaining_capacity,
    ROUND(100.0 * COALESCE(SUM(cba.block_length), 0) / t.max_hours_per_week, 2) AS utilization_percent
FROM teacher t
LEFT JOIN course_block_assignment cba ON t.id = cba.teacher_id
GROUP BY t.id, t.name, t.last_name, t.max_hours_per_week
ORDER BY utilization_percent DESC;

COMMENT ON VIEW v_teacher_workload IS 'Teacher workload and capacity utilization for block-based scheduling';

-- View: Room utilization (block-based)
CREATE VIEW v_room_utilization AS
SELECT
    r.name,
    r.building,
    r.type,
    COUNT(DISTINCT cba.id) AS assignments_count,
    COUNT(DISTINCT cba.block_timeslot_id) AS unique_timeslots_used,
    COALESCE(SUM(cba.block_length), 0) AS total_hours_used
FROM room r
LEFT JOIN course_block_assignment cba ON r.name = cba.room_name
GROUP BY r.name, r.building, r.type
ORDER BY assignments_count DESC;

COMMENT ON VIEW v_room_utilization IS 'Room usage statistics for block-based scheduling';

-- View: Courses and assigned teachers per group (block-based)
CREATE VIEW v_group_course_teachers AS
SELECT
    sg.id AS group_id,
    sg.name AS group_name,
    c.id AS course_id,
    c.name AS course_name,
    c.abbreviation AS course_abbreviation,
    c.required_hours_per_week,
    c.semester,
    c.designation,
    c.room_requirement,
    t.id AS teacher_id,
    CONCAT(t.name, ' ', t.last_name) AS teacher_name,
    COUNT(DISTINCT cba.id) AS total_block_assignments,
    COALESCE(SUM(cba.block_length), 0) AS scheduled_hours,
    STRING_AGG(
        CASE bt.day_of_week
            WHEN 1 THEN 'Lun'
            WHEN 2 THEN 'Mar'
            WHEN 3 THEN 'Mie'
            WHEN 4 THEN 'Jue'
            WHEN 5 THEN 'Vie'
            WHEN 6 THEN 'Sáb'
            WHEN 7 THEN 'Dom'
        END || ' ' || bt.start_hour || '-' || (bt.start_hour + bt.length_hours),
        ', ' ORDER BY bt.day_of_week, bt.start_hour
    ) AS scheduled_timeslots,
    ARRAY_AGG(bt.length_hours ORDER BY bt.day_of_week, bt.start_hour) FILTER (WHERE bt.length_hours IS NOT NULL) AS block_lengths,
    STRING_AGG(DISTINCT cba.room_name, ', ' ORDER BY cba.room_name) AS assigned_rooms,
    CASE
        WHEN COALESCE(SUM(cba.block_length), 0) >= c.required_hours_per_week THEN 'Complete'
        WHEN COALESCE(SUM(cba.block_length), 0) > 0 THEN 'Partial'
        ELSE 'Not Scheduled'
    END AS scheduling_status
FROM student_group sg
INNER JOIN group_course gc ON sg.id = gc.group_id
INNER JOIN course c ON gc.course_name = c.name
LEFT JOIN course_block_assignment cba ON sg.id = cba.group_id AND c.id = cba.course_id
LEFT JOIN teacher t ON cba.teacher_id = t.id
LEFT JOIN block_timeslot bt ON cba.block_timeslot_id = bt.id
GROUP BY sg.id, sg.name, c.id, c.name, c.abbreviation, c.required_hours_per_week,
         c.semester, c.designation, c.room_requirement, t.id, t.name, t.last_name
ORDER BY sg.name, c.name, teacher_name;

COMMENT ON VIEW v_group_course_teachers IS 'Shows courses and their assigned teachers for each student group with scheduling status and block lengths array (block-based)';

-- ============================================================================
-- TEACHER AVAILABILITY VIEWS
-- ============================================================================

-- View: Teacher availability in readable format with day names and hour ranges
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
    CONCAT(ta.hour, ':00-', ta.hour + 1, ':00') AS hour_range
FROM teacher t
INNER JOIN teacher_availability ta ON t.id = ta.teacher_id
ORDER BY t.last_name, t.name, ta.day_of_week, ta.hour;

COMMENT ON VIEW v_teacher_availability_by_day IS 'Teacher availability with readable day names and hour ranges';

-- View: Pivot-style view showing availability as a grid (teacher x day x hour)
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

-- View: Detailed view showing each teacher-day combination with all available hours
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
-- DUAL ROOM REQUIREMENTS & CUSTOM BLOCK TEMPLATES
-- ============================================================================
-- These tables support advanced scheduling features:
-- 1. course_room_requirement: Allows courses to require multiple room types
-- 2. course_block_template: Allows custom block decomposition patterns
-- ============================================================================

-- Table 1: Course Room Requirements (replaces single room_requirement)
-- Allows a course to specify multiple room types with different hour allocations
-- Example: A course might need 4 hours in a computer center + 1 hour in a standard room
CREATE TABLE IF NOT EXISTS course_room_requirement (
    id SERIAL PRIMARY KEY,
    course_id VARCHAR(10) NOT NULL,
    room_type VARCHAR(50) NOT NULL,
    hours_required INTEGER NOT NULL,
    priority INTEGER DEFAULT 1,
    default_preferred_room VARCHAR(100),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT check_hours_positive CHECK (hours_required > 0),
    CONSTRAINT fk_room_req_course FOREIGN KEY (course_id)
        REFERENCES course(id) ON DELETE CASCADE,
    CONSTRAINT fk_default_room FOREIGN KEY (default_preferred_room)
        REFERENCES room(name) ON DELETE SET NULL ON UPDATE CASCADE,
    CONSTRAINT fk_course_room_requirement_type FOREIGN KEY (room_type)
        REFERENCES room_type(name) ON DELETE RESTRICT ON UPDATE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_room_req_course ON course_room_requirement(course_id);
CREATE INDEX IF NOT EXISTS idx_room_req_type ON course_room_requirement(room_type);

COMMENT ON TABLE course_room_requirement IS 'Dual room requirements for courses needing multiple room types';
COMMENT ON COLUMN course_room_requirement.priority IS '1=primary requirement, 2=secondary, etc.';
COMMENT ON COLUMN course_room_requirement.default_preferred_room IS 'Suggested room for this requirement';

-- Table 2: Course Block Templates (custom decomposition)
-- Allows explicit specification of how a course should be decomposed into blocks
-- Example: A 6-hour course might be decomposed as 2h + 2h + 2h instead of default 4h + 2h
CREATE TABLE IF NOT EXISTS course_block_template (
    id SERIAL PRIMARY KEY,
    course_id VARCHAR(10) NOT NULL,
    group_id VARCHAR(10),  -- NULL = applies to all groups
    block_index INTEGER NOT NULL,
    block_length INTEGER NOT NULL,
    room_type VARCHAR(50),
    preferred_room_name VARCHAR(100),
    preferred_day INTEGER,
    pin_assignment BOOLEAN DEFAULT FALSE,
    preferred_timeslot_id VARCHAR(20),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT check_block_length CHECK (block_length BETWEEN 1 AND 4),
    CONSTRAINT check_preferred_day CHECK (preferred_day IS NULL OR (preferred_day BETWEEN 1 AND 5)),
    CONSTRAINT fk_template_course FOREIGN KEY (course_id)
        REFERENCES course(id) ON DELETE CASCADE,
    CONSTRAINT fk_template_group FOREIGN KEY (group_id)
        REFERENCES student_group(id) ON DELETE CASCADE,
    CONSTRAINT fk_preferred_room FOREIGN KEY (preferred_room_name)
        REFERENCES room(name) ON DELETE SET NULL ON UPDATE CASCADE,
    CONSTRAINT fk_preferred_timeslot FOREIGN KEY (preferred_timeslot_id)
        REFERENCES block_timeslot(id) ON DELETE SET NULL,
    CONSTRAINT fk_course_block_template_room_type FOREIGN KEY (room_type)
        REFERENCES room_type(name) ON DELETE RESTRICT ON UPDATE CASCADE,
    CONSTRAINT uq_block_template UNIQUE (course_id, group_id, block_index)
);

CREATE INDEX IF NOT EXISTS idx_block_template_course ON course_block_template(course_id);
CREATE INDEX IF NOT EXISTS idx_block_template_group ON course_block_template(group_id);

COMMENT ON TABLE course_block_template IS 'Custom block decomposition patterns for specific courses';
COMMENT ON COLUMN course_block_template.block_index IS 'Sequential index for blocks (1, 2, 3, ...)';
COMMENT ON COLUMN course_block_template.pin_assignment IS 'If true, this block should be pinned to preferred_timeslot_id';
COMMENT ON COLUMN course_block_template.preferred_day IS '1=Monday, 2=Tuesday, 3=Wednesday, 4=Thursday, 5=Friday';

-- Add new columns to course_block_assignment for dual room requirements
ALTER TABLE course_block_assignment
ADD COLUMN IF NOT EXISTS satisfies_room_type VARCHAR(50),
ADD COLUMN IF NOT EXISTS preferred_room_hint VARCHAR(100);

-- Add foreign key constraints if not exists
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'fk_cba_preferred_room'
    ) THEN
        ALTER TABLE course_block_assignment
        ADD CONSTRAINT fk_cba_preferred_room FOREIGN KEY (preferred_room_hint)
            REFERENCES room(name) ON DELETE SET NULL ON UPDATE CASCADE;
    END IF;
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'fk_cba_satisfies_room_type'
    ) THEN
        ALTER TABLE course_block_assignment
        ADD CONSTRAINT fk_cba_satisfies_room_type FOREIGN KEY (satisfies_room_type)
            REFERENCES room_type(name) ON DELETE RESTRICT ON UPDATE CASCADE;
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_cba_room_type ON course_block_assignment(satisfies_room_type);
CREATE INDEX IF NOT EXISTS idx_cba_preferred_room ON course_block_assignment(preferred_room_hint);

COMMENT ON COLUMN course_block_assignment.satisfies_room_type IS 'Which room requirement this assignment satisfies (for dual-requirement courses). References room_type(name).';
COMMENT ON COLUMN course_block_assignment.preferred_room_hint IS 'Preferred room for this assignment (soft constraint hint, not enforced)';

-- ============================================================================
-- SCHOOL TERM TABLE
-- ============================================================================
-- Singleton row holding the current term/period label shown in the UI (e.g.
-- "Fall 2026"). Purely organizational metadata; never read by the solver.
CREATE TABLE IF NOT EXISTS school_term (
    id INTEGER PRIMARY KEY DEFAULT 1 CHECK (id = 1),
    label VARCHAR(100) NOT NULL DEFAULT '',
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

INSERT INTO school_term (id, label) VALUES (1, '') ON CONFLICT (id) DO NOTHING;

COMMENT ON TABLE school_term IS 'Singleton row holding the current term/period label shown in the UI. Not used by the solver.';
COMMENT ON COLUMN school_term.label IS 'Free-text term label, editable by ADMIN in Settings.';

-- ============================================================================
-- SCHEDULE AUDIT LOG TABLE
-- ============================================================================
-- Records who made which write request (POST/PUT/DELETE) and when. Written
-- automatically by a Spring MVC interceptor, not by individual controllers.
CREATE TABLE IF NOT EXISTS schedule_audit_log (
    id BIGSERIAL PRIMARY KEY,
    username VARCHAR(100) NOT NULL,
    http_method VARCHAR(10) NOT NULL,
    path VARCHAR(500) NOT NULL,
    status_code INTEGER NOT NULL,
    occurred_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_schedule_audit_log_occurred_at ON schedule_audit_log(occurred_at DESC);

COMMENT ON TABLE schedule_audit_log IS 'Write requests (POST/PUT/DELETE) to /api/**, logged automatically by a servlet interceptor for basic accountability.';
COMMENT ON COLUMN schedule_audit_log.username IS 'Authenticated username that made the request.';
COMMENT ON COLUMN schedule_audit_log.status_code IS 'HTTP response status; only 2xx (successful) requests are logged.';

-- ============================================================================
-- COMPONENT BLOCK RULES TABLE
-- ============================================================================
-- Preferred block size (1-4h) and max blocks per day (1-4) per course
-- component. Block size is used by BlockGenerationService to decompose a
-- course's weekly hours into blocks; max blocks per day is enforced by the
-- solver's HARD constraint limiting same-day concentration of a course.
-- A component with no row here falls back to size 2 / max 2 per day in code.
CREATE TABLE IF NOT EXISTS component_block_rule (
    component VARCHAR(20) PRIMARY KEY,
    preferred_block_size INTEGER NOT NULL,
    max_blocks_per_day INTEGER NOT NULL DEFAULT 2,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT check_preferred_block_size CHECK (preferred_block_size BETWEEN 1 AND 4),
    CONSTRAINT check_max_blocks_per_day CHECK (max_blocks_per_day BETWEEN 1 AND 4),
    CONSTRAINT fk_component_block_rule_component FOREIGN KEY (component)
        REFERENCES course_designation(name) ON DELETE CASCADE ON UPDATE CASCADE
);

INSERT INTO component_block_rule (component, preferred_block_size, max_blocks_per_day)
VALUES ('Core', 1, 1)
ON CONFLICT (component) DO NOTHING;

COMMENT ON TABLE component_block_rule IS 'Preferred block size (1-4h) and max blocks per day (1-4) per course component, used by BlockGenerationService and the solver respectively. A component with no row here defaults to size 2 / max 2 per day in code.';
COMMENT ON COLUMN component_block_rule.component IS 'Matches course.designation (e.g. Core, TEM, TCS).';
COMMENT ON COLUMN component_block_rule.preferred_block_size IS 'Blocks are packed greedily at this size, with a trailing remainder block if hours don''t divide evenly.';
COMMENT ON COLUMN component_block_rule.max_blocks_per_day IS 'HARD limit on how many blocks of this component''s courses may land on the same day for the same group.';

-- ============================================================================
-- CALENDAR EXCEPTIONS TABLE
-- ============================================================================
-- Record-keeping only (v1): tracks the school's holiday/exam/half-day dates,
-- surfaced in Settings > Calendar. block_timeslot is a pure recurring weekly
-- template with no date concept at all (day-of-week + hour, not tied to any
-- actual calendar date), so this table does NOT yet gate block generation or
-- the solver - it exists so the term's calendar is tracked in the system for
-- the first time. Wiring it into generation/solving is a separate, larger
-- change (turning the single recurring week into a dated, multi-week
-- calendar) deferred until that architectural question is settled.
CREATE TABLE IF NOT EXISTS calendar_exception (
    exception_date DATE PRIMARY KEY,
    type VARCHAR(20) NOT NULL CHECK (type IN ('HOLIDAY', 'HALF_DAY', 'EXAM_DAY')),
    label VARCHAR(200),
    end_hour INTEGER CHECK (end_hour IS NULL OR end_hour BETWEEN 7 AND 15),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

COMMENT ON TABLE calendar_exception IS 'School calendar exceptions (holidays, exam days, half-days) - record-keeping only, not yet read by block generation or the solver.';
COMMENT ON COLUMN calendar_exception.exception_date IS 'The specific calendar date this exception applies to.';
COMMENT ON COLUMN calendar_exception.type IS 'HOLIDAY (no classes), HALF_DAY (classes end early, see end_hour), or EXAM_DAY (informational only).';
COMMENT ON COLUMN calendar_exception.label IS 'Optional human-readable description (e.g. "Día de la Independencia").';
COMMENT ON COLUMN calendar_exception.end_hour IS 'Only meaningful for HALF_DAY: the hour classes end that day (7-15).';

-- ============================================================================
-- SCHEDULE RUN HISTORY
-- ============================================================================
-- Separates "the problem" from "the last N solutions to it": course_block_
-- assignment is pure input (block_timeslot_id there is only meaningful for
-- pinned = true rows); the solver's actual placements live here instead,
-- one snapshot per run. DataSaver prunes schedule_run to the most recent 10
-- rows after every insert; ON DELETE CASCADE cleans up schedule_run_result.
CREATE TABLE IF NOT EXISTS schedule_run (
    id SERIAL PRIMARY KEY,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    hard_score INTEGER NOT NULL,
    soft_score INTEGER NOT NULL,
    -- Effective local search time budget used for this run (solverConfig.xml's
    -- own defaults unless overridden) - always populated, never null, so a run
    -- without an override is still fully described.
    minutes_spent_limit INTEGER NOT NULL DEFAULT 5,
    unimproved_minutes_spent_limit INTEGER NOT NULL DEFAULT 2
);

-- Frozen per-(run, assignment) snapshot: both the solved timeslot AND the
-- input fields the solver saw for that assignment at that moment. The
-- group/course/teacher/room columns are a copy, not a live FK - a later
-- rename or deletion of that teacher/room/course/group must not alter or
-- break a historical run's snapshot.
CREATE TABLE IF NOT EXISTS schedule_run_result (
    schedule_run_id INTEGER NOT NULL REFERENCES schedule_run(id) ON DELETE CASCADE,
    assignment_id VARCHAR(100) NOT NULL REFERENCES course_block_assignment(id) ON DELETE CASCADE,
    block_timeslot_id VARCHAR(50) REFERENCES block_timeslot(id),
    group_id VARCHAR(100),
    course_id VARCHAR(100),
    block_length INTEGER,
    pinned BOOLEAN,
    teacher_id VARCHAR(100),
    room_name VARCHAR(100),
    satisfies_room_type VARCHAR(100),
    preferred_room_hint VARCHAR(100),
    PRIMARY KEY (schedule_run_id, assignment_id)
);

CREATE INDEX IF NOT EXISTS idx_schedule_run_result_assignment ON schedule_run_result(assignment_id);
CREATE INDEX IF NOT EXISTS idx_schedule_run_created_at ON schedule_run(created_at);

-- Resolves "the current effective schedule" in one place: pinned rows use
-- their own (input) block_timeslot_id; every other row uses the most recent
-- schedule_run's result for that assignment. Everything that displays or
-- loads "the schedule" reads through this view instead of re-deriving the
-- pinned/latest-run rule itself.
CREATE OR REPLACE VIEW course_block_assignment_current AS
SELECT
    cba.id,
    cba.group_id,
    cba.course_id,
    cba.block_length,
    cba.pinned,
    cba.teacher_id,
    CASE WHEN cba.pinned THEN cba.block_timeslot_id ELSE latest.block_timeslot_id END AS block_timeslot_id,
    cba.room_name,
    cba.satisfies_room_type,
    cba.preferred_room_hint
FROM course_block_assignment cba
LEFT JOIN (
    SELECT srr.assignment_id, srr.block_timeslot_id
    FROM schedule_run_result srr
    WHERE srr.schedule_run_id = (SELECT MAX(id) FROM schedule_run)
) latest ON latest.assignment_id = cba.id;

COMMENT ON TABLE schedule_run IS 'One row per solver run. DataSaver prunes to the most recent 10 after every insert.';
COMMENT ON TABLE schedule_run_result IS 'One row per assignment per run: the solved (or still-unassigned) timeslot for that run.';
COMMENT ON VIEW course_block_assignment_current IS 'Resolved "current schedule": pinned rows use their own input timeslot, everything else uses the most recent schedule_run.';

-- ============================================================================
-- END OF SCHEMA
-- ============================================================================
