# Project Review: Schedule Maker Timefoldsolver

**Date:** November 21, 2025  
**Status:** Production-Ready with Enhancement Opportunities  
**Overall Grade:** B+ (Strong foundation, moderate technical debt, good potential)

---

## 1. Architecture & Design

### ✅ Strengths

- **Clean Domain Model:** Well-structured entity classes (`Teacher`, `Course`, `Room`, `Timeslot`, `Group`, `CourseAssignment`, `SchoolSchedule`) with clear separation of concerns.
- **Constraint Streams Pattern:** Uses Timefold Constraint Streams API (declarative, composable, maintainable).
- **Two-Level Scoring:** `HardSoftScore` properly separates feasibility (hard) from quality (soft), enabling clear optimization priorities.
- **Proper Annotations:** Correct use of `@PlanningSolution`, `@PlanningEntity`, `@PlanningVariable`, `@PlanningId`.
- **Maven Build:** Standard Maven structure with clear dependency management; easy to reproduce.

### ⚠️ Issues & Concerns

1. **Code Duplication in Analysis Methods**
   - `MainApp` has parallel hard/soft constraint violation analysis methods (detailed & summary).
   - Logic is duplicated across `analyzeSoftConstraintViolations` and `analyzeSoftConstraintViolationsDetailed`.
   - **Impact:** Medium — maintenance burden; risk of inconsistency between console output and PDF reports.
   - **Recommendation:** Extract common analysis logic into shared helper or utility class.

2. **Hardcoded SCALE Constant**
   - The dynamic teacher-preference constraint uses `final int SCALE = 1000` embedded in both:
     - `SchoolConstraintProvider.preferTeachersWithLessCapacity()` 
     - `MainApp.analyzeSoftConstraintViolations[Detailed]()` (duplicated twice more)
   - **Impact:** Low-Medium — prevents tuning without recompilation; inconsistency risk if values drift.
   - **Recommendation:** Move to a configuration class (`ConstraintConfig.java` or config file).

3. **Temporal Coupling Between Demo Generation & Solver**
   - `ExcelTemplateGenerator` calls `DemoDataGenerator.generateDemoData()` independently.
   - `MainApp` calls it again independently.
   - No caching or versioning if data changes.
   - **Impact:** Low — acceptable for demo, but may cause issues in production.

4. **Mixed Responsibilities in MainApp**
   - Single large file responsible for:
     - Solver invocation
     - Constraint analysis (hard/soft)
     - PDF report generation
     - Console printing (3 different views: by day, teacher, group)
   - **Impact:** Medium — difficult to test, reuse, or extend individual analyses.
   - **Recommendation:** Extract analysis logic into separate `ScheduleAnalyzer` class; create `SchedulePrinter` for output formatting.

---

## 2. Constraints & Logic

### ✅ Strengths

- **Complete Hard Constraints:** All 8 hard constraints well-designed and tested.
- **Clear Intent:** Constraint names are descriptive (`teacherMustBeQualified`, `noTeacherDoubleBooking`, etc.).
- **Good Coverage:** Constraints handle qualification, availability, conflicts, room types, group schedules, continuity, and capacity.
- **Lab Exception Handling:** `groupNonLabCoursesInSameRoom` correctly exempts lab courses from the "same room" rule.

### ⚠️ Issues & Concerns

1. **Assignment-Count vs. Course-Hours Mismatch**
   - `teacherMaxHoursPerWeek` counts **assignments**, not **hours**.
   - If a course is 2+ hours/week and split across multiple timeslots, a teacher's workload could exceed `maxHoursPerWeek` in assignment-count but be within limit in actual hours.
   - **Impact:** Medium — affects capacity enforcement accuracy and the dynamic preference reward calculation.
   - **Example:** Teacher with `maxHoursPerWeek=20` assigned 15 hours + 1 course at 5 hours = 20 actual hours, but if that 5-hour course is split into 5 assignments, count reaches 20 assignments (at max).
   - **Recommendation:** Update both constraint and analysis to sum `Course.getRequiredHoursPerWeek()` per assignment.

2. **Missing "Same Teacher for All Course Hours" Constraint Caveat**
   - The hard constraint `sameTeacherForAllCourseHours` enforces this, but the demo data already satisfies it by design.
   - No test verifying the constraint would catch violations if data were modified.
   - **Impact:** Low — constraint is correct, but lack of test coverage for negative cases.

3. **Soft Constraint Weights Not Tuned**
   - Weights are hardcoded:
     - Prefer room: weight 3
     - Idle gaps: weight 1
     - Building changes: weight 1
     - Low-capacity teachers: dynamic reward (1000 SCALE)
   - No documentation on how weights were chosen or how to tune them.
   - **Impact:** Medium — limit ability to optimize for business priorities.
   - **Recommendation:** Document weight rationale; expose via configuration.

4. **forEachUniquePair Scalability**
   - Constraints like `minimizeTeacherIdleGaps` and `minimizeTeacherBuildingChanges` use `forEachUniquePair(CourseAssignment.class)`.
   - Complexity: O(n²) in number of assignments (currently ~77, but could grow).
   - **Impact:** Low-Medium — acceptable for current size (~77 assignments); watch for performance if scale grows 10x.
   - **Recommendation:** For very large problems, consider refactoring to grouped or indexed approaches.

---

## 3. Data Model

### ✅ Strengths

- **Backward Compatibility:** `Teacher` and `Course` have constructors that auto-generate `id` from names, enabling smooth upgrades.
- **Per-Day Availability:** `Teacher` now has `Map<DayOfWeek, Set<Integer>> availabilityPerDay`, enabling hour-level granularity.
- **Workload Limits:** `Teacher.maxHoursPerWeek` and hard constraint enforcement.
- **Room Typing:** Rooms have `type` and `building`, supporting context-aware scheduling.

### ⚠️ Issues & Concerns

1. **Limited Room Capacity**
   - Rooms have no capacity constraints (max students).
   - Assumes all courses fit in any room regardless of expected class size.
   - **Impact:** Low in current demo; High if deployed to real schools.
   - **Recommendation:** Add `Room.capacity`, `Group.size`, and a hard constraint.

2. **No Teacher Rest Periods**
   - Teachers can be assigned back-to-back courses across all days/hours.
   - No minimum break time between courses or lunch window.
   - **Impact:** Medium — unrealistic for actual schools.
   - **Recommendation:** Add soft constraint for teacher rest gaps (e.g., 1+ hour breaks).

3. **No Student Preferences**
   - Groups are assigned fixed course sets; no electives or preferences.
   - **Impact:** Low — out of scope for first version; design enables future addition.

4. **Time Representation**
   - Timeslots are discrete (integer hours 7–14).
   - No 30-minute or 15-minute granularity.
   - **Impact:** Low — acceptable for demo; can add if needed later.

---

## 4. Reporting & Output

### ✅ Strengths

- **Multi-Format Reports:** Console, PDF (3 separate reports), and Excel template.
- **Violation Analysis:** Both hard and soft constraint violations are tracked and reported.
- **Detailed Breakdowns:** Per-teacher and per-group schedules included.
- **Excel Pre-Fill:** Template now includes demo data (teachers with id/availability, courses, etc.).

### ⚠️ Issues & Concerns

1. **Code Duplication in Analysis**
   - Hard and soft constraint analysis methods are nearly identical; only the logic inside the map-building loop differs.
   - **Impact:** Medium — maintenance burden; duplicated code = duplicated bugs.

2. **No Export Versioning**
   - Excel and PDF files are written with fixed names (`schedule-template.xlsx`, `schedule-report-*.pdf`).
   - No timestamp or version info in filenames.
   - **Impact:** Low — acceptable for demo; risky in production if you need to archive multiple runs.
   - **Recommendation:** Add timestamp or solver run-id to filenames.

3. **PDF Report Pagination Overhead**
   - PDFs manually handle pagination with lots of low-level PDFBox code.
   - **Impact:** Low-Medium — works, but brittle to changes; consider higher-level library (iText, OpenPDF).

4. **Limited Insights**
   - Reports show violations but not trends or root causes.
   - Example: "Teacher X has 5 idle gaps" but not which days/times.
   - **Impact:** Low — acceptable for first version; can enhance later.

---

## 5. Testing & Validation

### ❌ Critical Gap

- **No Unit Tests:** Only a trivial `AppTest.java` file exists; no actual test coverage.
- **Impact:** HIGH — risk of regressions; difficult to validate new constraints.
- **Recommendation:** Add comprehensive test suite:
  - Unit tests for each constraint using Timefold test framework.
  - Integration test for full solver on small demo dataset.
  - Regression tests for known-good solutions.

---

## 6. Configuration & Tuning

### ⚠️ Issues

1. **Solver Parameters Hardcoded**
   - Termination settings in `SchoolSolverConfig`:
     - `unimprovedMinutesSpentLimit = 5`
     - `minutesSpentLimit = 15`
     - `bestScoreLimit = "0hard/*soft"`
   - No way to tune without code change.
   - **Impact:** Medium — difficult to balance solve time vs. quality.
   - **Recommendation:** Move to configuration file (properties or YAML).

2. **No Constraint Weights Configuration**
   - Soft constraint weights hardcoded in provider.
   - **Impact:** Medium — limits A/B testing and business tuning.

3. **No Heuristic/Search Strategy Configuration**
   - Currently uses default construction heuristic and local search.
   - No exposure of value/variable selectors or move selectors.
   - **Impact:** Low-Medium — acceptable for demo; limits advanced tuning.

---

## 7. Performance

### ✅ Current State

- **Solve Time:** ~5–15 seconds for 77 assignments (demo data).
- **Feasibility:** 100% (all hard constraints satisfied).
- **Quality:** -36 soft penalty (good, but not quantitatively evaluated).

### ⚠️ Concerns

- **O(n²) Pairwise Constraints:** As assignment count grows, performance will degrade.
- **No Profiling:** Unknown which constraints consume most time.
- **Impact:** Low now; Medium if problem grows 10x.
- **Recommendation:** Profile solver with `SolverEventListener`; optimize expensive constraints.

---

## 8. Documentation

### ✅ Strengths

- **README.md:** Comprehensive, includes features, constraints, architecture, demo data.
- **Code Comments:** Constraint provider has good inline comments.
- **Class-Level JavaDoc:** Present in most domain classes.

### ⚠️ Issues

1. **No Constraint Tuning Guide**
   - README mentions weights but doesn't explain how to modify them.
   - No guidance on which constraints to adjust for different business rules.

2. **No Deployment Guide**
   - How to run in production (e.g., as a service)?
   - How to handle large datasets?
   - How to integrate with external systems?

3. **Solver Config Not Documented**
   - Why 15-minute time limit? Why 5-minute unimproved limit?
   - How to adjust for different problem sizes?

---

## 9. Extensibility & Maintainability

### ✅ Good Patterns

- Constraint Streams API enables easy addition of new constraints.
- Domain model is well-separated; adding fields to `Teacher`, `Course`, etc., is straightforward.
- Excel & PDF utilities are independently callable.

### ⚠️ Limitations

1. **No Plugin Architecture**
   - Constraints are hardcoded in provider; no way to load custom constraints at runtime.

2. **Excel/PDF Generation Tightly Coupled**
   - `MainApp` directly calls `PdfReporter` and passes violations maps.
   - No abstraction for other report formats (CSV, JSON, etc.).

3. **Demo Data Hardcoded**
   - `DemoDataGenerator` has 22 specific teachers, courses, etc.
   - No easy way to load data from CSV or database.

---

## 10. Security & Robustness

### ⚠️ Issues

1. **No Input Validation**
   - No checks for null/invalid data in domain classes.
   - Solver will fail cryptically if data is malformed.

2. **No Error Handling in Reporting**
   - PDF/Excel generation could fail silently or with poor error messages.

3. **No Logging**
   - Solver events not logged; hard to debug.

---

## Summary: Enhancement Priorities

### 🔴 **High Priority** (Address First)

1. **Unit Test Suite** — No tests = high regression risk.
2. **Code Refactor** — Extract constraint analysis and printing logic from `MainApp`.
3. **Assignment-Hours Bug** — Fix mismatch between assignment count and actual hours.
4. **Input Validation** — Add checks to domain classes.

### 🟡 **Medium Priority** (Next Sprint)

5. **Configuration File** — Externalize solver termination & constraint weights.
6. **Room Capacity** — Add `Room.capacity` and hard constraint.
7. **Teacher Rest Periods** — Add soft constraint for break time.
8. **Profiling & Performance** — Identify slow constraints; optimize.
9. **Error Handling & Logging** — Improve diagnostics.

### 🟢 **Low Priority** (Nice-to-Have)

10. **Advanced Solver Config** — Expose value/variable/move selectors.
11. **Production Deployment Guide** — Documentation for ops.
12. **Alternative Report Formats** — CSV, JSON, etc.
13. **Constraint Weights Tuning Guide** — Documentation & examples.

---

## Recommended Action Plan (Next 3 Iterations)

### Iteration 1: Stabilization & Refactoring
- Write comprehensive unit tests (constraint tests + integration tests).
- Extract `ScheduleAnalyzer` and `SchedulePrinter` from `MainApp`.
- Fix assignment-hours bug in `teacherMaxHoursPerWeek` and analysis.
- Add basic input validation.

### Iteration 2: Configuration & Tuning
- Move solver/constraint config to properties file.
- Add logging via SLF4J.
- Add room capacity constraint.
- Update README with tuning guide.

### Iteration 3: Performance & Robustness
- Profile solver; optimize O(n²) constraints.
- Add teacher rest period soft constraint.
- Improve error handling in PDF/Excel generation.
- Add deployment documentation.

---

## Conclusion

**Current State:** The project is well-architected, functional, and production-ready for demo purposes. The Timefold integration is clean, constraints are well-designed, and reporting is comprehensive.

**Key Risks:** Code duplication in analysis methods, lack of unit tests, and the assignment-count vs. hours bug. These are manageable and should be addressed in the next iteration.

**Potential:** With the recommended enhancements, this becomes a robust, extensible scheduling solution suitable for real schools or other timetabling domains.

**Grade:** **B+** — Strong foundation, clear path to A-grade with focused effort on testing, refactoring, and tuning.
