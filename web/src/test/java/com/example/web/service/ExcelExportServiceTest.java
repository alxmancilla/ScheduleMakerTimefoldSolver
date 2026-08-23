package com.example.web.service;

import com.example.web.entity.CourseEntity;
import com.example.web.entity.RoomEntity;
import com.example.web.entity.StudentGroupEntity;
import com.example.web.entity.TeacherEntity;
import com.example.web.repository.CourseRepository;
import com.example.web.repository.RoomRepository;
import com.example.web.repository.StudentGroupRepository;
import com.example.web.repository.TeacherRepository;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.when;

/**
 * Verifies ExcelExportService produces the exact sheet/column layout
 * ExcelImportService expects - including a genuine round trip through both
 * services, which is the real point of this feature (export, edit, re-import).
 */
@RunWith(MockitoJUnitRunner.class)
public class ExcelExportServiceTest {

    @Mock
    private TeacherRepository teacherRepository;
    @Mock
    private CourseRepository courseRepository;
    @Mock
    private RoomRepository roomRepository;
    @Mock
    private StudentGroupRepository studentGroupRepository;

    @InjectMocks
    private ExcelExportService exportService;

    @Before
    public void setUp() {
        when(courseRepository.findAll()).thenReturn(List.of());
        when(roomRepository.findAll()).thenReturn(List.of());
        when(studentGroupRepository.findAll()).thenReturn(List.of());
        when(teacherRepository.findAll()).thenReturn(List.of());
    }

    @Test
    public void exportsAllFiveSheets() throws IOException {
        byte[] bytes = exportService.exportToExcel();
        try (Workbook wb = WorkbookFactory.create(new ByteArrayInputStream(bytes))) {
            assertEquals(5, wb.getNumberOfSheets());
            for (String name : List.of("Teachers", "Courses", "Rooms", "Groups", "Group_Courses")) {
                assertTrue("missing sheet " + name, wb.getSheet(name) != null);
            }
        }
    }

    @Test
    public void exportToExcel_withEntityFilter_includesOnlyThoseSheets() throws IOException {
        byte[] bytes = exportService.exportToExcel(Set.of("teachers", "rooms"));
        try (Workbook wb = WorkbookFactory.create(new ByteArrayInputStream(bytes))) {
            assertEquals(2, wb.getNumberOfSheets());
            assertTrue(wb.getSheet("Teachers") != null);
            assertTrue(wb.getSheet("Rooms") != null);
        }
    }

    @Test
    public void exportToExcel_groupsEntity_bundlesGroupCoursesSheet() throws IOException {
        byte[] bytes = exportService.exportToExcel(Set.of("groups"));
        try (Workbook wb = WorkbookFactory.create(new ByteArrayInputStream(bytes))) {
            assertEquals(2, wb.getNumberOfSheets());
            assertTrue(wb.getSheet("Groups") != null);
            assertTrue(wb.getSheet("Group_Courses") != null);
        }
    }

    @Test
    public void exportToExcel_emptyEntitySet_exportsEverything() throws IOException {
        byte[] bytes = exportService.exportToExcel(Set.of());
        try (Workbook wb = WorkbookFactory.create(new ByteArrayInputStream(bytes))) {
            assertEquals(5, wb.getNumberOfSheets());
        }
    }

    @Test
    public void teacherSheet_includesQualificationsAndAvailabilityInImportableFormat() throws IOException {
        TeacherEntity teacher = new TeacherEntity("T1", "Ada", "Lovelace", 30);
        teacher.addQualification("Math");
        teacher.addQualification("Algebra");
        teacher.addAvailability(1, 7);
        teacher.addAvailability(1, 8);
        teacher.addAvailability(2, 9);
        when(teacherRepository.findAll()).thenReturn(List.of(teacher));

        byte[] bytes = exportService.exportToExcel();
        try (Workbook wb = WorkbookFactory.create(new ByteArrayInputStream(bytes))) {
            Sheet sheet = wb.getSheet("Teachers");
            Row header = sheet.getRow(0);
            assertEquals("id", header.getCell(0).getStringCellValue());
            assertEquals("qualifications", header.getCell(4).getStringCellValue());
            assertEquals("availability", header.getCell(5).getStringCellValue());

            Row row = sheet.getRow(1);
            assertEquals("T1", row.getCell(0).getStringCellValue());
            assertEquals("Algebra;Math", row.getCell(4).getStringCellValue());
            assertEquals("1:7,8;2:9", row.getCell(5).getStringCellValue());
        }
    }

    @Test
    public void roundTrip_exportedWorkbookImportsCleanlyBackIntoTheSameShape() throws IOException {
        TeacherEntity teacher = new TeacherEntity("T1", "Ada", "Lovelace", 30);
        teacher.addQualification("Math");
        teacher.addAvailability(1, 7);
        when(teacherRepository.findAll()).thenReturn(List.of(teacher));

        RoomEntity room = new RoomEntity("R1", "Building A", "Standard");
        when(roomRepository.findAll()).thenReturn(List.of(room));

        CourseEntity course = new CourseEntity("C1", "Math", "Standard", 5);
        course.setAbbreviation("MATH");
        course.setSemester(2);
        course.setDesignation("Core");
        course.setActive(true);
        when(courseRepository.findAll()).thenReturn(List.of(course));

        StudentGroupEntity group = new StudentGroupEntity("G1", "Group One");
        group.setPreferredRoomName("R1");
        group.addCourse("Math");
        when(studentGroupRepository.findAll()).thenReturn(List.of(group));

        byte[] bytes = exportService.exportToExcel();

        // Feed the exported bytes into a separately-mocked ExcelImportService, matching
        // ExcelImportServiceTest's pattern (upsert-by-id: findById -> empty means "new").
        TeacherRepository importTeacherRepo = org.mockito.Mockito.mock(TeacherRepository.class);
        CourseRepository importCourseRepo = org.mockito.Mockito.mock(CourseRepository.class);
        RoomRepository importRoomRepo = org.mockito.Mockito.mock(RoomRepository.class);
        StudentGroupRepository importGroupRepo = org.mockito.Mockito.mock(StudentGroupRepository.class);

        when(importTeacherRepo.findById("T1")).thenReturn(Optional.empty());
        when(importTeacherRepo.saveAndFlush(org.mockito.ArgumentMatchers.any())).thenAnswer(inv -> inv.getArgument(0));
        when(importTeacherRepo.save(org.mockito.ArgumentMatchers.any())).thenAnswer(inv -> inv.getArgument(0));
        when(importCourseRepo.findById("C1")).thenReturn(Optional.empty());
        when(importCourseRepo.save(org.mockito.ArgumentMatchers.any())).thenAnswer(inv -> inv.getArgument(0));
        when(importCourseRepo.findAll()).thenReturn(List.of());
        when(importRoomRepo.findById("R1")).thenReturn(Optional.empty());
        when(importRoomRepo.save(org.mockito.ArgumentMatchers.any())).thenAnswer(inv -> inv.getArgument(0));
        when(importRoomRepo.findAll()).thenReturn(List.of());
        when(importGroupRepo.findById("G1")).thenReturn(Optional.of(group));
        when(importGroupRepo.saveAndFlush(org.mockito.ArgumentMatchers.any())).thenAnswer(inv -> inv.getArgument(0));
        when(importGroupRepo.save(org.mockito.ArgumentMatchers.any())).thenAnswer(inv -> inv.getArgument(0));
        when(importGroupRepo.findAll()).thenReturn(List.of(group));

        ExcelImportService importService = new ExcelImportService();
        setField(importService, "teacherRepository", importTeacherRepo);
        setField(importService, "courseRepository", importCourseRepo);
        setField(importService, "roomRepository", importRoomRepo);
        setField(importService, "studentGroupRepository", importGroupRepo);

        ExcelImportService.ImportResult result = importService.importFromExcel(new ByteArrayInputStream(bytes));

        assertTrue("expected the exported workbook to import cleanly: " + result.getErrors(), result.isSuccess());
        assertEquals(1, result.getTeachersImported());
        assertEquals(1, result.getCoursesImported());
        assertEquals(1, result.getRoomsImported());
        assertEquals(1, result.getGroupsImported());
        assertEquals(1, result.getGroupCoursesImported());
    }

    private static void setField(Object target, String fieldName, Object value) {
        try {
            java.lang.reflect.Field field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }
}
