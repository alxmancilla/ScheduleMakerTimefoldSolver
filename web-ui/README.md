# Schedule Maker Web UI

A React-based web interface for viewing and editing school schedules, teachers, courses, rooms, student groups, and course block assignments.

## Prerequisites

- **Node.js 18+** and **npm** installed
- **Backend API** running on `http://localhost:8080`

## Quick Start

### 1. Install Dependencies

```bash
npm install
```

### 2. Start the Development Server

```bash
npm run dev
```

The application will start on **http://localhost:3000** (or **http://localhost:5173** depending on Vite configuration).

### 3. Open in Browser

Navigate to:
```
http://localhost:3000
```

## Available Scripts

- `npm run dev` - Start development server with hot reload
- `npm run build` - Build for production
- `npm run preview` - Preview production build locally

## Features

### Schedule View
- **Grid View**: Visual calendar-style schedule showing all assignments by day and hour
- **List View**: Tabular view of all assignments
- **Color Coding**: Pinned assignments highlighted in red
- **Statistics**: Total, assigned, and unassigned counts

### Entity Management (CRUD)

#### Teachers
- View all teachers with max hours per week
- Create, edit, and delete teachers
- Search teachers by name

#### Courses
- View all courses with details
- Create, edit, and delete courses
- Filter by active/inactive status
- Configure: name, semester, room requirement, hours per week

#### Rooms
- View all rooms with building and type
- Create, edit, and delete rooms
- Filter by room type or building
- Room types: estándar, laboratorio, taller, centro de cómputo, etc.

#### Student Groups
- View all student groups
- Create, edit, and delete groups
- Configure preferred room for each group
- Search groups by name

#### Course Block Assignments
- View all course block assignments
- Create, edit, and delete assignments
- Filter by: All, Assigned, Unassigned, Pinned
- Configure: group, course, block length, teacher, timeslot, room, pinned status

## API Configuration

The frontend is configured to proxy API requests to the backend:

- **Backend URL**: `http://localhost:8080`
- **API Base Path**: `/api`
- **Proxy Configuration**: See `vite.config.js`

All API calls are automatically proxied from `/api/*` to `http://localhost:8080/api/*`.

## Project Structure

```
web-ui/
├── src/
│   ├── components/          # React components
│   │   ├── Schedule.jsx     # Schedule viewer (grid/list views)
│   │   ├── Teachers.jsx     # Teacher management
│   │   ├── Courses.jsx      # Course management
│   │   ├── Rooms.jsx        # Room management
│   │   ├── Groups.jsx       # Student group management
│   │   └── Assignments.jsx  # Assignment management
│   ├── api.js               # API service (Axios)
│   ├── App.jsx              # Main app with routing
│   ├── main.jsx             # React entry point
│   └── index.css            # Global styles
├── index.html               # HTML template
├── vite.config.js           # Vite configuration
├── package.json             # Dependencies
└── README.md                # This file
```

## Troubleshooting

### Port 3000 Already in Use

If port 3000 is already in use, kill the process:

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
# From the project root directory
mvn spring-boot:run -Dspring-boot.run.mainClass=com.example.web.ScheduleWebApplication
```

Verify backend is running:

```bash
curl http://localhost:8080/api/teachers
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

You can test API endpoints directly using curl:

```bash
# Get all teachers
curl http://localhost:8080/api/teachers

# Get schedule view
curl http://localhost:8080/api/schedule/view

# Get all courses
curl http://localhost:8080/api/courses
```

### Browser DevTools

Use browser developer tools to:
- Inspect network requests to the API
- Debug React components
- View console logs for errors

## Next Steps

1. **Start the backend** (see main project README)
2. **Install dependencies**: `npm install`
3. **Start the frontend**: `npm run dev`
4. **Open browser**: http://localhost:3000
5. **View and edit** schedules and entities

For more information about the backend API, see `WEB_UI_SETUP.md` in the project root.

