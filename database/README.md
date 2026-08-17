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

1. **`teacher`** — teacher info with workload capacity (`max_hours_per_week`)
2. **`teacher_qualification`** — courses each teacher is qualified to teach
3. **`teacher_availability`** — per-day, per-hour availability
4. **`course`** — courses offered, with legacy `room_requirement` plus the
   dual-requirement tables below
5. **`room`** — classrooms/labs, with `type`, `building`, optional `capacity`
6. **`student_group`** — student groups, with optional `preferred_room` and
   `student_count`
7. **`group_course`** — which courses each group takes
8. **`block_timeslot`** — a day + start hour + length (1-4h) a block can
   occupy
9. **`course_block_assignment`** — the `@PlanningEntity`; one
   `@PlanningVariable` (`timeslot`), teacher/room/course pre-assigned
10. **`course_room_requirement`** — dual room requirements (e.g. 4h in a
    computer center + 1h standard) for a course
11. **`course_block_template`** — explicit, hand-authored block decomposition
    for a course (optionally scoped to one group)
12. **`school_term`** — current term/period display label
13. **`schedule_audit_log`** — write-activity audit trail
14. *(via migration)* **`app_user`** — login accounts + role (RBAC)

## Data Mapping from the Java Domain Model

- `Teacher` (`id`, qualifications, per-day availability, `maxHoursPerWeek`)
  → `teacher` / `teacher_qualification` / `teacher_availability`
- `Course` (`roomRequirement`, `roomRequirements`, `blockTemplates`,
  `requiredHoursPerWeek`) → `course` / `course_room_requirement` /
  `course_block_template`
- `Room` (`type`, `building`, optional `capacity`) → `room`
- `Group` (assigned courses, optional `preferredRoom`, `studentCount`) →
  `student_group` / `group_course`
- `BlockTimeslot` (`DayOfWeek`, start hour, length) → `block_timeslot`
- `CourseBlockAssignment` (one `@PlanningVariable`: `timeslot`; teacher/room/
  course fixed inputs) → `course_block_assignment`

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
