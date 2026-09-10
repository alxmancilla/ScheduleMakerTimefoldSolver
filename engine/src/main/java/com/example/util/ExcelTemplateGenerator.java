package com.example.util;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import com.example.data.DemoDataGenerator;
import com.example.domain.BlockTimeslot;
import com.example.domain.Course;
import com.example.domain.CourseBlockAssignment;
import com.example.domain.Group;
import com.example.domain.Room;
import com.example.domain.SchoolSchedule;
import com.example.domain.Teacher;

import java.io.FileOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Utility to generate an Excel template file for importing problem data.
 * Produces sheets: Teachers, Courses, Rooms, BlockTimeslots, Groups,
 * BlockAssignments
 *
 * @deprecated This utility is for hour-based scheduling and is no longer
 *             supported.
 *             Block-based scheduling uses database-driven configuration instead
 *             of Excel templates.
 */
@Deprecated
public class ExcelTemplateGenerator {

    public static void generateTemplate(String outputPath) throws IOException {
        throw new UnsupportedOperationException(
                "Excel template generation is no longer supported. " +
                        "Block-based scheduling uses database-driven configuration. " +
                        "Please use the SQL scripts in the database/ directory instead.");
    }

    @Deprecated
    private static void generateTemplateOld(String outputPath) throws IOException {
        throw new UnsupportedOperationException("Hour-based scheduling is no longer supported");
    }

    private static void autosizeColumns(Sheet s, int n) {
        for (int i = 0; i < n; i++) {
            s.autoSizeColumn(i);
        }
    }

    public static void main(String[] args) throws IOException {
        String path = args.length > 0 ? args[0] : "schedule-template.xlsx";
        generateTemplate(path);
        System.out.println("Excel template written to: " + path);
    }
}
