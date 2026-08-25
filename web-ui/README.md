# Schedule Maker Web UI

A React + Vite single-page app for managing the full scheduling problem — teachers, courses
(including dual room requirements and custom block templates), rooms, student groups, and
course block assignments — plus admin functions (users, timeslots, current-term label, audit
log, admin-triggered solver runs, Excel import/export, PDF reports), all through the Spring
Boot REST API in [`../web`](../web). See the root [README.md](../README.md) for backend
setup, architecture, and the full feature/constraint list, and
[WEB_UI_SETUP.md](../WEB_UI_SETUP.md) for the full REST API endpoint reference and RBAC table.

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

- Top-level nav ordered by frequency of use: Schedule, Assignments, Reports, a "Setup"
  dropdown (Teachers/Courses/Rooms/Groups), Import/Export (`WRITER`/`ADMIN`), and an Admin
  dropdown (Settings/Users, `ADMIN` only). Each dropdown shows which child page is active even
  while closed (e.g. "Setup · Rooms").
- Username, language switcher, and logout are consolidated into a single profile dropdown.
- `TEACHER` accounts see only "My Schedule" — every other nav item and route is hidden client-
  side and blocked server-side.

### Schedule View

- **Grid view**: calendar-style schedule by day and hour, with group/teacher filters and
  pinned-assignment highlighting. (The old flat List view was removed — it duplicated the
  same data with no filtering/sorting advantage.)
- **My Schedule** (`TEACHER` role): the same grid, scoped server-side to the logged-in
  teacher via `GET /api/schedule/view/me`.

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

### Reports
- `WRITER`/`ADMIN`-triggered PDF generation, versioned by run (past runs aren't overwritten);
  any authenticated role (except `TEACHER`) can browse and download past runs

### Import / Export
- **Import**: upload an `.xlsx` workbook to upsert Teachers/Courses/Rooms/Groups/
  Group_Courses (`WRITER`/`ADMIN`)
- **Export**: download the current data in the exact same layout Import expects, for a full
  export → edit → re-import round trip (any role except `TEACHER`)

### Settings (`ADMIN`)
- Block Rules: per-course-component preferred block size and max blocks per day
  (`component_block_rule`), read by "Generate Blocks" and the solver instead of being
  hardcoded — a component with no rule falls back to a size-2 / max-2-per-day default
- Timeslot management, grouped by day
- Current-term label (a free-text string like "Fall 2026", shown in the header for every role)
- Write-activity audit log viewer (who/what/when for every successful write)
- Admin-triggered solver run and block generation, with a compliance-snapshot PDF viewer

### Users (`ADMIN`)
- CRUD for application users and roles (`READER`/`WRITER`/`ADMIN`/`TEACHER`), with a linked-
  teacher picker for `TEACHER` accounts, plus last-admin and self-delete guards

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
│   │   ├── Schedule.jsx       # Schedule viewer (grid view only)
│   │   ├── MySchedule.jsx     # TEACHER-role self-service schedule view
│   │   ├── Teachers.jsx       # Teacher management + workload column
│   │   ├── Courses.jsx        # Course management (Details/Room Requirements/Block Templates tabs)
│   │   ├── Rooms.jsx          # Room management
│   │   ├── Groups.jsx         # Student group management + Group-Courses
│   │   ├── Assignments.jsx    # Course block assignment management
│   │   ├── Reports.jsx        # PDF report generation/download
│   │   ├── Import.jsx         # Excel import + export
│   │   ├── Settings.jsx       # Admin: block rules, timeslots, term, audit log, solver runs
│   │   ├── Users.jsx          # Admin: application user CRUD
│   │   └── Login.jsx          # Login form
│   ├── auth/                  # AuthContext, ProtectedRoute/AdminRoute/WriteRoute, AdminOnly/WriteOnly
│   ├── ui/                    # Shared ToastContext, ConfirmContext, Pagination
│   ├── i18n/                  # en.json / es.json (react-i18next)
│   ├── api.js                 # API service (Axios)
│   ├── App.jsx                # Routing + nav (Setup/Admin/Profile dropdowns)
│   ├── main.jsx                # React entry point
│   └── index.css              # Global styles
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
