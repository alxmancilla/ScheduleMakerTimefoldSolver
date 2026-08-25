# Web UI Setup and Usage Guide

## Overview

The Schedule Maker Web UI provides a complete interface for viewing and editing schedules,
teachers, courses (including dual room requirements and custom block templates), rooms,
student groups, course block assignments, and admin functions (users, timeslots, current-term
label, audit log, admin-triggered solver runs, Excel import/export, PDF reports). It consists
of:

- **Backend**: Spring Boot REST API (Java 17), one of four Maven modules — see the root
  [README.md](README.md) for the full multi-module architecture (`common`/`engine`/`reporter`/`web`)
- **Frontend**: React + Vite SPA (`web-ui/`)
- **Database**: PostgreSQL

This file is the detailed setup + full REST API reference. For architecture, constraints, the
engine/reporter batch workers, Docker/cron deployment, and the feature/changelog history, see
the root [README.md](README.md). For frontend-only development details (project structure,
`.env` config, troubleshooting), see [web-ui/README.md](web-ui/README.md).

## Architecture

### Backend (Spring Boot)
- **Port**: 8080
- **Base URL**: `http://localhost:8080/api`
- **Technology Stack**:
  - Spring Boot 3.2.1
  - Spring Security 6.2 (stateless JWT / OAuth2 Resource Server)
  - Spring Data JPA + Hibernate
  - PostgreSQL JDBC Driver

### Frontend (React)
- **Port**: 3000 (default; Vite tries the next available port if taken)
- **Base URL**: `http://localhost:3000`
- **Technology Stack**:
  - React 18.2
  - React Router 6.20
  - Axios 1.6
  - Vite 7.3
  - react-i18next (English/Spanish localization)

## Prerequisites

1. **Java 17** - For running the Spring Boot backend
2. **Node.js 18+** and **npm** - For running the React frontend
3. **PostgreSQL 12+** - Database must be running with the `school_schedule` database loaded
4. **Maven 3.8+** - For building the backend

## Setup Instructions

### 1. Database Setup

Ensure PostgreSQL is running and the schema is loaded:

```bash
# Check if PostgreSQL is running and the schema is loaded
psql -U mancilla -d school_schedule -c "SELECT COUNT(*) FROM teacher;"
```

If not set up yet, create the database and load the canonical block-based schema plus a
dataset (see the root README's [Database Setup](README.md#database-setup) for the demo vs.
production dataset choice):

```bash
createdb -U mancilla school_schedule
psql -U mancilla -d school_schedule -f database/schema_block_scheduling.sql
psql -U mancilla -d school_schedule -f database/datasets/load_final_dataset_blocks.sql
```

Reporting views are created automatically as part of the schema load.

### 2. Backend Setup

The backend is already configured in `web/pom.xml` and
`web/src/main/resources/application.properties`.

**Apply the users migration** (first time only) — this creates the `app_user` table backing
RBAC; it is **not** part of the schema file itself:

```bash
psql -U mancilla -d school_schedule -f database/migrations/add_app_users.sql
```

Six more migrations are already folded into a fresh `schema_block_scheduling.sql` load —
apply them only if your database predates that feature:
```bash
psql -U mancilla -d school_schedule -f database/migrations/add_room_capacity_and_group_size.sql   # room.capacity / student_group.student_count
psql -U mancilla -d school_schedule -f database/migrations/add_school_term.sql                    # current-term label
psql -U mancilla -d school_schedule -f database/migrations/add_schedule_audit_log.sql              # write-activity audit log
psql -U mancilla -d school_schedule -f database/migrations/add_component_block_rules.sql           # component_block_rule (Settings > Block Rules)
psql -U mancilla -d school_schedule -f database/migrations/add_group_course_default_teacher.sql    # group_course.default_teacher_id
psql -U mancilla -d school_schedule -f database/migrations/add_teacher_required_room.sql           # teacher.required_room_name
```
Two more manage the `app_user` table itself and are only needed if your `app_user` table
predates them (`add_app_users.sql` above already includes both for new installs):
```bash
psql -U mancilla -d school_schedule -f database/migrations/add_user_preferred_language.sql  # only if app_user predates preferred_language
psql -U mancilla -d school_schedule -f database/migrations/add_teacher_role.sql              # only if app_user predates the TEACHER role/teacher_id
```

**Start the Spring Boot backend** (set a strong `JWT_SECRET` and, on first run, an
`ADMIN_BOOTSTRAP_PASSWORD` to seed the initial admin — `app_user` must be empty for the
bootstrap to fire):

```bash
# From the project root directory
ADMIN_BOOTSTRAP_PASSWORD=change-me JWT_SECRET=$(openssl rand -hex 32) \
  mvn -pl web spring-boot:run
```

The backend starts on `http://localhost:8080`.

**Verify the backend is running** (every `/api/**` endpoint except `POST /api/auth/login`
requires a bearer token; log in first, then call with the returned token):

```bash
TOKEN=$(curl -s -X POST http://localhost:8080/api/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"admin","password":"change-me"}' | sed 's/.*"token":"\([^"]*\)".*/\1/')
curl -H "Authorization: Bearer $TOKEN" http://localhost:8080/api/teachers
```

### 3. Frontend Setup

**Install dependencies**:

```bash
cd web-ui
npm install
```

**Start the React development server**:

```bash
npm run dev
```

The frontend starts on `http://localhost:3000` and proxies `/api/*` requests to the backend on
port 8080 (see `web-ui/vite.config.js`).

**Open in browser**:

```
http://localhost:3000
```

## Features

See [web-ui/README.md](web-ui/README.md#features) for the full per-tab feature breakdown
(navigation, Schedule/My Schedule, Teachers/Courses/Rooms/Groups/Assignments, Reports, Import/
Export, Settings, Users) and the root README's [Features](README.md#features) section for the
solver-side capabilities.

## Authentication & Roles

The API is protected by **stateless JWT authentication**. Log in to obtain a token, then send
it as `Authorization: Bearer <token>` on every request. The SPA stores the token, attaches it
automatically via an Axios interceptor, and redirects to `/login` on a `401`.

**Roles** (one per user), enforced server-side in `SecurityConfig`:

| Role      | Permissions                                                                 |
|-----------|------------------------------------------------------------------------------|
| `READER`  | `GET` only (view schedule and all entities).                                 |
| `WRITER`  | `READER` + create/update/delete on domain entities (`POST`/`PUT`/`DELETE`).  |
| `ADMIN`   | `WRITER` + full access, including user management and everything else under `/api/admin/**`. |
| `TEACHER` | Scoped to itself only: `GET /api/schedule/view/me`, `GET /api/auth/me`, `GET /api/term`, and `PUT /api/auth/preferred-language`. **Not** included in the general `GET /api/**` rule the other three roles share, so it cannot read broader domain data (teachers, courses, rooms, the full schedule, Excel export, etc.). An admin links a `TEACHER` account to a teacher record from the Users tab. |

The React UI hides create/edit/delete controls for `READER`s and collapses the entire nav to
"My Schedule" for `TEACHER`s, but the backend (`SecurityConfig`) is the source of truth
regardless of what the UI shows.

## REST API Reference

Every endpoint is under `/api`. Unless noted, `GET` requires `READER`/`WRITER`/`ADMIN`
(not `TEACHER`), and `POST`/`PUT`/`DELETE` require `WRITER`/`ADMIN`. Everything under
`/api/admin/**` requires `ADMIN` regardless of HTTP method.

### Auth (`/api/auth`)
| Method & Path | Role | Description |
|---|---|---|
| `POST /auth/login` | public | Exchange `{username, password}` for `{token, username, role, expiresIn, preferredLanguage}` |
| `GET /auth/me` | any (incl. `TEACHER`) | Current authenticated `{username, role, preferredLanguage}` |
| `PUT /auth/preferred-language` | any (incl. `TEACHER`) | Update your own UI language (`en`/`es`) |

### Teachers (`/api/teachers`)
| Method & Path | Description |
|---|---|
| `GET /teachers` | All teachers |
| `GET /teachers/{id}` | One teacher |
| `GET /teachers/search?query=` | Search by name |
| `POST /teachers` | Create |
| `PUT /teachers/{id}` | Update |
| `DELETE /teachers/{id}` | Delete |

A teacher's optional `requiredRoomName` is set via `POST`/`PUT` like any other field. Setting or
changing it also **backfills every existing non-pinned block already assigned to that teacher**
whose room type is compatible — not just future ones.

### Courses (`/api/courses`)
| Method & Path | Description |
|---|---|
| `GET /courses` | All courses |
| `GET /courses/{id}` | One course |
| `GET /courses/search?query=` | Search by name |
| `GET /courses/active` | Active courses only |
| `GET /courses/components` | Distinct `component` values (for autocomplete) |
| `GET /courses/with-room-requirements` | IDs of courses that have dual room requirements |
| `POST /courses` | Create |
| `PUT /courses/{id}` | Update |
| `DELETE /courses/{id}` | Delete (blocked if the course still has scheduled blocks) |

**Course Room Requirements** (`/api/courses/{courseId}/room-requirements`) — dual room
requirements, e.g. 4h lab + 4h standard:
| Method & Path | Description |
|---|---|
| `GET .../room-requirements` | List for a course |
| `POST .../room-requirements` | Create |
| `PUT .../room-requirements/{id}` | Update |
| `DELETE .../room-requirements/{id}` | Delete |

**Course Block Templates** (`/api/courses/{courseId}/block-templates`) — explicit block
decomposition, optionally scoped to one group:
| Method & Path | Description |
|---|---|
| `GET .../block-templates` | List for a course |
| `POST .../block-templates` | Create |
| `PUT .../block-templates/{id}` | Update |
| `DELETE .../block-templates/{id}` | Delete |

### Rooms (`/api/rooms`)
| Method & Path | Description |
|---|---|
| `GET /rooms` | All rooms |
| `GET /rooms/{name}` | One room |
| `GET /rooms/type/{type}` | Rooms of a given type |
| `GET /rooms/building/{building}` | Rooms in a given building |
| `POST /rooms` | Create |
| `PUT /rooms/{name}` | Update |
| `DELETE /rooms/{name}` | Delete |

### Student Groups (`/api/groups`)
| Method & Path | Description |
|---|---|
| `GET /groups` | All groups |
| `GET /groups/{id}` | One group |
| `GET /groups/search?query=` | Search by name |
| `POST /groups` | Create |
| `PUT /groups/{id}` | Update |
| `DELETE /groups/{id}` | Delete (blocked if the group still has scheduled blocks) |

**Group Courses** (`/api/groups/{groupId}/courses`) — which courses a group takes:
| Method & Path | Description |
|---|---|
| `GET .../courses` | List for a group |
| `POST .../courses` | Add `{courseName}` |
| `DELETE .../courses/{courseName}` | Remove |
| `PUT .../courses/{courseName}/default-teacher` | Pre-assign (or clear, with `{teacherId: null}`) a teacher for this pairing before blocks exist — validated against that teacher's qualifications, then applied automatically to every block `POST /admin/blocks/generate` creates for it; has no effect once blocks already exist |

**Group Room Ranges** (`/api/groups/{groupId}/room-ranges`) — a group's curated acceptable
rooms per room type, replacing the old single `preferred_room_name`; a type with no rows is
unrestricted, one row fixes the group to that single room, 2+ rows lets the solver pick freely
among them:
| Method & Path | Description |
|---|---|
| `GET .../room-ranges` | List for a group |
| `POST .../room-ranges` | Create `{roomType, roomName}` |
| `PUT .../room-ranges/{id}` | Update |
| `DELETE .../room-ranges/{id}` | Delete |

Setting/changing/removing a range row also **backfills every existing non-pinned block already
assigned to that group** whose `satisfiesRoomType` matches, but only when that type's range
resolves to exactly one room — a range of 2+ rooms has no single deterministic choice, so those
blocks are left for the next solve to decide among the (now on-disk) narrowed range instead.

### Assignments (`/api/assignments`)
| Method & Path | Description |
|---|---|
| `GET /assignments` | All assignments |
| `GET /assignments/{id}` | One assignment |
| `GET /assignments/group/{groupId}` | By group |
| `GET /assignments/teacher/{teacherId}` | By teacher |
| `GET /assignments/room/{roomName}` | By room |
| `GET /assignments/assigned` | Assigned blocks only |
| `GET /assignments/unassigned` | Unassigned blocks only |
| `GET /assignments/pinned` | Pinned blocks only |
| `POST /assignments` | Create |
| `PUT /assignments/{id}` | Update |
| `DELETE /assignments/{id}` | Delete |

`POST`/`PUT` apply one override automatically: if the submitted `teacherId` resolves to a
teacher with a `requiredRoomName` whose type fits this block's `satisfiesRoomType`, `roomName`
is forced to it regardless of what was submitted — matching "this teacher always uses this
room" even if a different room (e.g. one from the group's curated range) was sent in the request body.

### Schedule (`/api/schedule`)
| Method & Path | Role | Description |
|---|---|---|
| `GET /schedule/view` | `READER`+ | Full schedule |
| `GET /schedule/view/group/{groupId}` | `READER`+ | Schedule for one group |
| `GET /schedule/view/teacher/{teacherId}` | `READER`+ | Schedule for one teacher |
| `GET /schedule/view/room/{roomName}` | `READER`+ | Schedule for one room |
| `GET /schedule/view/me` | any (incl. `TEACHER`) | The logged-in `TEACHER`'s own schedule, resolved server-side via `app_user.teacher_id` |

### Timeslots
| Method & Path | Role | Description |
|---|---|---|
| `GET /timeslots`, `GET /timeslots/{id}` | `READER`+ | Read-only, for building the Assignments form |
| `GET`/`POST`/`PUT`/`DELETE /admin/timeslots...` | `ADMIN` | Full CRUD (Settings tab) |

### Component Block Rules (`/api/admin/component-block-rules`, `ADMIN`)
Per-course-component preferred block size and max blocks per day (Settings → Block Rules tab),
read by `BlockGenerationService` and the solver instead of being hardcoded. A component with no
row here falls back to a size-2 / max-2-per-day default in code.
| Method & Path | Description |
|---|---|
| `GET /admin/component-block-rules` | All configured rules |
| `PUT /admin/component-block-rules/{component}` | Upsert `{preferredBlockSize, maxBlocksPerDay}` (1-4 each) for a component |
| `DELETE /admin/component-block-rules/{component}` | Reset a component to the code default (idempotent) |

### Excel Import / Export (`/api/import`)
| Method & Path | Role | Description |
|---|---|---|
| `POST /import/excel` | `WRITER`/`ADMIN` | Upload an `.xlsx` workbook to upsert Teachers/Courses/Rooms/Groups/Group_Courses |
| `GET /import/excel` | `READER`+ | Download the current data in the same layout, as `schedule-export-<date>.xlsx` |

### Reports (`/api/reports`) — WRITER-triggered PDF runs
| Method & Path | Role | Description |
|---|---|---|
| `GET /reports` | `READER`+ | Past report runs (newest first), each with its own files |
| `GET /reports/status` | `READER`+ | Whether a report generation run is currently in progress |
| `GET /reports/{runId}/{filename}` | `READER`+ | Download one PDF |
| `POST /reports/generate` | `WRITER`/`ADMIN` | Kick off a new run |

### Admin-only (`/api/admin/**`, `ADMIN` role, any HTTP method)
| Method & Path | Description |
|---|---|
| `POST /admin/engine/run` | Start a solver run |
| `GET /admin/engine/status` | Whether a solver run is currently in progress |
| `POST /admin/blocks/generate` | (Re)generate course block assignments from course/group data |
| `GET /admin/reports` | Compliance-snapshot PDF runs (auto-generated after each engine run), distinct from the WRITER-triggered `/api/reports` runs |
| `GET /admin/reports/{runId}/{filename}` | Download one compliance-snapshot PDF |
| `GET /admin/users` | List application users |
| `POST /admin/users` | Create a user |
| `PUT /admin/users/{username}` | Update role/enabled/linked-teacher |
| `PUT /admin/users/{username}/password` | Reset a user's password |
| `DELETE /admin/users/{username}` | Delete a user (blocked on the last admin or self-delete) |
| `PUT /admin/term` | Update the current-term label |
| `GET /admin/audit-log` | The 200 most recent write-activity log entries |

### Current Term (`/api/term`)
| Method & Path | Role | Description |
|---|---|---|
| `GET /term` | any (incl. `TEACHER`) | Current term label (shown in the header for every role) |

## Troubleshooting

### Backend Issues

**Port 8080 already in use**:
```bash
lsof -ti:8080 | xargs kill -9
```

**Database connection error**:
- Check PostgreSQL is running: `pg_isready`
- Verify credentials in `web/src/main/resources/application.properties`
- Test connection: `psql -U mancilla -d school_schedule`

### Frontend Issues

**Port 3000 already in use**:
```bash
lsof -ti:3000 | xargs kill -9
```

**CORS errors**:
- Ensure backend is running on port 8080
- Check CORS configuration in `web/src/main/java/com/example/web/security/SecurityConfig.java` (or set `CORS_ALLOWED_ORIGINS`)

**401 Unauthorized / redirected to `/login`**:
- The token is missing, expired, or invalid — log in again
- On a fresh database, ensure `database/migrations/add_app_users.sql` ran and `ADMIN_BOOTSTRAP_PASSWORD` was set on first boot
- Ensure `JWT_SECRET` is stable across restarts (a changed secret invalidates existing tokens)

**403 Forbidden on an endpoint that should work**:
- Check the role table above — most likely a `READER` hitting a write endpoint, or a
  `TEACHER` hitting anything outside its four allowed endpoints (this is enforced
  server-side by design, not a bug)

**API calls failing**:
- Verify backend is running (see the login + bearer-token verify commands above)
- Check browser console for errors
- Verify proxy configuration in `web-ui/vite.config.js`

## Development

### Building for Production

**Backend** (produces a self-contained jar for the `web` module specifically — this is a
Maven multi-module project, so plain `mvn clean package` from the root won't produce a
runnable jar on its own):
```bash
mvn -pl web -am -DskipTests package
java -jar web/target/scheduler-web-1.0.0.jar
```
Or run it directly without packaging: `mvn -pl web spring-boot:run`. See the root README's
[Web UI](README.md#web-ui) section for the Docker image alternative.

**Frontend**:
```bash
cd web-ui
npm run build
```

The production build lands in `web-ui/dist/`.

### Code Structure

**Backend** (`web/src/main/java/com/example/web/`):
- `controller/` - REST controllers (one per resource — see the API reference above)
- `entity/` + `repository/` - JPA entities and Spring Data repositories
- `dto/` - Request/response DTOs with bean validation
- `security/` - `SecurityConfig` (JWT + RBAC), `AuditLogInterceptor`
- `service/` - `BlockGenerationService`, `ExcelImportService`, `ExcelExportService`,
  `EngineRunnerService`, `ReportRunnerService`, ...
- `exception/` - `GlobalExceptionHandler`
- `src/main/resources/application.properties` - Configuration

**Frontend**: see [web-ui/README.md](web-ui/README.md#project-structure).

## Next Steps

1. **Run the solver** to generate a schedule (`mvn -pl engine exec:java -Dexec.mainClass="com.example.MainBlockSchedulingApp"`, or trigger it from the Settings tab)
2. **View the schedule** in the Web UI
3. **Edit assignments** as needed (pin specific assignments)
4. **Re-run the solver** with pinned assignments
5. **Generate PDF reports** from the Reports tab, or **export to Excel** from Import/Export
