-- Check for teacher gaps where teacher IS available during gap
SELECT 
    t.name,
    ts1.day_of_week,
    ts1.hour AS hour1,
    ts2.hour AS hour2,
    (ts2.hour - ts1.hour - 1) AS gap_size
FROM course_assignment ca1
JOIN course_assignment ca2 ON ca1.teacher_id = ca2.teacher_id
JOIN teacher t ON ca1.teacher_id = t.id
JOIN timeslot ts1 ON ca1.timeslot_id = ts1.id
JOIN timeslot ts2 ON ca2.timeslot_id = ts2.id
WHERE ts1.day_of_week = ts2.day_of_week
  AND ts2.hour > ts1.hour + 1
ORDER BY t.name, ts1.day_of_week, ts1.hour;
