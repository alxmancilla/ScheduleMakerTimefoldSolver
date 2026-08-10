package com.example.util;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.FileOutputStream;
import java.io.IOException;
import java.sql.*;
import java.util.*;

/**
 * Exports all data from the PostgreSQL database to an Excel file.
 * Creates sheets for: Teachers, Courses, Rooms, Timeslots, Groups,
 * Group_Courses, Assignments
 */
public class DatabaseToExcelExporter {

    private final String jdbcUrl;
    private final String username;
    private final String password;

    public DatabaseToExcelExporter(String jdbcUrl, String username, String password) {
        this.jdbcUrl = jdbcUrl;
        this.username = username;
        this.password = password;
    }

    /**
     * Export all database data to an Excel file.
     *
     * @param outputPath Path to the output Excel file
     * @throws SQLException if database access fails
     * @throws IOException  if file writing fails
     */
    public void exportToExcel(String outputPath) throws SQLException, IOException {
        try (Connection conn = DriverManager.getConnection(jdbcUrl, username, password);
                Workbook wb = new XSSFWorkbook()) {

            System.out.println("Exporting database to Excel: " + outputPath);

            // Create all sheets
            exportTeachers(conn, wb);
            exportCourses(conn, wb);
            exportRooms(conn, wb);
            exportTimeslots(conn, wb);
            exportGroups(conn, wb);
            exportGroupCourses(conn, wb);
            // exportCourseAssignments(conn, wb);

            // Write to file
            try (FileOutputStream fos = new FileOutputStream(outputPath)) {
                wb.write(fos);
            }

            System.out.println("✓ Excel file successfully created: " + outputPath);
        }
    }

    private void exportTeachers(Connection conn, Workbook wb) throws SQLException {
        Sheet sheet = wb.createSheet("Teachers");

        // Header
        Row header = sheet.createRow(0);
        header.createCell(0).setCellValue("id");
        header.createCell(1).setCellValue("name");
        header.createCell(2).setCellValue("last_name");
        header.createCell(3).setCellValue("max_hours_per_week");
        header.createCell(4).setCellValue("qualifications");
        header.createCell(5).setCellValue("availability");

        // Load teachers with qualifications and availability
        Map<String, List<String>> qualifications = loadTeacherQualifications(conn);
        Map<String, String> availability = loadTeacherAvailability(conn);

        String sql = "SELECT id, name, last_name, max_hours_per_week FROM teacher ORDER BY id";
        try (Statement stmt = conn.createStatement();
                ResultSet rs = stmt.executeQuery(sql)) {

            int rowNum = 1;
            while (rs.next()) {
                Row row = sheet.createRow(rowNum++);
                String teacherId = rs.getString("id");

                row.createCell(0).setCellValue(teacherId);
                row.createCell(1).setCellValue(rs.getString("name"));
                row.createCell(2).setCellValue(rs.getString("last_name"));
                row.createCell(3).setCellValue(rs.getInt("max_hours_per_week"));

                // Qualifications (semicolon-separated)
                List<String> quals = qualifications.getOrDefault(teacherId, new ArrayList<>());
                row.createCell(4).setCellValue(String.join(";", quals));

                // Availability (DAY:hour,hour;...)
                row.createCell(5).setCellValue(availability.getOrDefault(teacherId, ""));
            }
        }

        autosizeColumns(sheet, 6);
        System.out.println("  ✓ Exported Teachers sheet");
    }

    private Map<String, List<String>> loadTeacherQualifications(Connection conn) throws SQLException {
        Map<String, List<String>> result = new HashMap<>();
        String sql = "SELECT teacher_id, qualification FROM teacher_qualification ORDER BY teacher_id, qualification";

        try (Statement stmt = conn.createStatement();
                ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                String teacherId = rs.getString("teacher_id");
                String qual = rs.getString("qualification");
                result.computeIfAbsent(teacherId, k -> new ArrayList<>()).add(qual);
            }
        }
        return result;
    }

    private Map<String, String> loadTeacherAvailability(Connection conn) throws SQLException {
        Map<String, Map<Integer, List<Integer>>> availData = new HashMap<>();
        String sql = "SELECT teacher_id, day_of_week, hour FROM teacher_availability ORDER BY teacher_id, day_of_week, hour";

        try (Statement stmt = conn.createStatement();
                ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                String teacherId = rs.getString("teacher_id");
                int dayOfWeek = rs.getInt("day_of_week");
                int hour = rs.getInt("hour");

                availData.computeIfAbsent(teacherId, k -> new HashMap<>())
                        .computeIfAbsent(dayOfWeek, k -> new ArrayList<>())
                        .add(hour);
            }
        }

        // Convert to string format: "1:7,8,9;2:7,8,9;..."
        Map<String, String> result = new HashMap<>();
        for (Map.Entry<String, Map<Integer, List<Integer>>> entry : availData.entrySet()) {
            StringBuilder sb = new StringBuilder();
            for (Map.Entry<Integer, List<Integer>> dayEntry : entry.getValue().entrySet()) {
                if (sb.length() > 0)
                    sb.append(";");
                sb.append(dayEntry.getKey()).append(":");
                sb.append(String.join(",", dayEntry.getValue().stream()
                        .map(String::valueOf).toArray(String[]::new)));
            }
            result.put(entry.getKey(), sb.toString());
        }
        return result;
    }

    private void exportCourses(Connection conn, Workbook wb) throws SQLException {
        Sheet sheet = wb.createSheet("Courses");

        // Header
        Row header = sheet.createRow(0);
        header.createCell(0).setCellValue("id");
        header.createCell(1).setCellValue("name");
        header.createCell(2).setCellValue("abbreviation");
        header.createCell(3).setCellValue("semester");
        header.createCell(4).setCellValue("component");
        header.createCell(5).setCellValue("room_requirement");
        header.createCell(6).setCellValue("required_hours_per_week");
        header.createCell(7).setCellValue("active");

        String sql = "SELECT id, name, abbreviation, semester, component, room_requirement, required_hours_per_week, active FROM course ORDER BY id";
        try (Statement stmt = conn.createStatement();
                ResultSet rs = stmt.executeQuery(sql)) {

            int rowNum = 1;
            while (rs.next()) {
                Row row = sheet.createRow(rowNum++);
                row.createCell(0).setCellValue(rs.getString("id"));
                row.createCell(1).setCellValue(rs.getString("name"));
                row.createCell(2).setCellValue(rs.getString("abbreviation"));
                row.createCell(3).setCellValue(rs.getString("semester"));
                row.createCell(4).setCellValue(rs.getString("component"));
                row.createCell(5).setCellValue(rs.getString("room_requirement"));
                row.createCell(6).setCellValue(rs.getInt("required_hours_per_week"));
                row.createCell(7).setCellValue(rs.getBoolean("active"));
            }
        }

        autosizeColumns(sheet, 8);
        System.out.println("  ✓ Exported Courses sheet");
    }

    private void exportRooms(Connection conn, Workbook wb) throws SQLException {
        Sheet sheet = wb.createSheet("Rooms");

        // Header
        Row header = sheet.createRow(0);
        header.createCell(0).setCellValue("name");
        header.createCell(1).setCellValue("building");
        header.createCell(2).setCellValue("type");

        String sql = "SELECT name, building, type FROM room ORDER BY name";
        try (Statement stmt = conn.createStatement();
                ResultSet rs = stmt.executeQuery(sql)) {

            int rowNum = 1;
            while (rs.next()) {
                Row row = sheet.createRow(rowNum++);
                row.createCell(0).setCellValue(rs.getString("name"));
                row.createCell(1).setCellValue(rs.getString("building"));
                row.createCell(2).setCellValue(rs.getString("type"));
            }
        }

        autosizeColumns(sheet, 3);
        System.out.println("  ✓ Exported Rooms sheet");
    }

    private void exportTimeslots(Connection conn, Workbook wb) throws SQLException {
        Sheet sheet = wb.createSheet("Timeslots");

        // Header
        Row header = sheet.createRow(0);
        header.createCell(0).setCellValue("id");
        header.createCell(1).setCellValue("day_of_week");
        header.createCell(2).setCellValue("hour");
        header.createCell(3).setCellValue("display_name");

        String sql = "SELECT id, day_of_week, hour, display_name FROM timeslot ORDER BY day_of_week, hour";
        try (Statement stmt = conn.createStatement();
                ResultSet rs = stmt.executeQuery(sql)) {

            int rowNum = 1;
            while (rs.next()) {
                Row row = sheet.createRow(rowNum++);
                row.createCell(0).setCellValue(rs.getString("id"));
                row.createCell(1).setCellValue(rs.getInt("day_of_week"));
                row.createCell(2).setCellValue(rs.getInt("hour"));
                row.createCell(3).setCellValue(rs.getString("display_name"));
            }
        }

        autosizeColumns(sheet, 4);
        System.out.println("  ✓ Exported Timeslots sheet");
    }

    private void exportGroups(Connection conn, Workbook wb) throws SQLException {
        Sheet sheet = wb.createSheet("Groups");

        // Header
        Row header = sheet.createRow(0);
        header.createCell(0).setCellValue("id");
        header.createCell(1).setCellValue("name");
        header.createCell(2).setCellValue("preferred_room_name");

        String sql = "SELECT id, name, preferred_room_name FROM student_group ORDER BY id";
        try (Statement stmt = conn.createStatement();
                ResultSet rs = stmt.executeQuery(sql)) {

            int rowNum = 1;
            while (rs.next()) {
                Row row = sheet.createRow(rowNum++);
                row.createCell(0).setCellValue(rs.getString("id"));
                row.createCell(1).setCellValue(rs.getString("name"));
                String preferredRoom = rs.getString("preferred_room_name");
                row.createCell(2).setCellValue(preferredRoom != null ? preferredRoom : "");
            }
        }

        autosizeColumns(sheet, 3);
        System.out.println("  ✓ Exported Groups sheet");
    }

    private void exportGroupCourses(Connection conn, Workbook wb) throws SQLException {
        Sheet sheet = wb.createSheet("Group_Courses");

        // Header
        Row header = sheet.createRow(0);
        header.createCell(0).setCellValue("group_id");
        header.createCell(1).setCellValue("course_name");

        String sql = "SELECT group_id, course_name FROM group_course ORDER BY group_id, course_name";
        try (Statement stmt = conn.createStatement();
                ResultSet rs = stmt.executeQuery(sql)) {

            int rowNum = 1;
            while (rs.next()) {
                Row row = sheet.createRow(rowNum++);
                row.createCell(0).setCellValue(rs.getString("group_id"));
                row.createCell(1).setCellValue(rs.getString("course_name"));
            }
        }

        autosizeColumns(sheet, 2);
        System.out.println("  ✓ Exported Group_Courses sheet");
    }

    private void exportCourseAssignments(Connection conn, Workbook wb) throws SQLException {
        Sheet sheet = wb.createSheet("Course_Assignments");

        // Header
        Row header = sheet.createRow(0);
        header.createCell(0).setCellValue("id");
        header.createCell(1).setCellValue("group_id");
        header.createCell(2).setCellValue("course_id");
        header.createCell(3).setCellValue("sequence_index");
        header.createCell(4).setCellValue("teacher_id");
        header.createCell(5).setCellValue("room_name");
        header.createCell(6).setCellValue("timeslot_id");
        header.createCell(7).setCellValue("pinned");

        String sql = "SELECT id, group_id, course_id, sequence_index, teacher_id, room_name, timeslot_id, pinned FROM course_assignment ORDER BY id";
        try (Statement stmt = conn.createStatement();
                ResultSet rs = stmt.executeQuery(sql)) {

            int rowNum = 1;
            while (rs.next()) {
                Row row = sheet.createRow(rowNum++);
                row.createCell(0).setCellValue(rs.getString("id"));
                row.createCell(1).setCellValue(rs.getString("group_id"));
                row.createCell(2).setCellValue(rs.getString("course_id"));
                row.createCell(3).setCellValue(rs.getInt("sequence_index"));

                String teacherId = rs.getString("teacher_id");
                row.createCell(4).setCellValue(teacherId != null ? teacherId : "");

                String roomName = rs.getString("room_name");
                row.createCell(5).setCellValue(roomName != null ? roomName : "");

                String timeslotId = rs.getString("timeslot_id");
                row.createCell(6).setCellValue(timeslotId != null ? timeslotId : "");

                row.createCell(7).setCellValue(rs.getBoolean("pinned"));
            }
        }

        autosizeColumns(sheet, 8);
        System.out.println("  ✓ Exported Course_Assignments sheet");
    }

    private void autosizeColumns(Sheet sheet, int numColumns) {
        for (int i = 0; i < numColumns; i++) {
            sheet.autoSizeColumn(i);
        }
    }

    public static void main(String[] args) {
        if (args.length < 4) {
            System.err.println("Usage: DatabaseToExcelExporter <jdbcUrl> <username> <password> <outputFile>");
            System.err.println(
                    "Example: DatabaseToExcelExporter jdbc:postgresql://localhost:5432/school_schedule postgres password schedule-export.xlsx");
            System.exit(1);
        }

        String jdbcUrl = args[0];
        String username = args[1];
        String password = args[2];
        String outputFile = args[3];

        DatabaseToExcelExporter exporter = new DatabaseToExcelExporter(jdbcUrl, username, password);

        try {
            exporter.exportToExcel(outputFile);
        } catch (SQLException e) {
            System.err.println("✗ Database error: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        } catch (IOException e) {
            System.err.println("✗ File I/O error: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
}
