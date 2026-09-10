-- ============================================================================
-- Migration Script: Consolidate every course designation except Core/Digital
-- into Specialized
-- ============================================================================
-- Data-only update (not a course_designation lookup-value rename): every
-- course currently designated TCOM, TCSEG, TELE, TEM, TIA, TPIA, TPROG, or
-- TRH moves to the existing "Specialized" designation. Core and Digital
-- courses are left untouched. The now-unused designation values
-- (TCOM/TCSEG/TELE/TEM/TIA/TPIA/TPROG/TRH) are deliberately left in the
-- course_designation lookup table rather than deleted - still valid,
-- selectable options for future courses, just not currently in use by any.
--
-- Purpose: requested consolidation of course designations.
-- Date: 2026-08-22
-- ============================================================================

BEGIN;

UPDATE course SET designation = 'Specialized' WHERE designation NOT IN ('Core', 'Digital');

COMMIT;
