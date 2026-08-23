package com.example.data;

import ai.timefold.solver.core.api.score.buildin.hardsoft.HardSoftScore;
import com.example.domain.*;
import java.sql.*;
import java.util.*;

/**
 * DataSaver persists a solved SchoolSchedule as a new run in the schedule
 * run history, rather than overwriting course_block_assignment in place.
 * course_block_assignment is pure input from this point on -
 * block_timeslot_id there is only ever meaningful for pinned = true rows.
 * (Teacher and room are pre-assigned from database, only timeslot is solved,
 * and even that is never written back onto course_block_assignment itself.)
 *
 * Each save inserts one schedule_run row (with its hard/soft score) and one
 * schedule_run_result row per assignment (its solved, or still-unassigned,
 * timeslot for that run), then prunes schedule_run down to the most recent
 * {@link #MAX_RETAINED_RUNS} rows - ON DELETE CASCADE cleans up the
 * corresponding schedule_run_result rows automatically. Anything that needs
 * "the current schedule" (DataLoader, the web Schedule View, PDF reports)
 * reads through the course_block_assignment_current view instead, which
 * resolves pinned rows to their own input timeslot and every other row to
 * the most recent run's result.
 */
public class DataSaver {

    private static final int MAX_RETAINED_RUNS = 10;

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
     * Save a solved block-based schedule as a new schedule_run, then prune
     * old runs beyond {@link #MAX_RETAINED_RUNS}.
     *
     * @param schedule The solved SchoolSchedule from the Timefold solver
     * @throws SQLException if database access fails
     */
    public void saveSchedule(SchoolSchedule schedule) throws SQLException {
        try (Connection conn = DriverManager.getConnection(jdbcUrl, username, password)) {
            conn.setAutoCommit(false); // Start transaction
            try {
                int runId = insertScheduleRun(conn, schedule.getScore());
                insertScheduleRunResults(conn, runId, schedule.getCourseBlockAssignments());
                pruneOldRuns(conn);
                conn.commit();
                System.out.println("✓ Schedule run #" + runId + " saved (keeping the most recent "
                        + MAX_RETAINED_RUNS + " runs)");
            } catch (SQLException e) {
                conn.rollback();
                System.err.println("✗ Failed to save schedule. Changes rolled back.");
                throw e;
            }
        }
    }

    private int insertScheduleRun(Connection conn, HardSoftScore score) throws SQLException {
        String sql = "INSERT INTO schedule_run (hard_score, soft_score) VALUES (?, ?)";
        try (PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            stmt.setInt(1, score != null ? score.hardScore() : 0);
            stmt.setInt(2, score != null ? score.softScore() : 0);
            stmt.executeUpdate();
            try (ResultSet keys = stmt.getGeneratedKeys()) {
                if (keys.next()) {
                    return keys.getInt(1);
                }
            }
        }
        throw new SQLException("Failed to obtain generated schedule_run id");
    }

    private void insertScheduleRunResults(Connection conn, int runId, List<CourseBlockAssignment> assignments)
            throws SQLException {
        String sql = "INSERT INTO schedule_run_result (schedule_run_id, assignment_id, block_timeslot_id) VALUES (?, ?, ?)";

        int unassignedCount = 0;
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            for (CourseBlockAssignment assignment : assignments) {
                String blockTimeslotId = assignment.getTimeslot() != null ? assignment.getTimeslot().getId() : null;

                if (blockTimeslotId == null) {
                    unassignedCount++;
                }

                stmt.setInt(1, runId);
                stmt.setString(2, assignment.getId());
                stmt.setString(3, blockTimeslotId);
                stmt.addBatch();
            }
            stmt.executeBatch();
        }

        System.out.println("  Recorded " + assignments.size() + " assignment results for run #" + runId);
        if (unassignedCount > 0) {
            System.out.println("  ⚠ Warning: " + unassignedCount + " assignments remain unassigned");
        }
    }

    private void pruneOldRuns(Connection conn) throws SQLException {
        String sql = "DELETE FROM schedule_run WHERE id NOT IN "
                + "(SELECT id FROM schedule_run ORDER BY created_at DESC LIMIT " + MAX_RETAINED_RUNS + ")";
        try (Statement stmt = conn.createStatement()) {
            stmt.executeUpdate(sql);
        }
    }

    /**
     * Fetch the current block schedule from database (resolved via
     * course_block_assignment_current: pinned rows keep their own input
     * timeslot, every other row gets the most recent schedule_run's result)
     * and return it as a SchoolSchedule. Useful for verifying saved results.
     *
     * @return SchoolSchedule with the current resolved block assignments
     * @throws SQLException if database access fails
     */
    public SchoolSchedule loadCurrentBlockSchedule() throws SQLException {
        DataLoader loader = new DataLoader(jdbcUrl, username, password);
        return loader.loadDataForBlockScheduling();
    }

    /**
     * Get statistics about the current (resolved) block schedule.
     *
     * @return Map with block assignment statistics
     * @throws SQLException if database access fails
     */
    public Map<String, Integer> getBlockScheduleStatistics() throws SQLException {
        Map<String, Integer> stats = new HashMap<>();

        try (Connection conn = DriverManager.getConnection(jdbcUrl, username, password)) {
            // Total block assignments (same count on the base table or the view - the
            // view is a 1:1 LEFT JOIN over course_block_assignment)
            try (Statement stmt = conn.createStatement();
                    ResultSet rs = stmt.executeQuery("SELECT COUNT(*) as count FROM course_block_assignment")) {
                if (rs.next()) {
                    stats.put("total_block_assignments", rs.getInt("count"));
                }
            }

            // Assigned / unassigned block assignments: read through the resolved view,
            // since course_block_assignment.block_timeslot_id itself is only meaningful
            // for pinned rows now.
            try (Statement stmt = conn.createStatement();
                    ResultSet rs = stmt.executeQuery(
                            "SELECT COUNT(*) as count FROM course_block_assignment_current WHERE teacher_id IS NOT NULL AND block_timeslot_id IS NOT NULL AND room_name IS NOT NULL")) {
                if (rs.next()) {
                    stats.put("assigned_block_assignments", rs.getInt("count"));
                }
            }

            try (Statement stmt = conn.createStatement();
                    ResultSet rs = stmt.executeQuery(
                            "SELECT COUNT(*) as count FROM course_block_assignment_current WHERE teacher_id IS NULL OR block_timeslot_id IS NULL OR room_name IS NULL")) {
                if (rs.next()) {
                    stats.put("unassigned_block_assignments", rs.getInt("count"));
                }
            }

            // Unique teachers/rooms: teacher_id/room_name are untouched by this view
            // (always input), so the base table is equally correct here.
            try (Statement stmt = conn.createStatement();
                    ResultSet rs = stmt.executeQuery(
                            "SELECT COUNT(DISTINCT teacher_id) as count FROM course_block_assignment WHERE teacher_id IS NOT NULL")) {
                if (rs.next()) {
                    stats.put("unique_teachers_assigned", rs.getInt("count"));
                }
            }

            try (Statement stmt = conn.createStatement();
                    ResultSet rs = stmt.executeQuery(
                            "SELECT COUNT(DISTINCT block_timeslot_id) as count FROM course_block_assignment_current WHERE block_timeslot_id IS NOT NULL")) {
                if (rs.next()) {
                    stats.put("unique_block_timeslots_used", rs.getInt("count"));
                }
            }

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
