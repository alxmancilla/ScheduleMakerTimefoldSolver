# School Scheduling Solution with Timefold Solver

A comprehensive Java 17 application for school schedule generation using Timefold Solver 1.x. This solution implements complex constraint optimization for assigning teachers, courses, timeslots, and rooms while respecting hard constraints and optimizing soft preferences.

## Features

### Domain Model
- **Teachers**: With qualifications, availability constraints, and contract rules
- **Courses**: With room requirements (standard/science_lab) and weekly hour requirements
- **Rooms**: Typed as standard or lab, organized by buildings
- **Timeslots**: Organized by day (Mon-Fri) and hour (8-15, excluding 12-1 lunch)
- **Groups**: Student groups with course enrollments
- **CourseAssignment**: Planning entity representing a single class session
- **SchoolSchedule**: Planning solution aggregating all entities

### Hard Constraints (must be satisfied)
1. **Teacher Qualification**: Teacher must be qualified for the course
2. **Teacher Availability**: Teacher must be available at the timeslot
3. **No Teacher Double-Booking**: No teacher can teach two classes simultaneously
4. **No Room Double-Booking**: No room can host two classes simultaneously
5. **Room Type Matching**: Room type must satisfy course requirement
6. **Group Conflict**: Group cannot have two courses at the same time
7. **No Lunch Classes**: No classes scheduled during lunch (12-1 PM)
8. **Teacher Continuity**: Same teacher for all course hours of a group-course
9. **Group Room Consistency**: Group non-lab courses must use the same room all week

### Soft Constraints (optimized)
1. **Minimize Building Changes**: Prefer teacher classes in same building on same day
2. **Minimize Idle Gaps**: Reduce time gaps between teacher classes
3. **Prefer Earlier Timeslots**: Schedule classes earlier in the day
4. **Spread Courses**: Distribute courses across multiple days

## Sample Data

### Teachers (5 total)
- **Ms. Smith**: Math 101, Math 102, Calculus (Mon-Fri 8-11 AM)
- **Mr. Jones**: Math 101, Physics 101, Physics (Mon-Thu 8 AM-5 PM)
- **Dr. Brown**: Physics 101, Chemistry 101 (Mon-Wed 8 AM-5 PM)
- **Ms. Davis**: English 101 (Mon-Fri 11 AM-5 PM)
- **Dr. Lee**: Biology 101, Chemistry 101 (Tue-Fri 8 AM-5 PM)

### Courses (7 total)
- **Standard Courses**: Math 101, Math 102, English 101, Calculus
- **Lab Courses**: Physics 101, Chemistry 101, Biology 101

### Groups (4 total)
- **Group A**: Math 101, Physics 101, English 101
- **Group B**: Math 102, Chemistry 101, Biology 101
- **Group C**: Calculus, Physics 101, English 101
- **Group D**: Math 101, Chemistry 101, Biology 101

### Rooms (6 total)
- **Building A**: Room 101 (standard), Room 102 (standard), Lab 201 (lab)
- **Building B**: Room 301 (standard), Lab 302 (lab)
- **Building C**: Room 401 (standard)

### Timeslots (35 total)
- 5 days × 7 hours per day = 35 slots
- Hours: 8-9, 9-10, 10-11, 11-12, 1-2, 2-3, 3-4 (12-1 lunch excluded)

## Project Structure

```
src/
├── main/java/com/example/
│   ├── MainApp.java                    # Entry point - solver runner
│   ├── domain/
│   │   ├── Teacher.java                # Problem fact
│   │   ├── Course.java                 # Problem fact
│   │   ├── Room.java                   # Problem fact
│   │   ├── Timeslot.java               # Problem fact
│   │   ├── Group.java                  # Problem fact
│   │   ├── CourseAssignment.java       # @PlanningEntity
│   │   └── SchoolSchedule.java         # @PlanningSolution
│   ├── solver/
│   │   ├── SchoolConstraintProvider.java   # All constraints
│   │   └── SchoolSolverConfig.java         # Solver configuration
│   └── data/
│       └── DemoDataGenerator.java      # Test data generator
└── test/java/com/example/
    └── AppTest.java
pom.xml                                # Maven configuration with Timefold 1.13.0
```

## Building the Project

Compile with Maven:

```bash
mvn clean compile
```

## Running the Application

Run the scheduler:

```bash
mvn exec:java -Dexec.mainClass="com.example.MainApp"
```

Or build and execute the JAR:

```bash
mvn clean package
java -jar target/schedule-maker-timefoldsolver-1.0.0.jar
```

## Running Tests

```bash
mvn test
```

## Output Format

The application produces three schedule views:

### 1. By Day
Groups assignments by day with timeslot, course, group, teacher, and room details.

### 2. By Teacher
Lists each teacher's schedule with total teaching hours and free periods.

### 3. By Group
Shows each group's complete schedule across the week.

Example output:
```
=== School Schedule Solver ===
Initial problem:
  Teachers: 5
  Courses: 7
  Rooms: 6
  Timeslots: 35
  Groups: 4
  Course Assignments: 40

Solving...

=== Solved Schedule ===
Score: 0hard/-45soft

=== Schedule by Day ===
MONDAY:
  Mon 8-9: Math 101 (Group: Group A, Teacher: Ms. Smith, Room: Room 101)
  ...
```

## Technical Details

### Solver Configuration
- **Engine**: Local Search with hill climbing
- **Time Limit**: 30 seconds (configurable)
- **Score Type**: HardSoftScore
  - Hard score: Constraint violations (0 = all satisfied)
  - Soft score: Preference violations (higher is better)

### Score Explanation
- `0hard/-45soft` means: All hard constraints satisfied, 45 soft constraint points to improve

### Dependencies
- **Timefold Solver Core 1.13.0**: Constraint optimization engine
- **SLF4J + Logback**: Logging framework
- **JUnit 4.13.2**: Testing (test scope)

## Customization

### Modify Solver Time Limit
Edit `SchoolSolverConfig.java`:
```java
.withTerminationConfig(new TerminationConfig()
    .withSecondsSpentLimit(60L))  // Change to 60 seconds
```

### Add Teachers/Courses/Groups
Edit `DemoDataGenerator.java` to add new entities or modify existing ones.

### Adjust Constraints
Edit `SchoolConstraintProvider.java`:
- Modify penalty weights in soft constraints
- Add new constraint methods
- Adjust hard constraint thresholds

### Change Availability Windows
Modify teacher availability in `DemoDataGenerator.generateTeachers()`

### Add Room Types
Extend room type checking in `Room.satisfiesRequirement()`

## Implementation Notes

- **Lab Exception**: Lab courses can use any lab room (override group room consistency)
- **Group Room Rule**: All non-lab courses for a group must use the same room for the entire week
- **Teacher Continuity**: Once a teacher is assigned to a group-course, they teach all its sessions
- **Lunch Block**: The 12-1 hour is hardcoded as lunch and blocked for all assignments
- **Score Interpretation**: Solver runs for up to 30 seconds and returns best solution found

## Performance Characteristics

- **Problem Size**: 40 course assignments × 30 planning variables (teacher/timeslot/room)
- **Constraint Count**: 19 total (9 hard, 10 soft)
- **Average Solve Time**: 5-30 seconds to find good solution
- **Optimality**: Local search does not guarantee global optimum, but finds practical solutions

## Future Enhancements

- Add teacher weekly hour limits
- Implement teacher prep periods
- Add room capacity constraints
- Support multiple weeks/semesters
- GUI interface with constraint visualization
- Advanced termination strategies (tabu search, simulated annealing)

## License

This is a demonstration project for Timefold Solver.
