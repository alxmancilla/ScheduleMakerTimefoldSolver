# Constraint Violation Fix Recommendations

## 🚨 **Problem: 28 Violations of "Maximum 1 Block Per Course Per Group Per Day"**

### **Root Cause Analysis**

The constraint `maxOneBlockPerCoursePerGroupPerDay` is currently **ONLY applied to BASICAS courses**, but the violations are occurring for **NON-BASICAS courses**.

**Current constraint implementation** (line 282-285 in `SchoolConstraintProvider.java`):
```java
// Only apply to BASICAS courses
String component = a1.getCourse().getComponent();
return component != null && component.equalsIgnoreCase("BASICAS");
```

**The problem**: The constraint name says "Maximum 1 block per course per group per day" but it's only enforcing this for BASICAS courses. The violation report is showing that **non-BASICAS courses** are violating this rule.

---

## 🔍 **Why Are Non-BASICAS Courses Creating Multiple Blocks Per Day?**

Looking at the block decomposition strategy in the database loading script:

### **Current Block Decomposition Patterns**

| Hours | Block Pattern | Example |
|-------|---------------|---------|
| 3 hrs | 2h + 1h | Two separate blocks |
| 4 hrs | 2h + 2h | Two separate blocks |
| 5 hrs | 3h + 2h | Two separate blocks |
| 6 hrs | 3h + 3h | Two separate blocks |
| 7 hrs | 4h + 3h | Two separate blocks |
| 8 hrs | 4h + 4h | Two separate blocks |
| 9 hrs | 4h + 3h + 2h | Three separate blocks |
| 10 hrs | 4h + 3h + 3h | Three separate blocks |
| 11 hrs | 4h + 3h + 2h + 2h | Four separate blocks |

**The issue**: The solver is assigning multiple blocks for the same course to the **same day**, which creates violations.

**Example from violations**:
- Group 2APRO, Course "CODIFICA SOFTWARE..." on TUESDAY has **3 blocks**: Mar 12-14, Mar 10-12, Mar 14-15
- Group 4AARH, Course "GESTIONA LOS PROCESOS..." on THURSDAY has **3 blocks**: Jue 11-13, Jue 9-11, Jue 13-15

---

## 💡 **Recommended Solutions**

### **Solution 1: Extend Constraint to ALL Courses** 🎯 **RECOMMENDED**

**Change**: Modify the constraint to apply to **ALL courses** (not just BASICAS).

**Rationale**:
- Pedagogically sound: Students shouldn't have 3 blocks of the same course on the same day
- Reduces schedule fragmentation
- Spreads learning across the week
- Matches the constraint name and violation report expectations

**Implementation**:

```java
private Constraint maxOneBlockPerCoursePerGroupPerDay(ConstraintFactory constraintFactory) {
    // Each group can have at most 1 block per course per day
    // This prevents courses from having multiple blocks on the same day
    // Applies to ALL courses (BASICAS and non-BASICAS)
    // OPTIMIZED: Uses Joiners to pre-filter pairs by group, course, and day
    return constraintFactory
            .forEachUniquePair(CourseBlockAssignment.class,
                    Joiners.equal(CourseBlockAssignment::getGroup),
                    Joiners.equal(CourseBlockAssignment::getCourse),
                    Joiners.equal(a -> a.getTimeslot() != null ? a.getTimeslot().getDayOfWeek() : null))
            .filter((a1, a2) -> {
                // Exclude pinned assignments
                if (a1.isPinned() || a2.isPinned()) {
                    return false;
                }
                // Apply to ALL courses (removed BASICAS-only filter)
                return true;
            })
            .penalize(HardSoftScore.ONE_HARD)
            .asConstraint("Maximum 1 block per course per group per day");
}
```

**Impact**:
- ✅ Eliminates all 28 violations
- ✅ Forces solver to spread blocks across different days
- ✅ Improves schedule quality (better learning distribution)
- ⚠️ May increase solving time (more constraints to satisfy)

---

### **Solution 2: Make It a Soft Constraint** ⚖️

**Change**: Convert from HARD to SOFT constraint with appropriate weight.

**Rationale**:
- Allows solver more flexibility
- Prioritizes feasibility over perfect distribution
- Still penalizes multiple blocks per day, but doesn't make it impossible

**Implementation**:

```java
private Constraint maxOneBlockPerCoursePerGroupPerDay(ConstraintFactory constraintFactory) {
    // SOFT: Prefer to spread course blocks across different days
    // Weight: 2 (medium priority - pedagogical preference)
    return constraintFactory
            .forEachUniquePair(CourseBlockAssignment.class,
                    Joiners.equal(CourseBlockAssignment::getGroup),
                    Joiners.equal(CourseBlockAssignment::getCourse),
                    Joiners.equal(a -> a.getTimeslot() != null ? a.getTimeslot().getDayOfWeek() : null))
            .filter((a1, a2) -> !a1.isPinned() && !a2.isPinned())
            .penalize(HardSoftScore.ONE_SOFT, (a1, a2) -> 2) // Weight: 2
            .asConstraint("Prefer spreading course blocks across different days");
}
```

**Impact**:
- ✅ Solver can find feasible solutions more easily
- ✅ Still encourages spreading blocks across days
- ❌ May still have some courses with multiple blocks per day
- ⚠️ Requires tuning weight to balance with other soft constraints

---

### **Solution 3: Hybrid Approach - Different Rules for Different Course Types** 🔀

**Change**: Apply HARD constraint to BASICAS, SOFT constraint to non-BASICAS.

**Rationale**:
- BASICAS courses are shorter (1-4 hours) - strict rule makes sense
- Non-BASICAS courses are longer (6-11 hours) - may need flexibility
- Balances pedagogical requirements with scheduling feasibility

**Implementation**:

```java
private Constraint maxOneBlockPerBasicasCoursePerGroupPerDay(ConstraintFactory constraintFactory) {
    // HARD: BASICAS courses limited to 1 block per day
    return constraintFactory
            .forEachUniquePair(CourseBlockAssignment.class,
                    Joiners.equal(CourseBlockAssignment::getGroup),
                    Joiners.equal(CourseBlockAssignment::getCourse),
                    Joiners.equal(a -> a.getTimeslot() != null ? a.getTimeslot().getDayOfWeek() : null))
            .filter((a1, a2) -> {
                if (a1.isPinned() || a2.isPinned()) return false;
                String component = a1.getCourse().getComponent();
                return component != null && component.equalsIgnoreCase("BASICAS");
            })
            .penalize(HardSoftScore.ONE_HARD)
            .asConstraint("Maximum 1 block per BASICAS course per group per day");
}

private Constraint preferSpreadingNonBasicasBlocks(ConstraintFactory constraintFactory) {
    // SOFT: Non-BASICAS courses prefer spreading across days (weight: 2)
    return constraintFactory
            .forEachUniquePair(CourseBlockAssignment.class,
                    Joiners.equal(CourseBlockAssignment::getGroup),
                    Joiners.equal(CourseBlockAssignment::getCourse),
                    Joiners.equal(a -> a.getTimeslot() != null ? a.getTimeslot().getDayOfWeek() : null))
            .filter((a1, a2) -> {
                if (a1.isPinned() || a2.isPinned()) return false;
                String component = a1.getCourse().getComponent();
                return component == null || !component.equalsIgnoreCase("BASICAS");
            })
            .penalize(HardSoftScore.ONE_SOFT, (a1, a2) -> 2)
            .asConstraint("Prefer spreading non-BASICAS blocks across different days");
}
```

**Impact**:
- ✅ Maintains strict rule for BASICAS courses
- ✅ Allows flexibility for complex non-BASICAS courses
- ✅ Balances feasibility and quality
- ⚠️ Adds complexity (two constraints instead of one)

---

### **Solution 4: Improve Block Decomposition Strategy** 🔧

**Change**: Modify the database loading script to create smarter block patterns that naturally avoid same-day conflicts.

**Rationale**:
- Fix the problem at the source (data generation)
- Reduce solver burden
- Create more realistic schedules from the start

**Current problematic patterns**:
- 4 hrs → 2h + 2h (both blocks could be assigned to same day)
- 6 hrs → 3h + 3h (both blocks could be assigned to same day)
- 8 hrs → 4h + 4h (both blocks could be assigned to same day)

**Improved patterns** (for courses requiring 4+ hours):
- 4 hrs → **1×4** (single block - no conflict possible)
- 6 hrs → **1×4 + 1×2** (different sizes help solver distribute)
- 8 hrs → **1×4 + 1×4** (keep as is, but add constraint)

**Implementation**: Modify `generate_course_blocks()` function in database script:

```sql
CASE p_hours
    WHEN 3 THEN
        -- Keep as 2h + 1h for flexibility
        INSERT INTO course_block_assignment (id, group_id, course_id, block_length, ...)
        VALUES
            (p_group_id || '_' || p_course_id || '_0', p_group_id, p_course_id, 2, ...),
            (p_group_id || '_' || p_course_id || '_1', p_group_id, p_course_id, 1, ...);
    WHEN 4 THEN
        -- CHANGED: Use single 4-hour block instead of 2×2h
        INSERT INTO course_block_assignment (id, group_id, course_id, block_length, ...)
        VALUES
            (p_group_id || '_' || p_course_id || '_0', p_group_id, p_course_id, 4, ...);
    WHEN 5 THEN
        -- Keep as 3h + 2h (different sizes)
        INSERT INTO course_block_assignment (id, group_id, course_id, block_length, ...)
        VALUES
            (p_group_id || '_' || p_course_id || '_0', p_group_id, p_course_id, 3, ...),
            (p_group_id || '_' || p_course_id || '_1', p_group_id, p_course_id, 2, ...);
    WHEN 6 THEN
        -- CHANGED: Use 4h + 2h instead of 3h + 3h
        INSERT INTO course_block_assignment (id, group_id, course_id, block_length, ...)
        VALUES
            (p_group_id || '_' || p_course_id || '_0', p_group_id, p_course_id, 4, ...),
            (p_group_id || '_' || p_course_id || '_1', p_group_id, p_course_id, 2, ...);
    -- ... continue for other hour counts
END CASE;
```

**Impact**:
- ✅ Reduces violations at the source
- ✅ Creates more realistic block patterns
- ✅ Easier for solver to find feasible solutions
- ⚠️ Requires reloading database with new patterns
- ⚠️ May create longer blocks that are harder to schedule

---

## 🎯 **My Recommendation: Solution 1 (Extend Constraint to ALL Courses)**

### **Why This Is the Best Approach**

1. **Pedagogically Sound** ✅
   - Students shouldn't have 3 blocks of the same course on the same day
   - Better learning distribution across the week
   - Reduces cognitive overload

2. **Matches Expectations** ✅
   - The constraint name implies it applies to all courses
   - The violation report is flagging non-BASICAS courses
   - Users expect this behavior

3. **Simple Implementation** ✅
   - One-line change (remove BASICAS filter)
   - No additional complexity
   - Easy to understand and maintain

4. **Proven Approach** ✅
   - This is how most school scheduling systems work
   - Industry best practice
   - Aligns with real-world scheduling needs

### **Implementation Steps**

#### **Step 1: Modify the Constraint**

Edit `src/main/java/com/example/solver/SchoolConstraintProvider.java`:

```java
private Constraint maxOneBlockPerCoursePerGroupPerDay(ConstraintFactory constraintFactory) {
    // Each group can have at most 1 block per course per day
    // This prevents courses from having multiple blocks on the same day
    // Applies to ALL courses (BASICAS and non-BASICAS)
    // OPTIMIZED: Uses Joiners to pre-filter pairs by group, course, and day
    return constraintFactory
            .forEachUniquePair(CourseBlockAssignment.class,
                    Joiners.equal(CourseBlockAssignment::getGroup),
                    Joiners.equal(CourseBlockAssignment::getCourse),
                    Joiners.equal(a -> a.getTimeslot() != null ? a.getTimeslot().getDayOfWeek() : null))
            .filter((a1, a2) -> {
                // Exclude pinned assignments
                if (a1.isPinned() || a2.isPinned()) {
                    return false;
                }
                // Apply to ALL courses (BASICAS and non-BASICAS)
                return true;
            })
            .penalize(HardSoftScore.ONE_HARD)
            .asConstraint("Maximum 1 block per course per group per day");
}
```

#### **Step 2: Update the Analyzer**

Edit `src/main/java/com/example/analysis/BlockScheduleAnalyzer.java` to match the new constraint logic (if needed).

#### **Step 3: Recompile and Test**

```bash
mvn clean compile
mvn exec:java -Dexec.mainClass="com.example.MainBlockSchedulingApp"
```

#### **Step 4: Verify Results**

Check that:
- ✅ 0 violations of "Maximum 1 block per course per group per day"
- ✅ Blocks are spread across different days
- ✅ Feasible solution still achievable (0hard score)

---

## 📊 **Expected Outcomes**

### **Before Fix**
- ❌ 28 violations of "Maximum 1 block per course per group per day"
- ❌ Courses with 3 blocks on same day (e.g., Mar 12-14, Mar 10-12, Mar 14-15)
- ❌ Poor learning distribution

### **After Fix (Solution 1)**
- ✅ 0 violations of "Maximum 1 block per course per group per day"
- ✅ Each course has at most 1 block per day
- ✅ Blocks spread across Mon-Fri
- ✅ Better learning distribution

### **Potential Trade-offs**
- ⚠️ Solving time may increase slightly (more constraints to satisfy)
- ⚠️ May need to adjust termination criteria if solver struggles
- ⚠️ Some courses with many hours (8-11 hrs) may be harder to schedule

---

## 🔄 **Alternative: If Solution 1 Doesn't Work**

If extending the constraint to all courses makes it **impossible to find a feasible solution**, then:

1. **Try Solution 3 (Hybrid Approach)**:
   - Keep HARD constraint for BASICAS
   - Use SOFT constraint (weight 2) for non-BASICAS
   - This gives solver more flexibility while still encouraging good distribution

2. **Try Solution 4 (Improve Block Decomposition)**:
   - Change 4 hrs from 2h+2h to 1×4h
   - Change 6 hrs from 3h+3h to 4h+2h
   - This reduces the number of blocks that need to be spread across days

3. **Increase Solver Time**:
   - Change `unimprovedMinutesSpentLimit` from 8 to 15 minutes
   - Give solver more time to explore solutions

---

## 📝 **Summary**

**Problem**: 28 violations because non-BASICAS courses have multiple blocks on the same day.

**Root Cause**: Constraint only applies to BASICAS courses, but violation report expects it to apply to all courses.

**Recommended Solution**: Extend constraint to ALL courses (Solution 1).

**Implementation**: Remove the BASICAS-only filter from the constraint.

**Expected Result**: 0 violations, better schedule quality, blocks spread across the week.

**Fallback**: If Solution 1 doesn't work, try Solution 3 (hybrid HARD/SOFT) or Solution 4 (improve block decomposition).

---

**Would you like me to implement Solution 1 for you?** 🚀


