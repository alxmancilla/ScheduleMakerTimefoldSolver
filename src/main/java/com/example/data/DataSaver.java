package com.example.data;

import com.example.domain.*;
import java.sql.*;
import java.util.*;

/**
 * DataSaver persists the solved SchoolSchedule results back to the PostgreSQL
 * database.
 * Updates the course_block_assignment table with the timeslot assignments
 * determined by the Timefold solver.
 * (Teacher and room are pre-assigned from database, only timeslot is solved)
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
     * Save the solved block-based schedule results to the database.
     * Updates the course_block_assignment table with block timeslot assignments.
     *
     * @param schedule The solved SchoolSchedule from the Timefold solver
     * @throws SQLException if database access fails
     */
    public void saveSchedule(SchoolSchedule schedule) throws SQLException {
        try (Connection conn = DriverManager.getConnection(jdbcUrl, username, password)) {
            conn.setAutoCommit(false); // Start transaction
            try {
                saveCourseBlockAssignments(conn, schedule.getCourseBlockAssignments());
                conn.commit();
                System.out.println("✓ Block-based schedule successfully saved to database");
            } catch (SQLException e) {
                conn.rollback();
                System.err.println("✗ Failed to save schedule. Changes rolled back.");
                throw e;
            }
        }
    }

    /**
     * Update all course block assignments with their solved block timeslot
     * assignments.
     * Note: Teacher and room are pre-assigned from database, only timeslot is
     * updated.
     */
    private void saveCourseBlockAssignments(Connection conn, List<CourseBlockAssignment> assignments)
            throws SQLException {
        String sql = "UPDATE course_block_assignment SET block_timeslot_id = ?, updated_at = CURRENT_TIMESTAMP WHERE id = ?";

        int totalUpdated = 0;
        int unassignedCount = 0;

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            for (CourseBlockAssignment assignment : assignments) {
                String blockTimeslotId = assignment.getTimeslot() != null ? assignment.getTimeslot().getId() : null;

                if (blockTimeslotId == null) {
                    unassignedCount++;
                }

                stmt.setString(1, blockTimeslotId);
                stmt.setString(2, assignment.getId());
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
     * Fetch the current block-based schedule from database and return as a
     * SchoolSchedule.
     * Useful for verifying saved results.
     *
     * @return SchoolSchedule with current block assignments from database
     * @throws SQLException if database access fails
     */
    public SchoolSchedule loadCurrentSchedule() throws SQLException {
        DataLoader loader = new DataLoader(jdbcUrl, username, password);
        return loader.loadDataForBlockScheduling();
    }

    /**
     * Get statistics about the saved block-based schedule.
     *
     * @return Map with assignment statistics
     * @throws SQLException if database access fails
     */
    public Map<String, Integer> getScheduleStatistics() throws SQLException {
        Map<String, Integer> stats = new HashMap<>();

        try (Connection conn = DriverManager.getConnection(jdbcUrl, username, password)) {
            // Total block assignments
            try (Statement stmt = conn.createStatement();
                    ResultSet rs = stmt.executeQuery("SELECT COUNT(*) as count FROM course_block_assignment")) {
                if (rs.next()) {
                    stats.put("total_assignments", rs.getInt("count"));
                }
            }

            // Assigned block assignments (timeslot assigned)
            try (Statement stmt = conn.createStatement();
                    ResultSet rs = stmt.executeQuery(
                            "SELECT COUNT(*) as count FROM course_block_assignment WHERE block_timeslot_id IS NOT NULL")) {
                if (rs.next()) {
                    stats.put("assigned_assignments", rs.getInt("count"));
                }
            }

            // Unassigned block assignments (no timeslot)
            try (Statement stmt = conn.createStatement();
                    ResultSet rs = stmt.executeQuery(
                            "SELECT COUNT(*) as count FROM course_block_assignment WHERE block_timeslot_id IS NULL")) {
                if (rs.next()) {
                    stats.put("unassigned_assignments", rs.getInt("count"));
                }
            }

            // Unique teachers assigned
            try (Statement stmt = conn.createStatement();
                    ResultSet rs = stmt.executeQuery(
                            "SELECT COUNT(DISTINCT teacher_id) as count FROM course_block_assignment WHERE teacher_id IS NOT NULL")) {
                if (rs.next()) {
                    stats.put("unique_teachers_assigned", rs.getInt("count"));
                }
            }

            // Unique block timeslots used
            try (Statement stmt = conn.createStatement();
                    ResultSet rs = stmt.executeQuery(
                            "SELECT COUNT(DISTINCT block_timeslot_id) as count FROM course_block_assignment WHERE block_timeslot_id IS NOT NULL")) {
                if (rs.next()) {
                    stats.put("unique_timeslots_used", rs.getInt("count"));
                }
            }

            // Unique rooms used
            try (Statement stmt = conn.createStatement();
                    ResultSet rs = stmt.executeQuery(
                            "SELECT COUNT(DISTINCT room_name) as count FROM course_block_assignment WHERE room_name IS NOT NULL")) {
                if (rs.next()) {
                    stats.put("unique_rooms_used", rs.getInt("count"));
                }
            }
        }

        return stats;
    }

    /**
     * Example usage: Save a solved schedule to the database.
     * stmt.setString(4, assignment.getId());
     * stmt.addBatch();
     * }
     * 
     * int[] updateCounts = stmt.executeBatch();
     * for (int count : updateCounts) {
     * if (count > 0) {
     * totalUpdated++;
     * }
     * }
     * }
     * 
     * System.out.println(" Updated " + totalUpdated + " course block assignments");
     * if (unassignedCount > 0) {
     * System.out.println(" ⚠ Warning: " + unassignedCount + " block assignments
     * remain unassigned");
     * }
     * }
     * 
     * /**
     * Clear all block assignments (set teacher, block timeslot, and room to NULL)
     * and reset the block schedule.
     * Useful for starting a fresh solve.
     *
     * @throws SQLException if database access fails
     */
    public void clearBlockSchedule() throws SQLException {
        try (Connection conn = DriverManager.getConnection(jdbcUrl, username, password)) {
            conn.setAutoCommit(false);
            try {
                String sql = "UPDATE course_block_assignment SET teacher_id = NULL, block_timeslot_id = NULL, room_name = NULL, updated_at = CURRENT_TIMESTAMP";
                try (Statement stmt = conn.createStatement()) {
                    int count = stmt.executeUpdate(sql);
                    conn.commit();
                    System.out.println("✓ Cleared " + count + " course block assignments");
                }
            } catch (SQLException e) {
                conn.rollback();
                System.err.println("✗ Failed to clear block schedule. Changes rolled back.");
                throw e;
            }
        }
    }

    /**
     * Fetch the current block schedule from database and return as a
     * SchoolSchedule.
     * Useful for verifying saved results.
     *
     * @return SchoolSchedule with current block assignments from database
     * @throws SQLException if database access fails
     */
    public SchoolSchedule loadCurrentBlockSchedule() throws SQLException {
        DataLoader loader = new DataLoader(jdbcUrl, username, password);
        return loader.loadDataForBlockScheduling();
    }

    /**
     * Get statistics about the saved block schedule.
     *
     * @return Map with block assignment statistics
     * @throws SQLException if database access fails
     */
    public Map<String, Integer> getBlockScheduleStatistics() throws SQLException {
        Map<String, Integer> stats = new HashMap<>();

        try (Connection conn = DriverManager.getConnection(jdbcUrl, username, password)) {
            // Total block assignments
            try (Statement stmt = conn.createStatement();
                    ResultSet rs = stmt.executeQuery("SELECT COUNT(*) as count FROM course_block_assignment")) {
                if (rs.next()) {
                    stats.put("total_block_assignments", rs.getInt("count"));
                }
            }

            // Assigned block assignments
            try (Statement stmt = conn.createStatement();
                    ResultSet rs = stmt.executeQuery(
                            "SELECT COUNT(*) as count FROM course_block_assignment WHERE teacher_id IS NOT NULL AND block_timeslot_id IS NOT NULL AND room_name IS NOT NULL")) {
                if (rs.next()) {
                    stats.put("assigned_block_assignments", rs.getInt("count"));
                }
            }

            // Unassigned block assignments
            try (Statement stmt = conn.createStatement();
                    ResultSet rs = stmt.executeQuery(
                            "SELECT COUNT(*) as count FROM course_block_assignment WHERE teacher_id IS NULL OR block_timeslot_id IS NULL OR room_name IS NULL")) {
                if (rs.next()) {
                    stats.put("unassigned_block_assignments", rs.getInt("count"));
                }
            }

            // Unique teachers assigned
            try (Statement stmt = conn.createStatement();
                    ResultSet rs = stmt.executeQuery(
                            "SELECT COUNT(DISTINCT teacher_id) as count FROM course_block_assignment WHERE teacher_id IS NOT NULL")) {
                if (rs.next()) {
                    stats.put("unique_teachers_assigned", rs.getInt("count"));
                }
            }

            // Unique block timeslots used
            try (Statement stmt = conn.createStatement();
                    ResultSet rs = stmt.executeQuery(
                            "SELECT COUNT(DISTINCT block_timeslot_id) as count FROM course_block_assignment WHERE block_timeslot_id IS NOT NULL")) {
                if (rs.next()) {
                    stats.put("unique_block_timeslots_used", rs.getInt("count"));
                }
            }

            // Unique rooms used
            try (Statement stmt = conn.createStatement();
                    ResultSet rs = stmt.executeQuery(
                            "SELECT COUNT(DISTINCT room_name) as count FROM course_block_assignment WHERE room_name IS NOT NULL")) {
                if (rs.next()) {
                    stats.put("unique_rooms_used", rs.getInt("count"));
                }
            }
        }

        return stats;
    }
}
