# School Timeslot Optimizer with Timefold Solver

A Java 17 application that places pre-assigned teacher/room/course blocks into weekly timeslots using **Timefold Solver 1.29.0**. Each `CourseBlockAssignment` arrives with its teacher, room, and course already assigned; the solver's single planning variable is the block's `timeslot`. It optimizes timeslot placement while respecting hard constraints and soft preferences.

> **Scope note:** Teacher and room are **not** planning variables — they are fixed inputs pre-assigned from the database (the `teacher`/`room` `@PlanningVariable` annotations are intentionally commented out, and `DataSaver` persists only `block_timeslot_id`). This is a **timeslot optimizer** over pre-assigned teacher/room blocks, not a full teacher/room scheduler.

## Current Status

✅ **Build & Tests: PASSING** — `mvn test` across all three modules (engine + reporter + web); 9 hard / 8 soft constraints, kept in sync with the analyzer by `ConstraintConsistencyTest`
✅ **Block-Based Scheduling:** Migrated from hour-based to block-based scheduling (multi-hour consecutive blocks)
✅ **PostgreSQL Integration:** Full database support with schema, views, migrations, and data loading
✅ **Production Dataset:** 399 course block assignments with 32 pinned assignments (see [Production Dataset](#production-dataset) below; the score figures there are from that dataset's last full solve, not necessarily the current database contents)
✅ **Dual Room Requirements & Custom Block Templates:** Courses can specify multiple room types with different hour allocations, or an explicit hand-authored block decomposition — both fully manageable from the web UI (Courses tab), not just the database
✅ **Computer Center Distribution:** Greenfield strategy for optimal CC 1/CC 2/CC 3 utilization (107h in 105h capacity)
✅ **Web App: RBAC, Bilingual UI, and a TEACHER Self-Service Role** — JWT auth with `READER`/`WRITER`/`ADMIN`/`TEACHER` roles, full English/Spanish localization, a write-activity audit log, and admin-editable current-term label — see [Authentication & Roles](#authentication--roles)
⚠️ **Current Score (production dataset):** -11hard/-1230soft (11 actual violations, 2pm constraint relaxed to SOFT)
⏱️ **Solver Config:** 5 min time limit, 2 min unimproved limit, lateAcceptanceSize: 10000, entityTabuSize: 7, best score: `0hard/0soft` (see `solverConfig.xml`)

## Project Overview

### Problem Definition
Place pre-assigned course blocks into a weekly timetable using **block-based scheduling**. Each block already carries its **teacher** (with qualifications and per-day availability), **room** (standard classroom or specialized lab), and **course**; the solver assigns only the block's **timeslot**:
- **Block Timeslots** (Monday–Friday, 7:00–15:00, with variable block lengths) — the single planning variable
- **Course Blocks** belong to student groups (multi-hour consecutive blocks, 1-4 hours)
- Teacher, room, and course are fixed inputs (not planning variables) and are validated against the chosen timeslot by the constraints

### Scheduling Modes

The system supports **block-based scheduling** (current implementation):

**Block-Based Scheduling** (`MainBlockSchedulingApp`):
- Uses `CourseBlockAssignment` entities representing multi-hour consecutive blocks
- Supports blocks of 1-4 consecutive hours
- **BASICAS courses**: Use multiple 1-hour blocks for maximum flexibility
- **Non-BASICAS courses**: Use larger blocks (3-4 hours) to minimize fragmentation
- Block decomposition strategy:
  - 3 hours: 1×3-hour block
  - 4 hours: 1×4-hour block
  - 5 hours: 1×3-hour + 1×2-hour
  - 6 hours: 2×3-hour blocks
  - 7 hours: 1×4-hour + 1×3-hour
  - 8 hours: 2×4-hour blocks
  - 9 hours: 2×4-hour + 1×1-hour
  - 11 hours: 2×4-hour + 1×3-hour

### Constraints

`SchoolConstraintProvider` and `BlockScheduleAnalyzer` are kept in lockstep by
`ConstraintConsistencyTest`, so this list is guaranteed accurate as of the last
test run (9 hard, 8 soft).

#### Hard Constraints (Must be satisfied)
1. **Block Length Must Match Timeslot Length** — Data integrity constraint (database validation)
2. **Teacher Qualification** — Teacher must be qualified for assigned course
3. **Teacher Availability for Entire Block** — Teacher must be available for all hours in the block
4. **No Teacher Double-Booking** — Teacher cannot teach two blocks that overlap
5. **No Room Double-Booking** — Room cannot host two blocks that overlap
6. **Room Type Must Satisfy Course Requirement** — Uses `assignment.satisfiesRoomType`, not `course.roomRequirement` (dual room requirement support)
7. **Group Cannot Have Two Courses at Same Time** — Student group cannot have overlapping blocks
8. **Maximum 2 Blocks Per Course Per Group Per Day** — No course/group pair gets more than 2 blocks on the same day
9. **Course Blocks Must Be Consecutive** — All of a course's blocks on the same day must be back-to-back

#### Soft Constraints (Quality optimization, weighted preferences)
1. **Non-Standard Rooms Should Finish by 2pm** (weight 10) — Labs, workshops, and computer centers should end by 14:00; relaxed from HARD to SOFT so it's a strong preference rather than a hard blocker
2. **Teacher Exceeds Max Hours Per Week** (weight 5) — Penalizes teachers exceeding their weekly hour limit
3. **Room Capacity Should Fit Group Size** (weight 4) — Warns when a group's headcount exceeds its assigned room's `capacity`; only applies when both `room.capacity` and `student_group.student_count` are set, so it's opt-in per room/group
4. **Minimize Group Idle Gaps** (weight 3 per hour) — Reduce gaps between blocks for the same student group on the same day (adjacent-pair only, so gaps aren't double-counted)
5. **Prefer Block's Specified Room** (weight 3) — Use `preferred_room_name` when specified (e.g. Computer Center distribution)
6. **Minimize Teacher Idle Gaps** (weight 2 per hour) — Reduce gaps between blocks for the same teacher on the same day (availability-aware, adjacent-pair only)
7. **Prefer Group's Preferred Room** (weight 2) — Groups prefer their pre-assigned room when specified (excludes lab-satisfying blocks)
8. **Minimize Teacher Building Changes** (weight 1) — Reduce building switches for teachers on the same day

## Features

### Solver / engine
- **Block-Based Scheduling**: Multi-hour consecutive blocks (1-4 hours) for efficient timetabling
- **PostgreSQL Integration**: Full database support with schema, views, and data loading scripts
- **Pinned Assignments**: Support for locking specific course blocks to teachers, rooms, and timeslots
- **Dual Room Requirements**: Courses can specify multiple room types with different hour allocations (e.g., 4h in lab + 4h in standard room), managed entirely through the web UI (`course_room_requirement` table)
- **Custom Block Templates**: Explicit, hand-authored block decomposition per course (optionally per group), overriding the generic decomposition — also managed through the web UI (`course_block_template` table)
- **Room Capacity Awareness**: Optional `room.capacity` / `student_group.student_count` — when both are set, a soft constraint flags oversized groups in undersized rooms
- **Computer Center Distribution**: Greenfield strategy for optimal CC 1/CC 2/CC 3 utilization across TPROG/TCS/TIA pathways
- **Flexible Teacher Management**: Teachers have stable `id`, qualifications, per-day availability maps, and `maxHoursPerWeek` workload limits
- **Multi-Room Scheduling**: Support for 6 room types (estándar, laboratorio, taller, taller electromecánica, taller electrónica, centro de cómputo)
- **Group Constraints**: Prevent overlapping blocks for student groups with optional preferred rooms
- **PDF Reports**: Three paginated PDF reports generated: violations analysis, schedule-by-teacher, and schedule-by-group
- **Database Views**: Pre-built views for teacher assignments, group schedules, and constraint validation
- **Scalable Architecture**: Timefold Constraint Streams for declarative, composable constraints

### Web app
- **Role-Based Access Control**: `READER` / `WRITER` / `ADMIN` / `TEACHER` roles over stateless JWT auth — see [Authentication & Roles](#authentication--roles)
- **Group-Course Management**: Manage which courses each student group takes (`group_course` table) directly in the Groups tab
- **Bilingual UI**: Full English/Spanish localization (`react-i18next`), per-user preferred-language setting
- **Current Term Label**: A free-text term/period label (e.g. "Fall 2026"), admin-editable, shown in the header for every role
- **Write-Activity Audit Log**: Every successful create/update/delete is logged automatically (who, what, when) and viewable by admins in Settings
- **Teacher Workload View**: A live workload column (assigned hours vs. weekly max) on the Teachers tab
- **Delete-Usage Guards**: Deleting a course/group that still has scheduled blocks is blocked with a specific error instead of silently cascading
- **Search, Pagination, Toasts, Confirm Dialogs**: Client-side search and pagination on list views, toast notifications on save/delete, and a styled confirm dialog replacing native browser confirms
- **Admin-Triggered Solver Runs**: Start the engine, generate blocks, and view compliance-snapshot PDFs, all from the Settings tab
- **Comprehensive Reporting**: Console analysis, PDF outputs, and database query support

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
│       ├── service/                     # BlockGenerationService, ExcelImportService, ...
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
│   ├── schema_block_scheduling.sql      # Canonical PostgreSQL schema (block-based only)
│   ├── migrations/                      # Incremental migrations applied on top of the
│   │                                     # schema (app_user/RBAC, room capacity, school_term,
│   │                                     # audit log, TEACHER role, ...)
│   ├── datasets/                        # Demo and production seed data
│   └── views/                           # create_views.sql - reporting views
└── scripts/                             # run-engine.sh, run-reporter.sh helper scripts
```

## Build Instructions

### Prerequisites
- **Java 17+**
- **Maven 3.8+**
- **PostgreSQL 12+** (for database integration)

### Database Setup

1. **Create database:**
```bash
createdb -U mancilla school_schedule
```

2. **Load schema:**
```bash
psql -U mancilla -d school_schedule -f database/schema_block_scheduling.sql
```

3. **Load dataset** (choose one):

**Demo dataset** (smaller, for testing):
```bash
psql -U mancilla -d school_schedule -f database/datasets/load_demo_data_blocks.sql
```

**Production dataset** (real-world data with 484 assignments):
```bash
psql -U mancilla -d school_schedule -f database/datasets/load_final_dataset_blocks.sql
```

4. **Create views** (optional, for reporting):
```bash
psql -U mancilla -d school_schedule -f database/views/create_views.sql
```

### Configure Database Connection

All modules (engine, reporter, and web) read the database connection from
environment variables. Set them once in your shell (or export them in your
deploy environment):

```bash
export DB_URL=jdbc:postgresql://localhost:5432/school_schedule
export DB_USER=mancilla
export DB_PASSWORD=your_password_here
```

If unset, they default to `jdbc:postgresql://localhost:5432/school_schedule`,
user `mancilla`, and an empty password.

### Compile
```bash
mvn clean compile
```

### Run Block-Based Solver
```bash
mvn -pl engine exec:java -Dexec.mainClass="com.example.MainBlockSchedulingApp"
```

This will:
1. Load data from PostgreSQL database
2. Run the solver (up to 5 minutes, or 2 minutes without improvement, or until a feasible `0hard/0soft` score is reached)
3. Save the solved assignments back to PostgreSQL
4. Display constraint violation analysis

PDF reports are produced separately by the **reporter** module (see below), which
reads the persisted schedule from the database.

### Run the Engine as a Worker

The engine is a one-shot batch job (load → solve → save), not a daemon, so it
runs well on demand or on a schedule. It writes the solved assignments back to
PostgreSQL, and the web API serves those same rows — so a completed run surfaces
automatically via `GET /api/schedule/view` (and related endpoints).

Connection settings come from environment variables (`DB_URL`, `DB_USER`,
`DB_PASSWORD`); the process exits non-zero on failure, so cron/orchestrators can
detect failed runs.

**1. Build the fat jar**
```bash
mvn -pl engine -am -DskipTests package
# -> engine/target/scheduler-engine-1.0.0.jar (self-contained)
```

**2. Run on demand** (via the helper script)
```bash
DB_URL=jdbc:postgresql://<host>:5432/school_schedule \
DB_USER=<user> DB_PASSWORD=<pass> \
./scripts/run-engine.sh
```

**3. Schedule with cron** (e.g. nightly at 02:00; log to a file)
```cron
0 2 * * *  DB_URL=jdbc:postgresql://localhost:5432/school_schedule DB_USER=mancilla DB_PASSWORD=secret /opt/schedule/scripts/run-engine.sh >> /var/log/schedule-engine.log 2>&1
```

**4. Run as a container** (see `engine/Dockerfile`)
```bash
docker build -f engine/Dockerfile -t scheduler-engine .
docker run --rm \
  --cpus=6 --memory=12g --memory-reservation=8g \
  -e DB_URL=jdbc:postgresql://<host>:5432/school_schedule \
  -e DB_USER=<user> -e DB_PASSWORD=<pass> \
  scheduler-engine
```
The solver is the most resource-hungry component: give it **at least 4 CPUs /
8 GB and at most 6 CPUs / 12 GB**. In plain `docker run`, `--cpus`/`--memory` set
the ceiling and `--memory-reservation` the memory floor (a CPU floor needs an
orchestrator — e.g. Kubernetes `requests: {cpu: "4", memory: 8Gi}` /
`limits: {cpu: "6", memory: 12Gi}`). The image defaults
`JAVA_OPTS="-XX:MaxRAMPercentage=75.0"`, so the heap tracks `--memory`
deterministically (~6 GB at `--memory=8g`, ~9 GB at `--memory=12g`).

The image is a batch worker (runs once and exits); schedule it with an ECS
scheduled task, a Kubernetes CronJob, or a host cron invoking `docker run`.

### Run the Reporter as a Worker

The reporter is a separate one-shot batch job that generates the three PDF
reports. It reconstructs the solved schedule from PostgreSQL (the same rows the
engine persists), recomputes constraint violations, and writes the PDFs into
`REPORTER_OUTPUT_DIR`. It shares only the database with the engine, so run it on
demand or right after an engine run.

**1. Build the fat jar**
```bash
mvn -pl reporter -am -DskipTests package
# -> reporter/target/scheduler-reporter-1.0.0.jar (self-contained)
```

**2. Run on demand** (via the helper script; PDFs land in `REPORTER_OUTPUT_DIR`)
```bash
DB_URL=jdbc:postgresql://<host>:5432/school_schedule \
DB_USER=<user> DB_PASSWORD=<pass> \
REPORTER_OUTPUT_DIR=./reports \
./scripts/run-reporter.sh
```
This writes:
- `calendario-incumplimientos.pdf` - Constraint violations
- `calendario-por-maestro.pdf` - Schedule by teacher
- `calendario-por-grupo.pdf` - Schedule by student group

**3. Run as a container** (see `reporter/Dockerfile`)
```bash
docker build -f reporter/Dockerfile -t scheduler-reporter .
docker run --rm \
  --cpus=1 --memory=1g \
  -e DB_URL=jdbc:postgresql://<host>:5432/school_schedule \
  -e DB_USER=<user> -e DB_PASSWORD=<pass> \
  -v "$PWD/reports:/work" \
  scheduler-reporter
```
The image defaults `JAVA_OPTS="-XX:MaxRAMPercentage=75.0"`, so the JVM heap sizes
to a fixed fraction of the `--memory` limit (e.g. ~768 MB at `--memory=1g`)
rather than to the whole host. Set `--cpus`/`--memory` to pin CPU and RAM, or
override `JAVA_OPTS` to tune further.

### Run All Services with Docker Compose

`docker-compose.yml` wires up all three images with declarative CPU/memory
limits and reservations (engine 4–6 CPU / 8–12 GB, web 1–2 CPU / 0.5–1 GB,
reporter 1 CPU / 0.5–1 GB). The engine and reporter are one-shot batch jobs
(under the `batch` profile); web is a long-running service.

```bash
# Long-running REST API on :8080
docker compose up -d web

# One-shot solve (writes the schedule to the database)
docker compose run --rm engine

# One-shot PDF generation (PDFs land in ./reports)
docker compose run --rm reporter
```
Point the services at your database with `DB_URL`/`DB_USER`/`DB_PASSWORD` (they
default to the host Postgres via `host.docker.internal`). Compose V2 applies the
`limits`; the `reservations` (floors, including the CPU floor) are fully enforced
only under an orchestrator (Swarm / Kubernetes).

### Run Tests
```bash
mvn test
```

## Web UI

A React + Spring Boot web interface manages the full problem — teachers, courses (including
dual room requirements and custom block templates), rooms, student groups (including which
courses each takes), and course block assignments — plus admin functions (users, timeslots,
current-term label, audit log, admin-triggered solver runs, Excel import, PDF reports) without
touching the database directly. See [Features](#features) and
[Authentication & Roles](#authentication--roles) for the full capability and role breakdown.

### 1. Start the Backend (Spring Boot REST API)
```bash
mvn -pl web spring-boot:run
```
Runs on `http://localhost:8080`, using the PostgreSQL database configured in `src/main/resources/application.properties` (defaults to `school_schedule`/`mancilla`; override with `DB_URL`/`DB_USER`/`DB_PASSWORD` env vars).

**Or run the backend as a container** (see `web/Dockerfile`)
```bash
docker build -f web/Dockerfile -t scheduler-web .
docker run --rm \
  --cpus=2 --memory=1g \
  -p 8080:8080 \
  -e DB_URL=jdbc:postgresql://<host>:5432/school_schedule \
  -e DB_USER=<user> -e DB_PASSWORD=<pass> \
  -e CORS_ALLOWED_ORIGINS=https://app.example.com \
  -e JWT_SECRET=<random-32+-byte-secret> \
  -e ADMIN_BOOTSTRAP_PASSWORD=<initial-admin-password> \
  scheduler-web
```
Unlike the engine and reporter (one-shot batch workers), the web image is a
long-running service on port 8080. It defaults `JAVA_OPTS="-XX:MaxRAMPercentage=75.0"`,
so the JVM heap tracks the `--memory` limit deterministically (e.g. ~768 MB at
`--memory=1g`). Set `--cpus`/`--memory` to pin resources.

### 2. Start the Frontend (React + Vite)
```bash
cd web-ui
npm install   # first time only
npm run dev
```
Runs on `http://localhost:3000` and proxies all `/api/*` requests to the backend on port 8080 (see `web-ui/vite.config.js`).

Both must be running at the same time — start the backend first, then the frontend, and open `http://localhost:3000` in a browser.

See [`web-ui/README.md`](web-ui/README.md) and [`WEB_UI_SETUP.md`](WEB_UI_SETUP.md) for the full feature list, REST API endpoint reference, and troubleshooting.

### Authentication & Roles

The web app is protected by **stateless JWT authentication** with role-based access
control. Every `/api/**` endpoint (except `POST /api/auth/login`) requires a valid
`Authorization: Bearer <token>` header; the SPA redirects to `/login` on a `401`.

**Roles** (one per user):

| Role      | Permissions                                                         |
|-----------|---------------------------------------------------------------------|
| `READER`  | `GET` only (view schedule and all entities).                        |
| `WRITER`  | `READER` + create/update/delete on domain entities (`POST/PUT/DELETE`). |
| `ADMIN`   | `WRITER` + full access, including user management under `/api/admin/**`. |
| `TEACHER` | Scoped to itself only: `GET /api/schedule/view/me` (its own schedule, resolved server-side via `app_user.teacher_id`), `GET /api/auth/me`, `GET /api/term`, and `PUT /api/auth/preferred-language`. **Not** included in the general `GET /api/**` rule the other three roles share, so it cannot read broader domain data (teachers, courses, rooms, the full schedule, etc.). An admin links a `TEACHER` account to a teacher record from the Users tab. |

The React UI hides create/edit/delete buttons for `READER`s and collapses the
nav down to just "My Schedule" for `TEACHER`s; the backend enforces the rules
regardless of the UI.

**Auth endpoints:**
- `POST /api/auth/login` — exchange `{username, password}` for `{token, username, role, expiresIn, preferredLanguage}`.
- `GET /api/auth/me` — echo the current authenticated `{username, role, preferredLanguage}`. Reachable by every role, including `TEACHER` (every page load calls this to restore the session).
- `PUT /api/auth/preferred-language` — update your own UI language (`en`/`es`); any authenticated role, including `TEACHER`.

**Security configuration** (env vars; see `web/src/main/resources/application.properties`):

| Variable                   | Default                    | Purpose                                                        |
|----------------------------|----------------------------|----------------------------------------------------------------|
| `JWT_SECRET`               | dev-only insecure default  | HMAC (HS256) signing secret, **≥32 bytes**. Override in prod.   |
| `JWT_TTL_SECONDS`          | `28800` (8h)               | Issued-token lifetime.                                          |
| `ADMIN_BOOTSTRAP_USERNAME` | `admin`                    | Username for the seeded initial admin.                         |
| `ADMIN_BOOTSTRAP_PASSWORD` | *(blank)*                  | When `app_user` is empty, seeds this ADMIN. No user if blank.  |

**First-time setup:** apply the users migration, then boot the web app with
`ADMIN_BOOTSTRAP_PASSWORD` set to seed the first admin:

```bash
psql -U mancilla -d school_schedule -f database/migrations/add_app_users.sql
ADMIN_BOOTSTRAP_PASSWORD=change-me JWT_SECRET=$(openssl rand -hex 32) mvn -pl web spring-boot:run
```

Log in at `http://localhost:3000/login`, then create additional users via the admin
API. Additional users can be created through the `ADMIN`-only `/api/admin/**`
endpoints.

**Upgrading an existing database:** a fresh `schema_block_scheduling.sql` load
already includes everything below; existing databases need each incremental
migration applied once, in order:

```bash
psql -U mancilla -d school_schedule -f database/migrations/add_app_users.sql                    # app_user table + RBAC (idempotent)
psql -U mancilla -d school_schedule -f database/migrations/add_user_preferred_language.sql       # per-user UI language
psql -U mancilla -d school_schedule -f database/migrations/add_room_capacity_and_group_size.sql  # optional room.capacity / student_group.student_count
psql -U mancilla -d school_schedule -f database/migrations/add_school_term.sql                   # current-term label
psql -U mancilla -d school_schedule -f database/migrations/add_schedule_audit_log.sql             # write-activity audit log
psql -U mancilla -d school_schedule -f database/migrations/add_teacher_role.sql                   # TEACHER role + app_user.teacher_id
```

### 3. Expose It on the Internet (temporary sharing)

⚠️ **Authentication is enabled, but treat public exposure with care** — the JWT
secret must be a strong random value (`JWT_SECRET`) and the bootstrap admin password
must be changed. Only share with people you trust, and tear it down when done.

**One-time setup:**
```bash
brew install cloudflared
```

**Each time you want a public link:**
```bash
# Terminal 1 — backend
mvn -pl web spring-boot:run

# Terminal 2 — frontend
cd web-ui && npm run dev

# Terminal 3 — tunnel
cloudflared tunnel --url http://localhost:3000
```
`cloudflared` prints a random `https://*.trycloudflare.com` URL — that's your public link. It proxies straight through Vite's `/api` proxy to the backend, so no extra tunnel is needed for port 8080.

`web-ui/vite.config.js` has `allowedHosts: true` so Vite accepts the tunnel's hostname (Vite otherwise rejects requests with an unrecognized `Host` header).

**To shut everything down:**
```bash
pkill -f "cloudflared tunnel"
pkill -f "spring-boot:run"
pkill -f "vite"
```

## Edge Cases and Special Scenarios

### 1. **Computer Center Capacity Overload**
**Problem**: 107 hours of CC demand vs 105 hours of capacity (2h overload)
**Impact**: Mathematically impossible to avoid all room conflicts
**Solution**: Greenfield distribution strategy minimizes conflicts:
- **CC 1 - "SOFTWARE DEVELOPMENT CENTER"** (36h, 102.9%): All TPROG courses + 2APRO CULTURA DIGITAL II
- **CC 2 - "CYBERSECURITY CENTER"** (36h, 102.9%): All TCS courses + 2ATCS CULTURA DIGITAL II
- **CC 3 - "AI & INNOVATION CENTER"** (35h, 100%): All TIA courses + 2ATIA CULTURA DIGITAL II + 6 general groups

**Implementation**: `preferred_room_name` field in `course_block_assignment` table guides solver to preferred CC

### 2. **Dual Room Requirements**
**Problem**: Some courses need different room types for different hours (e.g., 4h lab + 4h classroom)
**Solution**: `course_room_requirement` table with multiple entries per course:
```sql
-- Example: Course 15 needs 4h in lab + 4h in standard room
INSERT INTO course_room_requirement (course_id, room_type, hours_required)
VALUES ('15', 'laboratorio', 4), ('15', 'estándar', 4);
```
**Impact**: Solver creates separate assignments with different `satisfies_room_type` values

### 3. **Custom Block Decomposition**
**Problem**: Default block patterns don't match real-world scheduling needs
**Solution**: `course_block_template` table for explicit block patterns:
```sql
-- Example: CULTURA DIGITAL II for 2APRO uses 1×2-hour block (not default 2×1-hour)
INSERT INTO course_block_template (course_id, group_id, block_index, block_length, room_type, preferred_room_name)
VALUES ('6', '2APRO', 0, 2, 'centro de cómputo', 'CC 1');
```
**Special Cases**: 10 courses use custom templates (CULTURA DIGITAL II variants, REALIZA ANALISIS FISICOS, etc.)

### 4. **2PM Constraint for Non-Standard Rooms**
**Problem**: Labs, workshops, and computer centers must be freed by 14:00 for maintenance
**Constraint**: SOFT constraint (weight 10) penalizes blocks ending after 14:00 in non-standard rooms — relaxed from HARD so a 3-4 hour block can still extend past 14:00 when every other option scores worse, rather than making the schedule infeasible
**Impact**: Strongly discourages, but doesn't forbid, late-ending specialized-room blocks
**Current Status**: 6 violations in latest run (blocks ending at 14:00 or later)

### 5. **BASICAS vs Non-BASICAS Block Patterns**
**Problem**: BASICAS courses need flexibility; specialized courses need consolidation
**Solution**: Different block decomposition strategies:
- **BASICAS** (PENSAMIENTO MATEMATICO, INGLES, etc.): Multiple 1-hour blocks for maximum flexibility
- **Non-BASICAS** (TPIAL, TCS, TIA, etc.): Larger blocks (3-4 hours) to minimize fragmentation

**Constraint**: Maximum 1 block/day for BASICAS, 2 blocks/day for non-BASICAS

### 6. **Pinned Assignments**
**Problem**: Some course blocks must use specific teacher/room/timeslot combinations
**Solution**: `pinned` field in `course_block_assignment` table (32 pinned assignments)
**Examples**:
- REALIZA ANALISIS FISICOS (Course 15): 8 pinned blocks for 2APIA/2BPIA in LQ 1
- REALIZA ANALISIS MICROBIOLOGICOS (Course 16): 6 pinned blocks in LMICRO
- HUMANIDADES III (Course 49): 8 pinned blocks across 4 groups

**Impact**: Reduces solver search space but may create impossible constraints if pinned assignments conflict

### 7. **Teacher Availability Per Day**
**Problem**: Teachers have different availability on different days
**Solution**: `availability_per_day` JSONB field in `teacher` table:
```json
{
  "MONDAY": [7, 8, 9, 10, 11, 12, 13],
  "TUESDAY": [7, 8, 9, 10, 11, 12, 13, 14],
  "WEDNESDAY": [9, 10, 11, 12, 13],
  ...
}
```
**Constraint**: HARD constraint validates teacher is available for ALL hours in assigned block

### 8. **Teacher Workload Limits**
**Problem**: Teachers have different weekly hour limits (e.g., 20h, 30h, 40h)
**Solution**: `max_hours_per_week` field in `teacher` table
**Constraint**: SOFT constraint (weight 5) sums total assigned hours per teacher and penalizes exceeding the limit
**Edge Case**: Constraint counts `course.required_hours_per_week` per assignment (not block length)

### 9. **Analyzer vs Solver Score Mismatch**
**Problem**: Analyzer was missing "Non-standard rooms must be freed by 2pm" constraint
**Impact**: Reported score didn't match actual violations
**Solution**: Added missing constraint to `BlockScheduleAnalyzer.java` (lines 172-185, 448-462)
**Lesson**: Always verify analyzer constraints match `SchoolConstraintProvider.java` constraints

### 10. **Template vs Assignment Propagation**
**Problem**: `preferred_room_name` in templates wasn't being copied to assignments
**Impact**: 63 hours of CC courses had no preferred CC guidance
**Solution**: Added Step 5 to `load_final_dataset_blocks.sql` to set `preferred_room_name` based on course component:
```sql
UPDATE course_block_assignment SET preferred_room_name = 'CC 2' WHERE course.component = 'TCS';
UPDATE course_block_assignment SET preferred_room_name = 'CC 3' WHERE course.component = 'TIA';
```

## Production Dataset

### Overview
The production dataset (`load_final_dataset_blocks.sql`) contains real-world scheduling data:
- **399 course block assignments** across 20 student groups
- **32 pinned assignments** (locked teacher/room/timeslot combinations)
- **42 teachers** with qualifications and per-day availability
- **62 courses** (BASICAS and specialized courses)
- **32 rooms** (6 types: estándar, laboratorio, taller, taller electromecánica, taller electrónica, centro de cómputo)
- **130 block timeslots** (1-4 hour blocks, Monday–Friday, 7:00–15:00)

### Teachers (42 total)
- Qualified for specific courses (e.g., SUSANA LEONOR for microbiological analysis)
- Per-day availability maps (hours available per DayOfWeek)
- Weekly hour limits (`maxHoursPerWeek`)
- Examples: YARA ESTHER, ITZEL, LETICIA, ALFREDO, YASIR

### Courses (62 total)
- **BASICAS courses** (component='BASICAS'): Use 1-hour blocks for flexibility
  - Examples: PENSAMIENTO MATEMATICO II (4 hours), INGLES II (3 hours), TUTORIAS II (1 hour)
- **Specialized courses** (TPIAL, TPFV, etc.): Use larger blocks (3-4 hours)
  - Examples: REALIZA ANALISIS FISICOS (8 hours), TRANSFORMA CARNE (11 hours)

### Rooms (32 total)
- **Standard classrooms**: AULA 1–23 (various buildings)
- **Specialized labs**: LQ 1 (chemistry), LMICRO (microbiology), LPIAL (food processing)

### Student Groups (20 total)
- **Semester 2**: 9 groups (2APIA, 2BPIA, 2CPIA, etc.)
- **Semester 4**: 6 groups (4APIA, 4BPIA, etc.)
- **Semester 6**: 5 groups (6APIA, 6AARH, 6APRO, 6ATEC, 6ATEM)

### Block Timeslots (130 total)
- Monday–Friday, 7:00–15:00 hours
- Variable block lengths: 1, 2, 3, or 4 consecutive hours
- Examples: Monday 7:00-10:00 (3h), Tuesday 9:00-11:00 (2h), Thursday 12:00-15:00 (3h)

### Pinned Assignments (32 total)
Locked assignments for specific courses:
- **Course 15** (REALIZA ANALISIS FISICOS): 8 pinned blocks (2APIA, 2BPIA) - LQ 1 lab
- **Course 16** (REALIZA ANALISIS MICROBIOLOGICOS): 6 pinned blocks (2APIA, 2BPIA) - LMICRO lab
- **Course 33** (TRANSFORMA FRUTAS Y VERDURAS): 2 pinned blocks (4APIA) - LPIAL lab
- **Course 34** (TRANSFORMA CARNE): 3 pinned blocks (4APIA) - LPIAL lab
- **Course 38** (REALIZA MANTENIMIENTO A SISTEMAS ELECTRICOS): 1 pinned block (4ATEC) - TE 1 workshop
- **Course 40** (IMPLEMENTA BASE DE DATOS RELACIONALES): 2 pinned blocks (4APRO) - CC 1
- **Course 41** (IMPLEMENTA BASE DE DATOS NO RELACIONALES): 2 pinned blocks (4APRO) - CC 1
- **Course 53** (ANALISIS FISICOS/QUIMICOS/MICROBIOLOGICOS): 2 pinned blocks (6APIA) - LQ 1 lab
- **Course 54** (TRANSFORMA CEREALES): 2 pinned blocks (6APIA) - LPIAL lab
- **Course 59** (INSTALA SISTEMAS ELECTRONICOS): 1 pinned block (6ATEC) - TE 1 workshop
- **Course 60** (DISEÑA APLICACIONES MOVILES): 1 pinned block (6APRO) - CC 3
- **Course 61** (IMPLEMENTA APLICACIONES MOVILES): 2 pinned blocks (6APRO) - CC 1

### Custom Block Templates (10 special cases)
Courses with explicit block decomposition patterns:
- **Course 6** (CULTURA DIGITAL II): 3 templates for 2APRO (CC 1), 2ATCS (CC 2), 2ATIA (CC 3)
- **Course 15** (REALIZA ANALISIS FISICOS): 2 templates for 2APIA, 2BPIA (2+3+3 pattern)
- **Course 16** (REALIZA ANALISIS MICROBIOLOGICOS): 2 templates for 2APIA, 2BPIA (2+2+2+2 pattern)
- **Course 19** (CODIFICA SOFTWARE): 1 template for 2APRO (4+2+4 pattern)
- **Course 20** (DISEÑA SOFTWARE): 1 template for 2APRO (4+2 pattern)
- **Course 21** (IMPLEMENTA SOFTWARE): 1 template for 2APRO (4+2 pattern)
- **Course 33** (TRANSFORMA FRUTAS Y VERDURAS): 1 template for 4APIA (4+2+2 pattern)
- **Course 34** (TRANSFORMA CARNE): 1 template for 4APIA (4+4+3 pattern)
- **Course 38** (REALIZA MANTENIMIENTO): 1 template for 4ATEC (2+2+2+2 pattern)
- **Course 40** (IMPLEMENTA BD RELACIONALES): 1 template for 4APRO (4+4+2 pattern)
- **Course 41** (IMPLEMENTA BD NO RELACIONALES): 1 template for 4APRO (4+2 pattern)
- **Course 53** (ANALISIS FISICOS/QUIMICOS/MICROBIOLOGICOS): 1 template for 6APIA (4+2 pattern)
- **Course 54** (TRANSFORMA CEREALES): 1 template for 6APIA (4+2 pattern)
- **Course 59** (INSTALA SISTEMAS ELECTRONICOS): 1 template for 6ATEC (2+2+2+2 pattern)
- **Course 60** (DISEÑA APLICACIONES MOVILES): 1 template for 6APRO (4 pattern)
- **Course 61** (IMPLEMENTA APLICACIONES MOVILES): 1 template for 6APRO (4+2 pattern)

## Solution Output

### Score Format
`XhardYsoft`
- **X** = number of hard violations (0 = feasible)
- **Y** = accumulated soft penalty (lower is better)

### Example Run Output (Production Dataset - Historical)

> This capture predates the current 9-hard/8-soft constraint set described in
> [Constraints](#constraints) above (it's missing "Course blocks must be
> consecutive" as HARD and "Room capacity should fit group size" as SOFT, and
> still lists "Prefer course blocks consecutive" as SOFT). Kept as an
> illustration of the output format and the CC-overload tradeoff discussed
> below, not as a live number.

```
=== Block-Based School Schedule Solver ===
Loading data from PostgreSQL database...

Loaded 42 teachers
Loaded 62 courses
Loaded 32 rooms
Loaded 130 block timeslots
Loaded 20 groups
Loaded 399 course block assignments
Loaded 32 pinned block assignments

Solving... (24-25 minutes with optimizations)

=== Solved Schedule ===
Score: -11hard/-1230soft
Solving time: 24 minutes

=== Hard Constraint Violations (by rule) ===
- Block length must match timeslot length: 0
- Teacher must be qualified: 0
- Teacher must be available for entire block: 0
- No teacher double-booking: 2
- No room double-booking: 4
- Room type must satisfy course requirement: 0
- Group cannot have two courses at same time: 5
- Maximum 2 blocks per course per group per day: 0

=== Detailed Violations ===
Teacher double-bookings (2):
  - CESAR: Thu 10-12 (2h) teaching 4A TEM ELECTROMECANICA and 4A TEM ELECTROMECANICA

Room double-bookings (4):
  - AULA 1: Mar 7-9 (2h) hosting 6A TEC ELECTRONICA courses
  - TE 1: Mar 7-9 (2h) hosting 6A TEC ELECTRONICA courses

Group schedule conflicts (5):
  - 6A TEC ELECTRONICA: Mar 7-9 (2h) has overlapping courses

=== Soft Constraint Violations (by rule) ===
- Prefer non-standard rooms freed by 2pm: 6 violations (weight 5)
- Teacher exceeds max hours per week: 0 violations (weight 5)
- Minimize group idle gaps: 180 violations (weight 3 per hour)
- Prefer course blocks consecutive on same day: 89 violations (weight 3)
- Prefer block's specified room: 12 violations (weight 3)
- Prefer group's preferred room: 8 violations (weight 2)
- Minimize teacher idle gaps: 245 violations (weight 2 per hour)
- Minimize teacher building changes: 15 violations (weight 1)

PDF reports written to:
  - calendario-incumplimientos.pdf
  - calendario-por-maestro.pdf
  - calendario-por-grupo.pdf
```

**Current Challenges**:
- **Computer Center Overload**: 107h demand vs 105h capacity (2h unavoidable overload → 2 hard violations minimum)
- **Remaining Hard Violations**: 11 violations (mostly related to CC overload cascade effects)
- **Mathematical Limit**: Best achievable score is -2hard/-500soft (CC overload unavoidable)

## Recent Changes

### August 15, 2026 - Room Capacity, Current Term, Audit Log, and TEACHER Role

- **New Soft Constraint: Room Capacity Should Fit Group Size** (weight 4)
  - Optional `room.capacity` / `student_group.student_count` columns; the constraint only
    fires when both are set for a given assignment, so existing data needs no backfill
  - Mirrored in `BlockScheduleAnalyzer`; total soft constraints now 8 (was 7)
  - New `RoomCapacityConstraintTest` covering null/exact/over-capacity cases directly
- **Current Term Label** — a free-text label (e.g. "Fall 2026") shown in the header for
  every role, admin-editable in Settings; purely organizational, never read by the solver
- **Write-Activity Audit Log** — a `HandlerInterceptor` (`AuditLogInterceptor`) logs every
  successful `POST`/`PUT`/`DELETE` to `/api/**` automatically, with an admin-only "Recent
  Activity" viewer in Settings — no existing controller was touched to add this
- **New Role: `TEACHER`** — scoped to viewing only its own schedule
  (`GET /api/schedule/view/me`, resolved server-side from `app_user.teacher_id`) rather
  than the general domain-read access every other role has. Linked from the Users tab; the
  web UI collapses its nav to just "My Schedule" (new `MySchedule.jsx`) and routes it there
  instead of the full `Schedule` view.
  - **Bug caught during live verification**: the first cut also excluded `TEACHER` from
    `GET /api/auth/me`, which every page load calls to restore the session — a `TEACHER`
    account got silently logged out on every reload. Fixed with a dedicated
    `SecurityConfig` matcher, plus regression tests.
- Also from this pass: a live teacher-workload column on the Teachers tab (assigned hours
  vs. weekly max, computed client-side from existing data) and timeslots grouped by day in
  Settings instead of one flat table.

### August 15, 2026 - UI Navigation & Usability Overhaul

Prompted by a UI navigation/usability review; implemented the low-risk findings:
- Client-side search boxes on Teachers/Courses/Rooms/Groups, and client-side pagination
  (`usePagination` hook + `Pagination` component, `web-ui/src/ui/Pagination.jsx`) on all
  five list views — chosen over backend `Pageable` since the data is already fetched in one
  request at this app's scale
- Toast notifications (`ToastContext`) on every create/update/delete, replacing silence
- A styled, promise-based confirm dialog (`ConfirmContext`) replacing all native
  `window.confirm()` calls
- Admin-only nav items (Settings, Users) collapsed into a single "Admin ▾" dropdown
- Header title links home; the Courses edit form's three stacked cards (Details, Room
  Requirements, Block Templates) became tabs with live count badges
- Deduplicated the `ROOM_TYPES` constant (previously redeclared independently in three
  components) into `web-ui/src/constants.js`

### August 15, 2026 - Dual Room Requirements, Block Templates, and Group-Course Management (Web UI)

The `course_room_requirement` and `course_block_template` tables existed in the schema but
had no API or UI — and `course_room_requirement` was confirmed dead data, loaded but never
consumed anywhere:
- Full CRUD API for both (`/api/courses/{courseId}/room-requirements`,
  `/api/courses/{courseId}/block-templates`) plus `/api/groups/{groupId}/courses` for
  `group_course` management, the last remaining gap from a UI/backend gap assessment
- `BlockGenerationService` rewired to a three-tier precedence:
  `course_block_template` (if any applicable) > `course_room_requirement` (if any) >
  the legacy single `CourseEntity.roomRequirement` field, ported from the reference
  `generate_course_blocks()` PL/pgSQL function
- Delete-usage guards on Course/Group (blocks deleting an entity that still has scheduled
  blocks, instead of silently cascading) and a Component-field autocomplete
- Web UI: Room Requirements and Block Templates management inside the Courses tab; a
  Group-Courses card in Groups; a "dual" badge on courses with dual room requirements

### August 15, 2026 - Localization, User Management, and Compliance Snapshot Reporting

- Full English/Spanish localization of the web UI (`react-i18next`), including a per-user
  preferred-language setting persisted server-side and applied on login
- Admin CRUD for application users (the `Users` tab), with last-admin and self-delete guards
- Admin-only compliance-snapshot PDFs, generated automatically after each engine run and
  versioned by run (distinct from the WRITER-triggered "Generate Reports" PDFs) — the
  reporter module's output was split accordingly

### August 10-12, 2026 - Web App Expansion: Multi-Module Split, RBAC, Import/Reports/Solver Wiring

- **Split into a Maven multi-module project**: `engine` (Timefold + JDBC, no Spring),
  `reporter` (PDF generation, depends on `engine` as a library), and `web` (Spring Boot
  REST API) — integrating only through the shared PostgreSQL database
- **JWT-based RBAC** (`SecurityConfig`): stateless auth with `ADMIN`/`WRITER`/`READER`
  roles, an env-var-driven bootstrap admin, and role-scoped `/api/**` authorization rules
- Wired the web UI up to the engine/reporter: admin-triggered solver runs, admin-triggered
  block generation, Excel import of base problem data, and PDF report generation/download
- Docker images for all three modules plus a `docker-compose.yml` with declarative
  CPU/memory limits; environment-driven DB connection and CORS configuration throughout
- Settings tab (admin-only) for timeslot management; dropdown-based Assignments form
  replacing free-text ID fields

### July 25 - August 5, 2026 - API Hardening and Correctness Fixes

- DTO validation and global error handling added across Teacher, Course, Room, Group, and
  Assignment controllers (previously unvalidated free-text input)
- Fixed a null-teacher/null-room phantom double-booking bug in the solver, with integration
  and drift-guard regression tests
- Migrated `course.semester` from free text to a validated integer (1-12)
- Aligned the analyzer, tests, and docs with the block-based-only constraint model
  (hour-based scheduling fully removed by this point)

### February 14, 2026 Evening - Student Schedule Quality Enhancement

- **New Soft Constraint: Minimize Group Idle Gaps** (weight 3 per hour)
  - Added high-priority constraint to reduce gaps in student group schedules
  - Penalizes idle hours between classes for better student experience
  - Higher weight (3) than teacher idle gaps (2) prioritizes student schedule quality
  - Updated `SchoolConstraintProvider`, `BlockScheduleAnalyzer`, and tests
  - Total soft constraints: 7 → 8
  - Comprehensive test coverage ensures constraint consistency

### February 14, 2026 PM - Quick Wins Implementation & Optimization

- **Constraint Relaxation**
  - **Relaxed 2pm constraint from HARD to SOFT** (weight 5)
    - Removes 6 hard violations while maintaining strong preference
    - Allows 3-4 hour blocks in specialized rooms to extend past 14:00 when necessary
    - Updated constraint name: "Prefer non-standard rooms freed by 2pm"
    - Updated `SchoolConstraintProvider` and `BlockScheduleAnalyzer`

- **Solver Configuration Optimization**
  - Reduced `lateAcceptanceSize` from 10,000 to 3,000 (~20% faster solving)
  - Added step count limits: Phase 1 (10,000), Phase 3 (5,000) to prevent infinite loops
  - Increased `unimprovedMinutesSpentLimit` from 5 to 8 minutes for better exploration
  - Expected solve time: 24-25 minutes (down from 30 minutes)

- **Constraint Weight Centralization**
  - Created `SchoolConstraintProvider.Weights` static class
  - All soft constraint weights now centralized for easy tuning
  - Weights: NON_STANDARD_ROOMS_2PM(5), TEACHER_MAX_HOURS(5), COURSE_BLOCKS_CONSECUTIVE(3),
    PREFER_SPECIFIED_ROOM(3), PREFER_GROUP_ROOM(2), TEACHER_IDLE_GAPS_PER_HOUR(2), BUILDING_CHANGES(1)

- **Database Validation**
  - Added 5 comprehensive validation checks to `load_final_dataset_blocks.sql`
  - Validates: course hours match, pinned assignments complete, CC capacity, block/timeslot match, summary stats
  - Provides immediate feedback with ✅/⚠️ indicators on data load
  - Automatically detects data inconsistencies before solver runs

- **Current Status**
  - Score: -11hard/-1230soft (6 hard violations eliminated vs previous -17hard)
  - All data validation checks passing
  - Solve time improved by ~20%
  - Best possible score: -2hard/-500soft (CC overload unavoidable)

### February 14, 2026 AM - Computer Center Distribution & Analyzer Fixes

- **Computer Center Greenfield Distribution Strategy**
  - Designed optimal distribution for 107h demand across 105h capacity (3 computer centers)
  - CC 1 "SOFTWARE DEVELOPMENT CENTER" (36h, 102.9%): All TPROG courses + 2APRO CULTURA DIGITAL II
  - CC 2 "CYBERSECURITY CENTER" (36h, 102.9%): All TCS courses + 2ATCS CULTURA DIGITAL II
  - CC 3 "AI & INNOVATION CENTER" (35h, 100%): All TIA courses + 2ATIA CULTURA DIGITAL II + 6 general groups
  - Added Step 5 to `load_final_dataset_blocks.sql` to set `preferred_room_name` for all CC courses

- **Analyzer Bug Fixes**
  - **Bug #1**: Fixed SOFT constraints being reported as HARD violations
    - Moved "Prefer course blocks consecutive" from HARD to SOFT (weight 3)
    - Moved "Prefer group's preferred room" from HARD to SOFT (weight 2)
    - Moved "Minimize teacher building changes" from HARD to SOFT (weight 1)
  - **Bug #2**: Added missing "Non-standard rooms must be freed by 2pm" HARD constraint
    - Added to `analyzeHardConstraintViolations()` (lines 172-185)
    - Added to `analyzeHardConstraintViolationsDetailed()` (lines 448-462)

- **Documentation Updates**
  - Comprehensive README update with all 10 hard constraints and 5 soft constraints
  - Added "Edge Cases and Special Scenarios" section documenting 10 major edge cases
  - Updated production dataset statistics (399 assignments, 32 pinned)
  - Documented custom block templates and dual room requirements

- **Current Status**
  - Score: -17hard/-1291soft (11 actual violations + 6 "2pm constraint" violations)
  - CC conflicts reduced from 5 to 3 (40% improvement)
  - Teacher conflicts reduced from 3 to 2 (33% improvement)
  - Mathematical limit: Best possible score likely -10hard to -15hard given 2h CC overload

### February 2026 - Block-Based Scheduling Migration

- **Complete Migration to Block-Based Scheduling**
  - Removed all hour-based scheduling code (`CourseAssignment`, `Timeslot`, etc.)
  - Implemented `CourseBlockAssignment` with multi-hour consecutive blocks (1-4 hours)
  - Created `BlockTimeslot` domain class (day + start_hour + length_hours)
  - Updated all constraints to work with block overlaps and availability checking

- **PostgreSQL Database Integration**
  - Created `schema_block_scheduling.sql` with block-based tables only
  - Implemented `DataLoader` for loading data from PostgreSQL
  - Created database views for reporting and analysis
  - Added support for pinned assignments via database

- **Production Dataset Translation**
  - Translated `load_final_dataset.sql` to block-based format
  - Created `load_final_dataset_blocks.sql` with 399 course block assignments
  - Implemented dual room requirements architecture (multiple room types per course)
  - Created custom block templates for 10 special cases
  - 32 pinned assignments for critical course-teacher-room-timeslot combinations

- **Block Decomposition Strategy**
  - BASICAS courses: Multiple 1-hour blocks for maximum flexibility
  - Non-BASICAS courses: Larger blocks (3-4 hours) to minimize fragmentation
  - Custom templates for special cases (CULTURA DIGITAL II, REALIZA ANALISIS FISICOS, etc.)

- **Reporting & Analysis**
  - Updated `PdfReporter` for block-based schedules
  - Created `BlockScheduleAnalyzer` for constraint violation analysis
  - Generated three PDF reports: violations, by-teacher, by-group

### January 2, 2026

- **Timefold 1.29.0 Validation & Fixes**
  - Fixed syntax errors and updated imports for Timefold 1.29.0 API
  - All tests pass (`mvn test` returns BUILD SUCCESS)

### November 2025

- **Domain Model Refactor**
  - `Teacher` with stable `id`, per-day availability map, and `maxHoursPerWeek`
  - `Course` with `id` and `requiredHoursPerWeek`
  - Excel template generation and PDF reporting

## Architecture

### Technology Stack
- **Java 17** — Modern language features and performance
- **Timefold Solver 1.29.0** — Constraint Streams API for declarative constraint modeling
- **PostgreSQL 12+** — Database for storing courses, teachers, rooms, and assignments
- **Maven** — Build automation and dependency management
- **Apache PDFBox** — PDF report generation
- **HardSoftScore** — Two-level scoring (hard feasibility, soft quality)

### Domain Model (Block-Based)
- **`CourseBlockAssignment`** — @PlanningEntity representing a multi-hour block
- **`BlockTimeslot`** — Multi-hour timeslot (day + start_hour + length_hours)
- **`Teacher`** — With qualifications, per-day availability, and max hours per week
- **`Course`** — With required hours per week and room requirements
- **`Room`** — With type (standard/lab) and building
- **`Group`** — Student group with assigned courses and optional preferred room
- **`SchoolSchedule`** — @PlanningSolution holding all problem facts and planning entities

### Constraint Implementation
- **Timefold Constraint Streams** — Declarative, composable constraints
- **Block Overlap Detection** — Custom logic for detecting overlapping multi-hour blocks
- **Availability Checking** — Validates teacher availability for entire block duration
- **Pinning Support** — @PlanningPin annotation for locking assignments
- **No-arg Constructors** — Required by Timefold for reflection
- **@PlanningVariable** — Decision variable: `blockTimeslot` (teacher and room pre-assigned from DB)
- **@PlanningId** — Unique identifier for entity comparison

### Solver Configuration
- **Construction Heuristic** — Greedy initialization phase
- **Local Search** — Iterative improvement (Tabu Search, Simulated Annealing)
- **Termination Conditions** (local-search phase, see `solverConfig.xml`):
  - Best score limit: `0hard/0soft`
  - Time limit: 5 minutes (`minutesSpentLimit`)
  - Unimproved limit: 2 minutes without improvement (`unimprovedMinutesSpentLimit`)

## Known Limitations

1. **Room Capacity is Opt-In and Soft** — `room.capacity`/`student_group.student_count` are optional and, even when set, only produce a *soft* penalty (weight 4), not a hard block — a room can still be overbooked if every other option scores worse
2. **Pre-assigned Teachers** — Teachers are pre-assigned from database; solver only assigns timeslots
3. **Pre-assigned Rooms** — Rooms are pre-assigned from database; solver only assigns timeslots
4. **Fixed Block Lengths** — Block lengths are determined by course hours and component type (BASICAS vs non-BASICAS)
5. **No Multi-Teacher Courses** — Each course block is assigned to exactly one teacher
6. **Soft Constraint Scaling** — Pairwise soft constraints scale as O(n²); may need optimization for very large datasets
7. **No Calendar/Term Dates** — `BlockTimeslot` is a recurring weekly template (day-of-week + hour), not tied to actual calendar dates; there's no holiday/exception-date support and no way to run separate schedules per term (the "current term" label is a display-only string, not a scheduling boundary)

## Future Enhancements

- [ ] Dynamic teacher/room assignment (currently pre-assigned from database)
- [x] Room capacity constraints based on student group size (soft, opt-in — see Known Limitations)
- [ ] Teacher workload balancing across weeks
- [ ] Student preferences for elective courses
- [ ] Mandatory lunch break constraints (e.g., 12:00-13:00)
- [ ] Rest period constraints (minimum gap between blocks for teachers)
- [ ] Multi-week scheduling patterns
- [ ] Integration with calendar systems (iCal/Google Calendar export)
- [x] Web UI for viewing and editing schedules (see [Web UI](#web-ui) section)
- [ ] Real-time constraint violation feedback during manual edits

## Testing & Validation

### Database Queries for Validation

**Check total assignments:**
```sql
SELECT COUNT(*) FROM course_block_assignment;
```

**Check pinned assignments:**
```sql
SELECT COUNT(*) FILTER (WHERE pinned=TRUE) AS pinned_count
FROM course_block_assignment;
```

**View teacher schedules:**
```sql
SELECT * FROM v_teacher_schedule ORDER BY teacher_id, day_of_week, start_hour;
```

**View group schedules:**
```sql
SELECT * FROM v_group_schedule ORDER BY group_id, day_of_week, start_hour;
```

**Check for constraint violations:**
```sql
-- Teacher double-booking
SELECT teacher_id, day_of_week, start_hour, COUNT(*)
FROM course_block_assignment cba
JOIN block_timeslot bt ON cba.block_timeslot_id = bt.id
GROUP BY teacher_id, day_of_week, start_hour
HAVING COUNT(*) > 1;
```

### Running Diagnostics
The solver automatically analyzes constraint violations and displays them in the console output and PDF reports.

## Contributing

To modify constraints or data:
1. **Edit constraints**: Modify `engine/src/main/java/com/example/solver/SchoolConstraintProvider.java`, and keep `engine/src/main/java/com/example/analysis/BlockScheduleAnalyzer.java` in sync — `ConstraintConsistencyTest` will fail the build if they drift
2. **Edit database schema**: Update `database/schema_block_scheduling.sql` (fresh-install shape) and add a corresponding file under `database/migrations/` for existing databases (see [Upgrading an existing database](#authentication--roles))
3. **Edit dataset**: Modify `database/datasets/load_final_dataset_blocks.sql`
4. **Reload data**: Run `psql -U mancilla -d school_schedule -f database/datasets/load_final_dataset_blocks.sql`
5. **Test changes**: Run `mvn test` (all three modules) and `mvn -pl engine exec:java -Dexec.mainClass="com.example.MainBlockSchedulingApp"`
6. **Edit the web API or UI**: See [Project Structure](#project-structure) for where each concern lives (`web/.../controller`, `entity`, `dto`, `security`; `web-ui/src/components`, `api.js`, `i18n/{en,es}.json`)

## License

This project is provided as-is for educational and scheduling purposes.

## References

- [Timefold Solver Documentation](https://timefold.ai/)
- [Constraint Streams Guide](https://docs.timefold.ai/timefold-solver/latest/use-cases-and-examples)
- School Scheduling Problem (classical OR problem)
