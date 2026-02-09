-- ============================================================================
-- PINNED BLOCK ASSIGNMENTS - Manual Block Creation
-- ============================================================================
-- This script creates block assignments that exactly match the hour-based
-- pinned assignments from load_demo_data.sql
--
-- Strategy: Delete auto-generated blocks and create custom blocks that match
-- the exact consecutive hour patterns from the original pinned schedule
-- ============================================================================

-- First, delete the auto-generated blocks for courses 15 and 16
DELETE FROM course_block_assignment WHERE course_id IN ('15', '16');

-- ============================================================================
-- Course 15: REALIZA ANALISIS FISICOS Y QUIMICOS A LA MATERIA PRIMA (8 hours)
-- ============================================================================

-- Group 2APIA - Teacher: 48SLAMC
-- Original pinned schedule:
--   Mon 8-10 (2 hours) in AULA 6
--   Thu 12-15 (3 hours) in LQ 1
--   Fri 7-10 (3 hours) in LQ 1

INSERT INTO course_block_assignment (id, group_id, course_id, block_length, teacher_id, room_name, block_timeslot_id, pinned)
VALUES
    ('2APIA_15_0', '2APIA', '15', 2, '48SLAMC', 'AULA 6', 'block_6', TRUE),    -- Mon 8-10
    ('2APIA_15_1', '2APIA', '15', 3, '48SLAMC', 'LQ 1', 'block_101', TRUE),    -- Thu 12-15
    ('2APIA_15_2', '2APIA', '15', 3, '48SLAMC', 'LQ 1', 'block_107', TRUE);    -- Fri 7-10


-- Group 2BPIA - Teacher: 48YESMR
-- Original pinned schedule:
--   Wed 11-13 (2 hours) in AULA 8
--   Thu 7-10 (3 hours) in LQ 1
--   Fri 12-15 (3 hours) in LQ 1

INSERT INTO course_block_assignment (id, group_id, course_id, block_length, teacher_id, room_name, block_timeslot_id, pinned)
VALUES
    ('2BPIA_15_0', '2BPIA', '15', 2, '48YESMR', 'AULA 8', 'block_70', TRUE),   -- Wed 11-13
    ('2BPIA_15_1', '2BPIA', '15', 3, '48YESMR', 'LQ 1', 'block_81', TRUE),     -- Thu 7-10
    ('2BPIA_15_2', '2BPIA', '15', 3, '48YESMR', 'LQ 1', 'block_127', TRUE);    -- Fri 12-15


-- ============================================================================
-- Course 16: REALIZA ANALISIS MICROBIOLOGICOS A LA MATERIA PRIMA (9 hours)
-- ============================================================================

-- Group 2APIA - Teacher: 48SLAMC
-- Original pinned schedule:
--   Tue 7-11 (4 hours) in LMICRO
--   Wed 10-13 (3 hours) in LMICRO
--   Thu 8-10 (2 hours) in AULA 6

INSERT INTO course_block_assignment (id, group_id, course_id, block_length, teacher_id, room_name, block_timeslot_id, pinned)
VALUES
    ('2APIA_16_0', '2APIA', '16', 4, '48SLAMC', 'LMICRO', 'block_30', TRUE),   -- Tue 7-11
    ('2APIA_16_1', '2APIA', '16', 3, '48SLAMC', 'LMICRO', 'block_67', TRUE),   -- Wed 10-13
    ('2APIA_16_2', '2APIA', '16', 2, '48SLAMC', 'AULA 6', 'block_84', TRUE);   -- Thu 8-10


-- Group 2BPIA - Teacher: 48SLAMC
-- Original pinned schedule:
--   Tue 11-15 (4 hours) in LMICRO
--   Wed 7-10 (3 hours) in LMICRO
--   Thu 10-12 (2 hours) in AULA 8

INSERT INTO course_block_assignment (id, group_id, course_id, block_length, teacher_id, room_name, block_timeslot_id, pinned)
VALUES
    ('2BPIA_16_0', '2BPIA', '16', 4, '48SLAMC', 'LMICRO', 'block_46', TRUE),   -- Tue 11-15
    ('2BPIA_16_1', '2BPIA', '16', 3, '48SLAMC', 'LMICRO', 'block_55', TRUE),   -- Wed 7-10
    ('2BPIA_16_2', '2BPIA', '16', 2, '48SLAMC', 'AULA 8', 'block_92', TRUE);   -- Thu 10-12


-- ============================================================================
-- VERIFICATION
-- ============================================================================

SELECT 'Pinned Block Assignments Created' AS status;
SELECT '' AS info;

SELECT 'Course 15 Blocks:' AS info;
SELECT cba.id, sg.name AS group_name, cba.block_length, 
       t.name AS teacher, cba.room_name, 
       bts.day_of_week, bts.start_hour, bts.length_hours, cba.pinned
FROM course_block_assignment cba
JOIN student_group sg ON cba.group_id = sg.id
LEFT JOIN teacher t ON cba.teacher_id = t.id
LEFT JOIN block_timeslot bts ON cba.block_timeslot_id = bts.id
WHERE cba.course_id = '15'
ORDER BY cba.group_id, cba.id;

SELECT '' AS info;
SELECT 'Course 16 Blocks:' AS info;
SELECT cba.id, sg.name AS group_name, cba.block_length, 
       t.name AS teacher, cba.room_name, 
       bts.day_of_week, bts.start_hour, bts.length_hours, cba.pinned
FROM course_block_assignment cba
JOIN student_group sg ON cba.group_id = sg.id
LEFT JOIN teacher t ON cba.teacher_id = t.id
LEFT JOIN block_timeslot bts ON cba.block_timeslot_id = bts.id
WHERE cba.course_id = '16'
ORDER BY cba.group_id, cba.id;

SELECT '' AS info;
SELECT 'Total Hours Verification:' AS info;
SELECT cba.group_id, cba.course_id, 
       SUM(cba.block_length) AS total_hours,
       c.required_hours_per_week AS expected_hours
FROM course_block_assignment cba
JOIN course c ON cba.course_id = c.id
WHERE cba.course_id IN ('15', '16')
GROUP BY cba.group_id, cba.course_id, c.required_hours_per_week
ORDER BY cba.course_id, cba.group_id;

