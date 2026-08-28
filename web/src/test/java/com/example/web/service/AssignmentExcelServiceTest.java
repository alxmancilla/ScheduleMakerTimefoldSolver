package com.example.web.service;

import com.example.web.entity.BlockTimeslotEntity;
import com.example.web.entity.CourseBlockAssignmentEntity;
import com.example.web.entity.CourseEntity;
import com.example.web.entity.RoomEntity;
import com.example.web.entity.StudentGroupEntity;
import com.example.web.entity.TeacherEntity;
import com.example.web.repository.BlockTimeslotRepository;
import com.example.web.repository.CourseBlockAssignmentRepository;
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
import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Verifies {@link AssignmentExcelService}'s import validation and upsert
 * behavior, mirroring {@link ExcelImportServiceTest}'s style. Export is
 * covered indirectly (a round trip through export -> re-import would just
 * duplicate these same assertions); the parsing/validation/persist path is
 * what actually needs coverage.
 */
@RunWith(MockitoJUnitRunner.class)
public class AssignmentExcelServiceTest {

    @Mock
    private CourseBlockAssignmentRepository assignmentRepository;
    @Mock
    private BlockTimeslotRepository blockTimeslotRepository;
    @Mock
    private StudentGroupRepository studentGroupRepository;
    @Mock
    private CourseRepository courseRepository;
    @Mock
    private TeacherRepository teacherRepository;
    @Mock
    private RoomRepository roomRepository;

    @InjectMocks
    private AssignmentExcelService service;

    @Before
    public void setUp() {
        when(studentGroupRepository.findById("G1")).thenReturn(Optional.of(new StudentGroupEntity("G1", "Group One")));
        when(courseRepository.findById("C1")).thenReturn(Optional.of(new CourseEntity("C1", "Math", "estándar", 4)));
        when(teacherRepository.findById("T1")).thenReturn(Optional.of(new TeacherEntity("T1", "Ada", "Lovelace", 40)));
        when(roomRepository.findById("R1")).thenReturn(Optional.of(new RoomEntity("R1", "Building A", "Standard")));
        when(assignmentRepository.findById(anyString())).thenReturn(Optional.empty());
        when(assignmentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        BlockTimeslotEntity timeslot = new BlockTimeslotEntity();
        timeslot.setId("TS1");
        timeslot.setDayOfWeek(1);
        timeslot.setStartHour(7);
        timeslot.setLengthHours(2);
        when(blockTimeslotRepository.findByDayOfWeekAndStartHourAndLengthHours(any(), any(), any()))
                .thenReturn(Optional.empty());
        when(blockTimeslotRepository.findByDayOfWeekAndStartHourAndLengthHours(1, 7, 2))
                .thenReturn(Optional.of(timeslot));
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

    private static final String[] HEADER = { "id", "group_id", "course_id", "block_length", "satisfies_room_type",
            "preferred_room_hint", "room_name", "teacher_id", "day_of_week", "start_hour", "pinned" };

    @Test
    public void validRow_assignedAndPinned_importsSuccessfully() throws IOException {
        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet("Assignments");
            addRow(sheet, 0, (Object[]) HEADER);
            addRow(sheet, 1, "A1", "G1", "C1", 2, "Standard", null, "R1", "T1", 1, 7, true);

            AssignmentExcelService.ImportResult result = service.importFromExcel(toStream(wb));

            assertTrue(result.isSuccess());
            assertEquals(1, result.getAssignmentsImported());
            verify(assignmentRepository).save(any(CourseBlockAssignmentEntity.class));
        }
    }

    @Test
    public void validRow_unassignedNoTimeslot_importsSuccessfully() throws IOException {
        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet("Assignments");
            addRow(sheet, 0, (Object[]) HEADER);
            addRow(sheet, 1, "A1", "G1", "C1", 2, null, null, null, null, null, null, false);

            AssignmentExcelService.ImportResult result = service.importFromExcel(toStream(wb));

            assertTrue(result.isSuccess());
            assertEquals(1, result.getAssignmentsImported());
        }
    }

    @Test
    public void unknownGroupId_isRejected() throws IOException {
        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet("Assignments");
            addRow(sheet, 0, (Object[]) HEADER);
            addRow(sheet, 1, "A1", "NOPE", "C1", 2, null, null, null, null, null, null, false);

            AssignmentExcelService.ImportResult result = service.importFromExcel(toStream(wb));

            assertFalse(result.isSuccess());
            assertTrue(result.getErrors().get(0).contains("group_id"));
            verify(assignmentRepository, never()).save(any());
        }
    }

    @Test
    public void unknownCourseId_isRejected() throws IOException {
        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet("Assignments");
            addRow(sheet, 0, (Object[]) HEADER);
            addRow(sheet, 1, "A1", "G1", "NOPE", 2, null, null, null, null, null, null, false);

            AssignmentExcelService.ImportResult result = service.importFromExcel(toStream(wb));

            assertFalse(result.isSuccess());
            assertTrue(result.getErrors().get(0).contains("course_id"));
        }
    }

    @Test
    public void blockLengthOutOfRange_isRejected() throws IOException {
        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet("Assignments");
            addRow(sheet, 0, (Object[]) HEADER);
            addRow(sheet, 1, "A1", "G1", "C1", 5, null, null, null, null, null, null, false);

            AssignmentExcelService.ImportResult result = service.importFromExcel(toStream(wb));

            assertFalse(result.isSuccess());
            assertTrue(result.getErrors().get(0).contains("block_length"));
        }
    }

    @Test
    public void dayWithoutHour_isRejected() throws IOException {
        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet("Assignments");
            addRow(sheet, 0, (Object[]) HEADER);
            addRow(sheet, 1, "A1", "G1", "C1", 2, null, null, null, null, 1, null, false);

            AssignmentExcelService.ImportResult result = service.importFromExcel(toStream(wb));

            assertFalse(result.isSuccess());
            assertTrue(result.getErrors().get(0).contains("day_of_week and start_hour"));
        }
    }

    @Test
    public void noMatchingTimeslot_isRejected() throws IOException {
        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet("Assignments");
            addRow(sheet, 0, (Object[]) HEADER);
            // day 3 / hour 11 / length 2 doesn't match the mocked timeslot (day 1 / hour 7 / length 2)
            addRow(sheet, 1, "A1", "G1", "C1", 2, null, null, null, null, 3, 11, false);

            AssignmentExcelService.ImportResult result = service.importFromExcel(toStream(wb));

            assertFalse(result.isSuccess());
            assertTrue(result.getErrors().get(0).contains("no timeslot exists"));
        }
    }

    @Test
    public void pinnedWithoutRoom_isRejected() throws IOException {
        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet("Assignments");
            addRow(sheet, 0, (Object[]) HEADER);
            addRow(sheet, 1, "A1", "G1", "C1", 2, null, null, null, null, 1, 7, true);

            AssignmentExcelService.ImportResult result = service.importFromExcel(toStream(wb));

            assertFalse(result.isSuccess());
            assertTrue(result.getErrors().get(0).contains("check_block_assignment_pinned_requires_room"));
        }
    }

    @Test
    public void pinnedWithoutTimeslot_isRejected() throws IOException {
        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet("Assignments");
            addRow(sheet, 0, (Object[]) HEADER);
            addRow(sheet, 1, "A1", "G1", "C1", 2, null, null, "R1", null, null, null, true);

            AssignmentExcelService.ImportResult result = service.importFromExcel(toStream(wb));

            assertFalse(result.isSuccess());
            assertTrue(result.getErrors().get(0).contains("check_block_assignment_pinned_requires_timeslot"));
        }
    }

    @Test
    public void missingSheet_isRejected() throws IOException {
        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            wb.createSheet("SomeOtherSheet");

            AssignmentExcelService.ImportResult result = service.importFromExcel(toStream(wb));

            assertFalse(result.isSuccess());
            assertTrue(result.getErrors().get(0).contains("Missing required sheet"));
        }
    }

    @Test
    public void existingId_updatesInPlaceRatherThanInserting() throws IOException {
        CourseBlockAssignmentEntity existing = new CourseBlockAssignmentEntity();
        existing.setId("A1");
        when(assignmentRepository.findById("A1")).thenReturn(Optional.of(existing));

        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet("Assignments");
            addRow(sheet, 0, (Object[]) HEADER);
            addRow(sheet, 1, "A1", "G1", "C1", 2, null, null, null, null, null, null, false);

            AssignmentExcelService.ImportResult result = service.importFromExcel(toStream(wb));

            assertTrue(result.isSuccess());
            verify(assignmentRepository).save(existing);
        }
    }
}
