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
    /**
     * @deprecated Hour-based scheduling is no longer supported. Use
     *             {@link #loadDataForBlockScheduling()} instead.
     *
     *             The database schema has been migrated to block-based scheduling
     *             only.
     *             The tables 'timeslot' and 'course_assignment' no longer exist.
     *
     * @return never returns
     * @throws UnsupportedOperationException always thrown
     */
    @Deprecated
    public SchoolSchedule loadData() {
        throw new UnsupportedOperationException(
                "Hour-based scheduling is no longer supported. " +
                        "The database schema has been migrated to block-based scheduling. " +
                        "Please use loadDataForBlockScheduling() instead.");
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
        String sql = "SELECT id, name, abbreviation, semester, component, room_requirement, required_hours_per_week, active FROM course";

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
                Boolean active = rs.getBoolean("active");

                courses.add(new Course(id, name, abbreviation, semester, component, roomRequirement, requiredHours,
                        active));
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

    // ============================================================================
    // BLOCK-BASED SCHEDULING METHODS
    // ============================================================================

    /**
     * Load the complete dataset for block-based scheduling from the database.
     *
     * @return SchoolSchedule with block timeslots and course block assignments
     * @throws SQLException if database access fails
     */
    public SchoolSchedule loadDataForBlockScheduling() throws SQLException {
        try (Connection conn = DriverManager.getConnection(jdbcUrl, username, password)) {
            List<Teacher> teachers = loadTeachers(conn);
            List<Course> courses = loadCourses(conn);
            List<Room> rooms = loadRooms(conn);
            List<BlockTimeslot> blockTimeslots = loadBlockTimeslots(conn);
            List<Group> groups = loadGroups(conn, rooms);
            List<CourseBlockAssignment> blockAssignments = loadCourseBlockAssignments(conn, groups, courses, teachers,
                    rooms, blockTimeslots);

            System.out.println("Loaded from database (block-based scheduling):");
            System.out.println("  - " + teachers.size() + " teachers");
            System.out.println("  - " + courses.size() + " courses");
            System.out.println("  - " + rooms.size() + " rooms");
            System.out.println("  - " + blockTimeslots.size() + " block timeslots");
            System.out.println("  - " + groups.size() + " groups");
            System.out.println("  - " + blockAssignments.size() + " course block assignments");

            return SchoolSchedule.forBlockScheduling(teachers, blockTimeslots, rooms, courses, groups,
                    blockAssignments);
        }
    }

    /**
     * Load all block timeslots from the database.
     */
    private List<BlockTimeslot> loadBlockTimeslots(Connection conn) throws SQLException {
        List<BlockTimeslot> blockTimeslots = new ArrayList<>();
        String sql = "SELECT id, day_of_week, start_hour, length_hours FROM block_timeslot ORDER BY day_of_week, start_hour, length_hours";

        try (Statement stmt = conn.createStatement();
                ResultSet rs = stmt.executeQuery(sql)) {

            while (rs.next()) {
                String id = rs.getString("id");
                int dayOfWeek = rs.getInt("day_of_week");
                int startHour = rs.getInt("start_hour");
                int lengthHours = rs.getInt("length_hours");

                DayOfWeek day = toDayOfWeek(dayOfWeek);
                blockTimeslots.add(new BlockTimeslot(id, day, startHour, lengthHours));
            }
        }

        return blockTimeslots;
    }

    /**
     * Load all course block assignments with teacher, room, and block timeslot
     * assignments when available.
     */
    private List<CourseBlockAssignment> loadCourseBlockAssignments(Connection conn, List<Group> groups,
            List<Course> courses, List<Teacher> teachers, List<Room> rooms, List<BlockTimeslot> blockTimeslots)
            throws SQLException {
        List<CourseBlockAssignment> assignments = new ArrayList<>();

        String sql = "SELECT id, group_id, course_id, block_length, teacher_id, room_name, block_timeslot_id, pinned FROM course_block_assignment ORDER BY id";

        try (Statement stmt = conn.createStatement();
                ResultSet rs = stmt.executeQuery(sql)) {

            while (rs.next()) {
                String id = rs.getString("id");
                String groupId = rs.getString("group_id");
                String courseId = rs.getString("course_id");
                int blockLength = rs.getInt("block_length");

                // Find corresponding group and course
                Group group = groups.stream()
                        .filter(g -> g.getId().equals(groupId))
                        .findFirst()
                        .orElseThrow(() -> new SQLException("Group not found: " + groupId));

                Course course = courses.stream()
                        .filter(c -> c.getId().equals(courseId))
                        .findFirst()
                        .orElseThrow(() -> new SQLException("Course not found: " + courseId));

                CourseBlockAssignment assignment = new CourseBlockAssignment(id, group, course, blockLength);

                // Assign teacher if available
                String teacherId = rs.getString("teacher_id");
                if (teacherId != null && !teacherId.isEmpty()) {
                    Teacher teacher = teachers.stream()
                            .filter(t -> t.getId().equals(teacherId))
                            .findFirst()
                            .orElse(null);
                    if (teacher != null) {
                        assignment.setTeacher(teacher);
                    }
                }

                // Assign room if available
                String roomName = rs.getString("room_name");
                if (roomName != null && !roomName.isEmpty()) {
                    Room room = rooms.stream()
                            .filter(r -> r.getName().equals(roomName))
                            .findFirst()
                            .orElse(null);
                    if (room != null) {
                        assignment.setRoom(room);
                    }
                }

                // Assign block timeslot if available
                String blockTimeslotId = rs.getString("block_timeslot_id");
                if (blockTimeslotId != null && !blockTimeslotId.isEmpty()) {
                    BlockTimeslot blockTimeslot = blockTimeslots.stream()
                            .filter(bts -> bts.getId().equals(blockTimeslotId))
                            .findFirst()
                            .orElse(null);
                    if (blockTimeslot != null) {
                        assignment.setTimeslot(blockTimeslot);
                    }
                }

                boolean pinned = rs.getBoolean("pinned");
                assignment.setPinned(pinned);

                if (assignment.isPinned()) {
                    System.out.println("Loaded pinned block assignment: " + assignment);
                } else {
                    System.out.println("Loaded unpinned block assignment: " + assignment.getId());
                }

                assignments.add(assignment);
            }
        }

        return assignments;
    }

    /**
     * Example usage: Load block-based data from database.
     */
    public static void main(String[] args) {
        String jdbcUrl = System.getenv().getOrDefault("DB_URL", "jdbc:postgresql://localhost:5432/school_schedule");
        String username = System.getenv().getOrDefault("DB_USER", "mancilla");
        String password = System.getenv().getOrDefault("DB_PASSWORD", "");

        DataLoader loader = new DataLoader(jdbcUrl, username, password);

        try {
            SchoolSchedule schedule = loader.loadDataForBlockScheduling();
            System.out.println("\nSuccessfully loaded block-based schedule from database!");
            System.out.println("Ready for Timefold solver.");
        } catch (SQLException e) {
            System.err.println("Failed to load data from database: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
