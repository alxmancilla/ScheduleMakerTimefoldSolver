# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

This is a school **timeslot optimizer** built with **Timefold Solver 1.x** and **Java 17**. Each `CourseBlockAssignment` arrives with its teacher and course already assigned (pre-assigned from the database); the solver's planning variables are the block's `timeslot` (always) and its `room` (only when nobody has already decided it). It places pre-assigned teacher/course blocks into weekly timeslots while satisfying hard constraints and optimizing soft preferences.

**Scope note**: Teacher is **not** a planning variable — it's a fixed input. The `teacher` `@PlanningVariable` annotation in `CourseBlockAssignment` is intentionally commented out. Treat this system as a timeslot (and, for a subset of blocks, room) optimizer over pre-assigned teacher blocks, not a full teacher scheduler. `room` **is** a genuine `@PlanningVariable`, but its entity-scoped value range (`CourseBlockAssignment.getMatchingRooms()`) collapses to a singleton whenever the block's group has a `preferredRoom` or its teacher has a `requiredRoomName` — for those "room-fixed" blocks the solver has nothing else to pick from, so in effect it's still fixed input; only genuinely roomless blocks are freely solved. See "Dynamic Room Assignment" under Room Assignment below.

**Schedule run history**: `course_block_assignment` is pure input — the solver never writes to it. `DataSaver` instead inserts one `schedule_run` row (score + timestamp) and one `schedule_run_result` row per assignment (its solved, or still-unassigned, `block_timeslot_id`) per solve, then prunes `schedule_run` down to the most recent 10 rows (`ON DELETE CASCADE` cleans up the rest). Anything that needs "the current schedule" — `DataLoader`, the web `ScheduleController`, PDF reports — reads through the `course_block_assignment_current` view instead of the raw table: pinned rows resolve to their own input `block_timeslot_id`, every other row resolves to the most recent `schedule_run`'s result. This also gives the solver warm-starting for free (it sees the latest run's placements as its starting point) without any separate seeding logic. `course_block_assignment.block_timeslot_id` itself is therefore only ever meaningful for `pinned = true` rows.

**Current Status**: This project uses **block-based scheduling only**. Hour-based scheduling has been deprecated and removed.

### Block-Based Scheduling
The system assigns multi-hour blocks (1-4 hours) to timeslots, allowing for more realistic scheduling patterns. Blocks represent consecutive teaching periods and are the only supported scheduling mode.

## Essential Commands

The project is a Maven multi-module build: an aggregator/parent `pom.xml` with four modules — `common` (`scheduler-common`, plain-Java shared business rules with no framework/persistence dependencies — currently just `RoomTypeCompatibility`), `engine` (`scheduler-engine`, Timefold + JDBC, no Spring), `reporter` (`scheduler-reporter`, reads the solved schedule from the database and generates PDF reports; depends on `scheduler-engine` as a library), and `web` (`scheduler-web`, Spring Boot REST API). `engine` and `web` both depend on `common` for shared rules, but do not depend on each other; all three modules that touch the database integrate only through the shared PostgreSQL schema. `common` exists specifically so a rule shared by the solver and the web API has exactly one implementation instead of two hand-synchronized copies. The React frontend lives in `web-ui/` (unchanged).

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
- `calendario-incumplimientos.pdf` - Constraint violation analysis
- `calendario-por-maestro.pdf` - Schedule grouped by teacher
- `calendario-por-grupo.pdf` - Schedule grouped by student group

## Architecture Overview

### Core Domain Model

**Planning Solution** (`SchoolSchedule`):
- The `@PlanningSolution` that holds all problem facts and planning entities
- Contains value range providers for teachers, timeslots, and rooms
- Holds the `HardSoftScore` calculated by the constraint provider

**Planning Entity** (`CourseBlockAssignment`):
- The `@PlanningEntity` with two `@PlanningVariable` fields: `timeslot` (always solved) and `room` (`allowsUnassigned = true` — solved only for "room-fixed"-false blocks, see `isRoomFixed()`/`getMatchingRooms()`; teacher is pre-assigned from database, not a variable)
- Represents a block of consecutive hours for a course
- Has `blockLength` field indicating the number of consecutive hours
- Uses `BlockTimeslot` (start hour + length) instead of single-hour `Timeslot`
- **CRITICAL**: Has `satisfiesRoomType` and `preferredRoomHint` fields for dual room requirement support

**Problem Facts**:
- `Teacher` - Has stable `id`, qualifications (Set<String>), per-day availability map (`Map<DayOfWeek, Set<Integer>>`), `maxHoursPerWeek` workload limit, and optional `requiredRoomName` (a room this teacher must always use, overriding the group's preferred room — see Room Assignment below)
- `Course` - Has `id`, name, `roomRequirement` (legacy single requirement), `roomRequirements` (List for dual requirements), `blockTemplates` (List for custom block decomposition), and `requiredHoursPerWeek`
- `Group` - Student group with assigned courses and optional `preferredRoom`
- `Room` - Classroom with `type` (Standard, Mixed, Specialized - Workshop, Specialized - Computer Lab) and `building` designation. `satisfiesRequirement(req)` delegates to `common`'s `RoomTypeCompatibility.satisfies()` (the single shared implementation of this rule, also used directly by `web`): a `Mixed` room also satisfies `Standard` and `Specialized - Workshop` requirements (it's equipped for both a regular class and a workshop), while every other type satisfies only itself — notably `Specialized - Computer Lab` stays strictly separate from `Specialized - Workshop` despite the shared "Specialized" label prefix (that prefix is a UI grouping convenience only, not a compatibility relationship). A plain `Standard` or `Specialized - Workshop` room does NOT satisfy a `Mixed` requirement. (`taller electromecánica`/`taller electrónica` were retired in favor of retyping their rooms into this type, first named `laboratorio` then `dual`, then `mixto`, before settling on `Mixed` — same rooms/behavior throughout, just renamed several times to avoid confusing terminology.) The 4 valid room-type values live in the `room_type` lookup table (DB-level, not a Java enum — every Java/JS field storing a room type is still a plain `String`); `room.type`, `course.room_requirement`, `course_room_requirement.room_type`, `course_block_template.room_type`, and `course_block_assignment.satisfies_room_type` all FK into it with `ON UPDATE CASCADE`, so renaming a room type is a single `UPDATE room_type SET name = ...` rather than editing a `CHECK` constraint by hand. Likewise, course designations (Core, Elective, TEM, TCOM, ...) live in the `course_designation` lookup table, FK'd from `course.designation` and `component_block_rule.component` — kept as a separate bare list rather than reusing `component_block_rule` as the FK target, since a designation with no `component_block_rule` row deliberately falls back to code defaults (see below) and forcing every designation to have a row there would break that. (`component_block_rule`/`.component` themselves keep that name — a distinct, not-yet-renamed feature that happens to be keyed by the same values.)
- `BlockTimeslot` - Specific day (`DayOfWeek`), start hour (int, 7-14), and length in hours (int, 1-4)
- `RoomRequirement` - Dual room requirements with `courseId`, `roomType`, `hoursRequired`, `priority`, `defaultPreferredRoom`
- `BlockTemplate` - Custom block decomposition with `courseId`, `groupId`, `blockIndex`, `blockLength`, `roomType`, `preferredRoomName`, `preferredDay`, `pinAssignment`, `preferredTimeslotId`

### Constraint System (Block-Based Scheduling)

**Hard Constraints** (12 total in `SchoolConstraintProvider`):
1. `blockLengthMustMatchTimeslotLength` - Block length must match timeslot length
2. `teacherMustBeQualified` - Teacher must have qualification matching course name
3. `teacherMustBeAvailable` - Teacher must be available for entire block duration (checks per-day availability map)
4. `noTeacherDoubleBooking` - Teacher cannot teach two blocks that overlap in time
5. `noRoomDoubleBooking` - Room cannot host two blocks that overlap in time
6. `roomTypeMustSatisfyRequirement` - **CRITICAL**: Uses `assignment.getSatisfiesRoomType()` (NOT `course.getRoomRequirement()`) to support dual room requirements
7. `teacherRequiredRoomMustBeUsed` - A block's room must match its teacher's `requiredRoomName` when one is set. NOT excluded for pinned assignments (a data-integrity check, like `blockLengthMustMatchTimeslotLength`): a non-pinned block's room is already structurally guaranteed correct by `CourseBlockAssignment.getMatchingRooms()`'s entity-scoped value range, so in practice this only ever fires for a pinned row whose room drifted out of sync with its teacher's current requirement (`TeacherController.backfillRequiredRoom()` explicitly skips pinned blocks when the requirement changes). Mirrored in `BlockScheduleAnalyzer`.
8. `groupCannotHaveTwoCoursesAtSameTime` - Student group cannot have overlapping blocks
9. `maxTwoBlocksPerCoursePerGroupPerDay` - Maximum blocks per course per group per day, capped at `course.getMaxBlocksPerDay()` (per-component, via the `component_block_rule` table / Settings > Block Rules), falling back to a code default of 2 for a component with no configured rule. Mirrored in `BlockScheduleAnalyzer`.
10. `courseBlocksMustBeConsecutive` - All course blocks on same day must be consecutive
11. `teacherMustHaveBreakAfterConsecutiveHours` - A teacher scheduled `MAX_CONSECUTIVE_HOURS_WITHOUT_BREAK` (4h) straight, back-to-back with zero idle time, must get a break before continuing. Pinned blocks excluded from the run (legacy pinned data can't block solver convergence). Mirrored in `BlockScheduleAnalyzer`.
12. `groupMustHaveBreakAfterConsecutiveHours` - Same rule as above, for student groups instead of teachers.

**Soft Constraints** (8 total, quality optimization):
1. `nonStandardRoomsShouldFinishBy2pm` (weight 10) - Non-standard rooms (CC, TEM, TE, AULA 4, LQ, LMICRO) should finish by 14:00. SOFT in the constraint provider **and** reported as SOFT by `BlockScheduleAnalyzer` (both exclude pinned assignments).
2. `teacherMaxHoursPerWeek` (weight 5) - Minimize teacher workload violations
3. `roomCapacityShouldFitGroupSize` (weight 4) - Group's `studentCount` shouldn't exceed the assigned room's `capacity`; only checked when both values are known.
4. `minimizeGroupIdleGaps` (weight 3) - Minimize idle time between blocks for student groups. Penalizes only ADJACENT block pairs (via `forEachUniquePair` + `ifNotExists`) so idle hours are counted once per gap, not re-counted by non-adjacent pairs. `BlockScheduleAnalyzer` mirrors this by grouping per (group, day), sorting, and summing adjacent-block gaps.
5. `preferBlockSpecifiedRoom` (weight 3) - Prefer room specified in `assignment.getPreferredRoomHint()` field
6. `groupPreferredRoomConstraint` (weight 2) - Groups prefer their pre-assigned room (excludes `Mixed`-required blocks using `getSatisfiesRoomType()`)
7. `minimizeTeacherIdleGaps` (weight 2) - Reduce gaps between blocks for same teacher on same day. Availability-aware (only counts idle hours the teacher is actually available) and penalizes only ADJACENT block pairs (via `forEachUniquePair` + `ifNotExists`) to avoid re-counting the same idle hours across non-adjacent pairs.
8. `minimizeTeacherBuildingChanges` (weight 1) - Reduce building switches for teachers on same day

### Solver Configuration

Loaded by `SchoolSolverConfig` from `solverConfig.xml`:
- **Termination** (local-search phase): best score limit `0hard/0soft` OR 5 minutes total (`minutesSpentLimit`) OR 2 minutes without improvement (`unimprovedMinutesSpentLimit`)
- **Acceptor**: Late Acceptance (`lateAcceptanceSize` 10000) + Tabu Search (`entityTabuSize` 7)
- Phases: `FIRST_FIT_DECREASING` construction heuristic, then local search. `CourseBlockAssignment.timeslot`'s value range is entity-scoped (`CourseBlockAssignment.getMatchingBlockTimeslots()`, filtered to timeslots whose length matches the block's own `blockLength`) rather than the full timeslot list, so a length mismatch is structurally unreachable for movable assignments — no separate pre-fill phase or defragmentation phase is needed to police it. `room`'s value range (`getMatchingRooms()`) mirrors this: a singleton for "room-fixed" blocks, the full type-filtered room list otherwise — see Room Assignment below. `MatchingLengthMoveFilter`/`MatchingLengthSwapFilter` filter `ChangeMove`/`SwapMove` for teacher availability (not covered by the timeslot value range), for swap only block length (swap doesn't consult either entity's own value range), and — for `room` specifically — reject any `ChangeMove`/`SwapMove` that would touch a room-fixed block's room at all, including unassigning it (`room` is `allowsUnassigned = true`, which makes "unassign" a legal `ChangeMove` target regardless of the entity's own value range; confirmed empirically that without this filter, local search silently unassigns correctly-fixed rooms).
- Constraint Streams API for declarative constraint modeling. **Every constraint in `SchoolConstraintProvider` uses `forEachIncludingUnassigned`/`ifNotExistsIncludingUnassigned`, never the plain `forEach`/`forEachUniquePair`/`ifNotExists`, for `CourseBlockAssignment`.** Timefold's plain `forEach`-family methods silently exclude any entity with a null value for *any* genuine planning variable — since `room` is nullable, that used to make a roomless block invisible to every constraint, not just room-related ones (confirmed empirically: `teacherMustBeQualified` and `groupCannotHaveTwoCoursesAtSameTime` both silently under-counted real violations before this fix). `forEachUniquePair`'s "unique pair, no self-pairs" behavior is replicated manually where needed: `forEachIncludingUnassigned(...).join(forEachIncludingUnassigned(...), Joiners.lessThan(getId), ...the constraint's own joiners...)`.

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
- Blocks requiring the `Mixed` room type (`satisfiesRoomType = "Mixed"`) must use an actual `Mixed` room; `Standard`/`Specialized - Workshop` blocks may additionally use a `Mixed` room (it doubles as either), but never the reverse. `Specialized - Computer Lab` stays strictly separate - never satisfied by `Mixed`.
- The `groupPreferredRoomConstraint` is soft (weight 2) and excludes `Mixed`-required blocks to reduce infeasibility
- Dual room requirements allow courses to specify multiple room types (e.g., 4h in Specialized - Computer Lab + 1h in Standard)
- Each block has its own room type requirement via `satisfiesRoomType` field

**Dynamic room assignment for roomless blocks**: `room` is a `@PlanningVariable` (`allowsUnassigned = true`). `CourseBlockAssignment.isRoomFixed()` is true when the block's group has a `preferredRoom` or its teacher has a `requiredRoomName` (teacher wins if both are set, matching `BlockGenerationService.defaultRoomFor()`'s own priority) — for those blocks `getMatchingRooms()` collapses the value range to a singleton, so the solver structurally cannot reassign them. Genuinely roomless blocks (`isRoomFixed() == false`) get the full type-filtered room list and are solved freely. `DataLoader` force-corrects a non-pinned fixed block's room to the current group/teacher preference at load time (construction heuristic never touches an already-non-null variable, so a stale `room_name` would otherwise survive every future solve unnoticed) — pinned rows are deliberately left alone; `teacherRequiredRoomMustBeUsed` reports a stale pinned room instead. `BlockLengthDifficultyComparator` schedules room-fixed blocks before room-movable ones (after teacher availability, before block length) so a fixed block gets first pick of open timeslots when it shares a physical room with a movable one. `check_block_assignment_pinned_requires_room` (DB constraint, mirrors the existing timeslot one) makes it impossible to pin a block without a room in the first place.

### Timefold Requirements
- All domain classes need no-arg constructors (required by Timefold reflection)
- Planning entities need `@PlanningId` for unique identification
- Value range providers are defined in `SchoolSchedule` with `@ValueRangeProvider` annotations

## Dual Room Requirements System (CRITICAL)

### Overview
The system supports **dual room requirements** where a single course can require different room types for different blocks. This is implemented through database tables and domain model fields.

### Database Tables
- `course_room_requirement` - Allows courses to specify multiple room types with different hour allocations (e.g., 4h in Specialized - Computer Lab + 1h in Standard)
- `course_block_template` - Allows explicit specification of how a course should be decomposed into blocks

### CourseBlockAssignment Fields
- `satisfiesRoomType` - Which room requirement this block satisfies (HARD constraint)
- `preferredRoomHint` - Preferred room for soft constraint optimization (SOFT constraint)

### Key Pattern
**CRITICAL**: Each block has its own room type requirement, not inherited from course.

**ALWAYS use**:
- `assignment.getSatisfiesRoomType()` - For checking room type requirements
- `assignment.getPreferredRoomHint()` - For preferred room optimization

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
