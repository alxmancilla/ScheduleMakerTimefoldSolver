# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

This is a school **timeslot optimizer** built with **Timefold Solver 1.x** and **Java 17**. Each `CourseBlockAssignment` arrives with its teacher and course already assigned (pre-assigned from the database); the solver's planning variables are the block's `timeslot` (always) and its `room` (only when nobody has already decided it). It places pre-assigned teacher/course blocks into weekly timeslots while satisfying hard constraints and optimizing soft preferences.

**Scope note**: Teacher is **not** a planning variable — it's a fixed input. The `teacher` `@PlanningVariable` annotation in `CourseBlockAssignment` is intentionally commented out. Treat this system as a timeslot (and, for a subset of blocks, room) optimizer over pre-assigned teacher blocks, not a full teacher scheduler. `room` **is** a genuine `@PlanningVariable`, but its entity-scoped value range (`CourseBlockAssignment.getMatchingRooms()`) collapses to a singleton whenever the block's group has a single-room curated range for its `satisfiesRoomType` (see `Group.getAcceptableRooms()`) or its teacher has a compatible `requiredRoomName` — for those "room-fixed" blocks the solver has nothing else to pick from, so in effect it's still fixed input. A group's range with 2+ rooms for that type narrows the value range without fixing it; a range with none, or a teacher requirement that doesn't satisfy the block's type, falls through toward the full type-filtered list. See "Dynamic Room Assignment" under Room Assignment below.

**Schedule run history**: `course_block_assignment` is pure input — the solver never writes to it. `DataSaver` instead inserts one `schedule_run` row (score + timestamp), one `schedule_run_result` row per assignment (its solved, or still-unassigned, `block_timeslot_id`), and one `schedule_run_constraint` row per constraint that was active for that solve (name + `is_hard`, sourced directly from `BlockScheduleAnalyzer.analyzeHardConstraintViolations(schedule).keySet()`/`analyzeSoftConstraintViolations(schedule).keySet()` — the same maps `MainBlockSchedulingApp` already computes for console/PDF reporting, not a second hand-maintained list that could drift out of sync with `SchoolConstraintProvider`) per solve, then prunes `schedule_run` down to the most recent 10 rows (`ON DELETE CASCADE` cleans up the rest, including `schedule_run_constraint`). `schedule_run_constraint` exists so score-history analysis can tell a genuine constraint-set change (a constraint enabled/disabled between runs) apart from ordinary solver-run variance (this project's dataset routinely doesn't converge to 0-hard within the 5-minute budget, so hard/soft scores already vary run to run even with an unchanged constraint set) — added 2026-08-24, so runs before that date have no rows here. Anything that needs "the current schedule" — `DataLoader`, the web `ScheduleController`, PDF reports — reads through the `course_block_assignment_current` view instead of the raw table: pinned rows resolve to their own input `block_timeslot_id`, every other row resolves to the most recent `schedule_run`'s result. This also gives the solver warm-starting for free (it sees the latest run's placements as its starting point) without any separate seeding logic. `course_block_assignment.block_timeslot_id` itself is therefore only ever meaningful for `pinned = true` rows.

`schedule_run` also carries per-run metadata beyond score/time-budget, added 2026-09-02 (`add_schedule_run_metadata.sql`; runs before that date have nulls here): `random_seed` (the Timefold seed actually used, null unless `SOLVER_RANDOM_SEED` was supplied — see "Exploring past a locally-optimal schedule" below), `environment_mode` (the Timefold `EnvironmentMode` in effect, currently always `solverConfig.xml`'s own unconfigured default), `skip_validation` (whether `SKIP_PRESOLVE_VALIDATION` let this run proceed despite `PreSolveValidator` finding blocking problems), `finished_at` (actual completion time, for comparing real duration against the configured budget), `engine_git_commit` (the engine's git commit at run time, from `scripts/run-engine.sh` via `git rev-parse HEAD` — same purpose as `schedule_run_constraint`, but for telling a genuine solver-logic change apart from ordinary variance), and `termination_reason` (a best-effort inferred label — `BEST_SCORE_LIMIT`/`TIME_SPENT_LIMIT`/`UNIMPROVED_TIME_SPENT_LIMIT` — computed by `MainBlockSchedulingApp` from the final score and actual elapsed/idle time against the configured limits, since Timefold's OR-combined termination doesn't report which condition fired via its public API). All six are bundled into a single `ScheduleRunMetadata` record passed to `DataSaver.saveSchedule(...)` rather than threaded as five more positional parameters.

**Exploring past a locally-optimal schedule ("Option B")**: `solverConfig.xml` defines no `<randomSeed>`, so Timefold's own fixed default seed applies — a re-solve of unchanged (warm-started) data reliably lands back on the same arrangement rather than exploring elsewhere. `SchoolSolverConfig.build(minutesSpentLimit, unimprovedMinutesSpentLimit, randomSeed)` (a third overload alongside the existing 2-arg one) overrides `solverConfig.xml`'s seed only when `randomSeed` is non-null, and its `Built` record now also reports the *effective* `randomSeed`/`environmentMode` actually resolved (override or XML default) so the caller can log what really ran. `MainBlockSchedulingApp` reads this from the `SOLVER_RANDOM_SEED` env var (`scripts/run-engine.sh` documents it alongside the existing `SOLVER_MINUTES_LIMIT`-family vars): unset leaves the fixed default seed alone; a specific numeric value replays that exact seed (e.g. one copied from a past run's console log or `schedule_run.random_seed`); `"random"` (case-insensitive) generates a fresh seed via `SecureRandom` and prints it back out so that exact search can be replayed later. The web "Run Solver" trigger exposes the same choice as an optional `randomSeed` string field on `EngineRunRequest` (validated to be `"random"` or a decimal value), threaded through `EngineRunnerService.tryStart(...)` to the subprocess env var exactly like the existing `skipValidation` parameter; Settings' Solver tab surfaces it as an "Explore with a new random seed" checkbox plus a mutually-exclusive "replay a specific seed" text field.

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
- `Teacher` - Has stable `id`, qualifications (Set<String>), per-day availability map (`Map<DayOfWeek, Set<Integer>>`), `maxHoursPerWeek` workload limit, and optional `requiredRoomName` (a room this teacher must always use, overriding the group's curated room range — see Room Assignment below). `maxHoursPerWeek` is a solver-enforced (soft) cap on total workload, but nothing structurally prevents a teacher's total *assigned* hours (sum of `course_block_assignment.block_length` across their blocks) from exceeding their total *availability* (count of `teacher_availability` rows) - if it does, no solve can place every block without a double-booking, since `timeslot` isn't nullable. `TeacherController`'s `POST`/`PUT /api/teachers` responses carry a non-blocking capacity warning for exactly this case (`buildCapacityWarning()`, added 2026-09-01 after this exact gap let several teachers' overloads go undetected until a solve's violation report surfaced them as double-bookings) - same advisory shape as `SemesterHourLimitController`'s guardrail #3, computed fresh from `CourseBlockAssignmentRepository`/the availability just saved, not stored anywhere.
- `Course` - Has `id`, name, `roomRequirement` (legacy single requirement), `roomRequirements` (List for dual requirements), `blockTemplates` (List for custom block decomposition), and `requiredHoursPerWeek`
- `Group` - Student group with assigned courses and an optional curated set of acceptable rooms per room type (`getAcceptableRooms(roomType)`, backed by the `group_room_range` table - see "Dynamic Room Assignment" under Room Assignment below)
- `Room` - Classroom with `type` (Standard, Mixed, Specialized - Workshop, Specialized - Computer Lab) and `building` designation. `satisfiesRequirement(req)` delegates to `common`'s `RoomTypeCompatibility.satisfies()` (the single shared implementation of this rule, also used directly by `web`): a `Mixed` room also satisfies `Standard` and `Specialized - Workshop` requirements (it's equipped for both a regular class and a workshop), while every other type satisfies only itself — notably `Specialized - Computer Lab` stays strictly separate from `Specialized - Workshop` despite the shared "Specialized" label prefix (that prefix is a UI grouping convenience only, not a compatibility relationship). A plain `Standard` or `Specialized - Workshop` room does NOT satisfy a `Mixed` requirement. (`taller electromecánica`/`taller electrónica` were retired in favor of retyping their rooms into this type, first named `laboratorio` then `dual`, then `mixto`, before settling on `Mixed` — same rooms/behavior throughout, just renamed several times to avoid confusing terminology.) The 4 valid room-type values live in the `room_type` lookup table (DB-level, not a Java enum — every Java/JS field storing a room type is still a plain `String`); `room.type`, `course.room_requirement`, `course_room_requirement.room_type`, `course_block_template.room_type`, and `course_block_assignment.satisfies_room_type` all FK into it with `ON UPDATE CASCADE`, so renaming a room type is a single `UPDATE room_type SET name = ...` rather than editing a `CHECK` constraint by hand. Likewise, course designations (Core, Elective, Dual, Specialized) live in the `course_designation` lookup table, FK'd from `course.designation` and `component_block_rule.component` — kept as a separate bare list rather than reusing `component_block_rule` as the FK target, since a designation with no `component_block_rule` row deliberately falls back to code defaults (see below) and forcing every designation to have a row there would break that. (`component_block_rule`/`.component` themselves keep that name — a distinct, not-yet-renamed feature that happens to be keyed by the same values.)
- `BlockTimeslot` - Specific day (`DayOfWeek`), start hour (int, 7-14), and length in hours (int, 1-4)
- `RoomRequirement` - Dual room requirements with `courseId`, `roomType`, `hoursRequired`, `priority`, `defaultPreferredRoom`
- `BlockTemplate` - Custom block decomposition with `courseId`, `groupId`, `blockIndex`, `blockLength`, `roomType`, `preferredRoomName`, `preferredDay`, `pinAssignment`, `preferredTimeslotId`

### Constraint System (Block-Based Scheduling)

**Hard Constraints** (13 defined in `SchoolConstraintProvider`; 11 currently active - the two break-after-consecutive-hours rules below are TEMP DISABLED):
1. `blockLengthMustMatchTimeslotLength` - Block length must match timeslot length
2. `teacherMustBeQualified` - Teacher must have qualification matching course name
3. `teacherMustBeAvailable` - Teacher must be available for entire block duration (checks per-day availability map)
4. `noTeacherDoubleBooking` - Teacher cannot teach two blocks that overlap in time
5. `noRoomDoubleBooking` - Room cannot host two blocks that overlap in time
6. `roomTypeMustSatisfyRequirement` - **CRITICAL**: Uses `assignment.getSatisfiesRoomType()` (NOT `course.getRoomRequirement()`) to support dual room requirements
7. `teacherRequiredRoomMustBeUsed` - A block's room must match its teacher's `requiredRoomName` when one is set. NOT excluded for pinned assignments (a data-integrity check, like `blockLengthMustMatchTimeslotLength`): a non-pinned block's room is already structurally guaranteed correct by `CourseBlockAssignment.getMatchingRooms()`'s entity-scoped value range, so in practice this only ever fires for a pinned row whose room drifted out of sync with its teacher's current requirement (`TeacherController.backfillRequiredRoom()` explicitly skips pinned blocks when the requirement changes). Mirrored in `BlockScheduleAnalyzer`.
8. `semesterOneBlocksMustFinishBy2pm` - A first-semester (`course.semester == 1`) block may never be assigned a timeslot ending after 14:00. NOT excluded for pinned assignments (a data-integrity check, like `blockLengthMustMatchTimeslotLength`/`teacherRequiredRoomMustBeUsed`): a non-pinned block's timeslot is already structurally guaranteed correct by `CourseBlockAssignment.getMatchingBlockTimeslots()`'s entity-scoped value range (it excludes any past-2pm-ending timeslot from the range entirely for a semester-1 block), so in practice this only ever fires for a pinned row whose timeslot predates this rule. Added 2026-08-27 (per request) as a hard guarantee rather than a soft preference, since the school's capacity comfortably covers every first-semester group's weekly hours within 7:00-14:00 (24h needed vs. 35h available). Mirrored in `BlockScheduleAnalyzer`.
9. `groupCannotHaveTwoCoursesAtSameTime` - Student group cannot have overlapping blocks
10. `maxTwoBlocksPerCoursePerGroupPerDay` - Maximum blocks per course per group per day, capped at `course.getMaxBlocksPerDay()` (per-component, via the `component_block_rule` table / Settings > Block Rules), falling back to a code default of 2 for a component with no configured rule. Mirrored in `BlockScheduleAnalyzer`.
11. `courseBlocksMustBeConsecutive` - All course blocks on same day must be consecutive
12. ~~`teacherMustHaveBreakAfterConsecutiveHours`~~ **TEMP DISABLED 2026-08-24** (per request) - A teacher scheduled `MAX_CONSECUTIVE_HOURS_WITHOUT_BREAK` (4h) straight, back-to-back with zero idle time, must get a break before continuing. Pinned blocks excluded from the run (legacy pinned data can't block solver convergence). Mirrored in `BlockScheduleAnalyzer`.
13. ~~`groupMustHaveBreakAfterConsecutiveHours`~~ **TEMP DISABLED 2026-08-24** (per request - groups are limited, don't need a break) - Same rule as above, for student groups instead of teachers.

**Soft Constraints** (11 defined, quality optimization; 8 currently active - `minimizeGroupIdleGaps`, `minimizeTeacherBuildingChanges`, and `preferCoreOneHourBlocksAtSameTimeAcrossDays` below are TEMP DISABLED):
1. `nonStandardRoomsShouldFinishBy2pm` (weight 10) - Non-standard rooms (CC, TEM, TE, AULA 4, LQ, LMICRO) should finish by 14:00. SOFT in the constraint provider **and** reported as SOFT by `BlockScheduleAnalyzer` (both exclude pinned assignments).
2. `preferSemesterOneBlocksStartEarly` (weight 6) - For a group's unpinned blocks whose `course.semester == 1`, prefer the earliest one each day to start at 7:00 (`EARLIEST_START_HOUR`). Penalty is the deviation in hours from 7:00 for that day's earliest semester-1 block, not a flat penalty. Mirrored in `BlockScheduleAnalyzer`. Raised from weight 4 on 2026-08-26 (per request) to outweigh `teacherMaxHoursPerWeek`.
3. `minimizeSemesterOneGroupIdleGaps` (weight 6) - Same adjacent-pair idle-gap logic as `minimizeGroupIdleGaps` below, but only penalizes a gap when **both** framing blocks are themselves semester-1; adjacency itself still considers a block of any semester, so a higher-semester block between two semester-1 blocks correctly breaks adjacency instead of the gap being mistaken for idle time. Added 2026-08-24 to give first-semester groups a distinct, higher-weight no-gaps preference than the general population; raised from weight 4 on 2026-08-26 (per request). Mirrored in `BlockScheduleAnalyzer`.
4. `teacherMaxHoursPerWeek` (weight 5) - Minimize teacher workload violations
5. `roomCapacityShouldFitGroupSize` (weight 4) - Group's `studentCount` shouldn't exceed the assigned room's `capacity`; only checked when both values are known.
6. ~~`minimizeGroupIdleGaps` (weight 3)~~ **TEMP DISABLED 2026-08-24** (per request - replaced for first-semester groups by `minimizeSemesterOneGroupIdleGaps` above; other groups' idle gaps are no longer minimized at all). Minimizes idle time between blocks for student groups. Penalizes only ADJACENT block pairs (via `forEachUniquePair` + `ifNotExists`) so idle hours are counted once per gap, not re-counted by non-adjacent pairs. `BlockScheduleAnalyzer` mirrors this by grouping per (group, day), sorting, and summing adjacent-block gaps.
7. `preferBlockSpecifiedRoom` (weight 3) - Prefer room specified in `assignment.getPreferredRoomHint()` field
8. `groupPreferredRoomConstraint` (weight 2) - Groups prefer a room from their curated `group_room_range` for the block's `satisfiesRoomType` (see "Dynamic Room Assignment" below); keyed by room type, so it naturally applies only when the group has actually curated a range for that specific type
9. `minimizeTeacherIdleGaps` (weight 2) - Reduce gaps between blocks for same teacher on same day. Availability-aware (only counts idle hours the teacher is actually available) and penalizes only ADJACENT block pairs (via `forEachUniquePair` + `ifNotExists`) to avoid re-counting the same idle hours across non-adjacent pairs.
10. ~~`minimizeTeacherBuildingChanges` (weight 1)~~ **TEMP DISABLED 2026-08-24** (per request - not required anymore) - Reduce building switches for teachers on same day
11. ~~`preferCoreOneHourBlocksAtSameTimeAcrossDays` (weight 2)~~ **TEMP DISABLED 2026-08-26** (per request) - For a `Core`-designation course's 1-hour blocks (one per day it meets, same group), prefer they all land on the same start hour - a predictable "Math is always at 8am" schedule. Doesn't apply to multi-hour blocks or pinned assignments. Penalty is the deviation from the most common ("mode") start hour among the group's blocks for that course, not a flat all-or-nothing penalty. Mirrored in `BlockScheduleAnalyzer`.

### Solver Configuration

Loaded by `SchoolSolverConfig` from `solverConfig.xml`:
- **Termination** (local-search phase): best score limit `0hard/0soft` OR 5 minutes total (`minutesSpentLimit`) OR 2 minutes without improvement (`unimprovedMinutesSpentLimit`)
- **Random seed**: not set in `solverConfig.xml`, so Timefold's own fixed default applies (fully reproducible given identical starting data) unless overridden per run via `SOLVER_RANDOM_SEED` — see "Exploring past a locally-optimal schedule" above.
- **Acceptor**: Late Acceptance (`lateAcceptanceSize` 10000) + Tabu Search (`entityTabuSize` 7)
- Phases: `FIRST_FIT_DECREASING` construction heuristic, then local search. `CourseBlockAssignment.timeslot`'s value range is entity-scoped (`CourseBlockAssignment.getMatchingBlockTimeslots()`, filtered to timeslots whose length matches the block's own `blockLength`; for a first-semester block, further filtered to exclude any timeslot ending after 14:00; and, for a non-pinned block, further filtered to exclude any timeslot overlapping a DIFFERENT pinned block belonging to its own teacher or its own group — see `getPinnedOccupiedTimeslots()`, built from a `Map<teacherId/groupId, List<BlockTimeslot>>` DataLoader computes once, in a second pass, after every row is read) rather than the full timeslot list, so a length mismatch, a past-2pm end time (semester-1), or a slot guaranteed to double-book a teacher/clash a group against fixed pinned data is structurally unreachable for movable assignments — no separate pre-fill phase or defragmentation phase is needed to police any of them. `room`'s value range (`getMatchingRooms()`) mirrors this: a singleton for "room-fixed" blocks, the full type-filtered room list otherwise — see Room Assignment below. `MatchingLengthMoveFilter`/`MatchingLengthSwapFilter` filter `ChangeMove`/`SwapMove` for teacher availability (not covered by the timeslot value range), for swap only block length plus the same pinned-occupancy exclusion above (swap doesn't consult either entity's own value range at all — confirmed necessary the same way the room-fixed case below was: without it, a `SwapMove` can hand a block a timeslot its own value range was supposed to exclude, silently reintroducing exactly the conflict the value range exists to prevent), and — for `room` specifically — reject any `ChangeMove`/`SwapMove` that would touch a room-fixed block's room at all, including unassigning it (`room` is `allowsUnassigned = true`, which makes "unassign" a legal `ChangeMove` target regardless of the entity's own value range; confirmed empirically that without this filter, local search silently unassigns correctly-fixed rooms).
- Constraint Streams API for declarative constraint modeling. **Every constraint in `SchoolConstraintProvider` uses `forEachIncludingUnassigned`/`ifNotExistsIncludingUnassigned`, never the plain `forEach`/`forEachUniquePair`/`ifNotExists`, for `CourseBlockAssignment`.** Timefold's plain `forEach`-family methods silently exclude any entity with a null value for *any* genuine planning variable — since `room` is nullable, that used to make a roomless block invisible to every constraint, not just room-related ones (confirmed empirically: `teacherMustBeQualified` and `groupCannotHaveTwoCoursesAtSameTime` both silently under-counted real violations before this fix). `forEachUniquePair`'s "unique pair, no self-pairs" behavior is replicated manually where needed: `forEachIncludingUnassigned(...).join(forEachIncludingUnassigned(...), Joiners.lessThan(getId), ...the constraint's own joiners...)`.

### Pre-Solve Validation

`PreSolveValidator.validate(schedule)` runs immediately after `DataLoader`, before the solver is even built (`MainBlockSchedulingApp`) — both the CLI and the web "Run Solver" button (`EngineController` → `EngineRunnerService`, which shells out to `scripts/run-engine.sh`) funnel through this same entry point; a "Run Validation" Tools page (`/validation`, `PreSolveValidationController`/`PreSolveValidationRunnerService`, open to WRITER and ADMIN) runs the identical check via `VALIDATE_ONLY=true` without ever reaching the solve phase, for a fast up-front report on its own. Every check it finds is a blocking `problem`, never a heuristic warning: a non-empty result prints the full report and calls `System.exit(1)` without solving at all (or, under `VALIDATE_ONLY`, just exits with a non-zero code). Ten checks total, in two groups:

**Pinned-data integrity** (checked only for `pinned = true` rows, via `validateSingle`/`validateConflicts`): the solver's hard constraints deliberately exclude pinned assignments (fixed input, not something the solver can fix), so an invalid pinned row would otherwise be silently accepted and never surface in the score. Re-applies: timeslot/room presence, block-length-matches-timeslot-length, room-type-satisfies-requirement, `teacherRequiredRoomMustBeUsed` (a pinned row's room out of sync with its teacher's current `requiredRoomName`), a HARD-severity `semesterHourLimitsMustBeRespected` violation (reuses `BlockScheduleMath.violatesHardSemesterHourLimit()` directly, so the two can't drift apart), teacher availability for the whole block, and pairwise pinned-vs-pinned conflicts (teacher double-booking, group clash, room double-booking).

**Whole-schedule structural facts** (checked for every assignment, pinned or movable — each is provable regardless of where the solver eventually places anything):
- `validateNoInactiveCourses` — any assignment whose course is `active = false`. Nothing else in the solve path checks this: `DataLoader` loads `course.active` into memory but nothing downstream reads it again; the two existing active-flag guards (`GroupCourseController.addCourse()`, `BlockGenerationService`) only stop *new* commitments, not rows that already exist. Grouped per course into one message rather than one per block.
- `validateTeacherQualifications` — a teacher not qualified for their assigned course, for *every* assignment (unlike the pinned-only group above) — teacher is fixed input, never a planning variable, so qualification doesn't depend on solve outcome.
- `validateCapacity` — a teacher's total assigned hours (pinned + movable) exceed their total weekly availability (`Teacher.getTotalAvailableHours()`) — pigeonhole guarantees at least one double-booking. Same math as `TeacherController.buildCapacityWarning()` (web, non-blocking, fires on save), but here it's blocking: `PreSolveValidator`'s whole point is stopping a solve that's already known to fail.
- `validateRoomFixedCapacity` — the mirror of the above for rooms: sum of hours assigned to a single structurally-fixed room (`CourseBlockAssignment.isRoomFixed()` — a teacher's required room, or a group's single-room curated range) vs. the school week's total hours (`SchoolCalendarConstants`). Deliberately scoped to fixed rooms only, not aggregate room-type demand (that's a real question too, but genuinely heuristic given type substitutability — `Mixed` satisfies `Standard`/`Workshop`).
- `validateBlockSpreadCapacity` — a "days, not hours" check: a (group, course) pair's blocks (all taught by one teacher) need `ceil(blockCount / maxBlocksPerDay)` distinct days; a teacher with enough total hours but too few distinct *available days* can still make the per-day cap (`BlockScheduleMath.maxBlocksPerDay()`) impossible to satisfy. Skipped when a (group, course) pair's blocks span more than one teacher, to avoid guessing which teacher's availability should govern.
- `validateNonEmptyTimeslotRanges` — a non-pinned assignment whose own `getMatchingBlockTimeslots()` has been narrowed down to nothing (see the pinned-occupancy exclusion in Solver Configuration above). Deliberately a direct emptiness check on the value range itself rather than an aggregate hours comparison: `validateCapacity` already catches the pure "not enough total hours" case (mathematically identical to comparing assigned hours against availability), but this exclusion can fail for a *shape* reason capacity math can't see — e.g. a teacher with plenty of hours left in total, but none of it in one contiguous window as long as a block still needs. Skipped for a pinned row or one whose `allTimeslots` was never wired up (Timefold never reassigns a pinned entity's variables, and an unset timeslot catalog is a different, uninteresting state from "wired up and genuinely empty").

All ten are deliberately blocking rather than advisory: each is a proven mathematical/structural fact, not a judgment call, so letting a solve run anyway only ever burns the full time budget to confirm what's already provable. `SKIP_PRESOLVE_VALIDATION=true` (CLI env var; `EngineRunRequest.skipValidation` / a checkbox on Settings' Solver tab for the web trigger) is a blanket escape hatch for testing/debugging — validation still runs and prints everything it finds either way, the flag only changes whether a non-empty result aborts the run. Deliberately all-or-nothing rather than a `constraint_config`-style per-check DB toggle: since every check is a proven fact rather than a judgment call, there's no legitimate reason to disable one selectively while leaving the others on.

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
- The `groupPreferredRoomConstraint` is soft (weight 2); since a group's acceptable-room range is keyed by room type (`Group.getAcceptableRooms(satisfiesRoomType)`), it naturally applies only when the group has actually curated a range for that block's specific type - a `Mixed`-required block is never penalized against a range curated for `Standard`, no special-case exclusion needed
- Dual room requirements allow courses to specify multiple room types (e.g., 4h in Specialized - Computer Lab + 1h in Standard)
- Each block has its own room type requirement via `satisfiesRoomType` field

**Dynamic room assignment for roomless blocks**: `room` is a `@PlanningVariable` (`allowsUnassigned = true`). A group's acceptable rooms are curated **per room type** (`group_room_range` table, `Group.getAcceptableRooms(roomType)` — replaces the old single `student_group.preferred_room_name`, migrated in `add_group_room_ranges.sql`): a room type with no rows for a group is unrestricted, one row is structurally fixed to that single room, 2+ rows is a narrowed-but-movable set the solver freely picks among. `CourseBlockAssignment.getMatchingRooms()` resolves this in priority order, each tier gated by room-type compatibility so an incompatible fixed choice falls through rather than locking the block to nothing: (1) the teacher's `requiredRoomName`, if it satisfies the block's `satisfiesRoomType`; (2) the group's curated range for that `satisfiesRoomType`, filtered to compatible rooms — singleton if exactly one, the full filtered set if more; (3) the full type-filtered room list. `CourseBlockAssignment.isRoomFixed()` is true only when tier (1) or (2) resolves to exactly one room — that's when `getMatchingRooms()` collapses to a singleton and the solver structurally cannot reassign it; a 2+-room group range is genuinely movable, just narrowed. `DataLoader` force-corrects a non-pinned fixed block's room to the current resolution at load time by delegating to `getMatchingRooms()` itself (construction heuristic never touches an already-non-null variable, so a stale `room_name` would otherwise survive every future solve unnoticed) — pinned rows are deliberately left alone; `teacherRequiredRoomMustBeUsed` reports a stale pinned room instead (using `isTeacherRequiredRoomApplicable()` so it only fires when the teacher's requirement actually governs that block). `BlockLengthDifficultyComparator` schedules room-fixed blocks before room-movable ones (after teacher availability, before block length) so a fixed block gets first pick of open timeslots when it shares a physical room with a movable one. `check_block_assignment_pinned_requires_room` (DB constraint, mirrors the existing timeslot one) makes it impossible to pin a block without a room in the first place. Ranges are edited via `/api/groups/{groupId}/room-ranges` (`GroupRoomRangeController`), which backfills the group's existing non-pinned assignments whenever a room type's range resolves to exactly one room (mirroring `TeacherController.backfillRequiredRoom`); `BlockGenerationService.defaultRoomFor()` applies the same range lookup when generating new blocks.

**Availability-aware block generation** (`BlockGenerationService`'s no-template fallback, `AvailabilityAwareBlockShaper`): a component's default block decomposition (its `component_block_rule.preferredBlockSize`, or `DEFAULT_BLOCK_SIZE`) is teacher-blind by default, which is exactly why `PreSolveValidator.validateBlockSpreadCapacity` (see below) can find a course needing more distinct days than its resolved teacher actually has. When a teacher is already resolved (`group_course.default_teacher_id`) and the naive shape wouldn't leave at least `AvailabilityAwareBlockShaper.DEFAULT_MARGIN_DAYS` (1) spare distinct day beyond the minimum needed under `component_block_rule.maxBlocksPerDay` — not just bare feasibility — `decomposeHours` asks `AvailabilityAwareBlockShaper.tryAvailabilityAwareShape()` for the first longer block size (up to the 4h structural max) that does have that margin; fewer, longer blocks need fewer days at the same daily cap. The margin requirement exists because a shape that's only just barely possible leaves the solver zero room to absorb any other scheduling pressure that day — confirmed live (2026-09-05): two pairs generated with exactly zero slack both went on to violate `maxBlocksPerDay` once solved. This is a probabilistic hedge, not a guarantee (a third pair that same night had a full spare day and still got violated). When no size reaches the margin, `tryAvailabilityAwareShape` gracefully settles for a merely bare-feasible shape rather than giving up outright — a course already needing every one of the teacher's available days has nowhere left to find a spare day from. This never assigns a specific day; the solver still freely places each block among the teacher's available days, exactly as for any other generated block. Falls back to the naive shape unchanged when there's no teacher yet, no availability data to reason from, or nothing up to 4h is even bare-feasible — a genuinely infeasible pairing `PreSolveValidator` still reports, exactly as before this existed.

Separately, when the resolved teacher's entire teaching load is this one (group, course) pairing (no other `group_course` row uses them as default teacher, and they have no pre-existing assignments at all), there is no real placement decision left for the solver to make — each block's day and hour are already forced by being the only room left in their calendar. `BlockGenerationService.tryPinExclusiveTeacherBlocks()` handles this: it greedily assigns each generated block a concrete timeslot from the teacher's actual contiguous available windows (`AvailabilityAwareBlockShaper.assignWindows()`, respecting `maxBlocksPerDay` per day — unlike most hard constraints, that cap is **not** re-checked by `PreSolveValidator` for pinned rows, so this is the only place enforcing it for a block pinned this way) and pins the block, but only when every block also resolves a single deterministic room (via the existing `defaultRoomFor()` priority chain), doesn't violate a HARD `semester_hour_limit` for the course's semester, and doesn't collide with anything already pinned — this group's own pinned data, or any other assignment (any group) already pinned to the same room — all-or-nothing across the whole (group, course) pairing's blocks, since a partial pin would mean an assumption here was wrong, not something to patch over block by block. These are deliberately the same facts `PreSolveValidator`'s pinned-data-integrity checks re-verify for any pinned row: confirmed live (2026-09-04) that omitting the semester-hour-limit check let a real pin land past a HARD limit, since nothing else about how the slot was computed happened to rule it out. Both mechanisms are heuristics over the teacher's declared availability alone, not a guarantee of a conflict-free eventual solve — but shape adaptation is safe regardless (it never fixes a day), and window assignment is only attempted for a teacher with no other commitments in the first place, which is what makes pinning safe there too.

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

## Modifying the System

**To change constraints**:
Edit `engine/src/main/java/com/example/solver/SchoolConstraintProvider.java`

**To modify demo data**:
Edit `engine/src/main/java/com/example/data/DemoDataGenerator.java`

**To adjust solver termination**:
Edit `engine/src/main/java/com/example/solver/SchoolSolverConfig.java`

**To change pre-solve validation (the blocking checks that run before every solve)**:
Edit `engine/src/main/java/com/example/validation/PreSolveValidator.java` — see "Pre-Solve Validation" above. Keep new checks to proven facts (not heuristics); an advisory-only check belongs in `ValidationResult`'s `warnings` list instead of `problems`.

**To change domain model**:
- Ensure no-arg constructors remain for Timefold compatibility
- Update constraint provider if new fields affect constraints
- Consider backwards-compatible constructors for existing call sites
