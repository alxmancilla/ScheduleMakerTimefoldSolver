-- ============================================================================
-- PLANNING PIN EXAMPLES: Pre-Assign Teachers and Rooms
-- ============================================================================
-- This file shows different strategies for using planning pins to lock
-- specific teacher/room assignments while letting the solver optimize others.
-- ============================================================================

-- ============================================================================
-- STRATEGY 1: Pin Specific Teacher-Course Pairs
-- ============================================================================
-- Use case: Principal wants specific teachers for specific courses
-- Example: JOSE always teaches HUMANIDADES III

UPDATE course_assignment ca
SET 
    teacher_id = 'JOSE',
    pinned = TRUE
WHERE ca.course_id = 'HUMANIDADES III';

-- Result: JOSE is locked for HUMANIDADES III, solver assigns room and timeslot


-- ============================================================================
-- STRATEGY 2: Pin All Lab Room Assignments
-- ============================================================================
-- Use case: Lab courses must use specific lab rooms (limited availability)
-- Example: All computer science courses must use CC 1 or CC 2

UPDATE course_assignment ca
SET 
    room_name = (
        CASE 
            WHEN ca.course_id LIKE '%SOFTWARE%' THEN 'CC 1'
            WHEN ca.course_id LIKE '%BASE DE DATOS%' THEN 'CC 1'
            WHEN ca.course_id LIKE '%APLICACIONES MOVILES%' THEN 'CC 2'
            ELSE NULL
        END
    ),
    pinned = (
        CASE 
            WHEN ca.course_id LIKE '%SOFTWARE%' THEN TRUE
            WHEN ca.course_id LIKE '%BASE DE DATOS%' THEN TRUE
            WHEN ca.course_id LIKE '%APLICACIONES MOVILES%' THEN TRUE
            ELSE FALSE
        END
    )
WHERE ca.course_id IN (
    SELECT id FROM course WHERE room_requirement = 'lab'
);

-- Result: Lab rooms are locked, solver assigns teachers and timeslots


-- ============================================================================
-- STRATEGY 3: Pin Complete Schedules for Specific Groups
-- ============================================================================
-- Use case: 6th semester schedule is already finalized and approved
-- Example: Lock all assignments for groups starting with '6'

UPDATE course_assignment
SET pinned = TRUE
WHERE group_id LIKE '6%';

-- Result: 6th semester is completely locked, solver only optimizes other semesters


-- ============================================================================
-- STRATEGY 4: Pin High-Priority Courses First
-- ============================================================================
-- Use case: Ensure critical courses get best teachers and timeslots
-- Example: Pin non-BASICAS courses with specific qualified teachers

UPDATE course_assignment ca
SET 
    teacher_id = (
        SELECT t.id 
        FROM teacher t
        JOIN teacher_qualification tq ON t.id = tq.teacher_id
        WHERE tq.course_name = ca.course_id
        ORDER BY t.max_hours_per_week DESC  -- Prefer teachers with more capacity
        LIMIT 1
    ),
    pinned = TRUE
WHERE ca.course_id IN (
    SELECT id FROM course WHERE component != 'BASICAS'
);

-- Result: Non-BASICAS courses have locked teachers, solver assigns rooms and timeslots


-- ============================================================================
-- STRATEGY 5: Pin Morning Slots for Specific Teachers
-- ============================================================================
-- Use case: Some teachers prefer/require morning schedules
-- Example: Lock assignments for teachers who only work mornings (7-11)

UPDATE course_assignment ca
SET 
    timeslot_id = (
        SELECT ts.id 
        FROM timeslot ts
        WHERE ts.hour BETWEEN 7 AND 10
        ORDER BY RANDOM()
        LIMIT 1
    ),
    pinned = TRUE
WHERE ca.teacher_id IN (
    SELECT id FROM teacher WHERE id IN ('LUCIA DANIELA', 'YAMMEL ANILU')
);

-- Result: Specific teachers locked to morning slots, solver assigns rooms


-- ============================================================================
-- STRATEGY 6: Unpin Everything (Reset to Let Solver Optimize Freely)
-- ============================================================================
-- Use case: Start fresh, let solver assign everything

UPDATE course_assignment
SET 
    pinned = FALSE,
    teacher_id = NULL,
    room_name = NULL,
    timeslot_id = NULL;

-- Result: Solver has complete freedom to optimize


-- ============================================================================
-- STRATEGY 7: Pin Only Assignments That Satisfy Constraints
-- ============================================================================
-- Use case: Pin assignments but verify they don't violate constraints
-- Example: Pin teacher-timeslot pairs only if teacher is available

UPDATE course_assignment ca
SET pinned = TRUE
WHERE ca.teacher_id IS NOT NULL
  AND ca.timeslot_id IS NOT NULL
  AND EXISTS (
      SELECT 1 
      FROM teacher t
      JOIN timeslot ts ON ts.id = ca.timeslot_id
      WHERE t.id = ca.teacher_id
        AND t.availability_json::jsonb ? ts.day_of_week::text
        AND (t.availability_json::jsonb->ts.day_of_week::text)::jsonb ? ts.hour::text
  );

-- Result: Only valid assignments are pinned, reduces risk of infeasibility


-- ============================================================================
-- VERIFICATION QUERIES
-- ============================================================================

-- Check how many assignments are pinned
SELECT 
    pinned,
    COUNT(*) as count,
    ROUND(100.0 * COUNT(*) / SUM(COUNT(*)) OVER (), 2) as percentage
FROM course_assignment
GROUP BY pinned;

-- Check pinned assignments by group
SELECT 
    group_id,
    COUNT(*) as total_assignments,
    SUM(CASE WHEN pinned THEN 1 ELSE 0 END) as pinned_count,
    ROUND(100.0 * SUM(CASE WHEN pinned THEN 1 ELSE 0 END) / COUNT(*), 2) as pinned_percentage
FROM course_assignment
GROUP BY group_id
ORDER BY group_id;

-- Check pinned assignments with pre-assigned values
SELECT 
    COUNT(*) as pinned_with_teacher,
    SUM(CASE WHEN room_name IS NOT NULL THEN 1 ELSE 0 END) as pinned_with_room,
    SUM(CASE WHEN timeslot_id IS NOT NULL THEN 1 ELSE 0 END) as pinned_with_timeslot
FROM course_assignment
WHERE pinned = TRUE;

