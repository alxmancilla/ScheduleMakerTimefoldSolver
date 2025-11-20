# School Scheduling Solution with Timefold Solver

A comprehensive Java 17 application for school schedule generation using **Timefold Solver 1.x**. This solution implements complex constraint optimization for assigning teachers, courses, timeslots, and rooms while respecting hard constraints and optimizing soft preferences.

## Current Status

✅ **Fully Feasible** — All hard constraints satisfied (0 hard violations)  
📊 **Optimal Score:** `0hard/-Xsoft` (soft constraints for quality optimization)  
🎯 **Real-world Ready:** Complete timetable for multiple groups and teachers

## Project Overview

### Problem Definition
Generate a weekly school timetable that assigns:
- **Teachers** to course hours (with qualifications and availability)
- **Courses** to student groups (with required hours per week)
- **Timeslots** (Monday–Friday, 7:00–15:00 hours)
- **Rooms** (standard classrooms and labs)

### Constraints

#### Hard Constraints (Must be satisfied)
1. **Teacher Qualification** — Teacher must be qualified for assigned course
2. **Teacher Availability** — Teacher must be available at assigned timeslot
3. **No Teacher Double-Booking** — Teacher cannot teach two courses simultaneously
4. **No Room Double-Booking** — Room cannot host two courses simultaneously
5. **Room Type Match** — Lab courses must use lab rooms; standard courses must use standard rooms
6. **Group Time Conflict** — Student group cannot have two courses at the same time
7. **Non-Lab Room Consistency** — All non-lab courses for a group prefer same room (hard, with lab exception)

#### Soft Constraints (Quality optimization, weighted preferences)
1. **Teacher Continuity** (weight 3) — Prefer same teacher for all hours of a course
2. **Minimize Idle Gaps** (weight 1) — Reduce gaps between teacher's courses (same day)
3. **Minimize Building Changes** (weight 1) — Reduce teacher building switches (same day)
4. **Prefer Group Room** (weight 3) — Groups prefer their pre-assigned room when specified

## Features

- **Flexible Teacher Management**: Teachers with qualifications, day/hour availability
- **Multi-Room Scheduling**: Support for standard classrooms and specialized labs
- **Group Constraints**: Prevent concurrent course scheduling for student groups
- **Pre-assigned Preferences**: Optional preferred room assignment per group (soft constraint)
- **Scalable Architecture**: Timefold Constraint Streams for efficient solving
- **Comprehensive Reporting**: Violation analysis, schedule by day/teacher/group

## Project Structure

```
src/
├── main/java/com/example/
│   ├── MainApp.java                    # Entry point; runs solver and prints results
│   ├── domain/
│   │   ├── Teacher.java                # Teacher with qualifications and availability
│   │   ├── Course.java                 # Course with room requirement and hours
│   │   ├── Room.java                   # Room with building and type (standard/lab)
│   │   ├── Timeslot.java               # Timeslot with day and hour
│   │   ├── Group.java                  # Student group with courses and optional preferred room
│   │   ├── CourseAssignment.java       # @PlanningEntity: teacher, timeslot, room assignment
│   │   └── SchoolSchedule.java         # @PlanningSolution: problem and solution holder
│   ├── solver/
│   │   ├── SchoolConstraintProvider.java # All constraint definitions (hard & soft)
│   │   └── SchoolSolverConfig.java     # Solver configuration (termination, time limits)
│   └── data/
│       └── DemoDataGenerator.java      # Generates demo dataset (teachers, courses, rooms, groups)
└── test/
    └── java/com/example/AppTest.java
```

## Build Instructions

### Prerequisites
- **Java 17+**
- **Maven 3.8+**

### Compile
```bash
mvn clean compile
```

### Run Solver
```bash
mvn exec:java -Dexec.mainClass="com.example.MainApp"
```

This will:
1. Generate demo data (22 teachers, 11 courses, 7 groups, 11 rooms, 40 timeslots)
2. Run the solver (up to 15 minutes or until optimal score reached)
3. Print the solved schedule grouped by day, teacher, and group
4. Display constraint violation analysis

### Run Tests
```bash
mvn test
```

## Demo Data

### Teachers (22 total)
- Qualified for specific courses
- Available on specific days/hours
- Examples: GUSTAVO MELO (Lengua y Comunicación), MONICA E. DIEGO (Inglés), DIANA R. LLUCK (Pensamiento Matemático)

### Courses (11 total)
- **Standard courses** (3–4 hours/week): Lengua y Comunicación, Inglés, Pensamiento Matemático, Humanidades, Ciencias Sociales
- **Lab courses** (3 hours/week): Cultura Digital, La Materia y Sus Interacciones
- **Extracurricular** (1–2 hours): Club de Ajedrez, Activación Física, Tutorias, Recursos Socioemocionales

### Rooms (11 total)
- **Standard** (6): Room 101–102 (Building A), Room 301 (Building B), Room 401–402 (Building C)
- **Lab** (2): Lab 201–202 (Building A), Lab 302 (Building B)

### Groups (7 total)
- **Grupo 1o C**: Assigned courses + optional preferred room (Room 101)
- **Grupo 1o G**: Assigned courses (flexible room)
- Plus 5 additional groups

### Timeslots (40 total)
- Monday–Friday, 7:00–14:00 hours
- One timeslot per hour

## Solution Output

### Score Format
`XhardYsoft`
- **X** = number of hard violations (0 = feasible)
- **Y** = accumulated soft penalty (lower is better)

### Example Run Output
```
=== School Schedule Solver ===
Initial problem:
  Teachers: 22
  Courses: 11
  Rooms: 11
  Timeslots: 40
  Groups: 7
  Course Assignments: 77

Solving...

=== Solved Schedule ===
Score: 0hard/-36soft

=== Hard Constraint Violations (by rule) ===
- Teacher must be qualified: 0
- Teacher must be available at timeslot: 0
- No teacher double-booking: 0
- No room double-booking: 0
- Room type must satisfy course requirement: 0
- Group cannot have two courses at same time: 0
- Group non-lab courses must use same room: 0

=== Schedule by Day ===
MONDAY:
  Lun 8-9: LENGUA Y COMUNICACIÓN I (Group: Grupo 1o C, Teacher: GUSTAVO MELO, Room: Room 101)
  ...
```

## Recent Changes

### Constraint Improvements (November 2025)
1. **`groupNonLabCoursesInSameRoom`** — Hard constraint with lab exception
   - Enforces all non-lab courses for a group to use the same room
   - Allows lab courses to use lab rooms (exception)
   
2. **`groupPreferredRoomConstraint`** — Converted to soft (weight 3)
   - Was: Hard constraint forcing groups into pre-assigned rooms
   - Now: Soft preference (weight 3) allowing flexibility when needed
   - Excludes lab-type rooms from enforcement
   - **Result:** Achieves full feasibility (0 hard violations)

3. **Pre-assigned Rooms** — Group.preferredRoom field
   - Groups can have optional preferred room assignment
   - `Grupo 1o C` assigned to `Room 101` (as soft preference)
   - Other groups have flexible room assignment

## Architecture

### Technology Stack
- **Java 17** — Modern language features and performance
- **Timefold Solver 1.x** — Constraint Streams API for declarative constraint modeling
- **Maven** — Build automation and dependency management
- **HardSoftScore** — Two-level scoring (hard feasibility, soft quality)

### Constraint Implementation
- **Timefold Constraint Streams** — Programmatic, composable constraints
- **No-arg Constructors** — Required by Timefold for reflection
- **@PlanningEntity/@PlanningSolution** — Domain model annotations
- **@PlanningVariable** — Decision variables (teacher, timeslot, room)
- **@PlanningId** — Unique identifier for entity comparison

### Solver Configuration
- **Construction Heuristic** — Greedy initialization phase
- **Local Search** — Iterative improvement (Tabu Search, Simulated Annealing)
- **Termination Conditions:**
  - Best score limit: `0hard/*soft` (stop if all hard constraints satisfied)
  - Time limit: 15 minutes
  - Unimproved limit: 5 minutes without improvement

## Known Limitations

1. **Capacity Constraints** — Rooms have no capacity limits (assumes single course per timeslot)
2. **Soft Constraint Scaling** — Pairwise soft constraints (forEachUniquePair) scale as O(n²); consider refactoring for very large problem sizes
3. **No Multi-Teacher Courses** — Each course hour is assigned to exactly one teacher
4. **Fixed Timeslots** — Timeslots cannot be adjusted; only room/teacher assignments are flexible

## Future Enhancements

- [ ] Room capacity constraints
- [ ] Teacher workload balancing
- [ ] Student preferences (elective course scheduling)
- [ ] Lunch break constraints
- [ ] Rest period constraints (no back-to-back courses for teachers)
- [ ] Multi-day course hour patterns (instead of weekly repetition)
- [ ] Integration with calendar systems (iCal export)

## Testing & Validation

### Constraint Analysis Report
See `CONSTRAINT_ANALYSIS_REPORT.md` for detailed analysis of:
- Hard constraint satisfaction
- Soft constraint optimization
- Violation breakdown
- Root cause analysis

### Running Diagnostics
To analyze constraint violations in the current solution, modify `MainApp.java` to call:
```java
Map<String, Integer> violations = analyzeHardConstraintViolations(solvedSchedule);
violations.forEach((k, v) -> System.out.println("- " + k + ": " + v));
```

## Contributing

To modify constraints or data:
1. Edit `SchoolConstraintProvider.java` for constraint logic
2. Edit `DemoDataGenerator.java` for data initialization
3. Run `mvn clean compile` and `mvn exec:java -Dexec.mainClass="com.example.MainApp"` to verify

## License

This project is provided as-is for educational and scheduling purposes.

## References

- [Timefold Solver Documentation](https://timefold.ai/)
- [Constraint Streams Guide](https://docs.timefold.ai/timefold-solver/latest/use-cases-and-examples)
- School Scheduling Problem (classical OR problem)
