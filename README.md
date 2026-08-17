# School Timeslot Optimizer with Timefold Solver

A Java 17 application that places pre-assigned teacher/room/course blocks into weekly timeslots using **Timefold Solver 1.29.0**. Each `CourseBlockAssignment` arrives with its teacher, room, and course already assigned; the solver's single planning variable is the block's `timeslot`. It optimizes timeslot placement while respecting hard constraints and soft preferences.

> **Scope note:** Teacher and room are **not** planning variables — they are fixed inputs pre-assigned from the database (the `teacher`/`room` `@PlanningVariable` annotations are intentionally commented out, and `DataSaver` persists only `block_timeslot_id`). This is a **timeslot optimizer** over pre-assigned teacher/room blocks, not a full teacher/room scheduler.

## Purpose

Building a school's weekly bell schedule by hand doesn't scale past a handful of groups —
every added course, teacher, or room multiplies the ways two things can collide. This
project automates that: given each course block's teacher, room, and course (assigned
separately, e.g. by an administrator or a prior planning step), it finds a weekly placement
that avoids every double-booking and honors teacher availability and qualifications, while
also optimizing for schedule quality (minimal idle gaps, room preferences, workload balance).

**Goal:** eliminate scheduling conflicts and minimize the manual effort of building and
maintaining a term timetable.

**Primary users:** school administrators and academic planners who own the master
timetable — not students or teachers browsing it (though the web app's `TEACHER` role gives
teachers read-only access to their own resulting schedule).

## At a Glance

- Block-based scheduling only (multi-hour consecutive blocks, 1-4 hours); hour-based scheduling has been fully removed
- 9 hard / 8 soft constraints, kept in sync with `BlockScheduleAnalyzer` by `ConstraintConsistencyTest`
- Dual room requirements and custom block templates are fully manageable from the web UI, not just the database
- Web app: JWT auth with `READER`/`WRITER`/`ADMIN`/`TEACHER` roles, bilingual (EN/ES) UI, Excel import/export, PDF reporting — see [Authentication & Roles](#authentication--roles)
- Solver termination: best score `0hard/0soft`, or 5 minutes, or 2 minutes without improvement (see `solverConfig.xml`)

## How It Works

Each course block already carries its **teacher** (with qualifications and per-day
availability), **room** (standard classroom or specialized lab), and **course** — the solver
assigns only the block's **timeslot** (day + start hour + length, Monday–Friday 7:00–15:00).
Teacher, room, and course are fixed inputs, validated against the chosen timeslot by the
constraints below rather than chosen by the solver.

Blocks are 1-4 consecutive hours; how a course's weekly hours decompose into blocks is
controlled by the course's component type (see `BlockGenerationService`/`DemoDataGenerator`)
or, per course/group, an explicit `course_block_template` override managed in the Courses tab.

## Constraints

`SchoolConstraintProvider` and `BlockScheduleAnalyzer` are kept in lockstep by
`ConstraintConsistencyTest`, so this list is guaranteed accurate as of the last
test run (9 hard, 8 soft).

#### Hard Constraints (must be satisfied)
1. **Block Length Must Match Timeslot Length**
2. **Teacher Qualification** — teacher must be qualified for the assigned course
3. **Teacher Availability for Entire Block**
4. **No Teacher Double-Booking**
5. **No Room Double-Booking**
6. **Room Type Must Satisfy Course Requirement** — uses `assignment.satisfiesRoomType`, not `course.roomRequirement` (dual room requirement support)
7. **Group Cannot Have Two Courses at Same Time**
8. **Maximum 2 Blocks Per Course Per Group Per Day**
9. **Course Blocks Must Be Consecutive** — a course's blocks on the same day must be back-to-back

#### Soft Constraints (weighted quality preferences)
1. **Non-Standard Rooms Should Finish by 2pm** (weight 10) — labs/workshops/computer centers
2. **Teacher Exceeds Max Hours Per Week** (weight 5)
3. **Room Capacity Should Fit Group Size** (weight 4) — opt-in: only fires when both `room.capacity` and `student_group.student_count` are set
4. **Minimize Group Idle Gaps** (weight 3/hour, adjacent-pair only)
5. **Prefer Block's Specified Room** (weight 3) — `preferred_room_name`
6. **Minimize Teacher Idle Gaps** (weight 2/hour, availability-aware, adjacent-pair only)
7. **Prefer Group's Preferred Room** (weight 2) — excludes lab-satisfying blocks
8. **Minimize Teacher Building Changes** (weight 1)

## Features

### Solver / engine
- Multi-hour consecutive blocks (1-4 hours), with pinning support for locking specific blocks to a teacher/room/timeslot
- Dual room requirements (a course can split its hours across multiple room types) and custom per-course/per-group block templates — both database-driven and web-UI-manageable
- Optional room-capacity awareness (`room.capacity` vs. `student_group.student_count`)
- 6 room types: estándar, laboratorio, taller, taller electromecánica, taller electrónica, centro de cómputo
- PostgreSQL-backed: schema, reporting views, and data loading scripts
- Three PDF reports (violations, by-teacher, by-group) via Constraint Streams-based analysis

### Web app
- Role-based access control (`READER`/`WRITER`/`ADMIN`/`TEACHER`) over stateless JWT — see [Authentication & Roles](#authentication--roles)
- Full CRUD for teachers, courses (incl. dual room requirements, block templates), rooms, groups (incl. group-course management), and course block assignments
- Bilingual UI (English/Spanish, `react-i18next`) with a per-user language preference
- Admin: user management, timeslot management, current-term label, write-activity audit log, admin-triggered solver runs and block generation
- Excel import/export (`POST`/`GET /api/import/excel`) — the same `.xlsx` layout both ways, for a full export → edit → re-import round trip
- Teacher self-service: a `TEACHER`-role account sees only its own schedule
- Search, pagination, toast notifications, and confirm dialogs throughout

## Project Structure

Maven multi-module build: an aggregator `pom.xml` at the root with three
modules that only integrate through the shared PostgreSQL database (no
module-to-module dependency between `engine` and `web`), plus the standalone
`web-ui/` React frontend.

```
.
├── pom.xml                              # Aggregator/parent POM
├── engine/                              # scheduler-engine: Timefold + JDBC, no Spring
│   └── src/main/java/com/example/
│       ├── MainBlockSchedulingApp.java  # Entry point: load -> solve -> save
│       ├── domain/                      # Teacher, Course, Room, Group, BlockTimeslot,
│       │                                 # CourseBlockAssignment (@PlanningEntity),
│       │                                 # RoomRequirement, BlockTemplate, SchoolSchedule
│       ├── solver/                      # SchoolConstraintProvider, SchoolSolverConfig,
│       │                                 # custom moves/filters/comparators
│       ├── analysis/                    # BlockScheduleAnalyzer (mirrors the constraints)
│       ├── validation/                  # PreSolveValidator (fail-fast data sanity checks)
│       ├── data/                        # DataLoader, DataSaver, DemoDataGenerator
│       └── util/                        # Excel import/export helpers
├── reporter/                            # scheduler-reporter: depends on engine as a library
│   └── src/main/java/com/example/reporter/
│       └── PdfReportApp.java            # Reads the solved schedule, generates 3 PDFs
├── web/                                 # scheduler-web: Spring Boot REST API + JWT/RBAC
│   └── src/main/java/com/example/web/
│       ├── controller/                  # One controller per resource (Teachers, Courses,
│       │                                 # Rooms, Groups, Assignments, Schedule, Auth, Users,
│       │                                 # Timeslots, Engine, Reports, Import, Term, AuditLog, ...)
│       ├── entity/ + repository/        # JPA entities and Spring Data repositories
│       ├── dto/                         # Request/response DTOs with bean validation
│       ├── security/                    # SecurityConfig (JWT + RBAC), AuditLogInterceptor
│       ├── service/                     # BlockGenerationService, ExcelImportService,
│       │                                 # ExcelExportService, EngineRunnerService, ...
│       └── exception/                   # GlobalExceptionHandler
├── web-ui/                              # React + Vite SPA (unchanged by the module split)
│   └── src/
│       ├── components/                  # One component per tab (Teachers, Courses, Rooms,
│       │                                 # Groups, Assignments, Schedule, MySchedule, Settings,
│       │                                 # Users, Reports, Import, Login)
│       ├── auth/                        # AuthContext, ProtectedRoute/AdminRoute/WriteRoute
│       ├── ui/                          # Shared ToastContext, ConfirmContext, Pagination
│       └── i18n/                        # en.json / es.json (react-i18next)
├── database/
│   ├── schema_block_scheduling.sql      # Canonical PostgreSQL schema (block-based only,
│   │                                     # includes all reporting views)
│   ├── migrations/                      # Incremental migrations applied on top of the
│   │                                     # schema (app_user/RBAC, room capacity, school_term,
│   │                                     # audit log, TEACHER role, ...)
│   └── datasets/                        # Demo and production seed data
└── scripts/                             # run-engine.sh, run-reporter.sh helper scripts
```

## Build Instructions

### Prerequisites
- **Java 17+**, **Maven 3.8+**, **PostgreSQL 12+**

### Database Setup

```bash
createdb -U mancilla school_schedule
psql -U mancilla -d school_schedule -f database/schema_block_scheduling.sql

# Then load one dataset:
psql -U mancilla -d school_schedule -f database/datasets/load_demo_data_blocks.sql        # demo
psql -U mancilla -d school_schedule -f database/datasets/load_final_dataset_blocks.sql    # production
```
Reporting views are created automatically as part of the schema load.

All modules read the database connection from `DB_URL` / `DB_USER` / `DB_PASSWORD`
environment variables (default: `jdbc:postgresql://localhost:5432/school_schedule`,
user `mancilla`, empty password).

### Compile & Test
```bash
mvn clean compile
mvn test
```

### Run the Solver
```bash
mvn -pl engine exec:java -Dexec.mainClass="com.example.MainBlockSchedulingApp"
```
Loads data → solves → saves the assignments back to PostgreSQL → prints a constraint
violation summary. PDF reports are generated separately by the **reporter** module, which
reads the persisted schedule from the database:
```bash
mvn -pl reporter exec:java -Dexec.mainClass="com.example.reporter.PdfReportApp"
```

### Run as Workers / Containers

The engine and reporter are one-shot batch jobs (not daemons); the web app is a long-running
service. Each module has a fat-jar build (`mvn -pl <module> -am -DskipTests package` →
`<module>/target/scheduler-<module>-1.0.0.jar`), a `Dockerfile`, and a helper script
(`scripts/run-engine.sh`, `scripts/run-reporter.sh`) that reads `DB_URL`/`DB_USER`/
`DB_PASSWORD` from the environment and exits non-zero on failure (safe for cron/orchestrators).

```bash
# One-shot solve, via the helper script or a container
DB_URL=... DB_USER=... DB_PASSWORD=... ./scripts/run-engine.sh
docker build -f engine/Dockerfile -t scheduler-engine . && docker run --rm -e DB_URL=... scheduler-engine

# Or all three services via Docker Compose (see docker-compose.yml for CPU/memory limits)
docker compose up -d web                # long-running REST API on :8080
docker compose run --rm engine          # one-shot solve
docker compose run --rm reporter        # one-shot PDF generation (./reports)
```
The solver is the most resource-hungry component — give it at least 4 CPUs / 8 GB (see
`docker-compose.yml` / `engine/Dockerfile` for the tuned defaults).

## Web UI

A React + Spring Boot web interface manages the full problem — teachers, courses, rooms,
student groups, and course block assignments — plus admin functions, without touching the
database directly.

```bash
# Terminal 1 - backend (http://localhost:8080)
mvn -pl web spring-boot:run

# Terminal 2 - frontend (http://localhost:3000, proxies /api to the backend)
cd web-ui && npm install && npm run dev
```

See [`web-ui/README.md`](web-ui/README.md) and [`WEB_UI_SETUP.md`](WEB_UI_SETUP.md) for the
full feature list, REST API endpoint reference, Docker/production deployment, and
troubleshooting.

### Authentication & Roles

Stateless JWT auth; every `/api/**` endpoint except `POST /api/auth/login` requires
`Authorization: Bearer <token>`.

| Role      | Permissions                                                         |
|-----------|---------------------------------------------------------------------|
| `READER`  | `GET` only.                                                          |
| `WRITER`  | `READER` + create/update/delete on domain entities.                 |
| `ADMIN`   | `WRITER` + full access, including user management under `/api/admin/**`. |
| `TEACHER` | Scoped to itself only: its own schedule, its own identity/language, and the term label — **not** general domain data. An admin links a `TEACHER` account to a teacher record from the Users tab. |

**First-time setup** — apply the users migration (creates `app_user`; not part of the schema
file itself), then boot with `ADMIN_BOOTSTRAP_PASSWORD` set to seed the first admin:
```bash
psql -U mancilla -d school_schedule -f database/migrations/add_app_users.sql
ADMIN_BOOTSTRAP_PASSWORD=change-me JWT_SECRET=$(openssl rand -hex 32) mvn -pl web spring-boot:run
```
Log in at `http://localhost:3000/login`; create additional users via the `ADMIN`-only Users tab.

See [WEB_UI_SETUP.md](WEB_UI_SETUP.md#authentication--roles) for the full endpoint reference,
security config env vars, and upgrade-migration details for existing databases.

### Temporary Public Sharing

```bash
brew install cloudflared            # one-time
mvn -pl web spring-boot:run &       # backend
(cd web-ui && npm run dev) &        # frontend
cloudflared tunnel --url http://localhost:3000
```
Prints a public `https://*.trycloudflare.com` URL that proxies through Vite's `/api` proxy —
no separate tunnel needed for port 8080. Use a strong `JWT_SECRET` and admin password before
sharing, and tear the tunnel down when done (`pkill -f "cloudflared tunnel"`).

## Architecture

- **Java 17**, **Timefold Solver 1.29.0** (Constraint Streams), **PostgreSQL 12+**, **Maven**, **Apache PDFBox**, **HardSoftScore** (two-level: hard feasibility, soft quality)
- **Domain model**: `CourseBlockAssignment` (`@PlanningEntity`), `BlockTimeslot`, `Teacher`, `Course`, `Room`, `Group`, `SchoolSchedule` (`@PlanningSolution`)
- **Solver phases**: custom construction heuristic phases, then local search (Late Acceptance + Tabu Search); termination per `solverConfig.xml` (best score `0hard/0soft`, 5 min limit, 2 min unimproved limit)

## Known Limitations

1. **Room capacity is opt-in and soft** — even when `room.capacity`/`student_group.student_count` are both set, it's a soft penalty (weight 4), not a hard block
2. **Teachers and rooms are pre-assigned** — the solver only assigns timeslots (see the Scope note above)
3. **No multi-teacher courses** — each block has exactly one teacher
4. **Soft constraints scale O(n²)** (pairwise) — may need optimization for much larger datasets
5. **No calendar/term dates** — `BlockTimeslot` is a recurring weekly template (day-of-week + hour), not tied to actual dates; no holiday/exception-date support, and the "current term" label is display-only, not a scheduling boundary

## Future Enhancements

- [ ] Dynamic teacher/room assignment (currently pre-assigned)
- [ ] Teacher workload balancing across weeks
- [ ] Student preferences for elective courses
- [ ] Mandatory lunch break / minimum rest-period constraints
- [ ] Multi-week scheduling patterns
- [ ] Calendar system integration (iCal/Google Calendar export)
- [ ] Real-time constraint violation feedback during manual edits

## Contributing

1. **Constraints**: edit `engine/.../solver/SchoolConstraintProvider.java`, keeping `engine/.../analysis/BlockScheduleAnalyzer.java` in sync — `ConstraintConsistencyTest` fails the build if they drift
2. **Schema**: update `database/schema_block_scheduling.sql` (fresh-install shape) and add a corresponding file under `database/migrations/` for existing databases
3. **Dataset**: modify `database/datasets/load_final_dataset_blocks.sql`, then reload it
4. **Test**: `mvn test` (all three modules), plus a solver run if you touched constraints
5. **Web API/UI**: see [Project Structure](#project-structure) for where each concern lives (`web/.../controller`, `entity`, `dto`, `security`; `web-ui/src/components`, `api.js`, `i18n/{en,es}.json`)

For the change history, use `git log` rather than this file.

## License

This project is provided as-is for educational and scheduling purposes.

## References

- [Timefold Solver Documentation](https://timefold.ai/)
- [Constraint Streams Guide](https://docs.timefold.ai/timefold-solver/latest/use-cases-and-examples)
