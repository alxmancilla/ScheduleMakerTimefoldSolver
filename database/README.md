# PostgreSQL Database — School Scheduling System

This directory holds the PostgreSQL schema and data-loading scripts for the
block-based scheduling system. **See the root [README.md](../README.md) for
the authoritative setup steps** (Database Setup, Authentication & Roles,
Upgrading an existing database) — this file only orients you within
`database/`.

## Files

- **`schema_block_scheduling.sql`** — canonical fresh-install schema
  (block-based only; the old single-hour `timeslot`/`course_assignment`
  model has been removed). Includes all reporting views.
- **`migrations/`** — incremental migrations applied on top of the schema for
  databases created before a given feature shipped (RBAC/`app_user`, room
  capacity, school term, audit log, TEACHER role, ...). A fresh
  `schema_block_scheduling.sql` load does **not** include `app_user`/RBAC —
  run `migrations/add_app_users.sql` (and any other migrations you need)
  after loading the schema regardless of whether the database is new or
  existing. Gitignored except for an explicit allow-list of filenames (see
  `.gitignore`).
- **`datasets/`** — demo (`load_demo_data_blocks.sql`) and production
  (`load_final_dataset_blocks*.sql`) seed data. Gitignored (local-only).
- **`backups/`** — `pg_dump` snapshots. Gitignored (local-only).
- **`create_view_group_course_teachers.sql`**,
  **`view_teacher_availability_by_day.sql`** — the source for two of the
  views already baked into `schema_block_scheduling.sql`
  (`v_group_course_teachers`, `v_teacher_availability_by_day`); kept
  standalone for reference if you need to recreate just one view.
- **`example_pinning_strategies.sql`**, **`pinned_block_assignments.sql`**,
  **`fix_unique_constraint.sql`**, **`teacher_gaps_violation.sql`** — ad hoc
  query examples and one-off fixes written during development, not part of
  the setup flow.
- **`schema_block_scheduling_0212.sql`** — a dated snapshot of the schema
  kept for historical reference; not used by any setup step.

## Core Tables (block-based)

1. **`teacher`** — teacher info, workload capacity (`max_hours_per_week`), and
   an optional `required_room_name` override (always used for this teacher's
   blocks, ahead of the group's curated room range, when room-type compatible)
2. **`teacher_qualification`** — courses each teacher is qualified to teach
3. **`teacher_availability`** — per-day, per-hour availability
4. **`course`** — courses offered, with legacy `room_requirement` plus the
   dual-requirement tables below
5. **`room`** — classrooms/labs, with `type`, `building`, optional `capacity`
6. **`student_group`** — student groups, with optional `student_count`
7. **`group_course`** — which courses each group takes, with an optional
   `default_teacher_id` pre-assignment applied by `BlockGenerationService`
   the next time blocks are generated for that pairing
8. **`block_timeslot`** — a day + start hour + length (1-4h) a block can
   occupy
9. **`course_block_assignment`** — the `@PlanningEntity`; `timeslot` is
   always a `@PlanningVariable`, `room` is one too (`allowsUnassigned = true`)
   but structurally collapses to a singleton for "room-fixed" blocks (see
   `group_room_range` below and `CourseBlockAssignment.isRoomFixed()`);
   teacher/course are fixed inputs
10. **`course_room_requirement`** — dual room requirements (e.g. 4h in a
    computer center + 1h standard) for a course
11. **`course_block_template`** — explicit, hand-authored block decomposition
    for a course (optionally scoped to one group)
12. **`component_block_rule`** — per-course-component preferred block size and
    max blocks per day, editable from Settings → Block Rules; a component with
    no row falls back to a size-2 / max-2-per-day default in code
13. **`school_term`** — current term/period display label
14. **`schedule_audit_log`** — write-activity audit trail
15. **`group_room_range`** — a group's curated acceptable rooms per room type
    (`group_id`, `room_type`, `room_name`), replacing the old single
    `student_group.preferred_room_name`; a type with no rows is unrestricted,
    one row fixes the group to that single room, 2+ rows is a narrowed but
    movable set the solver picks freely among
16. **`schedule_run`** — one row per solver run (score + the effective
    `minutes_spent_limit`/`unimproved_minutes_spent_limit` budget used);
    `course_block_assignment` itself is pure input, so this - not that table -
    is where the solver's actual placements live. `DataSaver` prunes to the
    most recent 10 rows after every insert (`ON DELETE CASCADE` cleans up
    `schedule_run_result`/`schedule_run_constraint`)
17. **`schedule_run_result`** — one row per assignment per run: the solved
    (or still-unassigned) `block_timeslot_id`, plus a frozen copy of that
    assignment's input fields at that moment. `course_block_assignment_current`
    (a view) resolves "the current schedule" from this: pinned rows keep their
    own input timeslot, everything else uses the most recent run's result -
    which also gives the solver warm-starting for free
18. **`schedule_run_constraint`** — which constraints (by name, HARD/SOFT) were
    active for a given run, sourced from `BlockScheduleAnalyzer`'s own
    active-constraint maps at save time rather than a hand-maintained list;
    lets score-history analysis separate a genuine constraint-set change
    between runs from ordinary solver-run variance. Added 2026-08-24, so runs
    before that date have no rows here
19. *(via migration)* **`app_user`** — login accounts + role (RBAC)

## Data Mapping from the Java Domain Model

- `Teacher` (`id`, qualifications, per-day availability, `maxHoursPerWeek`,
  optional `requiredRoomName`) → `teacher` / `teacher_qualification` /
  `teacher_availability`
- `Course` (`roomRequirement`, `roomRequirements`, `blockTemplates`,
  `requiredHoursPerWeek`, `maxBlocksPerDay`) → `course` /
  `course_room_requirement` / `course_block_template` /
  `component_block_rule` (looked up by component, not a direct FK)
- `Room` (`type`, `building`, optional `capacity`) → `room`
- `Group` (assigned courses, `getAcceptableRooms(roomType)`, `studentCount`) →
  `student_group` / `group_course` (each course link can carry an optional
  `defaultTeacherId` pre-assignment) / `group_room_range`
- `BlockTimeslot` (`DayOfWeek`, start hour, length) → `block_timeslot`
- `CourseBlockAssignment` (`@PlanningVariable`s: `timeslot`, and `room` for
  genuinely roomless blocks; teacher/course fixed inputs) →
  `course_block_assignment`

## Integration with the Java Application

`DataLoader` (`com.example.data.DataLoader`, engine module) loads the
scheduling dataset from PostgreSQL into a `SchoolSchedule` for the Timefold
solver, and `DataSaver` persists the solved `block_timeslot_id` back. Both
read `DB_URL` / `DB_USER` / `DB_PASSWORD` from the environment — see the root
README's "Configure Database Connection" section.

## Support

For issues or questions about the schema, refer to:
- The Java domain model in `engine/src/main/java/com/example/domain/`
- `DemoDataGenerator.java` for how demo data is generated
- `SchoolConstraintProvider.java` for how these tables map to constraints
- The root [README.md](../README.md) for full setup/upgrade instructions
