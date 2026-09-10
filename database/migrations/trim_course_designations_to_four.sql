-- ============================================================================
-- Migration Script: Trim course_designation to Core / Elective / Dual / Specialized
-- ============================================================================
-- Removes the other seeded designation values (Digital, TCOM, TCSEG, TELE,
-- TEM, TIA, TPIA, TPROG, TRH) that were never actually assigned to any
-- course - course.designation's FK is ON DELETE RESTRICT, so this fails
-- loudly instead of silently orphaning a course if that assumption is ever
-- wrong on a different database. component_block_rule.component (ON DELETE
-- CASCADE) only has a row for 'Core', so nothing cascades from this either.
--
-- Purpose: keep the Courses UI's designation dropdown to just the four
-- values actually in use, per request.
-- Date: 2026-08-24
-- ============================================================================

DELETE FROM course_designation
WHERE name NOT IN ('Core', 'Elective', 'Dual', 'Specialized');
