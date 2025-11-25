-- ============================================================================
-- School Scheduling System - Demo Data Loading Script
-- ============================================================================
-- This script loads the same demo data used in DemoDataGenerator.java
-- Corresponds to the data generated in:
-- - generateTeachers()
-- - generateCourses()
-- - generateRooms()
-- - generateTimeslots()
-- - generateGroups()
-- - generateCourseAssignments()
-- ============================================================================

-- Set client encoding to UTF-8 for proper handling of special characters
SET client_encoding = 'UTF8';

-- Clear existing data (in correct order due to foreign keys)
DELETE FROM course_assignment;
DELETE FROM group_course;
DELETE FROM teacher_qualification;
DELETE FROM teacher_availability;
DELETE FROM student_group;
DELETE FROM timeslot;
DELETE FROM room;
DELETE FROM course;
DELETE FROM teacher;

-- ============================================================================
-- TEACHERS DATA
-- ============================================================================
-- 22 teachers with varying maxHoursPerWeek (sorted by capacity in Java)
-- Note: Java sorts by maxHoursPerWeek ascending, so order matters for consistency
-- Note: ID is generated from lastName (sanitized)

INSERT INTO teacher (id, name, last_name, max_hours_per_week) VALUES
('t_melo', 'GUSTAVO', 'MELO', 30),
('t_diego', 'MONICA E. ', 'DIEGO', 30),
('t_ramirez', 'QUESIA ALONDRA', 'RAMIREZ', 40),
('t_sanchez', 'LUIS', 'SANCHEZ', 30),
('t_lluck', 'DIANA R.', 'LLUCK', 30),
('t_acevedo', 'JUAN A.', 'ACEVEDO', 30),
('t_garcia', 'HUGO', 'GARCIA', 40),
('t_retana', 'JOSÉ CARLOS', 'RETANA', 30),
('t_catalan', 'BALBINA', 'CATALAN', 20),
('t_verdiguel', 'MARIO', 'VERDIGUEL', 40),
('t_santana', 'ISRAEL', 'SANTANA', 20),
('t_salas', 'ALFREDO', 'SALAS', 40),
('t_barrios', 'ANDRES', 'BARRIOS', 40),
('t_uribe', 'ITZEL', 'URIBE', 30),
('t_herrera', 'YASIR', 'HERRERA', 30),
('t_mart_nez', 'YAMEL A.', 'MARTÍNEZ', 40),
('t_de_los_santos', 'LETICIA', 'DE LOS SANTOS', 40),
('t_bahena', 'JOSE', 'BAHENA', 30),
('t_nu_ez', 'LUCIA DANIELA', 'NUÑEZ', 30),
('t_rosete', 'PABLO B.', 'ROSETE', 30),
('t_adame', 'CARLOS IVAN', 'ADAME', 30),
('t_guzman_contreras', 'MIGUEL A.', 'GUZMAN CONTRERAS', 40);

-- ============================================================================
-- TEACHER QUALIFICATIONS
-- ============================================================================

-- GUSTAVO MELO - LENGUA Y COMUNICACIÓN I
INSERT INTO teacher_qualification (teacher_id, qualification) VALUES
('t_melo', 'LENGUA Y COMUNICACIÓN I');

-- MONICA E. DIEGO - INGLÉS I
INSERT INTO teacher_qualification (teacher_id, qualification) VALUES
('t_diego', 'INGLÉS I');

-- QUESIA ALONDRA RAMIREZ - INGLÉS I
INSERT INTO teacher_qualification (teacher_id, qualification) VALUES
('t_ramirez', 'INGLÉS I');

-- LUIS SANCHEZ - PENSAMIENTO MATEMÁTICO I
INSERT INTO teacher_qualification (teacher_id, qualification) VALUES
('t_sanchez', 'PENSAMIENTO MATEMÁTICO I');

-- DIANA R. LLUCK - PENSAMIENTO MATEMÁTICO I
INSERT INTO teacher_qualification (teacher_id, qualification) VALUES
('t_lluck', 'PENSAMIENTO MATEMÁTICO I');

-- JUAN A. ACEVEDO - PENSAMIENTO MATEMÁTICO I
INSERT INTO teacher_qualification (teacher_id, qualification) VALUES
('t_acevedo', 'PENSAMIENTO MATEMÁTICO I');

-- HUGO GARCIA - PENSAMIENTO MATEMÁTICO I
INSERT INTO teacher_qualification (teacher_id, qualification) VALUES
('t_garcia', 'PENSAMIENTO MATEMÁTICO I');

-- JOSÉ CARLOS RETANA - CULTURA DIGITAL I
INSERT INTO teacher_qualification (teacher_id, qualification) VALUES
('t_retana', 'CULTURA DIGITAL I');

-- BALBINA CATALAN - CULTURA DIGITAL I
INSERT INTO teacher_qualification (teacher_id, qualification) VALUES
('t_catalan', 'CULTURA DIGITAL I');

-- MARIO VERDIGUEL - CULTURA DIGITAL I
INSERT INTO teacher_qualification (teacher_id, qualification) VALUES
('t_verdiguel', 'CULTURA DIGITAL I');

-- ISRAEL SANTANA - CULTURA DIGITAL I
INSERT INTO teacher_qualification (teacher_id, qualification) VALUES
('t_santana', 'CULTURA DIGITAL I');

-- ALFREDO SALAS - LA MATERIA Y SUS INTERACCIONES
INSERT INTO teacher_qualification (teacher_id, qualification) VALUES
('t_salas', 'LA MATERIA Y SUS INTERACCIONES');

-- ANDRES BARRIOS - Multiple qualifications
INSERT INTO teacher_qualification (teacher_id, qualification) VALUES
('t_barrios', 'RECURSOS SOCIOEMOCIONALES I'),
('t_barrios', 'TUTORIAS I');

-- ITZEL URIBE - Multiple qualifications
INSERT INTO teacher_qualification (teacher_id, qualification) VALUES
('t_uribe', 'LA MATERIA Y SUS INTERACCIONES'),
('t_uribe', 'RECURSOS SOCIOEMOCIONALES I'),
('t_uribe', 'TUTORIAS I');

-- YASIR HERRERA - Multiple qualifications
INSERT INTO teacher_qualification (teacher_id, qualification) VALUES
('t_herrera', 'LA MATERIA Y SUS INTERACCIONES'),
('t_herrera', 'RECURSOS SOCIOEMOCIONALES I'),
('t_herrera', 'TUTORIAS I');

-- YAMEL A. MARTÍNEZ - CIENCIAS SOCIALES I
INSERT INTO teacher_qualification (teacher_id, qualification) VALUES
('t_mart_nez', 'CIENCIAS SOCIALES I');

-- LETICIA DE LOS SANTOS - HUMANIDADES I
INSERT INTO teacher_qualification (teacher_id, qualification) VALUES
('t_de_los_santos', 'HUMANIDADES I');

-- JOSE BAHENA - Multiple qualifications
INSERT INTO teacher_qualification (teacher_id, qualification) VALUES
('t_bahena', 'RECURSOS SOCIOEMOCIONALES I'),
('t_bahena', 'HUMANIDADES I'),
('t_bahena', 'TUTORIAS I');

-- LUCIA DANIELA NUÑEZ - Multiple qualifications
INSERT INTO teacher_qualification (teacher_id, qualification) VALUES
('t_nu_ez', 'RECURSOS SOCIOEMOCIONALES I'),
('t_nu_ez', 'TUTORIAS I');

-- PABLO B. ROSETE - Multiple qualifications
INSERT INTO teacher_qualification (teacher_id, qualification) VALUES
('t_rosete', 'CLUB DE AJEDREZ'),
('t_rosete', 'RECURSOS SOCIOEMOCIONALES I'),
('t_rosete', 'TUTORIAS I');

-- CARLOS IVAN ADAME - ACTIVACIÓN FÍSICA
INSERT INTO teacher_qualification (teacher_id, qualification) VALUES
('t_adame', 'ACTIVACIÓN FÍSICA');

-- MIGUEL A. GUZMAN CONTRERAS - CIENCIAS SOCIALES I
INSERT INTO teacher_qualification (teacher_id, qualification) VALUES
('t_guzman_contreras', 'CIENCIAS SOCIALES I');

-- ============================================================================
-- TEACHER AVAILABILITY
-- ============================================================================
-- Most teachers available Mon-Fri 7-15, with some variations
-- Helper function would be ideal, but we'll generate inline

-- Function to generate availability for a teacher
-- Parameters: teacher_id, days (1-5 for Mon-Fri), start_hour, end_hour

-- GUSTAVO MELO: Mon-Fri 7-15
INSERT INTO teacher_availability (teacher_id, day_of_week, hour)
SELECT 't_melo', d, h
FROM generate_series(1, 5) AS d  -- Mon-Fri
CROSS JOIN generate_series(7, 14) AS h;

-- MONICA E. DIEGO: Mon-Fri 7-15
INSERT INTO teacher_availability (teacher_id, day_of_week, hour)
SELECT 't_diego', d, h
FROM generate_series(1, 5) AS d
CROSS JOIN generate_series(7, 14) AS h;

-- QUESIA ALONDRA RAMIREZ: Mon-Fri 7-15
INSERT INTO teacher_availability (teacher_id, day_of_week, hour)
SELECT 't_ramirez', d, h
FROM generate_series(1, 5) AS d
CROSS JOIN generate_series(7, 14) AS h;

-- LUIS SANCHEZ: Mon-Fri 7-15
INSERT INTO teacher_availability (teacher_id, day_of_week, hour)
SELECT 't_sanchez', d, h
FROM generate_series(1, 5) AS d
CROSS JOIN generate_series(7, 14) AS h;

-- DIANA R. LLUCK: Mon-Fri 7-15
INSERT INTO teacher_availability (teacher_id, day_of_week, hour)
SELECT 't_lluck', d, h
FROM generate_series(1, 5) AS d
CROSS JOIN generate_series(7, 14) AS h;

-- JUAN A. ACEVEDO: Mon-Fri 11-15 (different hours!)
INSERT INTO teacher_availability (teacher_id, day_of_week, hour)
SELECT 't_acevedo', d, h
FROM generate_series(1, 5) AS d
CROSS JOIN generate_series(11, 14) AS h;

-- HUGO GARCIA: Mon-Fri 7-15
INSERT INTO teacher_availability (teacher_id, day_of_week, hour)
SELECT 't_garcia', d, h
FROM generate_series(1, 5) AS d
CROSS JOIN generate_series(7, 14) AS h;

-- JOSÉ CARLOS RETANA: Mon-Fri 7-15
INSERT INTO teacher_availability (teacher_id, day_of_week, hour)
SELECT 't_retana', d, h
FROM generate_series(1, 5) AS d
CROSS JOIN generate_series(7, 14) AS h;

-- BALBINA CATALAN: Mon-Fri 7-15
INSERT INTO teacher_availability (teacher_id, day_of_week, hour)
SELECT 't_catalan', d, h
FROM generate_series(1, 5) AS d
CROSS JOIN generate_series(7, 14) AS h;

-- MARIO VERDIGUEL: Mon-Fri 7-15
INSERT INTO teacher_availability (teacher_id, day_of_week, hour)
SELECT 't_verdiguel', d, h
FROM generate_series(1, 5) AS d
CROSS JOIN generate_series(7, 14) AS h;

-- ISRAEL SANTANA: Mon-Fri 10-14 (different hours!)
INSERT INTO teacher_availability (teacher_id, day_of_week, hour)
SELECT 't_santana', d, h
FROM generate_series(1, 5) AS d
CROSS JOIN generate_series(10, 13) AS h;

-- ALFREDO SALAS: Mon-Fri 7-15
INSERT INTO teacher_availability (teacher_id, day_of_week, hour)
SELECT 't_salas', d, h
FROM generate_series(1, 5) AS d
CROSS JOIN generate_series(7, 14) AS h;

-- ANDRES BARRIOS: Mon-Fri 7-15
INSERT INTO teacher_availability (teacher_id, day_of_week, hour)
SELECT 't_barrios', d, h
FROM generate_series(1, 5) AS d
CROSS JOIN generate_series(7, 14) AS h;

-- ITZEL URIBE: Mon-Fri 7-15
INSERT INTO teacher_availability (teacher_id, day_of_week, hour)
SELECT 't_uribe', d, h
FROM generate_series(1, 5) AS d
CROSS JOIN generate_series(7, 14) AS h;

-- YASIR HERRERA: Mon-Fri 7-14 (ends at 14, not 15!)
INSERT INTO teacher_availability (teacher_id, day_of_week, hour)
SELECT 't_herrera', d, h
FROM generate_series(1, 5) AS d
CROSS JOIN generate_series(7, 13) AS h;

-- YAMEL A. MARTÍNEZ: Mon-Fri 7-15
INSERT INTO teacher_availability (teacher_id, day_of_week, hour)
SELECT 't_mart_nez', d, h
FROM generate_series(1, 5) AS d
CROSS JOIN generate_series(7, 14) AS h;

-- LETICIA DE LOS SANTOS: Mon-Fri 7-13 (ends at 13!)
INSERT INTO teacher_availability (teacher_id, day_of_week, hour)
SELECT 't_de_los_santos', d, h
FROM generate_series(1, 5) AS d
CROSS JOIN generate_series(7, 12) AS h;

-- JOSE BAHENA: Mon-Fri 7-13 (ends at 13!)
INSERT INTO teacher_availability (teacher_id, day_of_week, hour)
SELECT 't_bahena', d, h
FROM generate_series(1, 5) AS d
CROSS JOIN generate_series(7, 12) AS h;

-- LUCIA DANIELA NUÑEZ: Mon-Fri 7-13 (ends at 13!)
INSERT INTO teacher_availability (teacher_id, day_of_week, hour)
SELECT 't_nu_ez', d, h
FROM generate_series(1, 5) AS d
CROSS JOIN generate_series(7, 12) AS h;

-- PABLO B. ROSETE: Mon-Fri 7-15
INSERT INTO teacher_availability (teacher_id, day_of_week, hour)
SELECT 't_rosete', d, h
FROM generate_series(1, 5) AS d
CROSS JOIN generate_series(7, 14) AS h;

-- CARLOS IVAN ADAME: Mon-Fri 9-15 (starts at 9!)
INSERT INTO teacher_availability (teacher_id, day_of_week, hour)
SELECT 't_adame', d, h
FROM generate_series(1, 5) AS d
CROSS JOIN generate_series(9, 14) AS h;

-- MIGUEL A. GUZMAN CONTRERAS: Mon-Fri 7-15
INSERT INTO teacher_availability (teacher_id, day_of_week, hour)
SELECT 't_guzman_contreras', d, h
FROM generate_series(1, 5) AS d
CROSS JOIN generate_series(7, 14) AS h;

-- ============================================================================
-- COURSES DATA
-- ============================================================================
-- 11 courses with varying hours per week

INSERT INTO course (id, name, room_requirement, required_hours_per_week) VALUES
('c_tutorias_i', 'TUTORIAS I', 'standard', 1),
('c_club_de_ajedrez', 'CLUB DE AJEDREZ', 'standard', 1),
('c_activaci_n_f_sica', 'ACTIVACIÓN FÍSICA', 'standard', 1),
('c_recursos_socioemocionales_i', 'RECURSOS SOCIOEMOCIONALES I', 'standard', 1),
('c_ciencias_sociales_i', 'CIENCIAS SOCIALES I', 'standard', 2),
('c_lengua_y_comunicaci_n_i', 'LENGUA Y COMUNICACIÓN I', 'standard', 3),
('c_ingl_s_i', 'INGLÉS I', 'standard', 3),
('c_cultura_digital_i', 'CULTURA DIGITAL I', 'lab', 3),
('c_la_materia_y_sus_interacciones', 'LA MATERIA Y SUS INTERACCIONES', 'standard', 3),
('c_humanidades_i', 'HUMANIDADES I', 'standard', 4),
('c_pensamiento_matem_tico_i', 'PENSAMIENTO MATEMÁTICO I', 'standard', 4);

-- ============================================================================
-- ROOMS DATA
-- ============================================================================
-- 11 rooms total: 9 standard classrooms + 2 labs

INSERT INTO room (name, building, type) VALUES
('Room 05', 'A', 'standard'),
('Room 06', 'A', 'standard'),
('Room 07', 'A', 'standard'),
('Room 08', 'A', 'standard'),
('Room 09', 'A', 'standard'),
('Room 10', 'A', 'standard'),
('Room 11', 'A', 'standard'),
('Room 12', 'A', 'standard'),
('Room 13', 'A', 'standard'),
('Lab CC3', 'A', 'lab'),
('Lab CC4', 'A', 'lab');

-- ============================================================================
-- TIMESLOTS DATA
-- ============================================================================
-- 40 timeslots: Mon-Fri, 7-14 (8 hours per day)

INSERT INTO timeslot (id, day_of_week, hour, display_name) VALUES
-- Monday
('slot_0', 1, 7, 'Lun 7-8'),
('slot_1', 1, 8, 'Lun 8-9'),
('slot_2', 1, 9, 'Lun 9-10'),
('slot_3', 1, 10, 'Lun 10-11'),
('slot_4', 1, 11, 'Lun 11-12'),
('slot_5', 1, 12, 'Lun 12-13'),
('slot_6', 1, 13, 'Lun 13-14'),
('slot_7', 1, 14, 'Lun 14-15'),
-- Tuesday
('slot_8', 2, 7, 'Mar 7-8'),
('slot_9', 2, 8, 'Mar 8-9'),
('slot_10', 2, 9, 'Mar 9-10'),
('slot_11', 2, 10, 'Mar 10-11'),
('slot_12', 2, 11, 'Mar 11-12'),
('slot_13', 2, 12, 'Mar 12-13'),
('slot_14', 2, 13, 'Mar 13-14'),
('slot_15', 2, 14, 'Mar 14-15'),
-- Wednesday
('slot_16', 3, 7, 'Mie 7-8'),
('slot_17', 3, 8, 'Mie 8-9'),
('slot_18', 3, 9, 'Mie 9-10'),
('slot_19', 3, 10, 'Mie 10-11'),
('slot_20', 3, 11, 'Mie 11-12'),
('slot_21', 3, 12, 'Mie 12-13'),
('slot_22', 3, 13, 'Mie 13-14'),
('slot_23', 3, 14, 'Mie 14-15'),
-- Thursday
('slot_24', 4, 7, 'Jue 7-8'),
('slot_25', 4, 8, 'Jue 8-9'),
('slot_26', 4, 9, 'Jue 9-10'),
('slot_27', 4, 10, 'Jue 10-11'),
('slot_28', 4, 11, 'Jue 11-12'),
('slot_29', 4, 12, 'Jue 12-13'),
('slot_30', 4, 13, 'Jue 13-14'),
('slot_31', 4, 14, 'Jue 14-15'),
-- Friday
('slot_32', 5, 7, 'Vie 7-8'),
('slot_33', 5, 8, 'Vie 8-9'),
('slot_34', 5, 9, 'Vie 9-10'),
('slot_35', 5, 10, 'Vie 10-11'),
('slot_36', 5, 11, 'Vie 11-12'),
('slot_37', 5, 12, 'Vie 12-13'),
('slot_38', 5, 13, 'Vie 13-14'),
('slot_39', 5, 14, 'Vie 14-15');

-- ============================================================================
-- STUDENT GROUPS DATA
-- ============================================================================
-- 9 groups (Gpo 1oA through Gpo 1oI), each with a preferred room

INSERT INTO student_group (id, name, preferred_room_name) VALUES
('g_1A', 'Gpo 1oA', 'Room 08'),
('g_1B', 'Gpo 1oB', 'Room 05'),
('g_1C', 'Gpo 1oC', 'Room 06'),
('g_1D', 'Gpo 1oD', 'Room 09'),
('g_1E', 'Gpo 1oE', 'Room 10'),
('g_1F', 'Gpo 1oF', 'Room 11'),
('g_1G', 'Gpo 1oG', 'Room 07'),
('g_1H', 'Gpo 1oH', 'Room 12'),
('g_1I', 'Gpo 1oI', 'Room 13');

-- ============================================================================
-- GROUP COURSES (which courses each group takes)
-- ============================================================================
-- All 9 groups take all 11 courses

INSERT INTO group_course (group_id, course_name)
SELECT g.id, c.name
FROM student_group g
CROSS JOIN course c;

-- ============================================================================
-- COURSE ASSIGNMENTS
-- ============================================================================
-- Generate course assignments for each group+course combination
-- Each assignment represents one hour of the course
-- For example, "PENSAMIENTO MATEMÁTICO I" requires 4 hours, so we create 4 assignments
-- with sequence_index 0, 1, 2, 3

-- Note: teacher_id, timeslot_id, and room_name are NULL initially
-- The Timefold solver will assign these values

-- Counter starts at 0 and increments for each assignment
INSERT INTO course_assignment (id, group_id, course_id, sequence_index, teacher_id, timeslot_id, room_name)
SELECT
    'assignment_' || ROW_NUMBER() OVER (ORDER BY g.id, c.id, seq.n) - 1 AS id,
    g.id AS group_id,
    c.id AS course_id,
    seq.n AS sequence_index,
    NULL AS teacher_id,
    NULL AS timeslot_id,
    NULL AS room_name
FROM student_group g
CROSS JOIN course c
CROSS JOIN LATERAL generate_series(0, c.required_hours_per_week - 1) AS seq(n)
ORDER BY g.id, c.id, seq.n;

-- ============================================================================
-- VERIFICATION QUERIES
-- ============================================================================

-- Count summary
SELECT 'Teachers' AS entity, COUNT(*) AS count FROM teacher
UNION ALL
SELECT 'Teacher Qualifications', COUNT(*) FROM teacher_qualification
UNION ALL
SELECT 'Teacher Availability Hours', COUNT(*) FROM teacher_availability
UNION ALL
SELECT 'Courses', COUNT(*) FROM course
UNION ALL
SELECT 'Rooms', COUNT(*) FROM room
UNION ALL
SELECT 'Timeslots', COUNT(*) FROM timeslot
UNION ALL
SELECT 'Student Groups', COUNT(*) FROM student_group
UNION ALL
SELECT 'Group-Course Relationships', COUNT(*) FROM group_course
UNION ALL
SELECT 'Course Assignments', COUNT(*) FROM course_assignment;

-- Sample data verification
SELECT 'Sample Teachers:' AS info;
SELECT id, name, max_hours_per_week FROM teacher LIMIT 5;

SELECT 'Sample Courses:' AS info;
SELECT id, name, room_requirement, required_hours_per_week FROM course LIMIT 5;

SELECT 'Sample Assignments (unassigned):' AS info;
SELECT ca.id, sg.name AS group_name, c.name AS course_name, ca.sequence_index
FROM course_assignment ca
JOIN student_group sg ON ca.group_id = sg.id
JOIN course c ON ca.course_id = c.id
LIMIT 10;

-- ============================================================================
-- END OF DATA LOADING
-- ============================================================================
