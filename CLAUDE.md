# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

This is a school scheduling constraint optimization system built with **Timefold Solver 1.x** and **Java 17**. The solver automatically generates weekly timetables by assigning teachers, courses, timeslots, and rooms while satisfying hard constraints and optimizing soft preferences.

## Essential Commands

### Build and Run
```bash
# Compile the project
mvn clean compile

# Run the solver (main application)
mvn exec:java -Dexec.mainClass="com.example.MainApp"

# Run tests
mvn test

# Debug mode
mvn -X clean compile
```

### Output Files
The solver generates three PDF reports in the project root:
- `schedule-report-violations.pdf` - Constraint violation analysis
- `schedule-report-by-teacher.pdf` - Schedule grouped by teacher
- `schedule-report-by-group.pdf` - Schedule grouped by student group

## Architecture Overview

### Core Domain Model

**Planning Solution** (`SchoolSchedule`):
- The `@PlanningSolution` that holds all problem facts and planning entities
- Contains value range providers for teachers, timeslots, and rooms
- Holds the `HardSoftScore` calculated by the constraint provider

**Planning Entity** (`CourseAssignment`):
- The `@PlanningEntity` with three `@PlanningVariable` fields: `teacher`, `timeslot`, `room`
- Represents one hour of a course for a specific group
- Each assignment has a `sequenceIndex` to track which hour of the multi-hour course it represents

**Problem Facts**:
- `Teacher` - Has stable `id`, qualifications (Set<String>), per-day availability map (`Map<DayOfWeek, Set<Integer>>`), and `maxHoursPerWeek` workload limit
- `Course` - Has `id`, name, `roomRequirement` ("standard" or "lab"), and `requiredHoursPerWeek`
- `Group` - Student group with assigned courses and optional `preferredRoom`
- `Room` - Classroom with `type` ("standard" or "lab") and `building` designation
- `Timeslot` - Specific day (`DayOfWeek`) and hour (int, 7-15)

### Constraint System

**Hard Constraints** (in `SchoolConstraintProvider`):
1. `teacherMustBeQualified` - Teacher must have qualification matching course name
2. `teacherMustBeAvailable` - Teacher must be available at the assigned timeslot (checks per-day availability map)
3. `noTeacherDoubleBooking` - Teacher cannot teach two courses at same time
4. `noRoomDoubleBooking` - Room cannot host two courses at same time
5. `roomTypeMustSatisfyRequirement` - Lab courses need lab rooms; standard courses need standard rooms
6. `groupCannotHaveTwoCoursesAtSameTime` - Student group cannot have schedule conflicts
7. `sameTeacherForAllCourseHours` - All hours of the same course for a group must have the same teacher
8. `teacherMaxHoursPerWeek` - Teacher cannot exceed their `maxHoursPerWeek` limit (sums `course.requiredHoursPerWeek` per assignment)

**Soft Constraints** (quality optimization):
1. `groupPreferredRoomConstraint` (weight 3) - Groups prefer their pre-assigned room (excludes lab rooms)
2. `minimizeTeacherIdleGaps` (weight 1) - Reduce gaps between courses for same teacher on same day
3. `minimizeTeacherBuildingChanges` (weight 1) - Reduce building switches for teachers on same day
4. `preferTeachersWithLessCapacity` (weight 1) - Favor assigning to teachers with lower `maxHoursPerWeek` (utilization-based penalty)

### Solver Configuration

Located in `SchoolSolverConfig`:
- **Termination**: Best score limit of `0hard/*soft` OR 15 minutes OR 5 minutes without improvement
- Uses **Construction Heuristic** + **Local Search** (Tabu Search, Simulated Annealing)
- Constraint Streams API for declarative constraint modeling

### Data Generation

`DemoDataGenerator.generateDemoData()`:
- Creates 22 teachers with varying `maxHoursPerWeek` (sorted ascending for value-ordering bias)
- 11 courses (standard, lab, extracurricular)
- 7 student groups
- 11 rooms (6 standard, 2 labs in various buildings)
- 40 timeslots (Mon-Fri, 7:00-14:00)
- Generates `CourseAssignment` objects for each course hour per group

### Analysis and Reporting

**ScheduleAnalyzer** (`com.example.analysis.ScheduleAnalyzer`):
- Analyzes hard and soft constraint violations by manually checking the solution against each rule
- Returns violation counts and detailed offender descriptions

**PdfReporter** (`com.example.util.PdfReporter`):
- Generates paginated PDF reports using Apache PDFBox
- Three reports: violations, by-teacher schedule, by-group schedule

**ExcelTemplateGenerator** (`com.example.util.ExcelTemplateGenerator`):
- Uses Apache POI to pre-fill Excel workbook with demo data
- Includes teacher `id`, serialized per-day availability, and `maxHoursPerWeek`

## Important Implementation Notes

### Teacher Availability
- Teachers use a per-day availability map: `Map<DayOfWeek, Set<Integer>> availabilityPerDay`
- The `isAvailableAt(Timeslot)` method checks if the hour is in the teacher's set for that day
- Multiple backwards-compatible constructors exist for common initialization patterns

### Course Hours
- Multi-hour courses (e.g., 3 hours/week) generate multiple `CourseAssignment` objects
- Each assignment has a `sequenceIndex` (0, 1, 2, etc.)
- The hard constraint `sameTeacherForAllCourseHours` ensures consistency
- When counting teacher workload, constraints sum `course.requiredHoursPerWeek` per assignment

### Room Assignment
- Lab courses (`roomRequirement = "lab"`) must use lab rooms
- Non-lab courses for the same group should ideally use the same room (see commented-out `groupNonLabCoursesInSameRoom` constraint)
- The `groupPreferredRoomConstraint` is soft (weight 3) and excludes lab rooms to reduce infeasibility

### Timefold Requirements
- All domain classes need no-arg constructors (required by Timefold reflection)
- Planning entities need `@PlanningId` for unique identification
- Value range providers are defined in `SchoolSchedule` with `@ValueRangeProvider` annotations

## Modifying the System

**To change constraints**:
Edit `src/main/java/com/example/solver/SchoolConstraintProvider.java`

**To modify demo data**:
Edit `src/main/java/com/example/data/DemoDataGenerator.java`

**To adjust solver termination**:
Edit `src/main/java/com/example/solver/SchoolSolverConfig.java`

**To change domain model**:
- Ensure no-arg constructors remain for Timefold compatibility
- Update constraint provider if new fields affect constraints
- Consider backwards-compatible constructors for existing call sites
