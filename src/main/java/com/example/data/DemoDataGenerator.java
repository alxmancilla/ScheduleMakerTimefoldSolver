package com.example.data;

import com.example.domain.*;
import java.time.DayOfWeek;
import java.util.*;

public class DemoDataGenerator {

        public static SchoolSchedule generateDemoData() {
                List<Teacher> teachers = generateTeachers();
                List<Course> courses = generateCourses();
                List<Room> rooms = generateRooms();
                List<Timeslot> timeslots = generateTimeslots();
                List<Group> groups = generateGroups(rooms);
                List<CourseAssignment> assignments = generateCourseAssignments(groups, courses);

                return new SchoolSchedule(teachers, timeslots, rooms, courses, groups, assignments);
        }

        private static List<Teacher> generateTeachers() {
                List<Teacher> teachers = new ArrayList<>();

                // Build teachers with per-day availability maps (Mon-Fri ranges)
                teachers.add(new Teacher("GUSTAVO MELO",
                                Set.of("LENGUA Y COMUNICACIÓN I"),
                                availability(Set.of(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY,
                                                DayOfWeek.THURSDAY, DayOfWeek.FRIDAY), 7, 15),
                                30));

                teachers.add(new Teacher("MONICA E. DIEGO",
                                Set.of("INGLÉS I"),
                                availability(Set.of(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY,
                                                DayOfWeek.THURSDAY, DayOfWeek.FRIDAY), 7, 15),
                                30));

                teachers.add(new Teacher("QUESIA ALONDRA RAMIREZ",
                                Set.of("INGLÉS I"),
                                availability(Set.of(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY,
                                                DayOfWeek.THURSDAY, DayOfWeek.FRIDAY), 7, 15),
                                30));

                teachers.add(new Teacher("LUIS SANCHEZ",
                                Set.of("PENSAMIENTO MATEMÁTICO I"),
                                availability(Set.of(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY,
                                                DayOfWeek.THURSDAY, DayOfWeek.FRIDAY), 7, 15),
                                30));

                teachers.add(new Teacher("DIANA R. LLUCK",
                                Set.of("PENSAMIENTO MATEMÁTICO I"),
                                availability(Set.of(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY,
                                                DayOfWeek.THURSDAY, DayOfWeek.FRIDAY), 7, 15),
                                30));

                teachers.add(new Teacher("JUAN A. ACEVEDO",
                                Set.of("PENSAMIENTO MATEMÁTICO I"),
                                availability(Set.of(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY,
                                                DayOfWeek.THURSDAY, DayOfWeek.FRIDAY), 11, 15),
                                30));

                teachers.add(new Teacher("HUGO GARCIA",
                                Set.of("PENSAMIENTO MATEMÁTICO I"),
                                availability(Set.of(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY,
                                                DayOfWeek.THURSDAY, DayOfWeek.FRIDAY), 7, 15),
                                30));

                teachers.add(new Teacher("JOSÉ CARLOS RETANA",
                                Set.of("CULTURA DIGITAL I"),
                                availability(Set.of(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY,
                                                DayOfWeek.THURSDAY, DayOfWeek.FRIDAY), 7, 15),
                                30));

                teachers.add(new Teacher("BALBINA CATALAN",
                                Set.of("CULTURA DIGITAL I"),
                                availability(Set.of(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY,
                                                DayOfWeek.THURSDAY, DayOfWeek.FRIDAY), 7, 15),
                                20));

                teachers.add(new Teacher("MARIO VERDIGUEL",
                                Set.of("CULTURA DIGITAL I"),
                                availability(Set.of(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY,
                                                DayOfWeek.THURSDAY, DayOfWeek.FRIDAY), 7, 15),
                                20));

                teachers.add(new Teacher("ISRAEL SANTANA",
                                Set.of("CULTURA DIGITAL I"),
                                availability(Set.of(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY,
                                                DayOfWeek.THURSDAY, DayOfWeek.FRIDAY), 10, 14),
                                20));

                teachers.add(new Teacher("ALFREDO SALAS",
                                Set.of("LA MATERIA Y SUS INTERACCIONES"),
                                availability(Set.of(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY,
                                                DayOfWeek.THURSDAY, DayOfWeek.FRIDAY), 7, 15),
                                30));

                teachers.add(new Teacher("ANDRES BARRIOS",
                                Set.of("RECURSOS SOCIOEMOCIONALES I", "TUTORIAS I"),
                                availability(Set.of(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY,
                                                DayOfWeek.THURSDAY, DayOfWeek.FRIDAY), 7, 15),
                                40));

                teachers.add(new Teacher("ITZEL URIBE",
                                Set.of("LA MATERIA Y SUS INTERACCIONES", "RECURSOS SOCIOEMOCIONALES I", "TUTORIAS I"),
                                availability(Set.of(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY,
                                                DayOfWeek.THURSDAY, DayOfWeek.FRIDAY), 7, 17),
                                30));

                teachers.add(new Teacher("YASIR HERRERA",
                                Set.of("LA MATERIA Y SUS INTERACCIONES", "RECURSOS SOCIOEMOCIONALES I", "TUTORIAS I"),
                                availability(Set.of(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY,
                                                DayOfWeek.THURSDAY, DayOfWeek.FRIDAY), 7, 14),
                                30));

                teachers.add(new Teacher("YAMEL A. MARTÍNEZ",
                                Set.of("CIENCIAS SOCIALES I"),
                                availability(Set.of(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY,
                                                DayOfWeek.THURSDAY, DayOfWeek.FRIDAY), 10, 15),
                                30));

                teachers.add(new Teacher("LETICIA DE LOS SANTOS",
                                Set.of("HUMANIDADES I"),
                                availability(Set.of(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY,
                                                DayOfWeek.THURSDAY, DayOfWeek.FRIDAY), 7, 12),
                                30));

                teachers.add(new Teacher("JOSE BAHENA",
                                Set.of("RECURSOS SOCIOEMOCIONALES I", "HUMANIDADES I", "TUTORIAS I"),
                                availability(Set.of(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY,
                                                DayOfWeek.THURSDAY, DayOfWeek.FRIDAY), 7, 15),
                                30));

                teachers.add(new Teacher("LUCIA DANIELA NUÑEZ",
                                Set.of("RECURSOS SOCIOEMOCIONALES I", "TUTORIAS I"),
                                availability(Set.of(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY,
                                                DayOfWeek.THURSDAY, DayOfWeek.FRIDAY), 7, 12),
                                30));

                teachers.add(new Teacher("PABLO B. ROSETE",
                                Set.of("CLUB DE AJEDREZ", "RECURSOS SOCIOEMOCIONALES I", "TUTORIAS I"),
                                availability(Set.of(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY,
                                                DayOfWeek.THURSDAY, DayOfWeek.FRIDAY), 7, 15),
                                30));

                teachers.add(new Teacher("CARLOS IVAN ADAME",
                                Set.of("ACTIVACIÓN FÍSICA"),
                                availability(Set.of(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY,
                                                DayOfWeek.THURSDAY, DayOfWeek.FRIDAY), 10, 15),
                                30));

                teachers.add(new Teacher("MIGUEL A. GUZMAN CONTRERAS",
                                Set.of("CIENCIAS SOCIALES I"),
                                availability(Set.of(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY,
                                                DayOfWeek.THURSDAY, DayOfWeek.FRIDAY), 7, 15),
                                40));

                // Ensure all demo teachers have a reasonable default max hours/week
                for (Teacher t : teachers) {
                        if (t.getMaxHoursPerWeek() <= 0) {
                                t.setMaxHoursPerWeek(20);
                        }
                }

                // Sort teachers so teachers with smaller maxHoursPerWeek come first
                teachers.sort(java.util.Comparator.comparingInt(Teacher::getMaxHoursPerWeek));

                System.out.println("Generated " + teachers.size() + " teachers.");

                return teachers;
        }

        private static java.util.Map<DayOfWeek, java.util.Set<Integer>> availability(Set<DayOfWeek> days, int startHour,
                        int endHour) {
                java.util.Map<DayOfWeek, java.util.Set<Integer>> map = new java.util.HashMap<>();
                if (days == null)
                        return map;
                for (DayOfWeek d : days) {
                        java.util.Set<Integer> hours = new java.util.HashSet<>();
                        for (int h = startHour; h < endHour; h++) {
                                hours.add(h);
                        }
                        map.put(d, hours);
                }
                return map;
        }

        private static List<Course> generateCourses() {
                List<Course> courses = new ArrayList<>();

                courses.add(new Course("TUTORIAS I", "standard", 1));
                courses.add(new Course("CLUB DE AJEDREZ", "standard", 1));
                courses.add(new Course("ACTIVACIÓN FÍSICA", "standard", 1));
                courses.add(new Course("RECURSOS SOCIOEMOCIONALES I", "standard", 1));
                courses.add(new Course("CIENCIAS SOCIALES I", "standard", 2));
                courses.add(new Course("LENGUA Y COMUNICACIÓN I", "standard", 3));
                courses.add(new Course("INGLÉS I", "standard", 3));
                courses.add(new Course("CULTURA DIGITAL I", "lab", 3));
                courses.add(new Course("LA MATERIA Y SUS INTERACCIONES", "standard", 3));
                courses.add(new Course("HUMANIDADES I", "standard", 4));
                courses.add(new Course("PENSAMIENTO MATEMÁTICO I", "standard", 4));
                // courses.add(new Course("Math 101", "standard", 3));
                // courses.add(new Course("Math 102", "standard", 3));
                // courses.add(new Course("Physics 101", "science_lab", 3));
                // courses.add(new Course("Chemistry 101", "science_lab", 3));
                // courses.add(new Course("English 101", "standard", 2));
                // courses.add(new Course("Calculus", "standard", 3));
                // courses.add(new Course("Biology 101", "science_lab", 3));

                System.out.println("Generated " + courses.size() + " courses.");

                return courses;
        }

        private static List<Room> generateRooms() {
                List<Room> rooms = new ArrayList<>();

                rooms.add(new Room("Room 05", "A", "standard"));
                rooms.add(new Room("Room 06", "A", "standard"));
                rooms.add(new Room("Room 07", "A", "standard"));
                rooms.add(new Room("Room 08", "A", "standard"));
                rooms.add(new Room("Room 09", "A", "standard"));
                rooms.add(new Room("Room 10", "A", "standard"));
                rooms.add(new Room("Room 11", "A", "standard"));
                rooms.add(new Room("Room 12", "A", "standard"));
                rooms.add(new Room("Room 13", "A", "standard"));
                rooms.add(new Room("Lab CC3", "A", "lab"));

                System.out.println("Generated " + rooms.size() + " rooms.");

                return rooms;
        }

        private static List<Timeslot> generateTimeslots() {
                List<Timeslot> timeslots = new ArrayList<>();

                String[] days = { "Lun", "Mar", "Mie", "Jue", "Vie" };
                int[] hours = { 7, 8, 9, 10, 11, 12, 13, 14 };

                int counter = 0;
                for (String day : days) {
                        for (int hour : hours) {
                                DayOfWeek dayOfWeek = switch (day) {
                                        case "Lun" -> DayOfWeek.MONDAY;
                                        case "Mar" -> DayOfWeek.TUESDAY;
                                        case "Mie" -> DayOfWeek.WEDNESDAY;
                                        case "Jue" -> DayOfWeek.THURSDAY;
                                        case "Vie" -> DayOfWeek.FRIDAY;
                                        default -> DayOfWeek.MONDAY;
                                };

                                String displayName = day + " " + (hour) + "-" + (hour + 1);
                                timeslots.add(new Timeslot(
                                                "slot_" + counter++,
                                                dayOfWeek,
                                                hour,
                                                displayName));
                        }
                }

                System.out.println("Generated " + timeslots.size() + " timeslots.");

                return timeslots;
        }

        private static List<Group> generateGroups(List<Room> rooms) {
                List<Group> groups = new ArrayList<>();

                // Group 1o A: Math 101, Physics 101, English 101
                // Assign a preferred room to Grupo 1o C (pre-assigned room for the group)
                Room preferred = rooms.stream()
                                .filter(r -> r.getName().equals("Room 08"))
                                .findFirst()
                                .orElse(null);

                groups.add(new Group("g_1A", "Gpo 1oA",
                                Set.of("LENGUA Y COMUNICACIÓN I", "INGLÉS I", "PENSAMIENTO MATEMÁTICO I",
                                                "CULTURA DIGITAL I", "LA MATERIA Y SUS INTERACCIONES", "HUMANIDADES I",
                                                "CIENCIAS SOCIALES I",
                                                "RECURSOS SOCIOEMOCIONALES I", "TUTORIAS I", "CLUB DE AJEDREZ",
                                                "ACTIVACIÓN FÍSICA"),
                                preferred));

                // Group 1o C: Math 101, Physics 101, English 101
                // Assign a preferred room to Grupo 1o C (pre-assigned room for the group)

                preferred = rooms.stream()
                                .filter(r -> r.getName().equals("Room 05"))
                                .findFirst()
                                .orElse(null);

                groups.add(new Group("g_1B", "Gpo 1oB",
                                Set.of("LENGUA Y COMUNICACIÓN I", "INGLÉS I", "PENSAMIENTO MATEMÁTICO I",
                                                "CULTURA DIGITAL I", "LA MATERIA Y SUS INTERACCIONES", "HUMANIDADES I",
                                                "CIENCIAS SOCIALES I",
                                                "RECURSOS SOCIOEMOCIONALES I", "TUTORIAS I", "CLUB DE AJEDREZ",
                                                "ACTIVACIÓN FÍSICA"),
                                preferred));

                // Group 1o C: Math 101, Physics 101, English 101
                // Assign a preferred room to Grupo 1o C (pre-assigned room for the group)
                preferred = rooms.stream()
                                .filter(r -> r.getName().equals("Room 06"))
                                .findFirst()
                                .orElse(null);

                groups.add(new Group("g_1C", "Gpo 1oC",
                                Set.of("LENGUA Y COMUNICACIÓN I", "INGLÉS I", "PENSAMIENTO MATEMÁTICO I",
                                                "CULTURA DIGITAL I", "LA MATERIA Y SUS INTERACCIONES", "HUMANIDADES I",
                                                "CIENCIAS SOCIALES I",
                                                "RECURSOS SOCIOEMOCIONALES I", "TUTORIAS I", "CLUB DE AJEDREZ",
                                                "ACTIVACIÓN FÍSICA"),
                                preferred));

                // Group 1o C: Math 101, Physics 101, English 101
                // Assign a preferred room to Grupo 1o C (pre-assigned room for the group)
                preferred = rooms.stream()
                                .filter(r -> r.getName().equals("Room 09"))
                                .findFirst()
                                .orElse(null);

                groups.add(new Group("g_1D", "Gpo 1oD",
                                Set.of("LENGUA Y COMUNICACIÓN I", "INGLÉS I", "PENSAMIENTO MATEMÁTICO I",
                                                "CULTURA DIGITAL I", "LA MATERIA Y SUS INTERACCIONES", "HUMANIDADES I",
                                                "CIENCIAS SOCIALES I",
                                                "RECURSOS SOCIOEMOCIONALES I", "TUTORIAS I", "CLUB DE AJEDREZ",
                                                "ACTIVACIÓN FÍSICA"),
                                preferred));

                // Group 1o C: Math 101, Physics 101, English 101
                // Assign a preferred room to Grupo 1o C (pre-assigned room for the group)
                preferred = rooms.stream()
                                .filter(r -> r.getName().equals("Room 10"))
                                .findFirst()
                                .orElse(null);

                groups.add(new Group("g_1E", "Gpo 1oE",
                                Set.of("LENGUA Y COMUNICACIÓN I", "INGLÉS I", "PENSAMIENTO MATEMÁTICO I",
                                                "CULTURA DIGITAL I", "LA MATERIA Y SUS INTERACCIONES", "HUMANIDADES I",
                                                "CIENCIAS SOCIALES I",
                                                "RECURSOS SOCIOEMOCIONALES I", "TUTORIAS I", "CLUB DE AJEDREZ",
                                                "ACTIVACIÓN FÍSICA"),
                                preferred));

                // Group 10 G: Math 101, Physics 101, English 101

                preferred = rooms.stream()
                                .filter(r -> r.getName().equals("Room 11"))
                                .findFirst()
                                .orElse(null);

                groups.add(new Group("g_1F", "Gpo 1oF",
                                Set.of("LENGUA Y COMUNICACIÓN I", "INGLÉS I", "PENSAMIENTO MATEMÁTICO I",
                                                "CULTURA DIGITAL I", "LA MATERIA Y SUS INTERACCIONES", "HUMANIDADES I",
                                                "CIENCIAS SOCIALES I",
                                                "RECURSOS SOCIOEMOCIONALES I", "TUTORIAS I", "CLUB DE AJEDREZ",
                                                "ACTIVACIÓN FÍSICA"),
                                preferred));

                // Group 10 G: Math 101, Physics 101, English 101
                preferred = rooms.stream()
                                .filter(r -> r.getName().equals("Room 07"))
                                .findFirst()
                                .orElse(null);

                groups.add(new Group("g_1G", "Gpo 1oG",
                                Set.of("LENGUA Y COMUNICACIÓN I", "INGLÉS I", "PENSAMIENTO MATEMÁTICO I",
                                                "CULTURA DIGITAL I", "LA MATERIA Y SUS INTERACCIONES", "HUMANIDADES I",
                                                "CIENCIAS SOCIALES I",
                                                "RECURSOS SOCIOEMOCIONALES I", "TUTORIAS I", "CLUB DE AJEDREZ",
                                                "ACTIVACIÓN FÍSICA"),
                                preferred));

                // Group 10 G: Math 101, Physics 101, English 101
                preferred = rooms.stream()
                                .filter(r -> r.getName().equals("Room 12"))
                                .findFirst()
                                .orElse(null);

                groups.add(new Group("g_1H", "Gpo 1oH",
                                Set.of("LENGUA Y COMUNICACIÓN I", "INGLÉS I", "PENSAMIENTO MATEMÁTICO I",
                                                "CULTURA DIGITAL I", "LA MATERIA Y SUS INTERACCIONES", "HUMANIDADES I",
                                                "CIENCIAS SOCIALES I",
                                                "RECURSOS SOCIOEMOCIONALES I", "TUTORIAS I", "CLUB DE AJEDREZ",
                                                "ACTIVACIÓN FÍSICA"),
                                preferred));

                // Group 10 G: Math 101, Physics 101, English 101
                preferred = rooms.stream()
                                .filter(r -> r.getName().equals("Room 13"))
                                .findFirst()
                                .orElse(null);

                groups.add(new Group("g_1I", "Gpo 1oI",
                                Set.of("LENGUA Y COMUNICACIÓN I", "INGLÉS I", "PENSAMIENTO MATEMÁTICO I",
                                                "CULTURA DIGITAL I", "LA MATERIA Y SUS INTERACCIONES", "HUMANIDADES I",
                                                "CIENCIAS SOCIALES I",
                                                "RECURSOS SOCIOEMOCIONALES I", "TUTORIAS I", "CLUB DE AJEDREZ",
                                                "ACTIVACIÓN FÍSICA"),
                                preferred));

                // Group A: Math 101, Physics 101, English 101
                // groups.add(new Group("group_A", "Group A", Set.of("Math 101", "Physics 101",
                // "English 101")));

                // Group B: Math 102, Chemistry 101, Biology 101
                // groups.add(new Group("group_B", "Group B", Set.of("Math 102", "Chemistry
                // 101", "Biology 101")));

                // Group C: Calculus, Physics 101, English 101
                // groups.add(new Group("group_C", "Group C", Set.of("Calculus", "Physics 101",
                // "English 101")));

                // Group D: Math 101, Chemistry 101, Biology 101
                // groups.add(new Group("group_D", "Group D", Set.of("Math 101", "Chemistry
                // 101", "Biology 101")));

                System.out.println("Generated " + groups.size() + " groups.");

                return groups;
        }

        private static List<CourseAssignment> generateCourseAssignments(List<Group> groups, List<Course> courses) {
                List<CourseAssignment> assignments = new ArrayList<>();

                int counter = 0;
                for (Group group : groups) {
                        for (String courseName : group.getCourseNames()) {
                                System.out.println("courseName: " + courseName + " .");
                                Course course = courses.stream()
                                                .filter(c -> c.getName().equals(courseName))
                                                .findFirst()
                                                .orElseThrow();

                                // Create an assignment for each hour required for the course
                                for (int i = 0; i < course.getRequiredHoursPerWeek(); i++) {
                                        CourseAssignment assignment = new CourseAssignment(
                                                        "assignment_" + counter++,
                                                        group,
                                                        course,
                                                        i);
                                        assignments.add(assignment);
                                }
                        }
                }
                System.out.println("Generated " + assignments.size() + " assignments.");

                return assignments;
        }
}
