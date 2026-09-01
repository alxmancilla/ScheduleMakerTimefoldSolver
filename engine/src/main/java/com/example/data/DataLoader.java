package com.example.data;

import ai.timefold.solver.core.api.domain.solution.ConstraintWeightOverrides;
import ai.timefold.solver.core.api.score.buildin.hardsoft.HardSoftScore;
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
        String sql = "SELECT id, name, last_name, max_hours_per_week, required_room_name FROM teacher ORDER BY max_hours_per_week, id";
        try (Statement stmt = conn.createStatement();
                ResultSet rs = stmt.executeQuery(sql)) {

            while (rs.next()) {
                String id = rs.getString("id");
                String name = rs.getString("name");
                String lastName = rs.getString("last_name");
                int maxHours = rs.getInt("max_hours_per_week");
                String requiredRoomName = rs.getString("required_room_name");

                // Create teacher with empty qualifications and availability (will be populated
                // below)
                Teacher teacher = new Teacher(id, name, lastName, new HashSet<>(),
                        new HashMap<>(), maxHours);
                teacher.setRequiredRoomName(requiredRoomName);
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
            newTeacher.setRequiredRoomName(oldTeacher.getRequiredRoomName());

            teacherMap.put(teacherId, newTeacher);
        }
    }

    /**
     * Load all courses.
     */
    private List<Course> loadCourses(Connection conn) throws SQLException {
        List<Course> courses = new ArrayList<>();
        String sql = "SELECT id, name, abbreviation, semester, designation, room_requirement, required_hours_per_week, active FROM course";

        try (Statement stmt = conn.createStatement();
                ResultSet rs = stmt.executeQuery(sql)) {

            while (rs.next()) {
                String id = rs.getString("id");
                String name = rs.getString("name");
                String abbreviation = rs.getString("abbreviation");
                int semester = rs.getInt("semester");
                String designation = rs.getString("designation");
                String roomRequirement = rs.getString("room_requirement");
                int requiredHours = rs.getInt("required_hours_per_week");
                Boolean active = rs.getBoolean("active");

                courses.add(new Course(id, name, abbreviation, semester, designation, roomRequirement, requiredHours,
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
        String sql = "SELECT name, building, type, capacity FROM room";

        try (Statement stmt = conn.createStatement();
                ResultSet rs = stmt.executeQuery(sql)) {

            while (rs.next()) {
                String name = rs.getString("name");
                String building = rs.getString("building");
                String type = rs.getString("type");
                Integer capacity = rs.getObject("capacity", Integer.class);

                rooms.add(new Room(name, building, type, capacity));
            }
        }

        return rooms;
    }

    /**
     * Load every group's curated acceptable-room ranges from
     * group_room_range, resolved against the already-loaded room pool and
     * grouped as group_id -> room_type -> that type's list of Room objects.
     * A group/room-type pair with no rows here is absent from the returned
     * map entirely (not an empty list) - Group.getAcceptableRooms() then
     * returns null for it, which CourseBlockAssignment.getMatchingRooms()
     * treats as "unrestricted, fall through to the full type-filtered list."
     */
    private Map<String, Map<String, List<Room>>> loadGroupRoomRanges(Connection conn, List<Room> rooms)
            throws SQLException {
        Map<String, Room> roomsByName = new HashMap<>();
        for (Room room : rooms) {
            roomsByName.put(room.getName(), room);
        }

        Map<String, Map<String, List<Room>>> result = new HashMap<>();
        String sql = "SELECT group_id, room_type, room_name FROM group_room_range ORDER BY group_id, room_type";
        try (Statement stmt = conn.createStatement();
                ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                String groupId = rs.getString("group_id");
                String roomType = rs.getString("room_type");
                Room room = roomsByName.get(rs.getString("room_name"));
                if (room == null) {
                    continue; // dangling room reference - skip rather than fail the whole load
                }
                result.computeIfAbsent(groupId, k -> new HashMap<>())
                        .computeIfAbsent(roomType, k -> new ArrayList<>())
                        .add(room);
            }
        }
        return result;
    }

    /**
     * Load all student groups with their courses.
     */
    private List<Group> loadGroups(Connection conn, List<Room> rooms) throws SQLException {
        Map<String, Group> groupMap = new HashMap<>();

        // Load each group's curated acceptable-room ranges, keyed by (group,
        // room type), before building the Group objects themselves - see
        // loadGroupRoomRanges().
        Map<String, Map<String, List<Room>>> roomRangesByGroup = loadGroupRoomRanges(conn, rooms);

        // Load basic group info
        String sql = "SELECT id, name, student_count FROM student_group";
        try (Statement stmt = conn.createStatement();
                ResultSet rs = stmt.executeQuery(sql)) {

            while (rs.next()) {
                String id = rs.getString("id");
                String name = rs.getString("name");
                Integer studentCount = rs.getObject("student_count", Integer.class);

                // Create group with empty course set (will be populated below)
                Group group = new Group(id, name, new HashSet<>(), roomRangesByGroup.get(id), studentCount);
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
     * Load room requirements from the database and populate them in courses.
     */
    private void loadRoomRequirements(Connection conn, List<com.example.domain.Course> courses) throws SQLException {
        String sql = "SELECT course_id, room_type, hours_required, priority, default_preferred_room FROM course_room_requirement ORDER BY course_id, priority";

        Map<String, List<com.example.domain.RoomRequirement>> requirementsByCourse = new HashMap<>();

        try (Statement stmt = conn.createStatement();
                ResultSet rs = stmt.executeQuery(sql)) {

            while (rs.next()) {
                String courseId = rs.getString("course_id");
                String roomType = rs.getString("room_type");
                int hoursRequired = rs.getInt("hours_required");
                int priority = rs.getInt("priority");
                String defaultPreferredRoom = rs.getString("default_preferred_room");

                com.example.domain.RoomRequirement req = new com.example.domain.RoomRequirement(
                        null, courseId, roomType, hoursRequired, priority, defaultPreferredRoom);

                requirementsByCourse.computeIfAbsent(courseId, k -> new ArrayList<>()).add(req);
            }
        }

        // Populate room requirements in courses
        for (com.example.domain.Course course : courses) {
            List<com.example.domain.RoomRequirement> reqs = requirementsByCourse.get(course.getId());
            if (reqs != null) {
                course.setRoomRequirements(reqs);
            }
        }
    }

    /**
     * Load block templates from the database and populate them in courses.
     */
    private void loadBlockTemplates(Connection conn, List<com.example.domain.Course> courses) throws SQLException {
        String sql = "SELECT course_id, group_id, block_index, block_length, room_type, preferred_room_name, preferred_day, pin_assignment, preferred_timeslot_id FROM course_block_template ORDER BY course_id, group_id, block_index";

        Map<String, List<com.example.domain.BlockTemplate>> templatesByCourse = new HashMap<>();

        try (Statement stmt = conn.createStatement();
                ResultSet rs = stmt.executeQuery(sql)) {

            while (rs.next()) {
                String courseId = rs.getString("course_id");
                String groupId = rs.getString("group_id");
                int blockIndex = rs.getInt("block_index");
                int blockLength = rs.getInt("block_length");
                String roomType = rs.getString("room_type");
                String preferredRoomName = rs.getString("preferred_room_name");
                Integer preferredDay = (Integer) rs.getObject("preferred_day");
                boolean pinAssignment = rs.getBoolean("pin_assignment");
                String preferredTimeslotId = rs.getString("preferred_timeslot_id");

                com.example.domain.BlockTemplate template = new com.example.domain.BlockTemplate(
                        null, courseId, groupId, blockIndex, blockLength, roomType,
                        preferredRoomName, preferredDay, pinAssignment, preferredTimeslotId);

                templatesByCourse.computeIfAbsent(courseId, k -> new ArrayList<>()).add(template);
            }
        }

        // Populate block templates in courses
        for (com.example.domain.Course course : courses) {
            List<com.example.domain.BlockTemplate> templates = templatesByCourse.get(course.getId());
            if (templates != null) {
                course.setBlockTemplates(templates);
            }
        }
    }

    /**
     * Load per-component max-blocks-per-day rules and populate them in courses.
     * A component with no row keeps {@code maxBlocksPerDay} null; the solver's
     * constraint provider applies its own code default in that case.
     */
    private void loadComponentBlockRules(Connection conn, List<com.example.domain.Course> courses)
            throws SQLException {
        String sql = "SELECT component, max_blocks_per_day FROM component_block_rule";

        Map<String, Integer> maxBlocksPerDayByComponent = new HashMap<>();

        try (Statement stmt = conn.createStatement();
                ResultSet rs = stmt.executeQuery(sql)) {

            while (rs.next()) {
                String component = rs.getString("component");
                int maxBlocksPerDay = rs.getInt("max_blocks_per_day");
                maxBlocksPerDayByComponent.put(component, maxBlocksPerDay);
            }
        }

        for (com.example.domain.Course course : courses) {
            Integer maxBlocksPerDay = maxBlocksPerDayByComponent.get(course.getDesignation());
            if (maxBlocksPerDay != null) {
                course.setMaxBlocksPerDay(maxBlocksPerDay);
            }
        }
    }

    /**
     * Load per-semester "must/should finish by hour X" limits from
     * semester_hour_limit onto each course of that semester (see
     * Course.getLatestEndHour()/getLatestEndHourSeverity(),
     * CourseBlockAssignment.getMatchingBlockTimeslots(), and
     * BlockScheduleMath.violatesHardSemesterHourLimit()/softSemesterHourLimitExcess()
     * for how the engine actually uses this). A semester with no row here
     * leaves every course of that semester unrestricted - same "absent row =
     * unrestricted" convention as loadComponentBlockRules above.
     *
     * @return the number of semesters with a configured limit (for the
     *         startup log line - see loadDataForBlockScheduling below).
     */
    private int loadSemesterHourLimits(Connection conn, List<com.example.domain.Course> courses)
            throws SQLException {
        String sql = "SELECT semester, latest_end_hour, severity FROM semester_hour_limit";

        Map<Integer, Integer> hourBySemester = new HashMap<>();
        Map<Integer, String> severityBySemester = new HashMap<>();

        try (Statement stmt = conn.createStatement();
                ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                int semester = rs.getInt("semester");
                hourBySemester.put(semester, rs.getInt("latest_end_hour"));
                severityBySemester.put(semester, rs.getString("severity"));
            }
        }

        for (com.example.domain.Course course : courses) {
            Integer semester = course.getSemester();
            if (semester != null && hourBySemester.containsKey(semester)) {
                course.setLatestEndHour(hourBySemester.get(semester));
                course.setLatestEndHourSeverity(severityBySemester.get(semester));
            }
        }
        return hourBySemester.size();
    }

    /**
     * Load per-constraint soft-weight overrides from constraint_config,
     * built into a ConstraintWeightOverrides Timefold applies transparently
     * on top of each constraint's hardcoded-default weight (see
     * SchoolSchedule.getConstraintWeightOverrides() /
     * SoftConstraintDefaults). A constraint with no row here keeps its
     * hardcoded default - this table only ever holds explicit overrides,
     * not a full copy of every known constraint's weight.
     */
    private ConstraintWeightOverrides<HardSoftScore> loadConstraintWeightOverrides(Connection conn)
            throws SQLException {
        String sql = "SELECT constraint_name, weight_soft FROM constraint_config";
        Map<String, HardSoftScore> overrides = new HashMap<>();
        try (Statement stmt = conn.createStatement();
                ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                String constraintName = rs.getString("constraint_name");
                int weightSoft = rs.getInt("weight_soft");
                overrides.put(constraintName, HardSoftScore.ofSoft(weightSoft));
            }
        }
        return overrides.isEmpty() ? ConstraintWeightOverrides.none() : ConstraintWeightOverrides.of(overrides);
    }

    /**
     * Load the complete dataset for block-based scheduling from the database,
     * resolving assignments to the current schedule (see the single-arg
     * overload below).
     *
     * @return SchoolSchedule with block timeslots and course block assignments
     * @throws SQLException if database access fails
     */
    public SchoolSchedule loadDataForBlockScheduling() throws SQLException {
        return loadDataForBlockScheduling(null);
    }

    /**
     * Load the complete dataset for block-based scheduling from the database.
     *
     * @param scheduleRunId null resolves assignments the same way the live
     *                      Schedule View's default does (course_block_assignment_current:
     *                      pinned rows keep their own input timeslot, everything else
     *                      is the latest schedule_run). A specific id instead reads that
     *                      run's own frozen schedule_run_result snapshot directly - the
     *                      same "don't let a later edit change what a historical run
     *                      appears to have used" guarantee ScheduleController relies on.
     * @return SchoolSchedule with block timeslots and course block assignments
     * @throws SQLException if database access fails
     */
    public SchoolSchedule loadDataForBlockScheduling(Integer scheduleRunId) throws SQLException {
        try (Connection conn = DriverManager.getConnection(jdbcUrl, username, password)) {
            List<Teacher> teachers = loadTeachers(conn);
            List<Course> courses = loadCourses(conn);

            // NEW: Load room requirements and block templates
            loadRoomRequirements(conn, courses);
            loadBlockTemplates(conn, courses);
            loadComponentBlockRules(conn, courses);
            int semesterHourLimitCount = loadSemesterHourLimits(conn, courses);

            List<Room> rooms = loadRooms(conn);
            List<BlockTimeslot> blockTimeslots = loadBlockTimeslots(conn);
            List<Group> groups = loadGroups(conn, rooms);
            List<CourseBlockAssignment> blockAssignments = loadCourseBlockAssignments(conn, groups, courses, teachers,
                    rooms, blockTimeslots, scheduleRunId);
            ConstraintWeightOverrides<HardSoftScore> constraintWeightOverrides = loadConstraintWeightOverrides(conn);

            System.out.println("Loaded from database (block-based scheduling):");
            System.out.println("  - " + teachers.size() + " teachers");
            System.out.println("  - " + courses.size() + " courses");
            System.out.println("  - " + rooms.size() + " rooms");
            System.out.println("  - " + blockTimeslots.size() + " block timeslots");
            System.out.println("  - " + groups.size() + " groups");
            System.out.println("  - " + blockAssignments.size() + " course block assignments");
            System.out.println(
                    "  - " + constraintWeightOverrides.getKnownConstraintNames().size() + " constraint weight overrides");
            System.out.println("  - " + semesterHourLimitCount + " semester hour limits");

            SchoolSchedule schedule = SchoolSchedule.forBlockScheduling(teachers, blockTimeslots, rooms, courses,
                    groups, blockAssignments);
            schedule.setConstraintWeightOverrides(constraintWeightOverrides);
            return schedule;
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
            List<Course> courses, List<Teacher> teachers, List<Room> rooms, List<BlockTimeslot> blockTimeslots,
            Integer scheduleRunId)
            throws SQLException {
        List<CourseBlockAssignment> assignments = new ArrayList<>();

        // scheduleRunId == null reads through course_block_assignment_current rather
        // than the raw table: pinned rows resolve to their own input block_timeslot_id,
        // every other row resolves to the most recent schedule_run's result - which is
        // also what gives the solver warm-starting (continuing from the last run's
        // placements) without any extra seeding logic here. A specific scheduleRunId
        // instead reads schedule_run_result directly for that run - both sources have
        // the same column shape (assignment_id aliased to id), so the row-mapping loop
        // below is identical either way.
        String sql = scheduleRunId == null
                ? "SELECT id, group_id, course_id, block_length, teacher_id, room_name, block_timeslot_id, pinned, satisfies_room_type, preferred_room_hint FROM course_block_assignment_current ORDER BY id"
                : "SELECT assignment_id AS id, group_id, course_id, block_length, teacher_id, room_name, block_timeslot_id, pinned, satisfies_room_type, preferred_room_hint FROM schedule_run_result WHERE schedule_run_id = ? ORDER BY assignment_id";

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            if (scheduleRunId != null) {
                stmt.setInt(1, scheduleRunId);
            }
            try (ResultSet rs = stmt.executeQuery()) {
            while (rs.next()) {
                String id = rs.getString("id");
                String groupId = rs.getString("group_id");
                String courseId = rs.getString("course_id");
                int blockLength = rs.getInt("block_length");

                // A schedule_run saved before the input-snapshot columns existed has
                // NULL group_id/course_id in schedule_run_result (that historical detail
                // was genuinely never captured) - skip rather than fail the whole load,
                // since scheduleRunId == null (the default course_block_assignment_current
                // path) never hits this: group_id/course_id there always come from the
                // live, always-populated course_block_assignment table.
                if (scheduleRunId != null && (groupId == null || courseId == null)) {
                    System.out.println("Skipping assignment " + id + " for run #" + scheduleRunId
                            + ": no input snapshot captured for this row (run predates that feature)");
                    continue;
                }

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
                assignment.setAllTimeslots(blockTimeslots);
                assignment.setAllRooms(rooms);

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

                // NEW: Load room requirement fields - must happen BEFORE the
                // isRoomFixed()/getMatchingRooms() correction below, since both
                // now factor satisfiesRoomType into whether a teacher's/group's
                // fixed room actually applies (see CourseBlockAssignment).
                String satisfiesRoomType = rs.getString("satisfies_room_type");
                if (satisfiesRoomType != null && !satisfiesRoomType.isEmpty()) {
                    assignment.setSatisfiesRoomType(satisfiesRoomType);
                }

                String preferredRoomHint = rs.getString("preferred_room_hint");
                if (preferredRoomHint != null && !preferredRoomHint.isEmpty()) {
                    assignment.setPreferredRoomHint(preferredRoomHint);
                }

                // A non-pinned "room-fixed" block's room must always match its
                // group's preferred room / teacher's required room, not whatever
                // room_name happens to be sitting on this row - that value can be
                // stale relative to a preference that was set/changed after this
                // row was created (course_block_assignment is pure input; nothing
                // retroactively fixes it), and the solver's construction heuristic
                // never touches an already-non-null variable, so a stale value
                // would otherwise survive every future solve unnoticed. Pinned
                // rows are deliberately left alone here - see
                // teacherRequiredRoomMustBeUsed, which reports this as a hard
                // violation instead of silently rewriting locked-in data.
                //
                // Delegates to getMatchingRooms() itself (a singleton for a
                // genuinely fixed block, empty for a dangling required-room
                // reference) rather than re-deriving the teacher-then-group
                // priority here, so this stays in lockstep with its
                // compatibility-fallback logic instead of a second copy that
                // could drift out of sync.
                if (!pinned && assignment.isRoomFixed()) {
                    List<Room> matchingRooms = assignment.getMatchingRooms();
                    assignment.setRoom(matchingRooms.size() == 1 ? matchingRooms.get(0) : null);
                }

                if (assignment.isPinned()) {
                    System.out.println("Loaded pinned block assignment: " + assignment);
                } else {
                    System.out.println("Loaded unpinned block assignment: " + assignment.getId());
                }

                assignments.add(assignment);
            }
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
