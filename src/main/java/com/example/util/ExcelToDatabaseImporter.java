package com.example.util;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.FileInputStream;
import java.io.IOException;
import java.sql.*;

/**
 * Imports data from an Excel file into the PostgreSQL database.
 * Reads sheets: Teachers, Courses, Rooms, Timeslots, Groups, Group_Courses,
 * Course_Assignments
 * 
 * IMPORTANT: This will CLEAR existing data and import fresh data from Excel.
 */
public class ExcelToDatabaseImporter {

    private final String jdbcUrl;
    private final String username;
    private final String password;

    public ExcelToDatabaseImporter(String jdbcUrl, String username, String password) {
        this.jdbcUrl = jdbcUrl;
        this.username = username;
        this.password = password;
    }

    /**
     * Import all data from Excel file to database.
     * Uses a transaction to ensure all-or-nothing import.
     *
     * @param excelPath Path to the Excel file
     * @throws SQLException if database access fails
     * @throws IOException  if file reading fails
     */
    public void importFromExcel(String excelPath) throws SQLException, IOException {
        System.out.println("Importing data from Excel: " + excelPath);

        try (FileInputStream fis = new FileInputStream(excelPath);
                Workbook wb = new XSSFWorkbook(fis);
                Connection conn = DriverManager.getConnection(jdbcUrl, username, password)) {

            conn.setAutoCommit(false); // Start transaction

            try {
                // Clear existing data (in reverse dependency order)
                clearExistingData(conn);

                // Import data (in dependency order)
                importTeachers(conn, wb);
                importCourses(conn, wb);
                importRooms(conn, wb);
                importTimeslots(conn, wb);
                importGroups(conn, wb);
                importGroupCourses(conn, wb);
                importCourseAssignments(conn, wb);

                conn.commit();
                System.out.println("✓ All data successfully imported!");

            } catch (Exception e) {
                conn.rollback();
                System.err.println("✗ Import failed. All changes rolled back.");
                throw e;
            }
        }
    }

    private void clearExistingData(Connection conn) throws SQLException {
        System.out.println("Clearing existing data...");

        // Delete in reverse dependency order
        String[] tables = {
                "course_assignment",
                "group_course",
                "student_group",
                "timeslot",
                "room",
                "course",
                "teacher_availability",
                "teacher_qualification",
                "teacher"
        };

        for (String table : tables) {
            try (Statement stmt = conn.createStatement()) {
                stmt.executeUpdate("DELETE FROM " + table);
                System.out.println("  ✓ Cleared " + table);
            }
        }
    }

    private void importTeachers(Connection conn, Workbook wb) throws SQLException {
        Sheet sheet = wb.getSheet("Teachers");
        if (sheet == null) {
            throw new IllegalArgumentException("Sheet 'Teachers' not found in Excel file");
        }

        String insertTeacher = "INSERT INTO teacher (id, name, last_name, max_hours_per_week, created_at, updated_at) VALUES (?, ?, ?, ?, NOW(), NOW())";
        String insertQual = "INSERT INTO teacher_qualification (teacher_id, qualification, created_at) VALUES (?, ?, NOW())";
        String insertAvail = "INSERT INTO teacher_availability (teacher_id, day_of_week, hour, created_at) VALUES (?, ?, ?, NOW())";

        try (PreparedStatement psTeacher = conn.prepareStatement(insertTeacher);
                PreparedStatement psQual = conn.prepareStatement(insertQual);
                PreparedStatement psAvail = conn.prepareStatement(insertAvail)) {

            int count = 0;
            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null)
                    continue;

                String id = getCellValueAsString(row.getCell(0));
                String name = getCellValueAsString(row.getCell(1));
                String lastName = getCellValueAsString(row.getCell(2));
                int maxHours = (int) getCellValueAsNumber(row.getCell(3));
                String qualifications = getCellValueAsString(row.getCell(4));
                String availability = getCellValueAsString(row.getCell(5));

                // Insert teacher
                psTeacher.setString(1, id);
                psTeacher.setString(2, name);
                psTeacher.setString(3, lastName);
                psTeacher.setInt(4, maxHours);
                psTeacher.executeUpdate();

                // Insert qualifications
                if (qualifications != null && !qualifications.trim().isEmpty()) {
                    for (String qual : qualifications.split(";")) {
                        qual = qual.trim();
                        if (!qual.isEmpty()) {
                            psQual.setString(1, id);
                            psQual.setString(2, qual);
                            psQual.executeUpdate();
                        }
                    }
                }

                // Insert availability (format: "1:7,8,9;2:7,8,9;...")
                if (availability != null && !availability.trim().isEmpty()) {
                    for (String dayBlock : availability.split(";")) {
                        String[] parts = dayBlock.split(":");
                        if (parts.length == 2) {
                            int dayOfWeek = Integer.parseInt(parts[0].trim());
                            for (String hourStr : parts[1].split(",")) {
                                int hour = Integer.parseInt(hourStr.trim());
                                psAvail.setString(1, id);
                                psAvail.setInt(2, dayOfWeek);
                                psAvail.setInt(3, hour);
                                psAvail.executeUpdate();
                            }
                        }
                    }
                }

                count++;
            }

            System.out.println("  ✓ Imported " + count + " teachers");
        }
    }

    private void importCourses(Connection conn, Workbook wb) throws SQLException {
        Sheet sheet = wb.getSheet("Courses");
        if (sheet == null) {
            throw new IllegalArgumentException("Sheet 'Courses' not found in Excel file");
        }

        String sql = "INSERT INTO course (id, name, abbreviation, semester, component, room_requirement, required_hours_per_week, active, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, NOW(), NOW())";

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            int count = 0;
            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null)
                    continue;

                ps.setString(1, getCellValueAsString(row.getCell(0)));
                ps.setString(2, getCellValueAsString(row.getCell(1)));
                ps.setString(3, getCellValueAsString(row.getCell(2)));
                ps.setString(4, getCellValueAsString(row.getCell(3)));
                ps.setString(5, getCellValueAsString(row.getCell(4)));
                ps.setString(6, getCellValueAsString(row.getCell(5)));
                ps.setInt(7, (int) getCellValueAsNumber(row.getCell(6)));
                ps.setBoolean(8, getCellValueAsBoolean(row.getCell(7)));
                ps.executeUpdate();
                count++;
            }

            System.out.println("  ✓ Imported " + count + " courses");
        }
    }

    private void importRooms(Connection conn, Workbook wb) throws SQLException {
        Sheet sheet = wb.getSheet("Rooms");
        if (sheet == null) {
            throw new IllegalArgumentException("Sheet 'Rooms' not found in Excel file");
        }

        String sql = "INSERT INTO room (name, building, type, created_at, updated_at) VALUES (?, ?, ?, NOW(), NOW())";

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            int count = 0;
            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null)
                    continue;

                ps.setString(1, getCellValueAsString(row.getCell(0)));
                ps.setString(2, getCellValueAsString(row.getCell(1)));
                ps.setString(3, getCellValueAsString(row.getCell(2)));
                ps.executeUpdate();
                count++;
            }

            System.out.println("  ✓ Imported " + count + " rooms");
        }
    }

    private void importTimeslots(Connection conn, Workbook wb) throws SQLException {
        Sheet sheet = wb.getSheet("Timeslots");
        if (sheet == null) {
            throw new IllegalArgumentException("Sheet 'Timeslots' not found in Excel file");
        }

        String sql = "INSERT INTO timeslot (id, day_of_week, hour, display_name, created_at, updated_at) VALUES (?, ?, ?, ?, NOW(), NOW())";

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            int count = 0;
            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null)
                    continue;

                ps.setString(1, getCellValueAsString(row.getCell(0)));
                ps.setInt(2, (int) getCellValueAsNumber(row.getCell(1)));
                ps.setInt(3, (int) getCellValueAsNumber(row.getCell(2)));
                ps.setString(4, getCellValueAsString(row.getCell(3)));
                ps.executeUpdate();
                count++;
            }

            System.out.println("  ✓ Imported " + count + " timeslots");
        }
    }

    private void importGroups(Connection conn, Workbook wb) throws SQLException {
        Sheet sheet = wb.getSheet("Groups");
        if (sheet == null) {
            throw new IllegalArgumentException("Sheet 'Groups' not found in Excel file");
        }

        String sql = "INSERT INTO student_group (id, name, preferred_room_name, created_at, updated_at) VALUES (?, ?, ?, NOW(), NOW())";

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            int count = 0;
            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null)
                    continue;

                String preferredRoom = getCellValueAsString(row.getCell(2));

                ps.setString(1, getCellValueAsString(row.getCell(0)));
                ps.setString(2, getCellValueAsString(row.getCell(1)));
                if (preferredRoom == null || preferredRoom.trim().isEmpty()) {
                    ps.setNull(3, Types.VARCHAR);
                } else {
                    ps.setString(3, preferredRoom);
                }
                ps.executeUpdate();
                count++;
            }

            System.out.println("  ✓ Imported " + count + " groups");
        }
    }

    private void importGroupCourses(Connection conn, Workbook wb) throws SQLException {
        Sheet sheet = wb.getSheet("Group_Courses");
        if (sheet == null) {
            throw new IllegalArgumentException("Sheet 'Group_Courses' not found in Excel file");
        }

        String sql = "INSERT INTO group_course (group_id, course_id, created_at) VALUES (?, ?, NOW())";

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            int count = 0;
            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null)
                    continue;

                ps.setString(1, getCellValueAsString(row.getCell(0)));
                ps.setString(2, getCellValueAsString(row.getCell(1)));
                ps.executeUpdate();
                count++;
            }

            System.out.println("  ✓ Imported " + count + " group-course relationships");
        }
    }

    private void importCourseAssignments(Connection conn, Workbook wb) throws SQLException {
        Sheet sheet = wb.getSheet("Course_Assignments");
        if (sheet == null) {
            throw new IllegalArgumentException("Sheet 'Course_Assignments' not found in Excel file");
        }

        String sql = "INSERT INTO course_assignment (id, group_id, course_id, sequence_index, teacher_id, room_name, timeslot_id, pinned, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, NOW(), NOW())";

        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            int count = 0;
            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null)
                    continue;

                String teacherId = getCellValueAsString(row.getCell(4));
                String roomName = getCellValueAsString(row.getCell(5));
                String timeslotId = getCellValueAsString(row.getCell(6));

                ps.setString(1, getCellValueAsString(row.getCell(0)));
                ps.setString(2, getCellValueAsString(row.getCell(1)));
                ps.setString(3, getCellValueAsString(row.getCell(2)));
                ps.setInt(4, (int) getCellValueAsNumber(row.getCell(3)));

                if (teacherId == null || teacherId.trim().isEmpty()) {
                    ps.setNull(5, Types.VARCHAR);
                } else {
                    ps.setString(5, teacherId);
                }

                if (roomName == null || roomName.trim().isEmpty()) {
                    ps.setNull(6, Types.VARCHAR);
                } else {
                    ps.setString(6, roomName);
                }

                if (timeslotId == null || timeslotId.trim().isEmpty()) {
                    ps.setNull(7, Types.VARCHAR);
                } else {
                    ps.setString(7, timeslotId);
                }

                ps.setBoolean(8, getCellValueAsBoolean(row.getCell(7)));
                ps.executeUpdate();
                count++;
            }

            System.out.println("  ✓ Imported " + count + " course assignments");
        }
    }

    // Helper methods to safely extract cell values
    private String getCellValueAsString(Cell cell) {
        if (cell == null)
            return null;

        switch (cell.getCellType()) {
            case STRING:
                return cell.getStringCellValue();
            case NUMERIC:
                return String.valueOf((long) cell.getNumericCellValue());
            case BOOLEAN:
                return String.valueOf(cell.getBooleanCellValue());
            case BLANK:
                return null;
            default:
                return cell.toString();
        }
    }

    private double getCellValueAsNumber(Cell cell) {
        if (cell == null)
            return 0;

        switch (cell.getCellType()) {
            case NUMERIC:
                return cell.getNumericCellValue();
            case STRING:
                try {
                    return Double.parseDouble(cell.getStringCellValue());
                } catch (NumberFormatException e) {
                    return 0;
                }
            default:
                return 0;
        }
    }

    private boolean getCellValueAsBoolean(Cell cell) {
        if (cell == null)
            return false;

        switch (cell.getCellType()) {
            case BOOLEAN:
                return cell.getBooleanCellValue();
            case STRING:
                String val = cell.getStringCellValue().toLowerCase();
                return val.equals("true") || val.equals("yes") || val.equals("1");
            case NUMERIC:
                return cell.getNumericCellValue() != 0;
            default:
                return false;
        }
    }

    public static void main(String[] args) {
        if (args.length < 4) {
            System.err.println("Usage: ExcelToDatabaseImporter <jdbcUrl> <username> <password> <excelFile>");
            System.err.println(
                    "Example: ExcelToDatabaseImporter jdbc:postgresql://localhost:5432/school_schedule postgres password schedule-export.xlsx");
            System.exit(1);
        }

        String jdbcUrl = args[0];
        String username = args[1];
        String password = args[2];
        String excelFile = args[3];

        ExcelToDatabaseImporter importer = new ExcelToDatabaseImporter(jdbcUrl, username, password);

        try {
            importer.importFromExcel(excelFile);
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
