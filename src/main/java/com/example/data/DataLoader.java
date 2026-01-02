package com.example.data;

import com.example.domain.*;
import java.sql.*;
import java.time.DayOfWeek;
import java.util.*;

/**
 * DataLoader loads the initial scheduling dataset from PostgreSQL database.
 * Reads teachers, courses, rooms, timeslots, groups, and course assignments
 * and returns a SchoolSchedule ready for the Timefold solver.
 */
public class DataLoader {

    private final String jdbcUrl;
    private final String username;
    private final String password;

    /**
     * Create a DataLoader with database connection parameters.
     *
     * @param jdbcUrl  JDBC URL (e.g.,
     *                 "jdbc:postgresql://localhost:5432/school_schedule")
     * @param username Database username
     * @param password Database password
     */
    public DataLoader(String jdbcUrl, String username, String password) {
        this.jdbcUrl = jdbcUrl;
        this.username = username;
        this.password = password;
    }

    /**
     * Load the complete dataset from the database and return a SchoolSchedule.
     *
     * @return SchoolSchedule with all data loaded from database
     * @throws SQLException if database access fails
     */
    public SchoolSchedule loadData() throws SQLException {
        try (Connection conn = DriverManager.getConnection(jdbcUrl, username, password)) {
            List<Teacher> teachers = loadTeachers(conn);
            List<Course> courses = loadCourses(conn);
            List<Room> rooms = loadRooms(conn);
            List<Timeslot> timeslots = loadTimeslots(conn);
            List<Group> groups = loadGroups(conn, rooms);
            List<CourseAssignment> assignments = loadCourseAssignments(conn, groups, courses);

            System.out.println("Loaded from database:");
            System.out.println("  - " + teachers.size() + " teachers");
            System.out.println("  - " + courses.size() + " courses");
            System.out.println("  - " + rooms.size() + " rooms");
            System.out.println("  - " + timeslots.size() + " timeslots");
            System.out.println("  - " + groups.size() + " groups");
            System.out.println("  - " + assignments.size() + " course assignments");

            return new SchoolSchedule(teachers, timeslots, rooms, courses, groups, assignments);
        }
    }

    /**
     * Load all teachers with their qualifications and availability.
     */
    private List<Teacher> loadTeachers(Connection conn) throws SQLException {
        Map<String, Teacher> teacherMap = new HashMap<>();

        // Load basic teacher info
        String sql = "SELECT id, name, last_name, max_hours_per_week FROM teacher ORDER BY max_hours_per_week, id";
        try (Statement stmt = conn.createStatement();
                ResultSet rs = stmt.executeQuery(sql)) {

            while (rs.next()) {
                String id = rs.getString("id");
                String name = rs.getString("name");
                String lastName = rs.getString("last_name");
                int maxHours = rs.getInt("max_hours_per_week");

                // Create teacher with empty qualifications and availability (will be populated
                // below)
                Teacher teacher = new Teacher(id, name, lastName, new HashSet<>(),
                        new HashMap<>(), maxHours);
                teacherMap.put(id, teacher);
            }
        }

        // Load qualifications
        loadTeacherQualifications(conn, teacherMap);

        // Load availability
        loadTeacherAvailability(conn, teacherMap);

        return new ArrayList<>(teacherMap.values());
    }

    /**
     * Load teacher qualifications and add them to teachers.
     */
    private void loadTeacherQualifications(Connection conn, Map<String, Teacher> teacherMap) throws SQLException {
        String sql = "SELECT teacher_id, qualification FROM teacher_qualification";
        try (Statement stmt = conn.createStatement();
                ResultSet rs = stmt.executeQuery(sql)) {

            while (rs.next()) {
                String teacherId = rs.getString("teacher_id");
                String qualification = rs.getString("qualification");

                Teacher teacher = teacherMap.get(teacherId);
                if (teacher != null) {
                    teacher.getQualifications().add(qualification);
                }
            }
        }
    }

    /**
     * Load teacher availability and add to teachers.
     */
    private void loadTeacherAvailability(Connection conn, Map<String, Teacher> teacherMap) throws SQLException {
        String sql = "SELECT teacher_id, day_of_week, hour FROM teacher_availability ORDER BY teacher_id, day_of_week, hour";

        // Build availability map per teacher
        Map<String, Map<DayOfWeek, Set<Integer>>> availabilityData = new HashMap<>();

        try (Statement stmt = conn.createStatement();
                ResultSet rs = stmt.executeQuery(sql)) {

            while (rs.next()) {
                String teacherId = rs.getString("teacher_id");
                int dayOfWeek = rs.getInt("day_of_week");
                int hour = rs.getInt("hour");

                DayOfWeek day = toDayOfWeek(dayOfWeek);

                availabilityData
                        .computeIfAbsent(teacherId, k -> new HashMap<>())
                        .computeIfAbsent(day, k -> new HashSet<>())
                        .add(hour);
            }
        }

        // Now recreate teachers with proper availability
        for (Map.Entry<String, Teacher> entry : teacherMap.entrySet()) {
            String teacherId = entry.getKey();
            Teacher oldTeacher = entry.getValue();
            Map<DayOfWeek, Set<Integer>> availability = availabilityData.getOrDefault(teacherId, new HashMap<>());

            // Create new teacher with availability
            Teacher newTeacher = new Teacher(
                    oldTeacher.getId(),
                    oldTeacher.getName(),
                    oldTeacher.getLastName(),
                    oldTeacher.getQualifications(),
                    availability,
                    oldTeacher.getMaxHoursPerWeek());

            teacherMap.put(teacherId, newTeacher);
        }
    }

    /**
     * Load all courses.
     */
    private List<Course> loadCourses(Connection conn) throws SQLException {
        List<Course> courses = new ArrayList<>();
        String sql = "SELECT id, name, room_requirement, required_hours_per_week FROM course";

        try (Statement stmt = conn.createStatement();
                ResultSet rs = stmt.executeQuery(sql)) {

            while (rs.next()) {
                String id = rs.getString("id");
                String name = rs.getString("name");
                String abbreviation = rs.getString("abbreviation");
                String semester = rs.getString("semester");
                String component = rs.getString("component");
                String roomRequirement = rs.getString("room_requirement");
                int requiredHours = rs.getInt("required_hours_per_week");

                courses.add(new Course(id, name, abbreviation, semester, component, roomRequirement, requiredHours));
            }
        }

        return courses;
    }

    /**
     * Load all rooms.
     */
    private List<Room> loadRooms(Connection conn) throws SQLException {
        List<Room> rooms = new ArrayList<>();
        String sql = "SELECT name, building, type FROM room";

        try (Statement stmt = conn.createStatement();
                ResultSet rs = stmt.executeQuery(sql)) {

            while (rs.next()) {
                String name = rs.getString("name");
                String building = rs.getString("building");
                String type = rs.getString("type");

                rooms.add(new Room(name, building, type));
            }
        }

        return rooms;
    }

    /**
     * Load all timeslots.
     */
    private List<Timeslot> loadTimeslots(Connection conn) throws SQLException {
        List<Timeslot> timeslots = new ArrayList<>();
        String sql = "SELECT id, day_of_week, hour, display_name FROM timeslot ORDER BY day_of_week, hour";

        try (Statement stmt = conn.createStatement();
                ResultSet rs = stmt.executeQuery(sql)) {

            while (rs.next()) {
                String id = rs.getString("id");
                int dayOfWeek = rs.getInt("day_of_week");
                int hour = rs.getInt("hour");
                String displayName = rs.getString("display_name");

                DayOfWeek day = toDayOfWeek(dayOfWeek);
                timeslots.add(new Timeslot(id, day, hour, displayName));
            }
        }

        return timeslots;
    }

    /**
     * Load all student groups with their courses.
     */
    private List<Group> loadGroups(Connection conn, List<Room> rooms) throws SQLException {
        Map<String, Group> groupMap = new HashMap<>();

        // Load basic group info
        String sql = "SELECT id, name, preferred_room_name FROM student_group";
        try (Statement stmt = conn.createStatement();
                ResultSet rs = stmt.executeQuery(sql)) {

            while (rs.next()) {
                String id = rs.getString("id");
                String name = rs.getString("name");
                String preferredRoomName = rs.getString("preferred_room_name");

                // Find preferred room
                Room preferredRoom = null;
                if (preferredRoomName != null) {
                    preferredRoom = rooms.stream()
                            .filter(r -> r.getName().equals(preferredRoomName))
                            .findFirst()
                            .orElse(null);
                }

                // Create group with empty course set (will be populated below)
                Group group = new Group(id, name, new HashSet<>(), preferredRoom);
                groupMap.put(id, group);
            }
        }

        // Load group courses
        sql = "SELECT group_id, course_name FROM group_course";
        try (Statement stmt = conn.createStatement();
                ResultSet rs = stmt.executeQuery(sql)) {

            while (rs.next()) {
                String groupId = rs.getString("group_id");
                String courseName = rs.getString("course_name");

                Group group = groupMap.get(groupId);
                if (group != null) {
                    group.getCourseNames().add(courseName);
                }
            }
        }

        return new ArrayList<>(groupMap.values());
    }

    /**
     * Load all course assignments (initially unassigned).
     */
    private List<CourseAssignment> loadCourseAssignments(Connection conn, List<Group> groups, List<Course> courses)
            throws SQLException {
        List<CourseAssignment> assignments = new ArrayList<>();

        String sql = "SELECT id, group_id, course_id, sequence_index FROM course_assignment ORDER BY id";

        try (Statement stmt = conn.createStatement();
                ResultSet rs = stmt.executeQuery(sql)) {

            while (rs.next()) {
                String id = rs.getString("id");
                String groupId = rs.getString("group_id");
                String courseId = rs.getString("course_id");
                int sequenceIndex = rs.getInt("sequence_index");

                // Find corresponding group and course
                Group group = groups.stream()
                        .filter(g -> g.getId().equals(groupId))
                        .findFirst()
                        .orElseThrow(() -> new SQLException("Group not found: " + groupId));

                Course course = courses.stream()
                        .filter(c -> c.getId().equals(courseId))
                        .findFirst()
                        .orElseThrow(() -> new SQLException("Course not found: " + courseId));

                CourseAssignment assignment = new CourseAssignment(id, group, course, sequenceIndex);
                assignments.add(assignment);
            }
        }

        return assignments;
    }

    /**
     * Convert database day_of_week integer to Java DayOfWeek enum.
     * Database: 1=Monday, 2=Tuesday, ..., 7=Sunday
     */
    private DayOfWeek toDayOfWeek(int dayOfWeek) {
        return switch (dayOfWeek) {
            case 1 -> DayOfWeek.MONDAY;
            case 2 -> DayOfWeek.TUESDAY;
            case 3 -> DayOfWeek.WEDNESDAY;
            case 4 -> DayOfWeek.THURSDAY;
            case 5 -> DayOfWeek.FRIDAY;
            case 6 -> DayOfWeek.SATURDAY;
            case 7 -> DayOfWeek.SUNDAY;
            default -> throw new IllegalArgumentException("Invalid day of week: " + dayOfWeek);
        };
    }

    /**
     * Example usage: Load data from database.
     */
    public static void main(String[] args) {
        String jdbcUrl = System.getenv().getOrDefault("DB_URL", "jdbc:postgresql://localhost:5432/school_schedule");
        String username = System.getenv().getOrDefault("DB_USER", "mancilla");
        String password = System.getenv().getOrDefault("DB_PASSWORD", "");

        DataLoader loader = new DataLoader(jdbcUrl, username, password);

        try {
            SchoolSchedule schedule = loader.loadData();
            System.out.println("\nSuccessfully loaded schedule from database!");
            System.out.println("Ready for Timefold solver.");
        } catch (SQLException e) {
            System.err.println("Failed to load data from database: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
