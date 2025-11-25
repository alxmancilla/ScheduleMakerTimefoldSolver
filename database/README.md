# PostgreSQL Database Schema for School Scheduling System

This directory contains PostgreSQL database schema and data loading scripts for the Timefold-based school scheduling system.

## Files

- **schema.sql** - Complete database schema with tables, indexes, constraints, and views
- **load_demo_data.sql** - Data loading script with the same demo dataset from `DemoDataGenerator.java`
- **README.md** - This file

## Database Schema Overview

### Core Tables

1. **teacher** - Teacher information with workload capacity
2. **teacher_qualification** - Courses each teacher is qualified to teach
3. **teacher_availability** - Per-day, per-hour availability for teachers
4. **course** - Courses offered in the curriculum
5. **room** - Physical classrooms and labs
6. **timeslot** - Available time slots for scheduling
7. **student_group** - Student groups that take courses together
8. **group_course** - Which courses each group must take
9. **course_assignment** - Planning entities (teacher/timeslot/room assignments)

### Entity Relationships

```
teacher (1) ----< (M) teacher_qualification
teacher (1) ----< (M) teacher_availability

student_group (1) ----< (M) group_course (M) >---- (1) course
student_group (1) ----< (M) course_assignment (M) >---- (1) course
student_group (M) >---- (1) room [preferred_room]

course_assignment (M) >---- (1) teacher [nullable, assigned by solver]
course_assignment (M) >---- (1) timeslot [nullable, assigned by solver]
course_assignment (M) >---- (1) room [nullable, assigned by solver]
```

### Utility Views

- **v_teacher_summary** - Teacher info with aggregated qualifications and availability count
- **v_schedule** - Complete schedule view with all assignment details
- **v_teacher_workload** - Teacher workload and capacity utilization analysis
- **v_room_utilization** - Room usage statistics

## Quick Start

### 1. Create Database

```bash
# Connect to PostgreSQL
psql -U postgres

# Create database
CREATE DATABASE school_schedule;

# Connect to the new database
\c school_schedule
```

### 2. Create Schema

```bash
# Run schema creation script
psql -U postgres -d school_schedule -f schema.sql
```

### 3. Load Demo Data

```bash
# Load demo data (same as DemoDataGenerator.java)
psql -U postgres -d school_schedule -f load_demo_data.sql
```

## Data Mapping from Java Domain Model

### Teacher
- Java: `Teacher` class with `Map<DayOfWeek, Set<Integer>> availabilityPerDay`
- Database: `teacher` table + `teacher_availability` (normalized)
- Java: `Set<String> qualifications`
- Database: `teacher_qualification` (normalized many-to-many)

### Course
- Java: `Course` class with `roomRequirement` and `requiredHoursPerWeek`
- Database: Direct mapping to `course` table

### Room
- Java: `Room` class with `name` as identifier
- Database: `room` table with `name` as PRIMARY KEY

### Timeslot
- Java: `Timeslot` class with `DayOfWeek` enum
- Database: `timeslot` table with `day_of_week` as INTEGER (1-7)
  - 1 = Monday, 2 = Tuesday, 3 = Wednesday, 4 = Thursday, 5 = Friday

### Group
- Java: `Group` class with `Set<String> courseNames`
- Database: `student_group` + `group_course` (normalized many-to-many)

### CourseAssignment
- Java: `@PlanningEntity` with three `@PlanningVariable` fields
- Database: `course_assignment` table
  - Planning variables (`teacher_id`, `timeslot_id`, `room_name`) are nullable
  - Will be populated by the Timefold solver

## Demo Data Summary

The demo dataset includes:

- **22 teachers** with varying qualifications and availability
- **11 courses** (10 standard + 1 lab course)
- **11 rooms** (9 standard classrooms + 2 labs in Building A)
- **40 timeslots** (Monday-Friday, 7:00-15:00, 8 hours per day)
- **9 student groups** (Gpo 1oA through Gpo 1oI)
- **~231 course assignments** (9 groups × ~25.67 average hours per group)

Each group takes all 11 courses, requiring:
- 1 hour × 4 courses (TUTORIAS I, CLUB DE AJEDREZ, ACTIVACIÓN FÍSICA, RECURSOS SOCIOEMOCIONALES I)
- 2 hours × 1 course (CIENCIAS SOCIALES I)
- 3 hours × 4 courses (LENGUA Y COMUNICACIÓN I, INGLÉS I, CULTURA DIGITAL I, LA MATERIA Y SUS INTERACCIONES)
- 4 hours × 2 courses (HUMANIDADES I, PENSAMIENTO MATEMÁTICO I)
- **Total: 26 hours per group per week**

## Querying the Database

### View All Teachers with Qualifications

```sql
SELECT * FROM v_teacher_summary ORDER BY name;
```

### View Teacher Workload

```sql
SELECT * FROM v_teacher_workload WHERE utilization_percent > 0;
```

### View All Unassigned Course Assignments

```sql
SELECT
    sg.name AS group_name,
    c.name AS course_name,
    c.required_hours_per_week,
    COUNT(*) AS unassigned_hours
FROM course_assignment ca
JOIN student_group sg ON ca.group_id = sg.id
JOIN course c ON ca.course_id = c.id
WHERE ca.teacher_id IS NULL
  AND ca.timeslot_id IS NULL
  AND ca.room_name IS NULL
GROUP BY sg.name, c.name, c.required_hours_per_week
ORDER BY sg.name, c.name;
```

### View Complete Schedule (After Solver Runs)

```sql
SELECT * FROM v_schedule
WHERE teacher_name IS NOT NULL
ORDER BY group_name, day_of_week, hour;
```

### Check Teacher Availability

```sql
SELECT
    t.name,
    ta.day_of_week,
    STRING_AGG(ta.hour::TEXT, ', ' ORDER BY ta.hour) AS available_hours
FROM teacher t
JOIN teacher_availability ta ON t.id = ta.teacher_id
WHERE t.name = 'GUSTAVO MELO'
GROUP BY t.name, ta.day_of_week
ORDER BY ta.day_of_week;
```

### Room Utilization Report

```sql
SELECT * FROM v_room_utilization ORDER BY assignments_count DESC;
```

## Notes

### DayOfWeek Mapping

Java `DayOfWeek` enum values are stored as integers:
- 1 = MONDAY
- 2 = TUESDAY
- 3 = WEDNESDAY
- 4 = THURSDAY
- 5 = FRIDAY
- 6 = SATURDAY
- 7 = SUNDAY

### ID Generation

Teacher and course IDs are sanitized versions of their names:
- Original: "GUSTAVO MELO" → ID: "t_gustavo_melo"
- Original: "LENGUA Y COMUNICACIÓN I" → ID: "c_lengua_y_comunicaci_n_i"

### Planning Variables

The `course_assignment` table contains three nullable fields that represent Timefold planning variables:
- `teacher_id` - Which teacher is assigned
- `timeslot_id` - When the class occurs
- `room_name` - Where the class takes place

These fields are NULL initially and should be populated by the constraint solver.

## Integration with Java Application

To integrate this database with the Java application:

1. Add PostgreSQL JDBC driver to `pom.xml`
2. Create data access layer (DAO/Repository classes)
3. Load data from database instead of `DemoDataGenerator`
4. After solver completes, persist assignments back to database

### Example Dependencies (pom.xml)

```xml
<dependency>
    <groupId>org.postgresql</groupId>
    <artifactId>postgresql</artifactId>
    <version>42.7.1</version>
</dependency>

<dependency>
    <groupId>com.zaxxer</groupId>
    <artifactId>HikariCP</artifactId>
    <version>5.1.0</version>
</dependency>
```

## Maintenance

### Backup Database

```bash
pg_dump -U postgres -d school_schedule -f school_schedule_backup.sql
```

### Restore Database

```bash
psql -U postgres -d school_schedule -f school_schedule_backup.sql
```

### Reset Data

To reset to demo data:

```bash
psql -U postgres -d school_schedule -f load_demo_data.sql
```

## Support

For issues or questions about the database schema, refer to:
- The Java domain model in `src/main/java/com/example/domain/`
- The `DemoDataGenerator.java` for data generation logic
- The constraint definitions in `SchoolConstraintProvider.java`
