-- ============================================================================
-- Fix: Remove unique constraint that prevents multiple blocks per course
-- ============================================================================
-- 
-- Problem: The constraint uq_block_assignment_group_course prevents multiple
-- block assignments for the same group-course combination.
--
-- However, the block-based scheduling design requires multiple blocks per
-- course. For example:
-- - A 4-hour BASICAS course needs 4 separate 1-hour blocks
-- - A 5-hour non-BASICAS course needs 1×3-hour block + 1×2-hour block
--
-- Solution: Drop the unique constraint
-- ============================================================================

ALTER TABLE course_block_assignment 
DROP CONSTRAINT IF EXISTS uq_block_assignment_group_course;

-- Verify the constraint has been removed
SELECT 'Constraint removed successfully' AS status;

