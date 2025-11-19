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

                // Ms. Smith: Math 101, Math 102, Calculus - Mon-Fri mornings (8-11)
                teachers.add(new Teacher(
                                "GUSTAVO MELO",
                                Set.of("LENGUA Y COMUNICACIÓN I"),
                                Set.of(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY,
                                                DayOfWeek.THURSDAY, DayOfWeek.FRIDAY),
                                8, 11));

                // Ms. Smith: Math 101, Math 102, Calculus - Mon-Fri mornings (8-11)
                teachers.add(new Teacher(
                                "MONICA E. DIEGO",
                                Set.of("INGLÉS I"),
                                Set.of(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY,
                                                DayOfWeek.THURSDAY, DayOfWeek.FRIDAY),
                                8, 14));

                // Ms. Smith: Math 101, Math 102, Calculus - Mon-Fri mornings (8-11)
                teachers.add(new Teacher(
                                "DIANA R. LLUCK",
                                Set.of("PENSAMIENTO MATEMÁTICO I"),
                                Set.of(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY,
                                                DayOfWeek.THURSDAY, DayOfWeek.FRIDAY),
                                8, 14));

                // Ms. Smith: Math 101, Math 102, Calculus - Mon-Fri mornings (8-11)
                teachers.add(new Teacher(
                                "BALBINA CATALAN",
                                Set.of("CULTURA DIGITAL I"),
                                Set.of(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY,
                                                DayOfWeek.THURSDAY, DayOfWeek.FRIDAY),
                                10, 14));

                // Ms. Smith: Math 101, Math 102, Calculus - Mon-Fri mornings (8-11)
                teachers.add(new Teacher(
                                "ITZEL URIBE",
                                Set.of("LA MATERIA Y SUS INTERACCIONES", "RECURSOS SOCIOEMOCIONALES I", "TUTORIAS I"),
                                Set.of(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY,
                                                DayOfWeek.THURSDAY, DayOfWeek.FRIDAY),
                                8, 14));

                // Ms. Smith: Math 101, Math 102, Calculus - Mon-Fri mornings (8-11)
                teachers.add(new Teacher(
                                "PABLO B. ROSET",
                                Set.of("CLUB DE AJEDREZ"),
                                Set.of(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY,
                                                DayOfWeek.THURSDAY, DayOfWeek.FRIDAY),
                                8, 14));

                // Ms. Smith: Math 101, Math 102, Calculus - Mon-Fri mornings (8-11)
                teachers.add(new Teacher(
                                "CARLOS IVAN ADAME",
                                Set.of("ACTIVACIÓN FÍSICA"),
                                Set.of(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY,
                                                DayOfWeek.THURSDAY, DayOfWeek.FRIDAY),
                                10, 14));

                // Ms. Smith: Math 101, Math 102, Calculus - Mon-Fri mornings (8-11)
                teachers.add(new Teacher(
                                "JOSE BAHENA WENCES",
                                Set.of("HUMANIDADES I"),
                                Set.of(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY,
                                                DayOfWeek.THURSDAY, DayOfWeek.FRIDAY),
                                10, 14));

                // Ms. Smith: Math 101, Math 102, Calculus - Mon-Fri mornings (8-11)
                teachers.add(new Teacher(
                                "MIGUEL A. GUZMAN CONTRERAS",
                                Set.of("CIENCIAS SOCIALES I"),
                                Set.of(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY,
                                                DayOfWeek.THURSDAY, DayOfWeek.FRIDAY),
                                10, 14));

                // Ms. Smith: Math 101, Math 102, Calculus - Mon-Fri mornings (8-11)
                teachers.add(new Teacher(
                                "Ms. Smith",
                                Set.of("Math 101", "Math 102", "Calculus"),
                                Set.of(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY,
                                                DayOfWeek.THURSDAY, DayOfWeek.FRIDAY),
                                8, 11));

                // Mr. Jones: Math 101, Physics 101, Physics - Mon-Thu
                teachers.add(new Teacher(
                                "Mr. Jones",
                                Set.of("Math 101", "Physics 101", "Physics"),
                                Set.of(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY),
                                8, 17));

                // Dr. Brown: Physics 101, Chemistry 101 - Mon-Wed
                teachers.add(new Teacher(
                                "Dr. Brown",
                                Set.of("Physics 101", "Chemistry 101"),
                                Set.of(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY),
                                8, 17));

                // Ms. Davis: English 101 - Mon-Fri from 11am on
                teachers.add(new Teacher(
                                "Ms. Davis",
                                Set.of("English 101"),
                                Set.of(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY,
                                                DayOfWeek.THURSDAY, DayOfWeek.FRIDAY),
                                11, 17));

                // Dr. Lee: Biology 101, Chemistry 101 - Tue-Fri
                teachers.add(new Teacher(
                                "Dr. Lee",
                                Set.of("Biology 101", "Chemistry 101"),
                                Set.of(DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY),
                                8, 17));

                System.out.println("Generated " + teachers.size() + " teachers.");

                return teachers;
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
                courses.add(new Course("LA MATERIA Y SUS INTERACCIONES", "lab", 3));
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

                rooms.add(new Room("Room 101", "A", "standard"));
                rooms.add(new Room("Room 102", "A", "standard"));
                rooms.add(new Room("Lab 201", "A", "lab"));
                rooms.add(new Room("Room 301", "B", "standard"));
                rooms.add(new Room("Lab 302", "B", "lab"));
                rooms.add(new Room("Room 401", "C", "standard"));

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

                // Group 1o C: Math 101, Physics 101, English 101
                // Assign a preferred room to Grupo 1o C (pre-assigned room for the group)
                Room preferred = rooms.stream()
                                .filter(r -> r.getName().equals("Room 101"))
                                .findFirst()
                                .orElse(null);

                groups.add(new Group("group_1C", "Grupo 1o C",
                                Set.of("LENGUA Y COMUNICACIÓN I", "INGLÉS I", "PENSAMIENTO MATEMÁTICO I",
                                                "CULTURA DIGITAL I", "LA MATERIA Y SUS INTERACCIONES", "HUMANIDADES I",
                                                "CIENCIAS SOCIALES I",
                                                "RECURSOS SOCIOEMOCIONALES I", "TUTORIAS I", "CLUB DE AJEDREZ",
                                                "ACTIVACIÓN FÍSICA"),
                                preferred));

                // Group 10 G: Math 101, Physics 101, English 101

                preferred = rooms.stream()
                                .filter(r -> r.getName().equals("Room 102"))
                                .findFirst()
                                .orElse(null);

                groups.add(new Group("group_1G", "Grupo 1o G",
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
