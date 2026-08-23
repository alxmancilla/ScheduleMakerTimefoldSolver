package com.example.web.service;

import com.example.web.entity.CourseEntity;
import com.example.web.entity.RoomEntity;
import com.example.web.entity.StudentGroupEntity;
import com.example.web.entity.TeacherEntity;
import com.example.web.repository.CourseRepository;
import com.example.web.repository.RoomRepository;
import com.example.web.repository.StudentGroupRepository;
import com.example.web.repository.TeacherRepository;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Imports base problem data (Teachers, Courses, Rooms, Groups, Group_Courses)
 * from an .xlsx workbook, matching the sheet/column layout that
 * DatabaseToExcelExporter (engine module) still produces correctly for those
 * five sheets.
 *
 * Deliberately scoped to base data only: block_timeslot is managed via the
 * Settings tab, and course_block_assignment (the actual schedule) is the
 * solver's output, not something meant to be hand-edited in Excel.
 *
 * Upserts by primary key rather than the old CLI importer's destructive
 * wipe-and-reload: deleting a course or student_group CASCADEs to
 * course_block_assignment, so a full clear would silently destroy any
 * already-solved schedule. Validates every row across every sheet first and
 * only persists if everything is valid (single transaction, all-or-nothing),
 * so a bad row is reported clearly instead of leaving a half-imported DB.
 */
@Service
public class ExcelImportService {

    private static final Set<String> VALID_ROOM_TYPES = Set.of(
            "estándar", "mixto", "taller", "centro de cómputo");
    private static final Pattern ID_PATTERN = Pattern.compile("^[A-Za-z0-9_-]+$");

    @Autowired
    private TeacherRepository teacherRepository;
    @Autowired
    private CourseRepository courseRepository;
    @Autowired
    private RoomRepository roomRepository;
    @Autowired
    private StudentGroupRepository studentGroupRepository;

    @Transactional
    public ImportResult importFromExcel(InputStream excelInputStream) throws IOException {
        List<String> errors = new ArrayList<>();
        List<TeacherRow> teacherRows;
        List<RoomRow> roomRows;
        List<CourseRow> courseRows;
        List<GroupRow> groupRows;
        Map<String, List<String>> groupCourseRows;

        try (Workbook wb = WorkbookFactory.create(excelInputStream)) {
            teacherRows = parseTeachers(wb, errors);
            roomRows = parseRooms(wb, errors);
            courseRows = parseCourses(wb, errors);
            groupRows = parseGroups(wb, errors);
            groupCourseRows = parseGroupCourses(wb, errors);

            validateCrossReferences(roomRows, courseRows, groupRows, groupCourseRows, errors);
        }

        if (!errors.isEmpty()) {
            return ImportResult.failure(errors);
        }

        try {
            int teachersCount = persistTeachers(teacherRows);
            int roomsCount = persistRooms(roomRows);
            int coursesCount = persistCourses(courseRows);
            int groupsCount = persistGroups(groupRows);
            int groupCoursesCount = persistGroupCourses(groupCourseRows);
            return ImportResult.success(teachersCount, coursesCount, roomsCount, groupsCount, groupCoursesCount);
        } catch (RuntimeException e) {
            // Rethrow as unchecked so @Transactional still rolls back everything;
            // IllegalArgumentException maps to a clean 400 via GlobalExceptionHandler
            // instead of a raw 500.
            throw new IllegalArgumentException("Import failed while saving to the database: " + e.getMessage(), e);
        }
    }

    // ---- Teachers ----

    private List<TeacherRow> parseTeachers(Workbook wb, List<String> errors) {
        List<TeacherRow> rows = new ArrayList<>();
        Sheet sheet = wb.getSheet("Teachers");
        if (sheet == null) {
            return rows;
        }
        for (int i = 1; i <= sheet.getLastRowNum(); i++) {
            Row row = sheet.getRow(i);
            if (row == null || isBlankRow(row)) {
                continue;
            }
            String location = "Teachers row " + (i + 1);
            String id = stringOf(row.getCell(0));
            String name = stringOf(row.getCell(1));
            String lastName = stringOf(row.getCell(2));
            Integer maxHours = intOf(row.getCell(3));
            String qualifications = stringOf(row.getCell(4));
            String availability = stringOf(row.getCell(5));

            if (isBlank(id)) {
                errors.add(location + ": id is required");
            } else if (id.length() > 100) {
                errors.add(location + ": id must not exceed 100 characters");
            }
            if (isBlank(name)) {
                errors.add(location + ": name is required");
            }
            if (isBlank(lastName)) {
                errors.add(location + ": last_name is required");
            }
            if (maxHours == null || maxHours <= 0) {
                errors.add(location + ": max_hours_per_week must be a positive number");
            }

            List<String> quals = splitNonBlank(qualifications, ";");

            List<int[]> availPairs = new ArrayList<>();
            if (!isBlank(availability)) {
                for (String dayBlock : availability.split(";")) {
                    dayBlock = dayBlock.trim();
                    if (dayBlock.isEmpty()) continue;
                    String[] parts = dayBlock.split(":");
                    if (parts.length != 2) {
                        errors.add(location + ": availability entry '" + dayBlock + "' must be formatted as day:hour,hour");
                        continue;
                    }
                    Integer day = parseIntOrNull(parts[0].trim());
                    if (day == null || day < 1 || day > 7) {
                        errors.add(location + ": availability day '" + parts[0].trim() + "' must be 1-7");
                        continue;
                    }
                    for (String hourStr : parts[1].split(",")) {
                        Integer hour = parseIntOrNull(hourStr.trim());
                        if (hour == null) {
                            errors.add(location + ": availability hour '" + hourStr.trim() + "' is not a number");
                            continue;
                        }
                        availPairs.add(new int[] { day, hour });
                    }
                }
            }

            rows.add(new TeacherRow(id, name, lastName, maxHours, quals, availPairs));
        }
        return rows;
    }

    private int persistTeachers(List<TeacherRow> rows) {
        for (TeacherRow r : rows) {
            TeacherEntity entity = teacherRepository.findById(r.id).orElseGet(TeacherEntity::new);
            entity.setId(r.id);
            entity.setName(r.name);
            entity.setLastName(r.lastName);
            entity.setMaxHoursPerWeek(r.maxHours);
            entity.getQualifications().clear();
            entity.getAvailability().clear();
            // Flush the clears (orphan-removal DELETEs) before re-adding: Hibernate's
            // default flush order runs INSERTs before DELETEs, so re-adding an entry
            // with the same (teacher, day, hour)/(teacher, qualification) as one just
            // cleared would otherwise collide with the not-yet-deleted old row.
            teacherRepository.saveAndFlush(entity);
            for (String q : r.qualifications) {
                entity.addQualification(q);
            }
            for (int[] pair : r.availability) {
                entity.addAvailability(pair[0], pair[1]);
            }
            teacherRepository.save(entity);
        }
        return rows.size();
    }

    private record TeacherRow(String id, String name, String lastName, Integer maxHours,
            List<String> qualifications, List<int[]> availability) {
    }

    // ---- Rooms ----

    private List<RoomRow> parseRooms(Workbook wb, List<String> errors) {
        List<RoomRow> rows = new ArrayList<>();
        Sheet sheet = wb.getSheet("Rooms");
        if (sheet == null) {
            return rows;
        }
        for (int i = 1; i <= sheet.getLastRowNum(); i++) {
            Row row = sheet.getRow(i);
            if (row == null || isBlankRow(row)) {
                continue;
            }
            String location = "Rooms row " + (i + 1);
            String name = stringOf(row.getCell(0));
            String building = stringOf(row.getCell(1));
            String type = stringOf(row.getCell(2));

            if (isBlank(name)) {
                errors.add(location + ": name is required");
            } else if (name.length() > 100) {
                errors.add(location + ": name must not exceed 100 characters");
            }
            if (isBlank(building)) {
                errors.add(location + ": building is required");
            }
            if (isBlank(type)) {
                errors.add(location + ": type is required");
            } else if (!VALID_ROOM_TYPES.contains(type)) {
                errors.add(location + ": type '" + type + "' must be one of " + VALID_ROOM_TYPES);
            }

            rows.add(new RoomRow(name, building, type));
        }
        return rows;
    }

    private int persistRooms(List<RoomRow> rows) {
        for (RoomRow r : rows) {
            RoomEntity entity = roomRepository.findById(r.name).orElseGet(RoomEntity::new);
            entity.setName(r.name);
            entity.setBuilding(r.building);
            entity.setType(r.type);
            roomRepository.save(entity);
        }
        return rows.size();
    }

    private record RoomRow(String name, String building, String type) {
    }

    // ---- Courses ----

    private List<CourseRow> parseCourses(Workbook wb, List<String> errors) {
        List<CourseRow> rows = new ArrayList<>();
        Sheet sheet = wb.getSheet("Courses");
        if (sheet == null) {
            return rows;
        }
        for (int i = 1; i <= sheet.getLastRowNum(); i++) {
            Row row = sheet.getRow(i);
            if (row == null || isBlankRow(row)) {
                continue;
            }
            String location = "Courses row " + (i + 1);
            String id = stringOf(row.getCell(0));
            String name = stringOf(row.getCell(1));
            String abbreviation = stringOf(row.getCell(2));
            Integer semester = intOf(row.getCell(3));
            String component = stringOf(row.getCell(4));
            String roomRequirement = stringOf(row.getCell(5));
            Integer requiredHours = intOf(row.getCell(6));
            Boolean active = booleanOf(row.getCell(7));

            if (isBlank(id)) {
                errors.add(location + ": id is required");
            } else if (id.length() > 5 || !ID_PATTERN.matcher(id).matches()) {
                errors.add(location + ": id must be 1-5 letters/numbers/hyphens/underscores");
            }
            if (isBlank(name) || name.length() < 2 || name.length() > 200) {
                errors.add(location + ": name must be 2-200 characters");
            }
            if (isBlank(abbreviation) || abbreviation.length() > 100) {
                errors.add(location + ": abbreviation is required (max 100 characters)");
            }
            if (semester == null || semester < 1 || semester > 12) {
                errors.add(location + ": semester must be between 1 and 12");
            }
            if (isBlank(component) || component.length() > 20) {
                errors.add(location + ": component is required (max 20 characters)");
            }
            if (isBlank(roomRequirement)) {
                errors.add(location + ": room_requirement is required");
            } else if (!VALID_ROOM_TYPES.contains(roomRequirement)) {
                errors.add(location + ": room_requirement '" + roomRequirement + "' must be one of " + VALID_ROOM_TYPES);
            }
            if (requiredHours == null || requiredHours < 1 || requiredHours > 40) {
                errors.add(location + ": required_hours_per_week must be between 1 and 40");
            }

            rows.add(new CourseRow(id, name, abbreviation, semester, component, roomRequirement, requiredHours,
                    active == null || active));
        }
        return rows;
    }

    private int persistCourses(List<CourseRow> rows) {
        for (CourseRow r : rows) {
            CourseEntity entity = courseRepository.findById(r.id).orElseGet(CourseEntity::new);
            entity.setId(r.id);
            entity.setName(r.name);
            entity.setAbbreviation(r.abbreviation);
            entity.setSemester(r.semester);
            entity.setComponent(r.component);
            entity.setRoomRequirement(r.roomRequirement);
            entity.setRequiredHoursPerWeek(r.requiredHours);
            entity.setActive(r.active);
            courseRepository.save(entity);
        }
        return rows.size();
    }

    private record CourseRow(String id, String name, String abbreviation, Integer semester, String component,
            String roomRequirement, Integer requiredHours, boolean active) {
    }

    // ---- Groups ----

    private List<GroupRow> parseGroups(Workbook wb, List<String> errors) {
        List<GroupRow> rows = new ArrayList<>();
        Sheet sheet = wb.getSheet("Groups");
        if (sheet == null) {
            return rows;
        }
        for (int i = 1; i <= sheet.getLastRowNum(); i++) {
            Row row = sheet.getRow(i);
            if (row == null || isBlankRow(row)) {
                continue;
            }
            String location = "Groups row " + (i + 1);
            String id = stringOf(row.getCell(0));
            String name = stringOf(row.getCell(1));
            String preferredRoomName = stringOf(row.getCell(2));

            if (isBlank(id) || id.length() > 100) {
                errors.add(location + ": id is required (max 100 characters)");
            }
            if (isBlank(name) || name.length() > 200) {
                errors.add(location + ": name is required (max 200 characters)");
            }

            rows.add(new GroupRow(id, name, isBlank(preferredRoomName) ? null : preferredRoomName));
        }
        return rows;
    }

    private int persistGroups(List<GroupRow> rows) {
        for (GroupRow r : rows) {
            StudentGroupEntity entity = studentGroupRepository.findById(r.id).orElseGet(StudentGroupEntity::new);
            entity.setId(r.id);
            entity.setName(r.name);
            entity.setPreferredRoomName(r.preferredRoomName);
            studentGroupRepository.save(entity);
        }
        return rows.size();
    }

    private record GroupRow(String id, String name, String preferredRoomName) {
    }

    // ---- Group_Courses ----

    /** Maps group_id -> list of course_name, replacing that group's associations wholesale. */
    private Map<String, List<String>> parseGroupCourses(Workbook wb, List<String> errors) {
        Map<String, List<String>> result = new LinkedHashMap<>();
        Sheet sheet = wb.getSheet("Group_Courses");
        if (sheet == null) {
            return result;
        }
        for (int i = 1; i <= sheet.getLastRowNum(); i++) {
            Row row = sheet.getRow(i);
            if (row == null || isBlankRow(row)) {
                continue;
            }
            String location = "Group_Courses row " + (i + 1);
            String groupId = stringOf(row.getCell(0));
            String courseName = stringOf(row.getCell(1));

            if (isBlank(groupId)) {
                errors.add(location + ": group_id is required");
            }
            if (isBlank(courseName)) {
                errors.add(location + ": course_name is required");
            }
            if (!isBlank(groupId) && !isBlank(courseName)) {
                result.computeIfAbsent(groupId, k -> new ArrayList<>()).add(courseName);
            }
        }
        return result;
    }

    private int persistGroupCourses(Map<String, List<String>> groupCourseRows) {
        int count = 0;
        for (Map.Entry<String, List<String>> entry : groupCourseRows.entrySet()) {
            StudentGroupEntity group = studentGroupRepository.findById(entry.getKey())
                    .orElseThrow(() -> new IllegalStateException("Group '" + entry.getKey() + "' not found"));
            group.getCourses().clear();
            // Flush the clear (orphan-removal DELETE) before re-adding, same reasoning
            // as persistTeachers: avoids colliding with a not-yet-deleted (group,
            // course) pair that appears in both the old and new association set.
            studentGroupRepository.saveAndFlush(group);
            for (String courseName : entry.getValue()) {
                group.addCourse(courseName);
                count++;
            }
            studentGroupRepository.save(group);
        }
        return count;
    }

    // ---- Cross-sheet reference validation (before any DB write) ----

    private void validateCrossReferences(List<RoomRow> roomRows, List<CourseRow> courseRows, List<GroupRow> groupRows,
            Map<String, List<String>> groupCourseRows, List<String> errors) {
        Set<String> knownRoomNames = new HashSet<>();
        roomRows.forEach(r -> knownRoomNames.add(r.name));
        roomRepository.findAll().forEach(r -> knownRoomNames.add(r.getName()));

        Set<String> knownCourseNames = new HashSet<>();
        courseRows.forEach(c -> knownCourseNames.add(c.name));
        courseRepository.findAll().forEach(c -> knownCourseNames.add(c.getName()));

        Set<String> knownGroupIds = new HashSet<>();
        groupRows.forEach(g -> knownGroupIds.add(g.id));
        studentGroupRepository.findAll().forEach(g -> knownGroupIds.add(g.getId()));

        for (GroupRow g : groupRows) {
            if (g.preferredRoomName != null && !knownRoomNames.contains(g.preferredRoomName)) {
                errors.add("Groups: group '" + g.id + "' preferred_room_name '" + g.preferredRoomName
                        + "' does not match any room (existing or in this file)");
            }
        }

        for (Map.Entry<String, List<String>> entry : groupCourseRows.entrySet()) {
            if (!knownGroupIds.contains(entry.getKey())) {
                errors.add("Group_Courses: group_id '" + entry.getKey() + "' does not match any group (existing or in this file)");
            }
            for (String courseName : entry.getValue()) {
                if (!knownCourseNames.contains(courseName)) {
                    errors.add("Group_Courses: course_name '" + courseName
                            + "' (for group '" + entry.getKey() + "') does not match any course (existing or in this file)");
                }
            }
        }
    }

    // ---- Cell helpers ----

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

    private static List<String> splitNonBlank(String value, String delimiter) {
        List<String> result = new ArrayList<>();
        if (isBlank(value)) {
            return result;
        }
        for (String part : value.split(delimiter)) {
            part = part.trim();
            if (!part.isEmpty()) {
                result.add(part);
            }
        }
        return result;
    }

    private static Integer parseIntOrNull(String s) {
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return null;
        }
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
                return parseIntOrNull(cell.getStringCellValue().trim());
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

    /** Result of an import attempt: either a list of validation errors, or per-sheet counts. */
    public static final class ImportResult {
        private final boolean success;
        private final List<String> errors;
        private final int teachersImported;
        private final int coursesImported;
        private final int roomsImported;
        private final int groupsImported;
        private final int groupCoursesImported;

        private ImportResult(boolean success, List<String> errors, int teachersImported, int coursesImported,
                int roomsImported, int groupsImported, int groupCoursesImported) {
            this.success = success;
            this.errors = errors;
            this.teachersImported = teachersImported;
            this.coursesImported = coursesImported;
            this.roomsImported = roomsImported;
            this.groupsImported = groupsImported;
            this.groupCoursesImported = groupCoursesImported;
        }

        public static ImportResult failure(List<String> errors) {
            return new ImportResult(false, errors, 0, 0, 0, 0, 0);
        }

        public static ImportResult success(int teachers, int courses, int rooms, int groups, int groupCourses) {
            return new ImportResult(true, List.of(), teachers, courses, rooms, groups, groupCourses);
        }

        public boolean isSuccess() {
            return success;
        }

        public List<String> getErrors() {
            return errors;
        }

        public int getTeachersImported() {
            return teachersImported;
        }

        public int getCoursesImported() {
            return coursesImported;
        }

        public int getRoomsImported() {
            return roomsImported;
        }

        public int getGroupsImported() {
            return groupsImported;
        }

        public int getGroupCoursesImported() {
            return groupCoursesImported;
        }
    }
}
