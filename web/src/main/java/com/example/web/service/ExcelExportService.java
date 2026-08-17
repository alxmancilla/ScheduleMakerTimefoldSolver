package com.example.web.service;

import com.example.web.entity.CourseEntity;
import com.example.web.entity.GroupCourseEntity;
import com.example.web.entity.RoomEntity;
import com.example.web.entity.StudentGroupEntity;
import com.example.web.entity.TeacherAvailabilityEntity;
import com.example.web.entity.TeacherEntity;
import com.example.web.entity.TeacherQualificationEntity;
import com.example.web.repository.CourseRepository;
import com.example.web.repository.RoomRepository;
import com.example.web.repository.StudentGroupRepository;
import com.example.web.repository.TeacherRepository;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Exports base problem data (Teachers, Courses, Rooms, Groups, Group_Courses)
 * to an .xlsx workbook with the exact sheet/column layout ExcelImportService
 * expects, so a round trip (export -> edit -> re-import) works.
 *
 * Deliberately scoped to the same base data as import: block_timeslot is
 * managed via the Settings tab, and course_block_assignment (the actual
 * schedule) is the solver's output, not something meant to be hand-edited in
 * Excel - the old engine-module DatabaseToExcelExporter's Timeslots sheet
 * queried a "timeslot" table that no longer exists (superseded by
 * block_timeslot), so it isn't ported here.
 */
@Service
public class ExcelExportService {

    @Autowired
    private TeacherRepository teacherRepository;
    @Autowired
    private CourseRepository courseRepository;
    @Autowired
    private RoomRepository roomRepository;
    @Autowired
    private StudentGroupRepository studentGroupRepository;

    public byte[] exportToExcel() throws IOException {
        try (XSSFWorkbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            exportTeachers(wb);
            exportCourses(wb);
            exportRooms(wb);
            exportGroups(wb);
            exportGroupCourses(wb);
            wb.write(out);
            return out.toByteArray();
        }
    }

    private void exportTeachers(XSSFWorkbook wb) {
        Sheet sheet = wb.createSheet("Teachers");
        Row header = sheet.createRow(0);
        header.createCell(0).setCellValue("id");
        header.createCell(1).setCellValue("name");
        header.createCell(2).setCellValue("last_name");
        header.createCell(3).setCellValue("max_hours_per_week");
        header.createCell(4).setCellValue("qualifications");
        header.createCell(5).setCellValue("availability");

        List<TeacherEntity> teachers = teacherRepository.findAll().stream()
                .sorted(Comparator.comparing(TeacherEntity::getId))
                .toList();

        int rowNum = 1;
        for (TeacherEntity teacher : teachers) {
            Row row = sheet.createRow(rowNum++);
            row.createCell(0).setCellValue(teacher.getId());
            row.createCell(1).setCellValue(teacher.getName());
            row.createCell(2).setCellValue(teacher.getLastName());
            row.createCell(3).setCellValue(teacher.getMaxHoursPerWeek());

            String qualifications = teacher.getQualifications().stream()
                    .map(TeacherQualificationEntity::getQualification)
                    .sorted()
                    .collect(Collectors.joining(";"));
            row.createCell(4).setCellValue(qualifications);

            row.createCell(5).setCellValue(formatAvailability(teacher));
        }

        autosizeColumns(sheet, 6);
    }

    /** "day:hour,hour;day:hour,hour", days and hours both ascending, matching parseTeachers' expected format. */
    private String formatAvailability(TeacherEntity teacher) {
        return teacher.getAvailability().stream()
                .collect(Collectors.groupingBy(TeacherAvailabilityEntity::getDayOfWeek))
                .entrySet().stream()
                .sorted(java.util.Map.Entry.comparingByKey())
                .map(entry -> entry.getKey() + ":" + entry.getValue().stream()
                        .map(TeacherAvailabilityEntity::getHour)
                        .sorted()
                        .map(String::valueOf)
                        .collect(Collectors.joining(",")))
                .collect(Collectors.joining(";"));
    }

    private void exportCourses(XSSFWorkbook wb) {
        Sheet sheet = wb.createSheet("Courses");
        Row header = sheet.createRow(0);
        header.createCell(0).setCellValue("id");
        header.createCell(1).setCellValue("name");
        header.createCell(2).setCellValue("abbreviation");
        header.createCell(3).setCellValue("semester");
        header.createCell(4).setCellValue("component");
        header.createCell(5).setCellValue("room_requirement");
        header.createCell(6).setCellValue("required_hours_per_week");
        header.createCell(7).setCellValue("active");

        List<CourseEntity> courses = courseRepository.findAll().stream()
                .sorted(Comparator.comparing(CourseEntity::getId))
                .toList();

        int rowNum = 1;
        for (CourseEntity course : courses) {
            Row row = sheet.createRow(rowNum++);
            row.createCell(0).setCellValue(course.getId());
            row.createCell(1).setCellValue(course.getName());
            row.createCell(2).setCellValue(course.getAbbreviation());
            row.createCell(3).setCellValue(course.getSemester());
            row.createCell(4).setCellValue(course.getComponent());
            row.createCell(5).setCellValue(course.getRoomRequirement());
            row.createCell(6).setCellValue(course.getRequiredHoursPerWeek());
            row.createCell(7).setCellValue(Boolean.TRUE.equals(course.getActive()));
        }

        autosizeColumns(sheet, 8);
    }

    private void exportRooms(XSSFWorkbook wb) {
        Sheet sheet = wb.createSheet("Rooms");
        Row header = sheet.createRow(0);
        header.createCell(0).setCellValue("name");
        header.createCell(1).setCellValue("building");
        header.createCell(2).setCellValue("type");

        List<RoomEntity> rooms = roomRepository.findAll().stream()
                .sorted(Comparator.comparing(RoomEntity::getName))
                .toList();

        int rowNum = 1;
        for (RoomEntity room : rooms) {
            Row row = sheet.createRow(rowNum++);
            row.createCell(0).setCellValue(room.getName());
            row.createCell(1).setCellValue(room.getBuilding());
            row.createCell(2).setCellValue(room.getType());
        }

        autosizeColumns(sheet, 3);
    }

    private void exportGroups(XSSFWorkbook wb) {
        Sheet sheet = wb.createSheet("Groups");
        Row header = sheet.createRow(0);
        header.createCell(0).setCellValue("id");
        header.createCell(1).setCellValue("name");
        header.createCell(2).setCellValue("preferred_room_name");

        List<StudentGroupEntity> groups = studentGroupRepository.findAll().stream()
                .sorted(Comparator.comparing(StudentGroupEntity::getId))
                .toList();

        int rowNum = 1;
        for (StudentGroupEntity group : groups) {
            Row row = sheet.createRow(rowNum++);
            row.createCell(0).setCellValue(group.getId());
            row.createCell(1).setCellValue(group.getName());
            row.createCell(2).setCellValue(group.getPreferredRoomName() != null ? group.getPreferredRoomName() : "");
        }

        autosizeColumns(sheet, 3);
    }

    private void exportGroupCourses(XSSFWorkbook wb) {
        Sheet sheet = wb.createSheet("Group_Courses");
        Row header = sheet.createRow(0);
        header.createCell(0).setCellValue("group_id");
        header.createCell(1).setCellValue("course_name");

        List<StudentGroupEntity> groups = studentGroupRepository.findAll().stream()
                .sorted(Comparator.comparing(StudentGroupEntity::getId))
                .toList();

        int rowNum = 1;
        for (StudentGroupEntity group : groups) {
            List<String> courseNames = group.getCourses().stream()
                    .map(GroupCourseEntity::getCourseName)
                    .sorted()
                    .toList();
            for (String courseName : courseNames) {
                Row row = sheet.createRow(rowNum++);
                row.createCell(0).setCellValue(group.getId());
                row.createCell(1).setCellValue(courseName);
            }
        }

        autosizeColumns(sheet, 2);
    }

    private void autosizeColumns(Sheet sheet, int numColumns) {
        for (int i = 0; i < numColumns; i++) {
            sheet.autoSizeColumn(i);
        }
    }
}
