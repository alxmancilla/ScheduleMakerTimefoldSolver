package com.example.data;

import com.example.domain.*;
import java.sql.*;
import java.util.*;

/**
 * DataSaver persists the solved SchoolSchedule results back to the PostgreSQL
 * database.
 * Updates the course_assignment table with the teacher, timeslot, and room
 * assignments
 * determined by the Timefold solver.
 */
public class DataSaver {

    private final String jdbcUrl;
    private final String username;
    private final String password;

    /**
     * Create a DataSaver with database connection parameters.
     *
     * @param jdbcUrl  JDBC URL (e.g.,
     *                 "jdbc:postgresql://localhost:5432/school_schedule")
     * @param username Database username
     * @param password Database password
     */
    public DataSaver(String jdbcUrl, String username, String password) {
        this.jdbcUrl = jdbcUrl;
        this.username = username;
        this.password = password;
    }

    /**
     * Save the solved schedule results to the database.
     * Updates the course_assignment table with teacher, timeslot, and room
     * assignments.
     *
     * @param schedule The solved SchoolSchedule from the Timefold solver
     * @throws SQLException if database access fails
     */
    public void saveSchedule(SchoolSchedule schedule) throws SQLException {
        try (Connection conn = DriverManager.getConnection(jdbcUrl, username, password)) {
            conn.setAutoCommit(false); // Start transaction
            try {
                saveCourseAssignments(conn, schedule.getCourseAssignments());
                conn.commit();
                System.out.println("✓ Schedule successfully saved to database");
            } catch (SQLException e) {
                conn.rollback();
                System.err.println("✗ Failed to save schedule. Changes rolled back.");
                throw e;
            }
        }
    }

    /**
     * Update all course assignments with their solved teacher, timeslot, and room
     * assignments.
     */
    private void saveCourseAssignments(Connection conn, List<CourseAssignment> assignments) throws SQLException {
        String sql = "UPDATE course_assignment SET teacher_id = ?, timeslot_id = ?, room_name = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ?";

        int totalUpdated = 0;
        int unassignedCount = 0;

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            for (CourseAssignment assignment : assignments) {
                String teacherId = assignment.getTeacher() != null ? assignment.getTeacher().getId() : null;
                String timeslotId = assignment.getTimeslot() != null ? assignment.getTimeslot().getId() : null;
                String roomName = assignment.getRoom() != null ? assignment.getRoom().getName() : null;

                if (teacherId == null || timeslotId == null || roomName == null) {
                    unassignedCount++;
                }

                stmt.setString(1, teacherId);
                stmt.setString(2, timeslotId);
                stmt.setString(3, roomName);
                stmt.setString(4, assignment.getId());
                stmt.addBatch();
            }

            int[] updateCounts = stmt.executeBatch();
            for (int count : updateCounts) {
                if (count > 0) {
                    totalUpdated++;
                }
            }
        }

        System.out.println("  Updated " + totalUpdated + " course assignments");
        if (unassignedCount > 0) {
            System.out.println("  ⚠ Warning: " + unassignedCount + " assignments remain unassigned");
        }
    }

    /**
     * Clear all assignments (set teacher, timeslot, and room to NULL) and reset the
     * schedule.
     * Useful for starting a fresh solve.
     *
     * @throws SQLException if database access fails
     */
    public void clearSchedule() throws SQLException {
        try (Connection conn = DriverManager.getConnection(jdbcUrl, username, password)) {
            conn.setAutoCommit(false);
            try {
                String sql = "UPDATE course_assignment SET teacher_id = NULL, timeslot_id = NULL, room_name = NULL, updated_at = CURRENT_TIMESTAMP";
                try (Statement stmt = conn.createStatement()) {
                    int count = stmt.executeUpdate(sql);
                    conn.commit();
                    System.out.println("✓ Cleared " + count + " course assignments");
                }
            } catch (SQLException e) {
                conn.rollback();
                System.err.println("✗ Failed to clear schedule. Changes rolled back.");
                throw e;
            }
        }
    }

    /**
     * Fetch the current schedule from database and return as a SchoolSchedule.
     * Useful for verifying saved results.
     *
     * @return SchoolSchedule with current assignments from database
     * @throws SQLException if database access fails
     */
    public SchoolSchedule loadCurrentSchedule() throws SQLException {
        DataLoader loader = new DataLoader(jdbcUrl, username, password);
        return loader.loadData();
    }

    /**
     * Get statistics about the saved schedule.
     *
     * @return Map with assignment statistics
     * @throws SQLException if database access fails
     */
    public Map<String, Integer> getScheduleStatistics() throws SQLException {
        Map<String, Integer> stats = new HashMap<>();

        try (Connection conn = DriverManager.getConnection(jdbcUrl, username, password)) {
            // Total assignments
            try (Statement stmt = conn.createStatement();
                    ResultSet rs = stmt.executeQuery("SELECT COUNT(*) as count FROM course_assignment")) {
                if (rs.next()) {
                    stats.put("total_assignments", rs.getInt("count"));
                }
            }

            // Assigned assignments
            try (Statement stmt = conn.createStatement();
                    ResultSet rs = stmt.executeQuery(
                            "SELECT COUNT(*) as count FROM course_assignment WHERE teacher_id IS NOT NULL AND timeslot_id IS NOT NULL AND room_name IS NOT NULL")) {
                if (rs.next()) {
                    stats.put("assigned_assignments", rs.getInt("count"));
                }
            }

            // Unassigned assignments
            try (Statement stmt = conn.createStatement();
                    ResultSet rs = stmt.executeQuery(
                            "SELECT COUNT(*) as count FROM course_assignment WHERE teacher_id IS NULL OR timeslot_id IS NULL OR room_name IS NULL")) {
                if (rs.next()) {
                    stats.put("unassigned_assignments", rs.getInt("count"));
                }
            }

            // Unique teachers assigned
            try (Statement stmt = conn.createStatement();
                    ResultSet rs = stmt.executeQuery(
                            "SELECT COUNT(DISTINCT teacher_id) as count FROM course_assignment WHERE teacher_id IS NOT NULL")) {
                if (rs.next()) {
                    stats.put("unique_teachers_assigned", rs.getInt("count"));
                }
            }

            // Unique timeslots used
            try (Statement stmt = conn.createStatement();
                    ResultSet rs = stmt.executeQuery(
                            "SELECT COUNT(DISTINCT timeslot_id) as count FROM course_assignment WHERE timeslot_id IS NOT NULL")) {
                if (rs.next()) {
                    stats.put("unique_timeslots_used", rs.getInt("count"));
                }
            }

            // Unique rooms used
            try (Statement stmt = conn.createStatement();
                    ResultSet rs = stmt.executeQuery(
                            "SELECT COUNT(DISTINCT room_name) as count FROM course_assignment WHERE room_name IS NOT NULL")) {
                if (rs.next()) {
                    stats.put("unique_rooms_used", rs.getInt("count"));
                }
            }
        }

        return stats;
    }
}
