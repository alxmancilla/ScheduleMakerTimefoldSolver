# Web UI Setup and Usage Guide

## Overview

The Schedule Maker Web UI provides a complete interface for viewing and editing schedules, teachers, courses, rooms, student groups, and course block assignments. It consists of:

- **Backend**: Spring Boot REST API (Java 17)
- **Frontend**: React 18 with Vite
- **Database**: PostgreSQL

## Architecture

### Backend (Spring Boot)
- **Port**: 8080
- **Base URL**: `http://localhost:8080/api`
- **Technology Stack**:
  - Spring Boot 3.2.1
  - Spring Data JPA
  - PostgreSQL JDBC Driver
  - Hibernate ORM

### Frontend (React)
- **Port**: 3000
- **Base URL**: `http://localhost:3000`
- **Technology Stack**:
  - React 18.2.0
  - React Router 6.20.1
  - Axios 1.6.2
  - Vite 5.0.8

## Prerequisites

1. **Java 17** - For running the Spring Boot backend
2. **Node.js 18+** and **npm** - For running the React frontend
3. **PostgreSQL** - Database must be running with the `school_schedule` database loaded
4. **Maven** - For building the backend

## Setup Instructions

### 1. Database Setup

Ensure PostgreSQL is running and the database is loaded:

```bash
# Check if PostgreSQL is running
psql -U mancilla -d school_schedule -c "SELECT COUNT(*) FROM teacher;"
```

If the database is not set up, load it:

```bash
psql -U mancilla -d school_schedule -f database/schema_block_scheduling.sql
psql -U mancilla -d school_schedule -f database/load_final_dataset_blocks.sql
```

### 2. Backend Setup

The backend is already configured in `pom.xml` and `application.properties`.

**Start the Spring Boot backend**:

```bash
# From the project root directory
mvn spring-boot:run -Dspring-boot.run.mainClass=com.example.web.ScheduleWebApplication
```

The backend will start on `http://localhost:8080`.

**Verify the backend is running**:

```bash
curl http://localhost:8080/api/teachers
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

The frontend will start on `http://localhost:3000`.

**Open in browser**:

```
http://localhost:3000
```

## Features

### 1. Schedule View
- **Grid View**: Visual calendar-style schedule showing all assignments by day and hour
- **List View**: Tabular view of all assignments
- **Color Coding**: Pinned assignments are highlighted in red
- **Statistics**: Shows total, assigned, and unassigned counts

### 2. Teachers Management
- View all teachers with their max hours per week
- Create new teachers
- Edit existing teachers (name, last name, max hours)
- Delete teachers
- Search teachers by name

### 3. Courses Management
- View all courses with details
- Create new courses
- Edit existing courses (name, semester, room requirement, hours per week, active status)
- Delete courses
- Filter by active/inactive courses

### 4. Rooms Management
- View all rooms with building and type
- Create new rooms
- Edit existing rooms (building, type)
- Delete rooms
- Filter by room type or building

### 5. Student Groups Management
- View all student groups
- Create new groups
- Edit existing groups (name, preferred room)
- Delete groups
- Search groups by name

### 6. Assignments Management
- View all course block assignments
- Create new assignments
- Edit existing assignments (group, course, block length, teacher, timeslot, room, pinned status)
- Delete assignments
- Filter by: All, Assigned, Unassigned, Pinned
- Shows assignment statistics

## API Endpoints

### Teachers
- `GET /api/teachers` - Get all teachers
- `GET /api/teachers/{id}` - Get teacher by ID
- `POST /api/teachers` - Create teacher
- `PUT /api/teachers/{id}` - Update teacher
- `DELETE /api/teachers/{id}` - Delete teacher
- `GET /api/teachers/search?query={query}` - Search teachers

### Courses
- `GET /api/courses` - Get all courses
- `GET /api/courses/{id}` - Get course by ID
- `POST /api/courses` - Create course
- `PUT /api/courses/{id}` - Update course
- `DELETE /api/courses/{id}` - Delete course
- `GET /api/courses/search?query={query}` - Search courses
- `GET /api/courses/active` - Get active courses

### Rooms
- `GET /api/rooms` - Get all rooms
- `GET /api/rooms/{name}` - Get room by name
- `POST /api/rooms` - Create room
- `PUT /api/rooms/{name}` - Update room
- `DELETE /api/rooms/{name}` - Delete room
- `GET /api/rooms/type/{type}` - Get rooms by type
- `GET /api/rooms/building/{building}` - Get rooms by building

### Student Groups
- `GET /api/groups` - Get all groups
- `GET /api/groups/{id}` - Get group by ID
- `POST /api/groups` - Create group
- `PUT /api/groups/{id}` - Update group
- `DELETE /api/groups/{id}` - Delete group
- `GET /api/groups/search?query={query}` - Search groups

### Assignments
- `GET /api/assignments` - Get all assignments
- `GET /api/assignments/{id}` - Get assignment by ID
- `POST /api/assignments` - Create assignment
- `PUT /api/assignments/{id}` - Update assignment
- `DELETE /api/assignments/{id}` - Delete assignment
- `GET /api/assignments/group/{groupId}` - Get assignments by group
- `GET /api/assignments/teacher/{teacherId}` - Get assignments by teacher
- `GET /api/assignments/room/{roomName}` - Get assignments by room
- `GET /api/assignments/assigned` - Get assigned blocks
- `GET /api/assignments/unassigned` - Get unassigned blocks
- `GET /api/assignments/pinned` - Get pinned assignments

### Schedule
- `GET /api/schedule/view` - Get full schedule view
- `GET /api/schedule/view/group/{groupId}` - Get schedule for specific group
- `GET /api/schedule/view/teacher/{teacherId}` - Get schedule for specific teacher
- `GET /api/schedule/view/room/{roomName}` - Get schedule for specific room

## Troubleshooting

### Backend Issues

**Port 8080 already in use**:
```bash
# Find and kill the process using port 8080
lsof -ti:8080 | xargs kill -9
```

**Database connection error**:
- Check PostgreSQL is running: `pg_isready`
- Verify credentials in `src/main/resources/application.properties`
- Test connection: `psql -U mancilla -d school_schedule`

### Frontend Issues

**Port 3000 already in use**:
```bash
# Kill the process using port 3000
lsof -ti:3000 | xargs kill -9
```

**CORS errors**:
- Ensure backend is running on port 8080
- Check CORS configuration in `ScheduleWebApplication.java`

**API calls failing**:
- Verify backend is running: `curl http://localhost:8080/api/teachers`
- Check browser console for errors
- Verify proxy configuration in `vite.config.js`

## Development

### Building for Production

**Backend**:
```bash
mvn clean package
java -jar target/ScheduleMakerTimefoldSolver-1.0-SNAPSHOT.jar
```

**Frontend**:
```bash
cd web-ui
npm run build
```

The production build will be in `web-ui/dist/`.

### Code Structure

**Backend**:
- `src/main/java/com/example/web/entity/` - JPA entities
- `src/main/java/com/example/web/repository/` - JPA repositories
- `src/main/java/com/example/web/controller/` - REST controllers
- `src/main/java/com/example/web/dto/` - Data Transfer Objects
- `src/main/resources/application.properties` - Configuration

**Frontend**:
- `web-ui/src/components/` - React components
- `web-ui/src/api.js` - API service
- `web-ui/src/App.jsx` - Main application component
- `web-ui/src/index.css` - Global styles
- `web-ui/vite.config.js` - Vite configuration

## Next Steps

1. **Run the solver** to generate a schedule
2. **View the schedule** in the Web UI
3. **Edit assignments** as needed (pin specific assignments)
4. **Re-run the solver** with pinned assignments
5. **Export the schedule** to PDF using the existing PDF reporter

