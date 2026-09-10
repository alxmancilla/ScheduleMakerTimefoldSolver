-- ============================================================================
-- BACKFILL group_course.default_teacher_id
-- ============================================================================
-- default_teacher_id was added by add_group_course_default_teacher.sql but
-- was never backfilled from existing data - of the (group, course) pairs in
-- group_course at the time this was written, the vast majority had it unset.
-- BlockGenerationService reads this column as the teacher for every block it
-- generates for a pairing, and GroupCourseController's own note that setting
-- it has "no effect once blocks already exist" means it was silently unused
-- for every pairing that already had blocks - exactly the pairings that
-- would matter if those blocks were ever regenerated (e.g. to apply new
-- block-shaping rules), since regeneration reads this column, not the blocks
-- it's about to replace. Without this backfill, regenerating any of those
-- pairings' blocks would silently lose the teacher.
--
-- Safe to backfill mechanically: every (group, course) pair with existing
-- course_block_assignment rows was confirmed (before writing this migration)
-- to have exactly one distinct teacher_id across all of them - no pairing
-- here has two blocks disagreeing on who teaches it. The HAVING clause below
-- re-verifies that per pairing anyway, so a pairing with a genuine teacher
-- conflict is left alone (still NULL) rather than having one of the
-- conflicting teachers picked arbitrarily.
BEGIN;

UPDATE group_course gc
SET default_teacher_id = sub.teacher_id
FROM (
    SELECT cba.group_id, c.name AS course_name, min(cba.teacher_id) AS teacher_id
    FROM course_block_assignment cba
    JOIN course c ON c.id = cba.course_id
    WHERE cba.teacher_id IS NOT NULL
    GROUP BY cba.group_id, c.name
    HAVING count(DISTINCT cba.teacher_id) = 1
) sub
WHERE gc.group_id = sub.group_id
  AND gc.course_name = sub.course_name
  AND gc.default_teacher_id IS NULL;

COMMIT;
