# School Scheduling Solution with Timefold Solver

A comprehensive Java 17 application for school schedule generation using **Timefold Solver 1.13.0**. This solution implements complex constraint optimization for assigning teachers, courses, timeslots, and rooms while respecting hard constraints and optimizing soft preferences.

## Current Status

✅ **Build & Tests: PASSING** — Compiles successfully with Timefold 1.13.0
✅ **Block-Based Scheduling:** Migrated from hour-based to block-based scheduling (multi-hour consecutive blocks)
✅ **PostgreSQL Integration:** Full database support with schema, views, and data loading
✅ **Production Dataset:** 484 course block assignments with 29 pinned assignments
✅ **Perfect Score:** Achieved 0hard/-995soft (all hard constraints satisfied)
⏱️ **Solver Config:** 15 minutes time limit, 5 minutes unimproved limit, best score limit: `0hard/*soft`

## Project Overview

### Problem Definition
Generate a weekly school timetable using **block-based scheduling** that assigns:
- **Teachers** to course blocks (with qualifications and per-day availability)
- **Course Blocks** to student groups (multi-hour consecutive blocks, 1-4 hours)
- **Block Timeslots** (Monday–Friday, 7:00–15:00, with variable block lengths)
- **Rooms** (standard classrooms and specialized labs)

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

#### Hard Constraints (Must be satisfied)
1. **Teacher Qualification** — Teacher must be qualified for assigned course
2. **Teacher Availability for Entire Block** — Teacher must be available for all hours in the block
3. **No Teacher Double-Booking** — Teacher cannot teach two blocks that overlap
4. **No Room Double-Booking** — Room cannot host two blocks that overlap
5. **Room Type Match** — Lab courses must use lab rooms; standard courses can use any room
6. **Group Time Conflict** — Student group cannot have overlapping blocks
7. **Teacher Max Hours Per Week** — Teacher cannot exceed their weekly hour limit

#### Soft Constraints (Quality optimization, weighted preferences)
1. **Minimize Teacher Idle Gaps** (weight 1) — Reduce gaps between blocks for same teacher on same day (availability-aware)
2. **Group Preferred Room** (weight 3) — Groups prefer their pre-assigned room when specified (excludes lab rooms)
3. **Minimize Teacher Building Changes** (weight 1) — Reduce building switches for teachers on same day
4. **Prefer Teachers with Less Capacity** (weight 1) — Favor assigning to teachers with lower max hours per week

## Features

- **Block-Based Scheduling**: Multi-hour consecutive blocks (1-4 hours) for efficient timetabling
- **PostgreSQL Integration**: Full database support with schema, views, and data loading scripts
- **Pinned Assignments**: Support for locking specific course blocks to teachers, rooms, and timeslots
- **Flexible Teacher Management**: Teachers have stable `id`, qualifications, per-day availability maps, and `maxHoursPerWeek` workload limits
- **Multi-Room Scheduling**: Support for standard classrooms and specialized labs with building assignments
- **Group Constraints**: Prevent overlapping blocks for student groups with optional preferred rooms
- **PDF Reports**: Three paginated PDF reports generated: violations analysis, schedule-by-teacher, and schedule-by-group
- **Database Views**: Pre-built views for teacher assignments, group schedules, and constraint validation
- **Production Dataset**: Real-world dataset with 484 course block assignments across 20 student groups
- **Scalable Architecture**: Timefold Constraint Streams for declarative, composable constraints
- **Comprehensive Reporting**: Console analysis, PDF outputs, and database query support

## Project Structure

```
src/
├── main/java/com/example/
│   ├── MainBlockSchedulingApp.java     # Entry point for block-based solver
│   ├── domain/
│   │   ├── Teacher.java                # Teacher with qualifications and per-day availability
│   │   ├── Course.java                 # Course with room requirement and hours
│   │   ├── Room.java                   # Room with building and type (standard/lab)
│   │   ├── BlockTimeslot.java          # Multi-hour block (day + start_hour + length)
│   │   ├── Group.java                  # Student group with courses and optional preferred room
│   │   ├── CourseBlockAssignment.java  # @PlanningEntity: block assignment with pinning support
│   │   └── SchoolSchedule.java         # @PlanningSolution: problem and solution holder
│   ├── solver/
│   │   ├── BlockConstraintProvider.java # All constraint definitions (hard & soft)
│   │   └── BlockSolverConfig.java      # Solver configuration (termination, time limits)
│   ├── analysis/
│   │   └── BlockScheduleAnalyzer.java  # Analyzes constraint violations for blocks
│   ├── util/
│   │   ├── PdfReporter.java            # Generates PDF reports
│   │   └── DataLoader.java             # Loads data from PostgreSQL database
│   └── data/
│       └── DemoDataGenerator.java      # Generates demo dataset (deprecated)
├── database/
│   ├── schema_block_scheduling.sql     # PostgreSQL schema (block-based only)
│   ├── datasets/
│   │   ├── load_demo_data_blocks.sql   # Demo dataset (932 lines)
│   │   └── load_final_dataset_blocks.sql # Production dataset (2063 lines, 29 pinned)
│   └── views/
│       └── create_views.sql            # Database views for reporting
└── test/
    └── java/com/example/AppTest.java
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

### Compile
```bash
mvn clean compile
```

### Run Block-Based Solver
```bash
mvn exec:java -Dexec.mainClass="com.example.MainBlockSchedulingApp"
```

This will:
1. Load data from PostgreSQL database
2. Run the solver (up to 15 minutes or until optimal score reached)
3. Generate three PDF reports:
   - `calendario-bloques-incumplimientos.pdf` - Constraint violations
   - `calendario-bloques-por-maestro.pdf` - Schedule by teacher
   - `calendario-bloques-por-grupo.pdf` - Schedule by student group
4. Display constraint violation analysis

### Run Tests
```bash
mvn test
```

## Production Dataset

### Overview
The production dataset (`load_final_dataset_blocks.sql`) contains real-world scheduling data:
- **484 course block assignments** across 20 student groups
- **29 pinned assignments** (locked teacher/room/timeslot combinations)
- **42 teachers** with qualifications and per-day availability
- **62 courses** (BASICAS and specialized courses)
- **32 rooms** (standard classrooms and specialized labs)
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

### Pinned Assignments (29 total)
Locked assignments for specific courses:
- **Course 15** (REALIZA ANALISIS FISICOS): 4 pinned blocks (2APIA, 2BPIA)
- **Course 16** (REALIZA ANALISIS MICROBIOLOGICOS): 6 pinned blocks (2APIA, 2BPIA)
- **Course 33** (TRANSFORMA FRUTAS Y VERDURAS): 2 pinned blocks (4APIA)
- **Course 34** (TRANSFORMA CARNE): 3 pinned blocks (4APIA)
- **Course 48** (HUMANISMO): 2 pinned blocks (6AARH)
- **Course 49** (INTERACCIONES HUMANAS): 8 pinned blocks (6APIA, 6APRO, 6ATEC, 6ATEM)
- **Course 53** (ANALISIS FISICOS/QUIMICOS/MICROBIOLOGICOS): 2 pinned blocks (6APIA)
- **Course 54** (TRANSFORMA CEREALES): 2 pinned blocks (6APIA)

## Solution Output

### Score Format
`XhardYsoft`
- **X** = number of hard violations (0 = feasible)
- **Y** = accumulated soft penalty (lower is better)

### Example Run Output (Production Dataset)
```
=== Block-Based School Schedule Solver ===
Loading data from PostgreSQL database...

Loaded 42 teachers
Loaded 62 courses
Loaded 32 rooms
Loaded 130 block timeslots
Loaded 20 groups
Loaded 484 course block assignments
Loaded 29 pinned block assignments

Solving...

=== Solved Schedule ===
Score: 0hard/-995soft
Solving time: 12.2 seconds

=== Hard Constraint Violations (by rule) ===
- Teacher must be qualified: 0
- Teacher must be available for entire block: 0
- No teacher double-booking: 0
- No room double-booking: 0
- Room type must satisfy course requirement: 0
- Group cannot have two courses at same time: 0
- Teacher exceeds max hours per week: 0

=== Soft Constraint Violations (by rule) ===
- Minimize teacher idle gaps (availability-aware): 155 violations

=== Schedule by Teacher ===
YARA ESTHER:
  MONDAY 07:00-10:00: TRANSFORMA CARNE Y SUS DERIVADOS (Group: 4APIA, Room: AULA 4) PINNED
  MONDAY 10:00-13:00: TRANSFORMA CEREALES (Group: 6APIA, Room: AULA 4) PINNED
  ...

=== Schedule by Group ===
2APIA:
  MONDAY 07:00-10:00: PENSAMIENTO MATEMATICO II (Teacher: DIANA, Room: AULA 1)
  TUESDAY 07:00-11:00: REALIZA ANALISIS FISICOS (Teacher: SUSANA LEONOR, Room: LQ 1) PINNED
  ...

PDF reports written to:
  - calendario-bloques-incumplimientos.pdf
  - calendario-bloques-por-maestro.pdf
  - calendario-bloques-por-grupo.pdf
```

## Recent Changes

### February 2026 (Current Release) - Block-Based Scheduling Migration

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

- **Production Dataset**
  - Translated `load_final_dataset.sql` (2188 lines) to block-based format
  - Created `load_final_dataset_blocks.sql` with 484 course block assignments
  - Translated 79 hour-based pinned assignments to 29 block-based pinned assignments
  - Achieved perfect score: **0hard/-995soft** (all hard constraints satisfied)

- **Block Decomposition Strategy**
  - BASICAS courses: Multiple 1-hour blocks for maximum flexibility
  - Non-BASICAS courses: Larger blocks (3-4 hours) to minimize fragmentation
  - User-modified 5-hour strategy: 1×3 + 1×2 (instead of 1×4 + 1×1)

- **Reporting & Analysis**
  - Updated `PdfReporter` for block-based schedules
  - Created `BlockScheduleAnalyzer` for constraint violation analysis
  - Generated three PDF reports: violations, by-teacher, by-group

### January 2, 2026

- **Timefold 1.13.0 Validation & Fixes**
  - Fixed syntax errors and updated imports for Timefold 1.13.0 API
  - All tests pass (`mvn test` returns BUILD SUCCESS)

### November 2025

- **Domain Model Refactor**
  - `Teacher` with stable `id`, per-day availability map, and `maxHoursPerWeek`
  - `Course` with `id` and `requiredHoursPerWeek`
  - Excel template generation and PDF reporting

## Architecture

### Technology Stack
- **Java 17** — Modern language features and performance
- **Timefold Solver 1.13.0** — Constraint Streams API for declarative constraint modeling
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
- **Termination Conditions:**
  - Best score limit: `0hard/*soft` (stop if all hard constraints satisfied)
  - Time limit: 15 minutes
  - Unimproved limit: 5 minutes without improvement

## Known Limitations

1. **Room Capacity** — Rooms have no capacity limits (assumes single course per block timeslot)
2. **Pre-assigned Teachers** — Teachers are pre-assigned from database; solver only assigns timeslots
3. **Pre-assigned Rooms** — Rooms are pre-assigned from database; solver only assigns timeslots
4. **Fixed Block Lengths** — Block lengths are determined by course hours and component type (BASICAS vs non-BASICAS)
5. **No Multi-Teacher Courses** — Each course block is assigned to exactly one teacher
6. **Soft Constraint Scaling** — Pairwise soft constraints scale as O(n²); may need optimization for very large datasets

## Future Enhancements

- [ ] Dynamic teacher/room assignment (currently pre-assigned from database)
- [ ] Room capacity constraints based on student group size
- [ ] Teacher workload balancing across weeks
- [ ] Student preferences for elective courses
- [ ] Mandatory lunch break constraints (e.g., 12:00-13:00)
- [ ] Rest period constraints (minimum gap between blocks for teachers)
- [ ] Multi-week scheduling patterns
- [ ] Integration with calendar systems (iCal/Google Calendar export)
- [ ] Web UI for viewing and editing schedules
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
1. **Edit constraints**: Modify `BlockConstraintProvider.java` for constraint logic
2. **Edit database schema**: Update `database/schema_block_scheduling.sql`
3. **Edit dataset**: Modify `database/datasets/load_final_dataset_blocks.sql`
4. **Reload data**: Run `psql -U mancilla -d school_schedule -f database/datasets/load_final_dataset_blocks.sql`
5. **Test changes**: Run `mvn clean compile` and `mvn exec:java -Dexec.mainClass="com.example.MainBlockSchedulingApp"`

## License

This project is provided as-is for educational and scheduling purposes.

## References

- [Timefold Solver Documentation](https://timefold.ai/)
- [Constraint Streams Guide](https://docs.timefold.ai/timefold-solver/latest/use-cases-and-examples)
- School Scheduling Problem (classical OR problem)
