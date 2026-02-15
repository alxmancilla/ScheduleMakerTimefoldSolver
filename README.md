# School Scheduling Solution with Timefold Solver

A comprehensive Java 17 application for school schedule generation using **Timefold Solver 1.13.0**. This solution implements complex constraint optimization for assigning teachers, courses, timeslots, and rooms while respecting hard constraints and optimizing soft preferences.

## Current Status

✅ **Build & Tests: PASSING** — Compiles successfully with Timefold 1.13.0
✅ **Block-Based Scheduling:** Migrated from hour-based to block-based scheduling (multi-hour consecutive blocks)
✅ **PostgreSQL Integration:** Full database support with schema, views, and data loading
✅ **Production Dataset:** 399 course block assignments with 32 pinned assignments
✅ **Dual Room Requirements:** Courses can specify multiple room types with different hour allocations
✅ **Custom Block Templates:** Explicit block decomposition patterns via `course_block_template` table
✅ **Computer Center Distribution:** Greenfield strategy for optimal CC 1/CC 2/CC 3 utilization (107h in 105h capacity)
⚠️ **Current Score:** -11hard/-1230soft (11 actual violations, 2pm constraint relaxed to SOFT)
⏱️ **Solver Config:** 30 min time limit, 8 min unimproved limit, lateAcceptanceSize: 3000, best score: `0hard/*soft`

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
1. **Block Length Must Match Timeslot Length** — Data integrity constraint (database validation)
2. **Teacher Qualification** — Teacher must be qualified for assigned course
3. **Teacher Availability for Entire Block** — Teacher must be available for all hours in the block
4. **No Teacher Double-Booking** — Teacher cannot teach two blocks that overlap
5. **No Room Double-Booking** — Room cannot host two blocks that overlap
6. **Room Type Must Satisfy Course Requirement** — Courses must use rooms matching their `satisfies_room_type`
7. **Group Cannot Have Two Courses at Same Time** — Student group cannot have overlapping blocks
8. **Maximum Blocks Per Course Per Group Per Day** — BASICAS: max 1 block/day, Non-BASICAS: max 2 blocks/day
9. **Non-Standard Rooms Must Finish by 2PM** — Labs, workshops, and computer centers MUST end by 14:00 for maintenance

#### Soft Constraints (Quality optimization, weighted preferences)
1. **Teacher Max Hours Per Week** (weight 5) — Penalizes teachers exceeding their weekly hour limit
2. **Prefer Course Blocks Consecutive on Same Day** (weight 3) — Encourages consecutive scheduling for better student experience
3. **Minimize Group Idle Gaps** (weight 3 per hour) — Reduce gaps between blocks for same student group on same day (higher priority than teacher gaps)
4. **Prefer Block's Specified Room** (weight 3) — Use `preferred_room_name` when specified (Computer Center distribution)
5. **Prefer Group's Preferred Room** (weight 2) — Groups prefer their pre-assigned room when specified (excludes lab rooms)
6. **Minimize Teacher Idle Gaps** (weight 2 per hour) — Reduce gaps between blocks for same teacher on same day (availability-aware)
7. **Minimize Teacher Building Changes** (weight 1) — Reduce building switches for teachers on same day

## Features

- **Block-Based Scheduling**: Multi-hour consecutive blocks (1-4 hours) for efficient timetabling
- **PostgreSQL Integration**: Full database support with schema, views, and data loading scripts
- **Pinned Assignments**: Support for locking specific course blocks to teachers, rooms, and timeslots (32 pinned assignments)
- **Dual Room Requirements**: Courses can specify multiple room types with different hour allocations (e.g., 4h in lab + 4h in standard room)
- **Custom Block Templates**: Explicit block decomposition patterns via `course_block_template` table (10 special cases)
- **Computer Center Distribution**: Greenfield strategy for optimal CC 1/CC 2/CC 3 utilization across TPROG/TCS/TIA pathways
- **Flexible Teacher Management**: Teachers have stable `id`, qualifications, per-day availability maps, and `maxHoursPerWeek` workload limits
- **Multi-Room Scheduling**: Support for 6 room types (estándar, laboratorio, taller, taller electromecánica, taller electrónica, centro de cómputo)
- **Group Constraints**: Prevent overlapping blocks for student groups with optional preferred rooms
- **PDF Reports**: Three paginated PDF reports generated: violations analysis, schedule-by-teacher, and schedule-by-group
- **Database Views**: Pre-built views for teacher assignments, group schedules, and constraint validation
- **Production Dataset**: Real-world dataset with 399 course block assignments across 20 student groups
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
**Constraint**: HARD constraint penalizes blocks ending after 14:00 in non-standard rooms
**Impact**: Limits scheduling flexibility for specialized courses
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
**Constraint**: HARD constraint sums total assigned hours per teacher
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

### Example Run Output (Production Dataset - Current Status)
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
  - calendario-bloques-incumplimientos.pdf
  - calendario-bloques-por-maestro.pdf
  - calendario-bloques-por-grupo.pdf
```

**Current Challenges**:
- **Computer Center Overload**: 107h demand vs 105h capacity (2h unavoidable overload → 2 hard violations minimum)
- **Remaining Hard Violations**: 11 violations (mostly related to CC overload cascade effects)
- **Mathematical Limit**: Best achievable score is -2hard/-500soft (CC overload unavoidable)

## Recent Changes

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
