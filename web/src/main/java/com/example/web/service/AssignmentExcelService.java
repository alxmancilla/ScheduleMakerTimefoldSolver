package com.example.web.service;

import com.example.web.entity.BlockTimeslotEntity;
import com.example.web.entity.CourseBlockAssignmentEntity;
import com.example.web.repository.BlockTimeslotRepository;
import com.example.web.repository.CourseBlockAssignmentRepository;
import com.example.web.repository.CourseRepository;
import com.example.web.repository.RoomRepository;
import com.example.web.repository.StudentGroupRepository;
import com.example.web.repository.TeacherRepository;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

/**
 * Exports/imports course_block_assignment (the solved schedule) to/from a
 * single-sheet .xlsx workbook, for backup and for migrating just the
 * schedule between two databases that already share the same base data
 * (teachers/courses/rooms/groups) - a lighter-weight companion to the
 * whole-database export/import (DatabaseBackupService) for that narrower
 * case.
 *
 * ADMIN-only: mounted on CourseBlockAssignmentController, which lives entirely
 * under /api/assignments/**, restricted to ADMIN by SecurityConfig.
 *
 * Deliberately excluded from ExcelExportService/ExcelImportService (the base
 * data import/export): course_block_assignment is the solver's output, not
 * base data hand-edited the same way, and it's now access-restricted
 * differently (ADMIN-only vs. those services' WRITER+) - mixing the two would
 * blur that boundary.
 *
 * Upserts by primary key, same precedent as ExcelImportService: a row whose
 * id already exists is updated in place, a new id is inserted, and nothing
 * absent from the file is touched - never a destructive wipe-and-reload of
 * the whole table.
 */
@Service
public class AssignmentExcelService {

    private static final Set<String> VALID_ROOM_TYPES = Set.of(
            "Standard", "Mixed", "Specialized - Workshop", "Specialized - Computer Lab");
    private static final String SHEET_NAME = "Assignments";

    @Autowired
    private CourseBlockAssignmentRepository assignmentRepository;
    @Autowired
    private BlockTimeslotRepository blockTimeslotRepository;
    @Autowired
    private StudentGroupRepository studentGroupRepository;
    @Autowired
    private CourseRepository courseRepository;
    @Autowired
    private TeacherRepository teacherRepository;
    @Autowired
    private RoomRepository roomRepository;

    // ---- Export ----

    public byte[] exportToExcel() throws IOException {
        try (XSSFWorkbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = wb.createSheet(SHEET_NAME);
            Row header = sheet.createRow(0);
            String[] columns = { "id", "group_id", "course_id", "block_length", "satisfies_room_type",
                    "preferred_room_hint", "room_name", "teacher_id", "day_of_week", "start_hour", "pinned" };
            for (int i = 0; i < columns.length; i++) {
                header.createCell(i).setCellValue(columns[i]);
            }

            List<CourseBlockAssignmentEntity> assignments = assignmentRepository.findAll().stream()
                    .sorted(Comparator.comparing(CourseBlockAssignmentEntity::getId))
                    .toList();

            int rowNum = 1;
            for (CourseBlockAssignmentEntity a : assignments) {
                BlockTimeslotEntity timeslot = a.getBlockTimeslotId() != null
                        ? blockTimeslotRepository.findById(a.getBlockTimeslotId()).orElse(null)
                        : null;

                Row row = sheet.createRow(rowNum++);
                row.createCell(0).setCellValue(a.getId());
                row.createCell(1).setCellValue(a.getGroupId());
                row.createCell(2).setCellValue(a.getCourseId());
                row.createCell(3).setCellValue(a.getBlockLength());
                setOrBlank(row.createCell(4), a.getSatisfiesRoomType());
                setOrBlank(row.createCell(5), a.getPreferredRoomHint());
                setOrBlank(row.createCell(6), a.getRoomName());
                setOrBlank(row.createCell(7), a.getTeacherId());
                if (timeslot != null) {
                    row.createCell(8).setCellValue(timeslot.getDayOfWeek());
                    row.createCell(9).setCellValue(timeslot.getStartHour());
                }
                row.createCell(10).setCellValue(Boolean.TRUE.equals(a.getPinned()));
            }

            for (int i = 0; i < columns.length; i++) {
                sheet.autoSizeColumn(i);
            }
            wb.write(out);
            return out.toByteArray();
        }
    }

    private void setOrBlank(Cell cell, String value) {
        if (value != null) {
            cell.setCellValue(value);
        }
    }

    // ---- Import ----

    @Transactional
    public ImportResult importFromExcel(InputStream excelInputStream) throws IOException {
        List<String> errors = new ArrayList<>();
        List<AssignmentRow> rows;

        try (Workbook wb = WorkbookFactory.create(excelInputStream)) {
            rows = parseRows(wb, errors);
        }

        if (!errors.isEmpty()) {
            return ImportResult.failure(errors);
        }

        try {
            int count = persist(rows);
            return ImportResult.success(count);
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("Import failed while saving to the database: " + e.getMessage(), e);
        }
    }

    private List<AssignmentRow> parseRows(Workbook wb, List<String> errors) {
        List<AssignmentRow> rows = new ArrayList<>();
        Sheet sheet = wb.getSheet(SHEET_NAME);
        if (sheet == null) {
            errors.add("Missing required sheet: " + SHEET_NAME);
            return rows;
        }
        for (int i = 1; i <= sheet.getLastRowNum(); i++) {
            Row row = sheet.getRow(i);
            if (row == null || isBlankRow(row)) {
                continue;
            }
            String location = SHEET_NAME + " row " + (i + 1);
            String id = stringOf(row.getCell(0));
            String groupId = stringOf(row.getCell(1));
            String courseId = stringOf(row.getCell(2));
            Integer blockLength = intOf(row.getCell(3));
            String satisfiesRoomType = blankToNull(stringOf(row.getCell(4)));
            String preferredRoomHint = blankToNull(stringOf(row.getCell(5)));
            String roomName = blankToNull(stringOf(row.getCell(6)));
            String teacherId = blankToNull(stringOf(row.getCell(7)));
            Integer dayOfWeek = intOf(row.getCell(8));
            Integer startHour = intOf(row.getCell(9));
            Boolean pinned = booleanOf(row.getCell(10));

            if (isBlank(id)) {
                errors.add(location + ": id is required");
            }
            if (isBlank(groupId)) {
                errors.add(location + ": group_id is required");
            } else if (studentGroupRepository.findById(groupId).isEmpty()) {
                errors.add(location + ": group_id '" + groupId + "' does not match any existing group");
            }
            if (isBlank(courseId)) {
                errors.add(location + ": course_id is required");
            } else if (courseRepository.findById(courseId).isEmpty()) {
                errors.add(location + ": course_id '" + courseId + "' does not match any existing course");
            }
            if (blockLength == null || blockLength < 1 || blockLength > 4) {
                errors.add(location + ": block_length must be between 1 and 4");
            }
            if (satisfiesRoomType != null && !VALID_ROOM_TYPES.contains(satisfiesRoomType)) {
                errors.add(location + ": satisfies_room_type '" + satisfiesRoomType + "' must be one of " + VALID_ROOM_TYPES);
            }
            if (roomName != null && roomRepository.findById(roomName).isEmpty()) {
                errors.add(location + ": room_name '" + roomName + "' does not match any existing room");
            }
            if (preferredRoomHint != null && roomRepository.findById(preferredRoomHint).isEmpty()) {
                errors.add(location + ": preferred_room_hint '" + preferredRoomHint + "' does not match any existing room");
            }
            if (teacherId != null && teacherRepository.findById(teacherId).isEmpty()) {
                errors.add(location + ": teacher_id '" + teacherId + "' does not match any existing teacher");
            }

            boolean hasDay = dayOfWeek != null;
            boolean hasHour = startHour != null;
            String blockTimeslotId = null;
            if (hasDay != hasHour) {
                errors.add(location + ": day_of_week and start_hour must both be set, or both left blank");
            } else if (hasDay && blockLength != null) {
                var timeslot = blockTimeslotRepository.findByDayOfWeekAndStartHourAndLengthHours(
                        dayOfWeek, startHour, blockLength);
                if (timeslot.isEmpty()) {
                    errors.add(location + ": no timeslot exists for day " + dayOfWeek + ", hour " + startHour
                            + ", length " + blockLength);
                } else {
                    blockTimeslotId = timeslot.get().getId();
                }
            }

            boolean isPinned = Boolean.TRUE.equals(pinned);
            if (isPinned && roomName == null) {
                errors.add(location + ": pinned rows must have a room_name (check_block_assignment_pinned_requires_room)");
            }
            if (isPinned && blockTimeslotId == null) {
                errors.add(location
                        + ": pinned rows must have day_of_week/start_hour (check_block_assignment_pinned_requires_timeslot)");
            }

            rows.add(new AssignmentRow(id, groupId, courseId, blockLength, satisfiesRoomType, preferredRoomHint,
                    roomName, teacherId, blockTimeslotId, isPinned));
        }
        return rows;
    }

    private int persist(List<AssignmentRow> rows) {
        for (AssignmentRow r : rows) {
            CourseBlockAssignmentEntity entity = assignmentRepository.findById(r.id)
                    .orElseGet(CourseBlockAssignmentEntity::new);
            entity.setId(r.id);
            entity.setGroupId(r.groupId);
            entity.setCourseId(r.courseId);
            entity.setBlockLength(r.blockLength);
            entity.setSatisfiesRoomType(r.satisfiesRoomType);
            entity.setPreferredRoomHint(r.preferredRoomHint);
            entity.setRoomName(r.roomName);
            entity.setTeacherId(r.teacherId);
            entity.setBlockTimeslotId(r.blockTimeslotId);
            entity.setPinned(r.pinned);
            assignmentRepository.save(entity);
        }
        return rows.size();
    }

    private record AssignmentRow(String id, String groupId, String courseId, Integer blockLength,
            String satisfiesRoomType, String preferredRoomHint, String roomName, String teacherId,
            String blockTimeslotId, boolean pinned) {
    }

    // ---- Cell helpers (same conventions as ExcelImportService) ----

    private static boolean isBlankRow(Row row) {
        for (Cell cell : row) {
            if (cell != null && cell.getCellType() != org.apache.poi.ss.usermodel.CellType.BLANK
                    && !stringOf(cell).isEmpty()) {
                return false;
            }
        }
        return true;
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private static String blankToNull(String s) {
        return isBlank(s) ? null : s;
    }

    private static String stringOf(Cell cell) {
        if (cell == null) {
            return "";
        }
        switch (cell.getCellType()) {
            case STRING:
                return cell.getStringCellValue().trim();
            case NUMERIC:
                double d = cell.getNumericCellValue();
                return d == Math.floor(d) ? String.valueOf((long) d) : String.valueOf(d);
            case BOOLEAN:
                return String.valueOf(cell.getBooleanCellValue());
            case BLANK:
                return "";
            default:
                return cell.toString().trim();
        }
    }

    private static Integer intOf(Cell cell) {
        if (cell == null) {
            return null;
        }
        switch (cell.getCellType()) {
            case NUMERIC:
                return (int) cell.getNumericCellValue();
            case STRING:
                String s = cell.getStringCellValue().trim();
                if (s.isEmpty()) {
                    return null;
                }
                try {
                    return Integer.parseInt(s);
                } catch (NumberFormatException e) {
                    return null;
                }
            default:
                return null;
        }
    }

    private static Boolean booleanOf(Cell cell) {
        if (cell == null) {
            return null;
        }
        switch (cell.getCellType()) {
            case BOOLEAN:
                return cell.getBooleanCellValue();
            case STRING:
                String v = cell.getStringCellValue().trim().toLowerCase();
                if (v.isEmpty()) return null;
                return v.equals("true") || v.equals("yes") || v.equals("1");
            case NUMERIC:
                return cell.getNumericCellValue() != 0;
            default:
                return null;
        }
    }

    /** Result of an import attempt: either a list of validation errors, or the row count imported. */
    public static final class ImportResult {
        private final boolean success;
        private final List<String> errors;
        private final int assignmentsImported;

        private ImportResult(boolean success, List<String> errors, int assignmentsImported) {
            this.success = success;
            this.errors = errors;
            this.assignmentsImported = assignmentsImported;
        }

        public static ImportResult failure(List<String> errors) {
            return new ImportResult(false, errors, 0);
        }

        public static ImportResult success(int assignmentsImported) {
            return new ImportResult(true, List.of(), assignmentsImported);
        }

        public boolean isSuccess() {
            return success;
        }

        public List<String> getErrors() {
            return errors;
        }

        public int getAssignmentsImported() {
            return assignmentsImported;
        }
    }
}
