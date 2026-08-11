# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

This is a school **timeslot optimizer** built with **Timefold Solver 1.x** and **Java 17**. Each `CourseBlockAssignment` arrives with its teacher, room, and course already assigned (pre-assigned from the database); the solver's single planning variable is the block's `timeslot`. It places pre-assigned teacher/room/course blocks into weekly timeslots while satisfying hard constraints and optimizing soft preferences.

**Scope note**: Teacher and room are **not** planning variables — they are fixed inputs. The `teacher` and `room` `@PlanningVariable` annotations in `CourseBlockAssignment` are intentionally commented out, and `DataSaver` persists only `block_timeslot_id`. Treat this system as a timeslot optimizer over pre-assigned teacher/room blocks, not a full teacher/room scheduler.

**Current Status**: This project uses **block-based scheduling only**. Hour-based scheduling has been deprecated and removed.

### Block-Based Scheduling
The system assigns multi-hour blocks (1-4 hours) to timeslots, allowing for more realistic scheduling patterns. Blocks represent consecutive teaching periods and are the only supported scheduling mode.

## Essential Commands

The project is a Maven multi-module build: an aggregator/parent `pom.xml` with three modules — `engine` (`scheduler-engine`, Timefold + JDBC, no Spring), `reporter` (`scheduler-reporter`, reads the solved schedule from the database and generates PDF reports; depends on `scheduler-engine` as a library), and `web` (`scheduler-web`, Spring Boot REST API). The `engine` and `web` modules do not depend on each other; all three integrate through the shared PostgreSQL database. The React frontend lives in `web-ui/` (unchanged).

### Build and Run
```bash
# Compile all modules (run from the repository root)
mvn clean compile

# Run the block-based solver (engine module)
mvn -pl engine exec:java -Dexec.mainClass="com.example.MainBlockSchedulingApp"

# Generate PDF reports from the solved schedule (reporter module)
mvn -pl reporter exec:java -Dexec.mainClass="com.example.reporter.PdfReportApp"

# Run the Spring Boot web application (web module)
mvn -pl web spring-boot:run

# Run all tests (all modules, from the root)
mvn test

# Debug mode
mvn -X clean compile
```

### Output Files

The **reporter** module generates three PDF reports from the persisted schedule:
- `calendario-bloques-incumplimientos.pdf` - Constraint violation analysis
- `calendario-bloques-por-maestro.pdf` - Schedule grouped by teacher
- `calendario-bloques-por-grupo.pdf` - Schedule grouped by student group

## Architecture Overview

### Core Domain Model

**Planning Solution** (`SchoolSchedule`):
- The `@PlanningSolution` that holds all problem facts and planning entities
- Contains value range providers for teachers, timeslots, and rooms
- Holds the `HardSoftScore` calculated by the constraint provider

**Planning Entity** (`CourseBlockAssignment`):
- The `@PlanningEntity` with one `@PlanningVariable` field: `timeslot` (teacher and room pre-assigned from database)
- Represents a block of consecutive hours for a course
- Has `blockLength` field indicating the number of consecutive hours
- Uses `BlockTimeslot` (start hour + length) instead of single-hour `Timeslot`
- **CRITICAL**: Has `satisfiesRoomType` and `preferredRoomName` fields for dual room requirement support

**Problem Facts**:
- `Teacher` - Has stable `id`, qualifications (Set<String>), per-day availability map (`Map<DayOfWeek, Set<Integer>>`), and `maxHoursPerWeek` workload limit
- `Course` - Has `id`, name, `roomRequirement` (legacy single requirement), `roomRequirements` (List for dual requirements), `blockTemplates` (List for custom block decomposition), and `requiredHoursPerWeek`
- `Group` - Student group with assigned courses and optional `preferredRoom`
- `Room` - Classroom with `type` (estándar, laboratorio, taller, taller electromecánica, taller electrónica, centro de cómputo) and `building` designation. `satisfiesRequirement(req)` uses a convention-seeded capability set: a `laboratorio` room also satisfies an `estándar` requirement (a lab can double as a regular classroom), while every other type satisfies only itself. A plain `estándar` room does NOT satisfy a `laboratorio` requirement.
- `BlockTimeslot` - Specific day (`DayOfWeek`), start hour (int, 7-14), and length in hours (int, 1-4)
- `RoomRequirement` - Dual room requirements with `courseId`, `roomType`, `hoursRequired`, `priority`, `defaultPreferredRoom`
- `BlockTemplate` - Custom block decomposition with `courseId`, `groupId`, `blockIndex`, `blockLength`, `roomType`, `preferredRoomName`, `preferredDay`, `pinAssignment`, `preferredTimeslotId`

### Constraint System (Block-Based Scheduling)

**Hard Constraints** (9 total in `SchoolConstraintProvider`):
1. `blockLengthMustMatchTimeslotLength` - Block length must match timeslot length
2. `teacherMustBeQualified` - Teacher must have qualification matching course name
3. `teacherMustBeAvailable` - Teacher must be available for entire block duration (checks per-day availability map)
4. `noTeacherDoubleBooking` - Teacher cannot teach two blocks that overlap in time
5. `noRoomDoubleBooking` - Room cannot host two blocks that overlap in time
6. `roomTypeMustSatisfyRequirement` - **CRITICAL**: Uses `assignment.getSatisfiesRoomType()` (NOT `course.getRoomRequirement()`) to support dual room requirements
7. `groupCannotHaveTwoCoursesAtSameTime` - Student group cannot have overlapping blocks
8. `maxTwoBlocksPerCoursePerGroupPerDay` - Maximum 2 blocks per course per group per day
9. `courseBlocksMustBeConsecutive` - All course blocks on same day must be consecutive

**Soft Constraints** (7 total, quality optimization):
1. `nonStandardRoomsShouldFinishBy2pm` (weight 10) - Non-standard rooms (CC, TEM, TE, AULA 4, LQ, LMICRO) should finish by 14:00. SOFT in the constraint provider **and** reported as SOFT by `BlockScheduleAnalyzer` (both exclude pinned assignments).
2. `teacherMaxHoursPerWeek` (weight 5) - Minimize teacher workload violations
3. `minimizeGroupIdleGaps` (weight 3) - Minimize idle time between blocks for student groups. Penalizes only ADJACENT block pairs (via `forEachUniquePair` + `ifNotExists`) so idle hours are counted once per gap, not re-counted by non-adjacent pairs. `BlockScheduleAnalyzer` mirrors this by grouping per (group, day), sorting, and summing adjacent-block gaps.
4. `preferBlockSpecifiedRoom` (weight 3) - Prefer room specified in `assignment.getPreferredRoomName()` field
5. `groupPreferredRoomConstraint` (weight 2) - Groups prefer their pre-assigned room (excludes lab blocks using `getSatisfiesRoomType()`)
6. `minimizeTeacherIdleGaps` (weight 2) - Reduce gaps between blocks for same teacher on same day. Availability-aware (only counts idle hours the teacher is actually available) and penalizes only ADJACENT block pairs (via `forEachUniquePair` + `ifNotExists`) to avoid re-counting the same idle hours across non-adjacent pairs.
7. `minimizeTeacherBuildingChanges` (weight 1) - Reduce building switches for teachers on same day

### Solver Configuration

Loaded by `SchoolSolverConfig` from `solverConfig.xml`:
- **Termination** (local-search phase): best score limit `0hard/0soft` OR 5 minutes total (`minutesSpentLimit`) OR 2 minutes without improvement (`unimprovedMinutesSpentLimit`)
- **Acceptor**: Late Acceptance (`lateAcceptanceSize` 10000) + Tabu Search (`entityTabuSize` 7)
- Custom phases before local search: `MatchingLengthTimeslotAssigner`, `FIRST_FIT_DECREASING` construction heuristic, then `BlockDefragmenter`
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
- Analyzes hard and soft constraint violations for hour-based scheduling
- Returns violation counts and detailed offender descriptions

**BlockScheduleAnalyzer** (`com.example.analysis.BlockScheduleAnalyzer`) - **NEW**:
- Analyzes hard and soft constraint violations for block-based scheduling
- Handles block overlap detection and availability checking for multi-hour blocks
- Returns violation counts and detailed offender descriptions

**PdfReporter** (`com.example.util.PdfReporter`, in the `reporter` module):
- Generates paginated PDF reports using Apache PDFBox
- Three reports for block-based: violations, by-teacher schedule, by-group schedule
- Invoked by `com.example.reporter.PdfReportApp`, which loads the solved schedule via `DataLoader` and recomputes violations via `BlockScheduleAnalyzer`
- **NOTE**: PDF generation (and the PDFBox dependency) lives in the `reporter` module, not the engine

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
- **CRITICAL**: Always use `assignment.getSatisfiesRoomType()` instead of `course.getRoomRequirement()` for dual room requirement support
- Lab blocks (`satisfiesRoomType = "laboratorio"`) must use lab rooms; `estándar` blocks may use either an `estándar` room or a `laboratorio` room (labs double as regular classrooms), but never the reverse
- The `groupPreferredRoomConstraint` is soft (weight 2) and excludes lab blocks to reduce infeasibility
- Dual room requirements allow courses to specify multiple room types (e.g., 4h in CC + 1h in estándar)
- Each block has its own room type requirement via `satisfiesRoomType` field

### Timefold Requirements
- All domain classes need no-arg constructors (required by Timefold reflection)
- Planning entities need `@PlanningId` for unique identification
- Value range providers are defined in `SchoolSchedule` with `@ValueRangeProvider` annotations

## Dual Room Requirements System (CRITICAL)

### Overview
The system supports **dual room requirements** where a single course can require different room types for different blocks. This is implemented through database tables and domain model fields.

### Database Tables
- `course_room_requirement` - Allows courses to specify multiple room types with different hour allocations (e.g., 4h in CC + 1h in estándar)
- `course_block_template` - Allows explicit specification of how a course should be decomposed into blocks

### CourseBlockAssignment Fields
- `satisfiesRoomType` - Which room requirement this block satisfies (HARD constraint)
- `preferredRoomName` - Preferred room for soft constraint optimization (SOFT constraint)

### Key Pattern
**CRITICAL**: Each block has its own room type requirement, not inherited from course.

**ALWAYS use**:
- `assignment.getSatisfiesRoomType()` - For checking room type requirements
- `assignment.getPreferredRoomName()` - For preferred room optimization

**NEVER use**:
- `assignment.getCourse().getRoomRequirement()` - This is the OLD single-requirement system

### Recent Critical Fixes (2026-02-14)
**5 bugs were discovered and fixed** where constraints and analyzer were using the old single room requirement system:

**Constraints Fixed**:
1. `roomTypeMustSatisfyRequirement` - Now uses `getSatisfiesRoomType()`
2. `groupPreferredRoomConstraint` - Now uses `getSatisfiesRoomType()` for lab check

**Analyzer Fixed**:
3. `roomTypeMismatch` count - Now uses `getSatisfiesRoomType()`
4. `roomTypeMismatch` detailed - Now uses `getSatisfiesRoomType()` and shows both fields
5. `preferredRoomViolations` - Now uses `getSatisfiesRoomType()` for lab check

See `DUAL_ROOM_REQUIREMENT_FIX_SUMMARY.md` and `ANALYZER_DUAL_ROOM_REQUIREMENT_FIX.md` for details.

## Modifying the System

**To change constraints**:
Edit `engine/src/main/java/com/example/solver/SchoolConstraintProvider.java`

**To modify demo data**:
Edit `engine/src/main/java/com/example/data/DemoDataGenerator.java`

**To adjust solver termination**:
Edit `engine/src/main/java/com/example/solver/SchoolSolverConfig.java`

**To change domain model**:
- Ensure no-arg constructors remain for Timefold compatibility
- Update constraint provider if new fields affect constraints
- Consider backwards-compatible constructors for existing call sites
