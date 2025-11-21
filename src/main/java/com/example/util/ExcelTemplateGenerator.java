package com.example.util;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import com.example.data.DemoDataGenerator;
import com.example.domain.Course;
import com.example.domain.CourseAssignment;
import com.example.domain.Group;
import com.example.domain.Room;
import com.example.domain.SchoolSchedule;
import com.example.domain.Teacher;
import com.example.domain.Timeslot;

import java.io.FileOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Utility to generate an Excel template file for importing problem data.
 * Produces sheets: Teachers, Courses, Rooms, Timeslots, Groups, Assignments
 */
public class ExcelTemplateGenerator {

    public static void generateTemplate(String outputPath) throws IOException {
        try (Workbook wb = new XSSFWorkbook()) {
            // Load demo data and pre-fill rows
            SchoolSchedule demo = DemoDataGenerator.generateDemoData();

            // Prepare timeslot list early (used to compute teacher availability)
            List<Timeslot> timeslotList = demo.getTimeslots();

            // Teachers sheet
            Sheet teachers = wb.createSheet("Teachers");
            Row th = teachers.createRow(0);
            th.createCell(0).setCellValue("id");
            th.createCell(1).setCellValue("name");
            th.createCell(2).setCellValue("qualifications (semicolon-separated)");
            th.createCell(3).setCellValue("availability (DAY:hour,hour;...)");
            th.createCell(4).setCellValue("startHour (derived)");
            th.createCell(5).setCellValue("endHour (derived)");
            th.createCell(6).setCellValue("maxHoursPerWeek (int)");

            // Courses sheet
            Sheet courses = wb.createSheet("Courses");
            Row ch = courses.createRow(0);
            ch.createCell(0).setCellValue("name");
            ch.createCell(1).setCellValue("type (standard|lab)");
            ch.createCell(2).setCellValue("hours (int)");

            // Rooms sheet
            Sheet rooms = wb.createSheet("Rooms");
            Row rh = rooms.createRow(0);
            rh.createCell(0).setCellValue("name");
            rh.createCell(1).setCellValue("building");
            rh.createCell(2).setCellValue("type (standard|lab)");

            // Timeslots sheet
            Sheet times = wb.createSheet("Timeslots");
            Row thdr = times.createRow(0);
            thdr.createCell(0).setCellValue("id");
            thdr.createCell(1).setCellValue("dayOfWeek (MONDAY..FRIDAY)");
            thdr.createCell(2).setCellValue("hour (int)");
            thdr.createCell(3).setCellValue("displayName");

            // Groups sheet
            Sheet groups = wb.createSheet("Groups");
            Row gh = groups.createRow(0);
            gh.createCell(0).setCellValue("id");
            gh.createCell(1).setCellValue("name");
            gh.createCell(2).setCellValue("coursesAllowed (semicolon-separated course names)");
            gh.createCell(3).setCellValue("preferredRoomName (optional)");

            // Assignments sheet (for initial assignments or import)
            Sheet assigns = wb.createSheet("Assignments");
            Row ah = assigns.createRow(0);
            ah.createCell(0).setCellValue("id");
            ah.createCell(1).setCellValue("courseName");
            ah.createCell(2).setCellValue("groupId");
            ah.createCell(3).setCellValue("timeslotId (optional)");
            ah.createCell(4).setCellValue("teacherName (optional)");
            ah.createCell(5).setCellValue("roomName (optional)");

            // Teachers
            List<Teacher> teacherList = demo.getTeachers();
            int trow = 1;
            for (Teacher t : teacherList) {
                Row r = teachers.createRow(trow++);
                r.createCell(0).setCellValue(t.getId());
                r.createCell(1).setCellValue(t.getName());
                String quals = String.join(";", t.getQualifications());
                r.createCell(2).setCellValue(quals);
                // Compute availability per day by checking timeslots
                String availability = timeslotList.stream()
                        .filter(ts -> t.isAvailableAt(ts))
                        .collect(Collectors.groupingBy(Timeslot::getDayOfWeek,
                                Collectors.mapping(Timeslot::getHour, Collectors.toList())))
                        .entrySet().stream()
                        .map(e -> e.getKey().toString() + ":" + e.getValue().stream().map(Object::toString)
                                .collect(Collectors.joining("|")))
                        .collect(Collectors.joining(";"));
                r.createCell(3).setCellValue(availability);
                r.createCell(4).setCellValue(t.getStartHour());
                r.createCell(5).setCellValue(t.getEndHour());
                r.createCell(6).setCellValue(t.getMaxHoursPerWeek());
            }

            // Courses (include id column)
            List<Course> courseList = demo.getCourses();
            int crow = 1;
            for (Course c : courseList) {
                Row r = courses.createRow(crow++);
                r.createCell(0).setCellValue(c.getId());
                r.createCell(1).setCellValue(c.getName());
                r.createCell(2).setCellValue(c.getRoomRequirement());
                r.createCell(3).setCellValue(c.getRequiredHoursPerWeek());
            }

            // Rooms
            List<Room> roomList = demo.getRooms();
            int rrow = 1;
            for (Room rm : roomList) {
                Row r = rooms.createRow(rrow++);
                r.createCell(0).setCellValue(rm.getName());
                r.createCell(1).setCellValue(rm.getBuilding());
                r.createCell(2).setCellValue(rm.getType());
            }

            // Timeslots (already obtained above)
            int tsrow = 1;
            for (Timeslot ts : timeslotList) {
                Row r = times.createRow(tsrow++);
                r.createCell(0).setCellValue(ts.getId());
                r.createCell(1).setCellValue(ts.getDayOfWeek().toString());
                r.createCell(2).setCellValue(ts.getHour());
                r.createCell(3).setCellValue(ts.getDisplayName());
            }

            // Groups
            List<Group> groupList = demo.getGroups();
            int grow = 1;
            for (Group g : groupList) {
                Row r = groups.createRow(grow++);
                r.createCell(0).setCellValue(g.getId());
                r.createCell(1).setCellValue(g.getName());
                String allowed = String.join(";", g.getCourseNames());
                r.createCell(2).setCellValue(allowed);
                r.createCell(3).setCellValue(g.getPreferredRoom() != null ? g.getPreferredRoom().getName() : "");
            }

            // Assignments (pre-filled with id, courseName, groupId). timeslot/teacher/room
            // left empty
            List<CourseAssignment> assignments = demo.getCourseAssignments();
            int arow = 1;
            for (CourseAssignment ca : assignments) {
                Row r = assigns.createRow(arow++);
                r.createCell(0).setCellValue(ca.getId());
                r.createCell(1).setCellValue(ca.getCourse().getName());
                r.createCell(2).setCellValue(ca.getGroup().getId());
                r.createCell(3).setCellValue(ca.getTimeslot() != null ? ca.getTimeslot().getId() : "");
                r.createCell(4).setCellValue(ca.getTeacher() != null ? ca.getTeacher().getName() : "");
                r.createCell(5).setCellValue(ca.getRoom() != null ? ca.getRoom().getName() : "");
            }

            // Autosize a few sheets' columns
            autosizeColumns(teachers, 7);
            autosizeColumns(courses, 4);
            autosizeColumns(rooms, 3);
            autosizeColumns(times, 4);
            autosizeColumns(groups, 4);
            autosizeColumns(assigns, 6);

            try (FileOutputStream fos = new FileOutputStream(outputPath)) {
                wb.write(fos);
            }
        }
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
