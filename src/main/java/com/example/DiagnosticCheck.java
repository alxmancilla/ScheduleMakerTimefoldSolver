import java.sql.*;

public class DiagnosticCheck {
    public static void main(String[] args) {
        String jdbcUrl = "jdbc:postgresql://localhost:5432/school_schedule";
        String username = "mancilla";
        String password = "";

        try (Connection conn = DriverManager.getConnection(jdbcUrl, username, password)) {
            // Check table counts
            try (Statement stmt = conn.createStatement()) {
                checkCount(stmt, "teacher");
                checkCount(stmt, "course");
                checkCount(stmt, "room");
                checkCount(stmt, "timeslot");
                checkCount(stmt, "student_group");
                checkCount(stmt, "group_course");
                checkCount(stmt, "course_assignment");

                System.out.println("\n=== Sample course_assignment data ===");
                String sql = "SELECT id, group_id, course_id, sequence_index, teacher_id, timeslot_id, room_name FROM course_assignment LIMIT 5";
                try (ResultSet rs = stmt.executeQuery(sql)) {
                    while (rs.next()) {
                        System.out.printf(
                                "ID: %s, Group: %s, Course: %s, Seq: %d, Teacher: %s, Timeslot: %s, Room: %s%n",
                                rs.getString("id"), rs.getString("group_id"), rs.getString("course_id"),
                                rs.getInt("sequence_index"), rs.getString("teacher_id"),
                                rs.getString("timeslot_id"), rs.getString("room_name"));
                    }
                }
            }
        } catch (SQLException e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
        }
    }

    static void checkCount(Statement stmt, String table) throws SQLException {
        String sql = "SELECT COUNT(*) as cnt FROM " + table;
        try (ResultSet rs = stmt.executeQuery(sql)) {
            if (rs.next()) {
                System.out.println(table + ": " + rs.getInt("cnt"));
            }
        }
    }
}
