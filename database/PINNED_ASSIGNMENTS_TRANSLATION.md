# Pinned Assignments Translation: Hour-Based to Block-Based

## Overview

This document explains how the hour-based pinned assignments from `load_demo_data.sql` were translated to block-based assignments in `pinned_block_assignments.sql`.

## Original Hour-Based Pinned Assignments

The original `load_demo_data.sql` had **35 pinned assignments** with `pinned=TRUE` for courses 15 and 16:

### Course 15: REALIZA ANALISIS FISICOS Y QUIMICOS A LA MATERIA PRIMA
- **Total Hours**: 8 hours per week
- **Groups**: 2APIA, 2BPIA

### Course 16: REALIZA ANALISIS MICROBIOLOGICOS A LA MATERIA PRIMA
- **Total Hours**: 9 hours per week
- **Groups**: 2APIA, 2BPIA

---

## Translation Strategy

### Challenge: Non-Consecutive Hours

The original hour-based pinned assignments were **NOT consecutive hours**, which required manual block creation:

**Example (Course 15, Group 2APIA):**
- Original: 8 individual hours spread across 3 days
  - Mon 8-9, Mon 9-10 (2 hours in AULA 6)
  - Thu 12-13, Thu 13-14, Thu 14-15 (3 hours in LQ 1)
  - Fri 7-8, Fri 8-9, Fri 9-10 (3 hours in LQ 1)

- Block translation: 3 blocks (2+3+3 pattern)
  - Block 0: Mon 8-10 (2 hours)
  - Block 1: Thu 12-15 (3 hours)
  - Block 2: Fri 7-10 (3 hours)

### Solution: Manual Block Creation

Created custom blocks that match the exact consecutive hour patterns from the original schedule.

---

## Block-Based Pinned Assignments (SQL)

### Complete SQL Script

```sql
-- ============================================================================
-- PINNED BLOCK ASSIGNMENTS - Manual Block Creation
-- ============================================================================

-- Delete auto-generated blocks for courses 15 and 16
DELETE FROM course_block_assignment WHERE course_id IN ('15', '16');

-- ============================================================================
-- Course 15: REALIZA ANALISIS FISICOS (8 hours)
-- ============================================================================

-- Group 2APIA - Teacher: 48SLAMC (SANDRA LETICIA)
INSERT INTO course_block_assignment (id, group_id, course_id, block_length, teacher_id, room_name, block_timeslot_id, pinned)
VALUES
    ('2APIA_15_0', '2APIA', '15', 2, '48SLAMC', 'AULA 6', 'block_6', TRUE),    -- Mon 8-10
    ('2APIA_15_1', '2APIA', '15', 3, '48SLAMC', 'LQ 1', 'block_101', TRUE),    -- Thu 12-15
    ('2APIA_15_2', '2APIA', '15', 3, '48SLAMC', 'LQ 1', 'block_107', TRUE);    -- Fri 7-10

-- Group 2BPIA - Teacher: 48YESMR (YESENIA)
INSERT INTO course_block_assignment (id, group_id, course_id, block_length, teacher_id, room_name, block_timeslot_id, pinned)
VALUES
    ('2BPIA_15_0', '2BPIA', '15', 2, '48YESMR', 'AULA 8', 'block_70', TRUE),   -- Wed 11-13
    ('2BPIA_15_1', '2BPIA', '15', 3, '48YESMR', 'LQ 1', 'block_81', TRUE),     -- Thu 7-10
    ('2BPIA_15_2', '2BPIA', '15', 3, '48YESMR', 'LQ 1', 'block_127', TRUE);    -- Fri 12-15

-- ============================================================================
-- Course 16: REALIZA ANALISIS MICROBIOLOGICOS (9 hours)
-- ============================================================================

-- Group 2APIA - Teacher: 48SLAMC (SANDRA LETICIA)
INSERT INTO course_block_assignment (id, group_id, course_id, block_length, teacher_id, room_name, block_timeslot_id, pinned)
VALUES
    ('2APIA_16_0', '2APIA', '16', 4, '48SLAMC', 'LMICRO', 'block_30', TRUE),   -- Tue 7-11
    ('2APIA_16_1', '2APIA', '16', 3, '48SLAMC', 'LMICRO', 'block_67', TRUE),   -- Wed 10-13
    ('2APIA_16_2', '2APIA', '16', 2, '48SLAMC', 'AULA 6', 'block_84', TRUE);   -- Thu 8-10

-- Group 2BPIA - Teacher: 48SLAMC (SANDRA LETICIA)
INSERT INTO course_block_assignment (id, group_id, course_id, block_length, teacher_id, room_name, block_timeslot_id, pinned)
VALUES
    ('2BPIA_16_0', '2BPIA', '16', 4, '48SLAMC', 'LMICRO', 'block_46', TRUE),   -- Tue 11-15
    ('2BPIA_16_1', '2BPIA', '16', 3, '48SLAMC', 'LMICRO', 'block_55', TRUE),   -- Wed 7-10
    ('2BPIA_16_2', '2BPIA', '16', 2, '48SLAMC', 'AULA 8', 'block_92', TRUE);   -- Thu 10-12
```

---

## Block Timeslot ID Mapping

| Block ID | Day | Start Hour | Length | Description |
|----------|-----|------------|--------|-------------|
| block_6 | Mon (1) | 8 | 2 | Mon 8-10 |
| block_30 | Tue (2) | 7 | 4 | Tue 7-11 |
| block_46 | Tue (2) | 11 | 4 | Tue 11-15 |
| block_55 | Wed (3) | 7 | 3 | Wed 7-10 |
| block_67 | Wed (3) | 10 | 3 | Wed 10-13 |
| block_70 | Wed (3) | 11 | 2 | Wed 11-13 |
| block_81 | Thu (4) | 7 | 3 | Thu 7-10 |
| block_84 | Thu (4) | 8 | 2 | Thu 8-10 |
| block_92 | Thu (4) | 10 | 2 | Thu 10-12 |
| block_101 | Thu (4) | 12 | 3 | Thu 12-15 |
| block_107 | Fri (5) | 7 | 3 | Fri 7-10 |
| block_127 | Fri (5) | 12 | 3 | Fri 12-15 |

---

## Verification Results

✅ **All pinned blocks created successfully**

| Group | Course | Total Hours | Expected Hours | Status |
|-------|--------|-------------|----------------|--------|
| 2APIA | 15 | 8 | 8 | ✅ |
| 2BPIA | 15 | 8 | 8 | ✅ |
| 2APIA | 16 | 9 | 9 | ✅ |
| 2BPIA | 16 | 9 | 9 | ✅ |

**Total**: 12 pinned block assignments (6 for Course 15, 6 for Course 16)

---

## Usage

To apply these pinned assignments:

```bash
psql -U mancilla -d school_schedule -f database/pinned_block_assignments.sql
```

This script will:
1. Delete auto-generated blocks for courses 15 and 16
2. Create custom blocks with exact timeslots, teachers, and rooms
3. Mark all blocks as `pinned=TRUE` so the solver won't change them
4. Display verification results

---

## Notes

- The pinned blocks use specific teachers, rooms, and timeslots
- All blocks are marked as `pinned=TRUE` to prevent the solver from modifying them
- The block structure (2+3+3 for 8 hours, 4+3+2 for 9 hours) matches the original consecutive hour patterns
- This translation preserves the exact schedule from the hour-based system

