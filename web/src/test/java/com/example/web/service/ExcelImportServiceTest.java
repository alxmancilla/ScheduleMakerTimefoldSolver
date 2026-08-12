package com.example.web.service;

import com.example.web.entity.TeacherEntity;
import com.example.web.repository.CourseRepository;
import com.example.web.repository.RoomRepository;
import com.example.web.repository.StudentGroupRepository;
import com.example.web.repository.TeacherRepository;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class ExcelImportServiceTest {

    @Mock
    private TeacherRepository teacherRepository;
    @Mock
    private CourseRepository courseRepository;
    @Mock
    private RoomRepository roomRepository;
    @Mock
    private StudentGroupRepository studentGroupRepository;

    @InjectMocks
    private ExcelImportService service;

    @Before
    public void setUp() {
        when(teacherRepository.findById(org.mockito.ArgumentMatchers.anyString())).thenReturn(Optional.empty());
        when(teacherRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(teacherRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));
        when(courseRepository.findById(org.mockito.ArgumentMatchers.anyString())).thenReturn(Optional.empty());
        when(courseRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(roomRepository.findById(org.mockito.ArgumentMatchers.anyString())).thenReturn(Optional.empty());
        when(roomRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(studentGroupRepository.findById(org.mockito.ArgumentMatchers.anyString())).thenReturn(Optional.empty());
        when(studentGroupRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(studentGroupRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));
        when(roomRepository.findAll()).thenReturn(List.of());
        when(courseRepository.findAll()).thenReturn(List.of());
        when(studentGroupRepository.findAll()).thenReturn(List.of());
    }

    private InputStream toStream(XSSFWorkbook wb) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        wb.write(bos);
        wb.close();
        return new ByteArrayInputStream(bos.toByteArray());
    }

    private void addRow(Sheet sheet, int rowNum, Object... values) {
        Row row = sheet.createRow(rowNum);
        for (int i = 0; i < values.length; i++) {
            Object v = values[i];
            if (v == null) continue;
            if (v instanceof Integer) row.createCell(i).setCellValue((Integer) v);
            else if (v instanceof Boolean) row.createCell(i).setCellValue((Boolean) v);
            else row.createCell(i).setCellValue(v.toString());
        }
    }

    @Test
    public void validWorkbook_allSheets_importsSuccessfully() throws IOException {
        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            Sheet teachers = wb.createSheet("Teachers");
            addRow(teachers, 0, "id", "name", "last_name", "max_hours_per_week", "qualifications", "availability");
            addRow(teachers, 1, "T1", "Ada", "Lovelace", 40, "MATH;PHYSICS", "1:7,8;2:9,10");

            Sheet rooms = wb.createSheet("Rooms");
            addRow(rooms, 0, "name", "building", "type");
            addRow(rooms, 1, "A1", "Main", "estándar");

            Sheet courses = wb.createSheet("Courses");
            addRow(courses, 0, "id", "name", "abbreviation", "semester", "component", "room_requirement",
                    "required_hours_per_week", "active");
            addRow(courses, 1, "C1", "Mathematics", "MATH", 2, "BASICAS", "estándar", 4, true);

            Sheet groups = wb.createSheet("Groups");
            addRow(groups, 0, "id", "name", "preferred_room_name");
            addRow(groups, 1, "G1", "Group One", "A1");

            Sheet groupCourses = wb.createSheet("Group_Courses");
            addRow(groupCourses, 0, "group_id", "course_name");
            addRow(groupCourses, 1, "G1", "Mathematics");

            // In production, persistGroups() and persistGroupCourses() run in the same
            // transaction/persistence context, so Hibernate's identity map returns the
            // just-saved group on findById without a real round-trip. Mockito has no
            // such identity map, so stub it explicitly to simulate that.
            when(studentGroupRepository.findById("G1"))
                    .thenReturn(Optional.of(new com.example.web.entity.StudentGroupEntity("G1", "Group One")));

            ExcelImportService.ImportResult result = service.importFromExcel(toStream(wb));

            assertTrue("expected success but got errors: " + result.getErrors(), result.isSuccess());
            assertEquals(1, result.getTeachersImported());
            assertEquals(1, result.getRoomsImported());
            assertEquals(1, result.getCoursesImported());
            assertEquals(1, result.getGroupsImported());
            assertEquals(1, result.getGroupCoursesImported());

            verify(teacherRepository).save(any());
            verify(roomRepository).save(any());
            verify(courseRepository).save(any());
            verify(studentGroupRepository, org.mockito.Mockito.times(2)).save(any()); // once for group, once for group-courses
        }
    }

    @Test
    public void missingRequiredField_failsValidation_writesNothing() throws IOException {
        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            Sheet teachers = wb.createSheet("Teachers");
            addRow(teachers, 0, "id", "name", "last_name", "max_hours_per_week");
            addRow(teachers, 1, "", "Ada", "Lovelace", 40); // blank id

            ExcelImportService.ImportResult result = service.importFromExcel(toStream(wb));

            assertFalse(result.isSuccess());
            assertTrue(result.getErrors().stream().anyMatch(e -> e.contains("id is required")));
            verify(teacherRepository, never()).save(any());
        }
    }

    @Test
    public void invalidRoomType_failsValidation() throws IOException {
        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            Sheet rooms = wb.createSheet("Rooms");
            addRow(rooms, 0, "name", "building", "type");
            addRow(rooms, 1, "A1", "Main", "not-a-real-type");

            ExcelImportService.ImportResult result = service.importFromExcel(toStream(wb));

            assertFalse(result.isSuccess());
            assertTrue(result.getErrors().stream().anyMatch(e -> e.contains("must be one of")));
            verify(roomRepository, never()).save(any());
        }
    }

    @Test
    public void courseIdTooLong_failsValidation() throws IOException {
        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            Sheet courses = wb.createSheet("Courses");
            addRow(courses, 0, "id", "name", "abbreviation", "semester", "component", "room_requirement",
                    "required_hours_per_week", "active");
            addRow(courses, 1, "TOOLONG", "Mathematics", "MATH", 2, "BASICAS", "estándar", 4, true);

            ExcelImportService.ImportResult result = service.importFromExcel(toStream(wb));

            assertFalse(result.isSuccess());
            assertTrue(result.getErrors().stream().anyMatch(e -> e.contains("id must be 1-5")));
        }
    }

    @Test
    public void groupCoursesReferencingUnknownCourse_failsValidation() throws IOException {
        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            Sheet groups = wb.createSheet("Groups");
            addRow(groups, 0, "id", "name", "preferred_room_name");
            addRow(groups, 1, "G1", "Group One", null);

            Sheet groupCourses = wb.createSheet("Group_Courses");
            addRow(groupCourses, 0, "group_id", "course_name");
            addRow(groupCourses, 1, "G1", "Nonexistent Course");

            ExcelImportService.ImportResult result = service.importFromExcel(toStream(wb));

            assertFalse(result.isSuccess());
            assertTrue(result.getErrors().stream().anyMatch(e -> e.contains("does not match any course")));
            verify(studentGroupRepository, never()).save(any());
        }
    }

    @Test
    public void upsert_existingTeacher_updatesInPlaceRatherThanDuplicating() throws IOException {
        TeacherEntity existing = new TeacherEntity("T1", "Old Name", "Old Last", 30);
        when(teacherRepository.findById("T1")).thenReturn(Optional.of(existing));

        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            Sheet teachers = wb.createSheet("Teachers");
            addRow(teachers, 0, "id", "name", "last_name", "max_hours_per_week");
            addRow(teachers, 1, "T1", "New Name", "New Last", 35);

            ExcelImportService.ImportResult result = service.importFromExcel(toStream(wb));

            assertTrue(result.isSuccess());
            assertEquals("New Name", existing.getName());
            assertEquals(Integer.valueOf(35), existing.getMaxHoursPerWeek());
            verify(teacherRepository).save(existing);
        }
    }

    @Test
    public void emptyWorkbook_noSheets_succeedsWithZeroCounts() throws IOException {
        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            ExcelImportService.ImportResult result = service.importFromExcel(toStream(wb));
            assertTrue(result.isSuccess());
            assertEquals(0, result.getTeachersImported());
        }
    }
}
