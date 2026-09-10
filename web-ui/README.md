# Schedule Maker Web UI

A React + Vite single-page app for managing the full scheduling problem — teachers, courses
(including dual room requirements and custom block templates), rooms, student groups, and
course block assignments — plus scheduler functions (solver runs, constraint weights) and
admin functions (users, timeslots, current-term label, audit log, Excel import/export, PDF
reports), all through the Spring Boot REST API in [`../web`](../web). See the root
[README.md](../README.md) for backend setup, architecture, and the full feature/constraint
list, and [WEB_UI_SETUP.md](../WEB_UI_SETUP.md) for the full REST API endpoint reference and
RBAC table.

## Prerequisites

- **Node.js 18+** and **npm**
- **Backend API** running on `http://localhost:8080` — `mvn -pl web spring-boot:run` from the
  repository root (see the root README's [Web UI](../README.md#web-ui) section for first-time
  setup, including seeding the initial admin account)

## Quick Start

### 1. Install Dependencies

```bash
npm install
```

### 2. Start the Development Server

```bash
npm run dev
```

Opens on **http://localhost:3000** by default (Vite tries the next available port if 3000 is
taken, and prints whichever port it used).

### 3. Open in Browser and Log In

```
http://localhost:3000
```

Log in with an account created via the backend's admin bootstrap or `Users` tab (see root
README).

## Available Scripts

- `npm run dev` - Start development server with hot reload
- `npm run build` - Build for production
- `npm run preview` - Preview production build locally

## Features

### Navigation

- Top-level nav ordered by frequency of use: Timetable, Assignments, a "Setup" dropdown
  (Teachers/Courses/Rooms/Groups), a "Tools" dropdown (Reports, Course Coverage, Teacher
  Availability, and — `WRITER`/`SCHEDULER`/`ADMIN` only — Import/Export and Run Validation), a
  "Scheduler" link (`SCHEDULER`/`ADMIN` only — Solver + Constraint Weights, its own page rather
  than nested under Admin), and an Admin dropdown (Settings/Users, `ADMIN` only). Each dropdown
  shows which child page is active even while closed (e.g. "Setup · Rooms"). An `ADMIN` account
  sees both "Scheduler" and "Admin" side by side, not a merged menu. The nav label reads
  "Timetable" (renamed 2026-09-07, English only - see below) even though the underlying
  component/route/API/i18n-key namespace all still say "schedule," to avoid it sitting right
  next to "Scheduler" as a one-letter, easy-to-mis-click pair.
- Username, language switcher, and logout are consolidated into a single profile dropdown.
- `TEACHER` accounts see only "My Timetable" — every other nav item and route is hidden client-
  side and blocked server-side.

### Timetable View

- **Grid view**: calendar-style schedule by day and hour, with group/teacher/run filters and
  pinned-assignment highlighting; below a phone-width breakpoint it switches to a stacked
  day-by-day list instead (a table this wide isn't readable scrolled horizontally on a phone).
  (The old flat List view was removed — it duplicated the same data with no filtering/sorting
  advantage.) A pinned card's marker carries the same who/when/how tooltip as the Assignments
  table (added 2026-09-07) — "Pinned by _x_ on _date_", or "pinned automatically by the system"
  for a `BlockGenerationService`-pinned block; blank-of-history for a block pinned before
  provenance tracking, and absent entirely for a past run's snapshot. The marker itself is 🤖
  for a system pin and 📌 for a person's, so the two read apart without hovering.
- **Interactive editing** (`SCHEDULER`/`ADMIN`, opt-in): a confirm-protected "Enable schedule
  editing" toggle (off by default, resets every visit) makes each block clickable, opening a
  move/pin editor that validates the candidate change live against hard constraints
  (`POST /api/assignments/{id}/validate-move`) before Save is allowed — a currently
  SOFT-configured constraint shows as a non-blocking warning instead. Room/teacher reassignment
  is also available (same two roles), going through a separate, non-live-validated save path.
  Only available on the live schedule, not a past run. (`WRITER` had this access briefly,
  2026-09-06 to 2026-09-07; it moved to `SCHEDULER` when that role was introduced.)
- **Violations panel** (`SCHEDULER`/`ADMIN` only): a collapsible summary of every hard/soft
  constraint violation persisted for the selected run (`GET /api/schedule/violations`), grouped
  by constraint, each one linking to and highlighting the exact grid card(s) it's about when
  clicked — previously only visible by downloading the PDF report. Narrowed from
  READER-visible to `SCHEDULER`/`ADMIN` on 2026-09-07 (not shown to `WRITER`, `READER`, or
  `TEACHER` at all - the panel doesn't render and the underlying fetch doesn't fire for them).
  A Show: All/Hard only/Soft only filter (added 2026-09-07) controls both the panel's own
  sections and the grid's card badges/borders - purely a display filter over already-fetched
  data, not a change to how a constraint's severity is configured (that's the separate
  Constraint Weights mechanism, see below).
- **My Timetable** (`TEACHER` role): the same grid (read-only, no editing toggle), scoped
  server-side to the logged-in teacher via `GET /api/schedule/view/me`.

### Entity Management (CRUD, search, pagination)

#### Teachers
- Qualifications, per-day availability, `maxHoursPerWeek`, an optional required-room override
  (this teacher's blocks always use it, ahead of the group's curated room range, when the room
  type fits), and a live workload column (assigned hours vs. weekly max, computed client-side)

#### Courses
- Legacy single `roomRequirement` field, plus per-course **Room Requirements** (dual room-
  type/hour splits, e.g. 4h lab + 4h standard) and **Block Templates** (explicit, hand-
  authored block decomposition, optionally scoped to one group) — shown as count-badged tabs
  alongside Details, with an inline note on the block-template > room-requirement > legacy-
  field precedence

#### Rooms
- Type, building, and an optional `capacity` (paired with a group's `studentCount` for a soft
  room-capacity-vs-group-size constraint)

#### Student Groups
- Optional `studentCount`, a **Room Ranges** card curating which rooms this group may use per
  room type (a type with no rows is unrestricted; one row fixes the group to that single room;
  2+ rows lets the solver pick freely among them), and a Group-Courses card managing which
  courses each group takes — each course row flags when no teacher is qualified for it, and
  lets you pick a teacher directly: pre-assigned before blocks exist (applied automatically the
  next time blocks are generated), or applied straight to a course's existing blocks once they do

#### Course Block Assignments
- Group, course, block length, teacher, timeslot, room, pinned status; filter by All /
  Assigned / Unassigned / Pinned
- The pinned marker is 🤖 for a system pin, 📌 for a person's; its tooltip shows who pinned it
  and when (added 2026-09-07) - a person's username, or "pinned automatically by the system"
  for a block `BlockGenerationService` pinned itself at generation time (see
  `tryPinExclusiveTeacherBlocks` in the root README)

### Tools (any authenticated role; Import/Export and Run Validation need `WRITER`/`SCHEDULER`/`ADMIN`)

#### Reports
- `WRITER`/`SCHEDULER`/`ADMIN`-triggered PDF generation, versioned by run (past runs aren't
  overwritten); any authenticated role (except `TEACHER`) can browse and download past runs

#### Course Coverage
- For every group/course pair, how many hours are actually scheduled against how many are
  required — Complete / Partial / Not Scheduled at a glance

#### Teacher Availability
- Every teacher's declared weekly availability condensed into one row per teacher (hour
  ranges per day), instead of paging through each teacher's own record

#### Import / Export
- **Import**: upload an `.xlsx` workbook to upsert Teachers/Courses/Rooms/Groups/
  Group_Courses (`WRITER`/`SCHEDULER`/`ADMIN`)
- **Export**: download the current data in the exact same layout Import expects, for a full
  export → edit → re-import round trip (any role except `TEACHER`)

#### Run Validation (`WRITER`/`SCHEDULER`/`ADMIN`)
- Runs `PreSolveValidator` by itself, independent of actually solving — a fast up-front report
  on the same ten blocking checks (plus one advisory warning) the solver itself runs before
  every solve

### Scheduler (`SCHEDULER`/`ADMIN`), 2 tabs
Its own nav entry and page (added 2026-09-07), not nested under Admin — the two things
`SCHEDULER` actually has access to, split out of what used to be part of Settings:
- **Solver**: trigger solver runs, with optional random-seed control
- **Constraint Weights**: per-constraint soft-weight overrides, plus switching one of the four
  severity-configurable HARD constraints to SOFT

### Settings (`ADMIN`), 9 tabs
- **Term**: current-term label (a free-text string like "Fall 2026", shown in the header for
  every role)
- **Compliance Snapshots**: the PDF report auto-generated after each engine run
- **Generate Blocks**: admin-triggered block generation from course/group data, surfacing any
  shape adjustments it made
- **Block Rules**: per-course-component preferred block size, max blocks per day, and margin
  (`component_block_rule`), read by "Generate Blocks" and the solver instead of being
  hardcoded — a component with no rule falls back to a size-2 / max-2-per-day default
- **Semester Hour Limits**: per-semester "must/should finish by hour X" configuration
  (HARD or SOFT), replacing an earlier hardcoded semester-1-only rule
- **Calendar**: calendar exceptions (holidays, exam days, half-days) — record-keeping only,
  not yet read by block generation or the solver
- **Timeslots**: timeslot management, grouped by day
- **Database Backups**: export/import a full database snapshot
- **Audit Log**: write-activity log viewer (who/what/when for every successful write)

### Users (`ADMIN`)
- CRUD for application users and roles (`READER`/`WRITER`/`SCHEDULER`/`ADMIN`/`TEACHER`), with
  a linked-teacher picker for `TEACHER` accounts, plus last-admin and self-delete guards

### Cross-cutting
- Full English/Spanish localization (`react-i18next`), with a per-user preferred-language
  setting persisted server-side
- Toast notifications on save/delete, and a styled, promise-based confirm dialog replacing
  native `window.confirm()`
- Client-side search and pagination on every list view
- A loading spinner (one shared `.loading` CSS class, used everywhere)

## API Configuration

API calls are configured through environment variables (Vite). See `.env.example`
for the full list. Only variables prefixed with `VITE_` are exposed to the
client bundle, and they are baked in at **build time**, not read at runtime.

| Variable | Default | Purpose |
| --- | --- | --- |
| `VITE_API_BASE_URL` | `/api` | Base URL the SPA uses for API calls. |
| `VITE_DEV_PROXY_TARGET` | `http://localhost:8080` | Dev-only: backend the Vite proxy forwards `/api` to. |

### Development

`npm run dev` loads `.env.development`. `/api` requests are proxied to
`VITE_DEV_PROXY_TARGET` (default `http://localhost:8080`), so no CORS setup is
needed locally.

### Production

`npm run build` loads `.env.production`. Two deployment shapes are supported:

- **Same origin / reverse proxy** (default): serve the built SPA behind a proxy
  that forwards `/api` to the backend. Leave `VITE_API_BASE_URL=/api`.
- **Separate origin**: set `VITE_API_BASE_URL` to the backend's public API URL
  at build time, e.g. `VITE_API_BASE_URL=https://api.example.com/api`. The
  backend must also allow the SPA's origin via CORS — set
  `CORS_ALLOWED_ORIGINS` on the backend (e.g. `https://app.example.com`), which
  maps to the `app.cors.allowed-origins` property in `application.properties`.

## Project Structure

```
web-ui/
├── src/
│   ├── components/            # One component per tab/route
│   │   ├── Schedule.jsx       # Schedule viewer (grid + mobile list), interactive editing
│   │   ├── AssignmentMoveEditor.jsx  # Move/pin editor opened from the Schedule grid
│   │   ├── MySchedule.jsx     # TEACHER-role self-service schedule view (grid + mobile list)
│   │   ├── ScheduleEntryCard.jsx     # One schedule block's card, shared by both views above
│   │   ├── Teachers.jsx       # Teacher management + workload column
│   │   ├── Courses.jsx        # Course management (Details/Room Requirements/Block Templates tabs)
│   │   ├── Rooms.jsx          # Room management
│   │   ├── Groups.jsx         # Student group management + Group-Courses + Room Ranges
│   │   ├── Assignments.jsx    # Course block assignment management
│   │   ├── Reports.jsx        # PDF report generation/download
│   │   ├── CourseCoverage.jsx       # Required vs. scheduled hours per group/course
│   │   ├── TeacherAvailability.jsx  # Every teacher's weekly availability, condensed
│   │   ├── PreSolveValidation.jsx   # Standalone "Run Validation" tools page
│   │   ├── Import.jsx         # Excel import + export
│   │   ├── SchedulerSettings.jsx  # SCHEDULER/ADMIN: thin shell rendering 2 tabs (Solver,
│   │   │                      # ConstraintWeights) - its own page, not nested under Settings
│   │   ├── Settings.jsx       # ADMIN-only: thin shell rendering the other 9 tabs below (always-mounted, hidden via CSS)
│   │   ├── settings/          # One self-contained component per tab (Term, Solver,
│   │   │                      # ComplianceSnapshots, GenerateBlocks, BlockRules, ConstraintWeights,
│   │   │                      # SemesterHourLimits, Calendar, Timeslots, DatabaseBackups, AuditLog) -
│   │   │                      # SolverTab/ConstraintWeightsTab are mounted by SchedulerSettings.jsx,
│   │   │                      # the other 9 by Settings.jsx
│   │   ├── Users.jsx          # Admin: application user CRUD
│   │   └── Login.jsx          # Login form
│   ├── auth/                  # AuthContext, ProtectedRoute/AdminRoute/SchedulerRoute/WriteRoute,
│   │                          # AdminOnly/ScheduleEditOnly/WriteOnly
│   ├── ui/                    # Shared ToastContext, ConfirmContext, Pagination
│   ├── i18n/                  # en.json / es.json (react-i18next)
│   ├── api.js                 # API service (Axios)
│   ├── App.jsx                # Routing + nav (Setup/Tools/Scheduler/Admin/Profile dropdowns)
│   ├── main.jsx                # React entry point
│   └── index.css              # Global styles + design tokens
├── index.html                 # HTML template
├── vite.config.js             # Vite configuration (dev proxy, port 3000)
├── package.json                # Dependencies
└── README.md                  # This file
```

## Troubleshooting

### Backend Port (8080) Already in Use

```bash
lsof -ti:8080 | xargs kill -9
```

### Frontend Port (3000) Already in Use

```bash
lsof -ti:3000 | xargs kill -9
```

Or change the port in `vite.config.js`:

```javascript
export default defineConfig({
  server: {
    port: 3001,  // Change to any available port
    // ...
  }
})
```

### Backend Connection Issues

**Error**: API calls failing or CORS errors

**Solution**: Ensure the backend is running on port 8080:

```bash
# From the repository root directory
mvn -pl web spring-boot:run
```

Verify it's up (requires a token — see [WEB_UI_SETUP.md](../WEB_UI_SETUP.md) for the login
command):

```bash
curl -H "Authorization: Bearer $TOKEN" http://localhost:8080/api/teachers
```

### Dependencies Installation Issues

If `npm install` fails, try:

```bash
# Clear npm cache
npm cache clean --force

# Delete node_modules and package-lock.json
rm -rf node_modules package-lock.json

# Reinstall
npm install
```

## Development Tips

### Hot Reload

The development server supports hot module replacement (HMR). Changes to React components will automatically reload in the browser.

### API Testing

You can test API endpoints directly using curl (requires a bearer token — see
[WEB_UI_SETUP.md](../WEB_UI_SETUP.md)):

```bash
curl -H "Authorization: Bearer $TOKEN" http://localhost:8080/api/teachers
curl -H "Authorization: Bearer $TOKEN" http://localhost:8080/api/schedule/view
curl -H "Authorization: Bearer $TOKEN" http://localhost:8080/api/courses
```

### Browser DevTools

Use browser developer tools to:
- Inspect network requests to the API
- Debug React components
- View console logs for errors

## Next Steps

1. **Start the backend** — see the root [README.md](../README.md#web-ui)
2. **Install dependencies**: `npm install`
3. **Start the frontend**: `npm run dev`
4. **Open browser**: http://localhost:3000
5. **View and edit** schedules and entities

For the full REST API reference and RBAC table, see [WEB_UI_SETUP.md](../WEB_UI_SETUP.md) in
the project root.
