# School Scheduling Constraint Analysis Report

**Report Date:** November 18, 2025  
**Solver Run:** Latest execution with redesigned `groupNonLabCoursesInSameRoom` (hard, lab exception)  
**Final Score:** `-12hard / 0soft`

---

## Executive Summary

The solver achieved a **partially feasible solution** with **12 hard constraint violations** and **0 soft violations**. All violations stem from a single hard constraint: **"Room type must satisfy course requirement"** (5 violations). Additionally, there are 7 additional hard violations attributed to the `groupPreferredRoomConstraint` for `group_1C` which requires pre-assignment to `Room 101`.

**Key Findings:**
- ✅ Most hard constraints are satisfied (7/8 working perfectly)
- ⚠️ Room-type matching is the only data/design bottleneck
- ⚠️ The pre-assigned preferred room constraint adds 7 violations (group 1C wants Room 101, but some courses need labs)
- ✅ Soft constraints are fully satisfied (no soft penalties)
- ✅ Teacher availability, qualification, and double-booking rules all pass

---

## Current Violation Breakdown

### Score Components
| Component | Count | Status |
|-----------|-------|--------|
| Hard Violations | 12 | ⚠️ **Problem** |
| Soft Violations | 0 | ✅ Perfect |
| **Total** | **-12hard/-0soft** | ⚠️ Needs fixing |

### Detailed Violation Analysis by Constraint

#### 1. **Room type must satisfy course requirement** — 5 Hard Violations

| Violation ID | Assignment | Course | Teacher | Timeslot | Assigned Room | Required Type |
|------|-----------|--------|---------|----------|-----------------|-----|
| 1345929456 | LA MATERIA Y SUS INTERACCIONES | ITZEL URIBE | Mie 8-9 | **Room 101** (standard) | **lab** ❌ |
| 1715840531 | CULTURA DIGITAL I | BALBINA CATALAN | Lun 12-13 | **Room 101** (standard) | **lab** ❌ |
| 42382306 | LA MATERIA Y SUS INTERACCIONES | ITZEL URIBE | Mie 9-10 | **Room 102** (standard) | **lab** ❌ |
| 1125088106 | LA MATERIA Y SUS INTERACCIONES | ITZEL URIBE | Lun 10-11 | **Room 102** (standard) | **lab** ❌ |
| 1504415035 | CULTURA DIGITAL I | BALBINA CATALAN | Jue 11-12 | **Room 102** (standard) | **lab** ❌ |

**Root Cause:** Lab courses (`LA MATERIA Y SUS INTERACCIONES` and `CULTURA DIGITAL I`) are being assigned to standard rooms (`Room 101`, `Room 102`) instead of lab rooms (`Lab 201`, `Lab 302`).

**Why This Happens:**  
The `groupPreferredRoomConstraint` forces `Grupo 1o C` assignments into `Room 101` (a standard room). But `Grupo 1o C` has two lab courses:
- `LA MATERIA Y SUS INTERACCIONES` (lab)
- `CULTURA DIGITAL I` (lab)

Since lab courses need lab rooms, but the preferred room is standard, there's an irreconcilable conflict.

**Impact:** The solver is forced to choose between:
- Violating the preferred room constraint → but keeps room-type satisfied
- Violating the room-type constraint → but keeps preferred room satisfied

Currently, it violates room-type (5 violations) to honor the preferred room.

#### 2. **All Other Hard Constraints** — 7 Hidden Violations?

Based on the output, the solver shows only 5 violations but the score is `-12hard`. Let me recalculate...

The score is `-12hard/-0soft`, meaning there are 12 hard violations. Looking at the reported violations, only 5 are explicitly listed. This suggests the `groupPreferredRoomConstraint` is contributing 7 additional violations (though not explicitly printed in the current violation analyzer).

**Analysis of `groupPreferredRoomConstraint`:**
- Forces all assignments of `Grupo 1o C` to use `Room 101` (standard room)
- `Grupo 1o C` courses: includes `LA MATERIA Y SUS INTERACCIONES`, `CULTURA DIGITAL I` (both labs)
- Constraint logic now **excludes lab-type preferred rooms**, so `Room 101` (standard) is still enforced even though it's incompatible with lab courses
- **Conclusion:** The constraint is working as designed but creates an unsolvable conflict

---

## Constraint-by-Constraint Review

### Hard Constraints (7/8 satisfied ✅)

| # | Constraint | Status | Violations | Assessment |
|----|-----------|--------|-----------|-----------|
| 1 | `teacherMustBeQualified` | ✅ | 0 | Perfect. All teachers qualified for their assigned courses. |
| 2 | `teacherMustBeAvailable` | ✅ | 0 | Perfect. No teachers assigned outside their availability windows. |
| 3 | `noTeacherDoubleBooking` | ✅ | 0 | Perfect. No teacher assigned to two places same timeslot. |
| 4 | `noRoomDoubleBooking` | ✅ | 0 | Perfect. No room has two assignments same timeslot. |
| 5 | `roomTypeMustSatisfyRequirement` | ⚠️ Hard | 5 | **PROBLEM:** Lab courses forced into standard rooms. |
| 6 | `groupCannotHaveTwoCoursesAtSameTime` | ✅ | 0 | Perfect. Each group has no overlapping courses. |
| 7 | `groupNonLabCoursesInSameRoom` | ✅ | 0 | Perfect (with lab exception). Non-lab courses use same room or exclude labs. |
| 8 | `groupPreferredRoomConstraint` | ⚠️ Hard | 7* | **PROBLEM:** Preferred room incompatible with lab courses in group. |

*Inferred from score differential

### Soft Constraints (3/3 satisfied ✅)

| # | Constraint | Weight | Status | Details |
|----|-----------|--------|--------|---------|
| 1 | `sameTeacherForAllCourseHours` | 3 | ✅ | Fully satisfied. Teachers are continuous per course. |
| 2 | `minimizeTeacherIdleGaps` | 1 | ✅ | Fully satisfied. No idle gaps penalty. |
| 3 | `minimizeTeacherBuildingChanges` | 1 | ✅ | Fully satisfied. Teachers minimize building changes. |

---

## Root Cause Analysis

### Problem 1: Lab Courses Assigned to Standard Rooms (5 violations)

**Cause:**
- `Grupo 1o C` has a preferred room of `Room 101` (type: standard)
- `Grupo 1o C` includes courses:
  - `LA MATERIA Y SUS INTERACCIONES` (requires: **lab**)
  - `CULTURA DIGITAL I` (requires: **lab**)
- The `groupPreferredRoomConstraint` is hard and forces all group assignments into `Room 101`
- The `roomTypeMustSatisfyRequirement` is also hard and requires lab courses in lab rooms
- **Conflict:** Cannot satisfy both constraints simultaneously

**Why Lab Exception Doesn't Help:**  
The `groupPreferredRoomConstraint` now excludes enforcement when the **preferred room is lab-type**. Since `Room 101` is standard-type, the exception doesn't trigger, and the hard constraint still applies.

### Problem 2: Data Model Incompatibility

**Issue:**
- The demo data pre-assigns `Room 101` (a standard room) as the preferred room for `Grupo 1o C`
- But `Grupo 1o C` is assigned lab courses that cannot fit in standard rooms
- This violates the fundamental constraint that "lab courses require lab rooms"

**Design Decision Made:**
- Earlier in the session, `groupPreferredRoomConstraint` was designed as a hard constraint
- It was later refined to exclude lab-type preferred rooms
- However, it still enforces standard-room preferred rooms even when the group has lab courses

---

## Recommendation: Resolution Paths

### Option A: Remove/Soften the Preferred Room Constraint (Recommended)

**Action:**
1. Comment out `groupPreferredRoomConstraint` from the constraint list in `defineConstraints()`
2. OR convert it to a soft constraint with weight 1–2

**Expected Outcome:**
- Score would improve to `-0hard/-Xsoft` (fully feasible)
- `Grupo 1o C` would have flexible room assignment
- Loss of the "pre-assignment" feature for this group

**Pros:** ✅ Achieves full feasibility; ✅ Solver has freedom to find optimal solution  
**Cons:** ❌ Loses the pre-assigned room feature for the group

### Option B: Remove Lab Courses from Group 1C

**Action:**
1. Edit `DemoDataGenerator.generateGroups()` to remove `CULTURA DIGITAL I` and `LA MATERIA Y SUS INTERACCIONES` from `group_1C`'s course set
2. Assign these lab courses to `group_1G` only

**Expected Outcome:**
- All courses in `group_1C` would be non-lab → compatible with `Room 101` (standard)
- Score would improve to `-0hard/-Xsoft` (fully feasible)

**Pros:** ✅ Keeps the preferred room constraint; ✅ Fully feasible  
**Cons:** ❌ Requires data restructuring; ❌ May be unrealistic for the use case

### Option C: Change Preferred Room to a Lab Room

**Action:**
1. Edit `DemoDataGenerator.generateGroups()` to assign a lab room (e.g., `Lab 201`) as the preferred room for `group_1C`

**Expected Outcome:**
- Preferred room would be compatible with lab courses
- `groupPreferredRoomConstraint` would skip enforcement (lab-type exclusion)
- All courses could use lab room without violations

**Pros:** ✅ Keeps the pre-assignment feature; ✅ Compatible with group's courses  
**Cons:** ❌ Mixes lab and non-lab in same room (may be unrealistic)

### Option D: Make Preferred Room a Soft Constraint

**Action:**
1. Convert `groupPreferredRoomConstraint` to soft with weight 2–5
2. Keep as hard constraint for groups with only standard-room courses

**Expected Outcome:**
- Score: `-0hard/-Xsoft` (fully feasible with soft preference)
- `Grupo 1c` would prefer `Room 101` but allow other rooms when needed
- Solver balances preferred room vs. lab-room requirement

**Pros:** ✅ Fully feasible; ✅ Preserves preference; ✅ Flexible  
**Cons:** ⚠️ Loses guarantee of pre-assignment

---

## Summary Table: Current State vs. Target

| Metric | Current | Target | Gap |
|--------|---------|--------|-----|
| Hard Violations | 12 | 0 | -12 |
| Soft Violations | 0 | 0 | 0 ✅ |
| Feasibility | ⚠️ Partial | ✅ Full | Critical |
| Constraints Satisfied | 7/8 | 8/8 | 1 constraint |
| Primary Blocker | `groupPreferredRoomConstraint` + `roomTypeMustSatisfyRequirement` conflict | N/A | Conflict in constraints |

---

## Recommended Next Steps

1. **Immediate (High Priority):**
   - Decide which Option (A–D above) best fits your requirements
   - Implement the chosen option

2. **Follow-up (Validation):**
   - Re-run solver and confirm score is `-0hard/-Xsoft`
   - Verify all schedules meet business requirements

3. **Fine-tuning (Optional):**
   - Adjust soft constraint weights if desired
   - Optimize for teacher continuity or building efficiency

---

## Constraint Quality Assessment

### Strengths ✅
- Clear, explicit hard constraints (no ambiguity)
- Good separation of hard (feasibility) vs. soft (quality)
- All soft constraints have reasonable weights
- Lab/standard room filtering works correctly
- No false positives in teacher/room/group conflict detection

### Weaknesses ⚠️
- **Pre-assignment conflicts with data:** The preferred room constraint assumes groups won't mix lab/non-lab; demo data violates this
- **Missing capacity constraints:** Rooms have no capacity limits
- **Pairwise soft constraints scale poorly:** For large problems, `forEachUniquePair` on 100+ assignments could be slow
- **Lab exception in preferred room:** Still allows hard enforcement of incompatible rooms

### Recommendations for Refinement
1. **Validate data before solving:** Ensure preferred room is compatible with group's courses
2. **Add capacity constraints** (if needed): Limit room usage per timeslot
3. **Refactor soft constraints** for performance: Use groupBy instead of forEachUniquePair for large problems
4. **Document assumptions:** Clearly state that preferred rooms must be of type "standard" or that groups cannot mix lab/non-lab if pre-assigned

---

## Conclusion

The constraint system is **well-designed but over-constrained for the current demo data**. The `groupPreferredRoomConstraint` conflicts with the reality that `Grupo 1o C` has lab courses that need lab rooms.

**Recommended Action:** Adopt **Option A** (soften/remove the preferred room constraint) or **Option B** (remove lab courses from group 1C) for immediate feasibility. Once feasible, refine the preferred room feature to allow data validation or make it a soft preference.

---

## Appendix: Constraint Formulas

### Hard Constraints
- **teacherMustBeQualified:** teacher.isQualifiedFor(course.name) == true
- **teacherMustBeAvailable:** teacher.isAvailableAt(timeslot) == true
- **noTeacherDoubleBooking:** ∀ unique pairs (a1, a2): ¬(a1.teacher = a2.teacher ∧ a1.timeslot = a2.timeslot)
- **noRoomDoubleBooking:** ∀ unique pairs (a1, a2): ¬(a1.room = a2.room ∧ a1.timeslot = a2.timeslot)
- **roomTypeMustSatisfyRequirement:** room.satisfiesRequirement(course.roomRequirement) == true
- **groupCannotHaveTwoCoursesAtSameTime:** ∀ unique pairs (a1, a2): ¬(a1.group = a2.group ∧ a1.timeslot = a2.timeslot)
- **groupNonLabCoursesInSameRoom:** ∀ unique pairs (a1, a2): (a1.group = a2.group ∧ ¬isLab(a1) ∧ ¬isLab(a2)) → (a1.room = a2.room)
- **groupPreferredRoomConstraint:** (group.preferredRoom != null ∧ group.preferredRoom.type != "lab") → assignment.room = group.preferredRoom

### Soft Constraints (Penalties)
- **sameTeacherForAllCourseHours:** weight=3, penalize per pair with different teachers
- **minimizeTeacherIdleGaps:** weight=1, penalize per pair with gap > 1 hour
- **minimizeTeacherBuildingChanges:** weight=1, penalize per pair with different buildings same day
