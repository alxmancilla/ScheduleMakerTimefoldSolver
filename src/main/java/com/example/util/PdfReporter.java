package com.example.util;

import com.example.domain.CourseAssignment;
import com.example.domain.SchoolSchedule;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;

import java.io.IOException;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

public class PdfReporter {

    /**
     * Generate a simple PDF report with score, constraint summaries and a short
     * listing of assignments.
     */
    public static void generateReport(SchoolSchedule schedule,
            Map<String, Integer> hardViolations,
            Map<String, Integer> softViolations,
            String outputPath) throws IOException {
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.LETTER);
            doc.addPage(page);

            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                float margin = 50;
                float y = page.getMediaBox().getHeight() - margin;
                float leading = 14f;

                cs.beginText();
                cs.setFont(PDType1Font.HELVETICA_BOLD, 16);
                cs.newLineAtOffset(margin, y);
                cs.showText("School Schedule Report");
                cs.newLineAtOffset(0, -leading * 1.5f);

                cs.setFont(PDType1Font.HELVETICA, 11);
                cs.showText("Score: " + schedule.getScore());
                cs.newLineAtOffset(0, -leading);

                cs.setFont(PDType1Font.HELVETICA_BOLD, 12);
                cs.showText("Hard Constraint Violations:");
                cs.newLineAtOffset(0, -leading);
                cs.setFont(PDType1Font.HELVETICA, 11);
                for (Map.Entry<String, Integer> e : hardViolations.entrySet()) {
                    cs.showText("- " + e.getKey() + ": " + e.getValue());
                    cs.newLineAtOffset(0, -leading);
                }

                cs.setFont(PDType1Font.HELVETICA_BOLD, 12);
                cs.showText("Soft Constraint Violations:");
                cs.newLineAtOffset(0, -leading);
                cs.setFont(PDType1Font.HELVETICA, 11);
                for (Map.Entry<String, Integer> e : softViolations.entrySet()) {
                    cs.showText("- " + e.getKey() + ": " + e.getValue());
                    cs.newLineAtOffset(0, -leading);
                }

                cs.newLineAtOffset(0, -leading);
                cs.setFont(PDType1Font.HELVETICA_BOLD, 12);
                cs.showText("Sample Assignments:");
                cs.newLineAtOffset(0, -leading);
                cs.setFont(PDType1Font.HELVETICA, 10);

                DateTimeFormatter fmt = DateTimeFormatter.ofPattern("E HH:mm");
                int count = 0;
                for (CourseAssignment a : schedule.getCourseAssignments()) {
                    if (count++ > 30) { // limit lines to avoid overflow
                        cs.showText("... (truncated) ...");
                        cs.newLineAtOffset(0, -leading);
                        break;
                    }
                    String timeslot = a.getTimeslot() != null ? a.getTimeslot().toString() : "UNASSIGNED";
                    String teacher = a.getTeacher() != null ? a.getTeacher().getName() : "UNASSIGNED";
                    String room = a.getRoom() != null ? a.getRoom().getName() : "UNASSIGNED";
                    String line = String.format("%s - %s - %s - %s", a.getCourse().getName(), a.getGroup().getName(),
                            timeslot, teacher + "/" + room);
                    if (line.length() > 120)
                        line = line.substring(0, 116) + "...";
                    cs.showText(line);
                    cs.newLineAtOffset(0, -leading);
                }

                cs.endText();
            }

            doc.save(outputPath);
        }
    }

    /**
     * Generate three PDF reports:
     * - <baseName>-violations.pdf (hard/soft counts + sample)
     * - <baseName>-by-teacher.pdf (schedule grouped by teacher)
     * - <baseName>-by-group.pdf (schedule grouped by group)
     */
    public static void generateReports(SchoolSchedule schedule,
            Map<String, Integer> hardViolations,
            Map<String, Integer> softViolations,
            String baseName) throws IOException {
        String violationsPath = baseName + "-violations.pdf";
        String byTeacherPath = baseName + "-by-teacher.pdf";
        String byGroupPath = baseName + "-by-group.pdf";

        generateViolationsPdf(schedule, hardViolations, softViolations, violationsPath);
        generateScheduleByTeacherPdf(schedule, byTeacherPath);
        generateScheduleByGroupPdf(schedule, byGroupPath);
    }

    private static void generateViolationsPdf(SchoolSchedule schedule,
            Map<String, Integer> hardViolations,
            Map<String, Integer> softViolations,
            String outputPath) throws IOException {
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.LETTER);
            doc.addPage(page);

            PDPageContentStream cs = new PDPageContentStream(doc, page);
            try {
                float margin = 50;
                float pageHeight = page.getMediaBox().getHeight();
                float yStart = pageHeight - margin;
                float leading = 14f;
                float currentY = yStart;

                cs.beginText();
                cs.setFont(PDType1Font.HELVETICA_BOLD, 16);
                cs.newLineAtOffset(margin, currentY);
                cs.showText("Constraint Violations Report");
                cs.newLineAtOffset(0, -leading * 1.5f);
                currentY -= leading * 1.5f;

                cs.setFont(PDType1Font.HELVETICA, 11);
                cs.showText("Score: " + schedule.getScore());
                cs.newLineAtOffset(0, -leading);
                currentY -= leading;

                cs.setFont(PDType1Font.HELVETICA_BOLD, 12);
                cs.showText("Hard Constraint Violations:");
                cs.newLineAtOffset(0, -leading);
                currentY -= leading;
                cs.setFont(PDType1Font.HELVETICA, 11);

                for (Map.Entry<String, Integer> e : hardViolations.entrySet()) {
                    if (currentY - leading < margin) {
                        cs.endText();
                        cs.close();
                        page = new PDPage(PDRectangle.LETTER);
                        doc.addPage(page);
                        cs = new PDPageContentStream(doc, page);
                        currentY = yStart;
                        cs.beginText();
                        cs.setFont(PDType1Font.HELVETICA, 11);
                        cs.newLineAtOffset(margin, currentY);
                    }
                    cs.showText("- " + e.getKey() + ": " + e.getValue());
                    cs.newLineAtOffset(0, -leading);
                    currentY -= leading;
                }

                if (currentY - leading < margin) {
                    cs.endText();
                    cs.close();
                    page = new PDPage(PDRectangle.LETTER);
                    doc.addPage(page);
                    cs = new PDPageContentStream(doc, page);
                    currentY = yStart;
                    cs.beginText();
                    cs.setFont(PDType1Font.HELVETICA, 11);
                    cs.newLineAtOffset(margin, currentY);
                }

                cs.setFont(PDType1Font.HELVETICA_BOLD, 12);
                cs.showText("Soft Constraint Violations:");
                cs.newLineAtOffset(0, -leading);
                currentY -= leading;
                cs.setFont(PDType1Font.HELVETICA, 11);

                for (Map.Entry<String, Integer> e : softViolations.entrySet()) {
                    if (currentY - leading < margin) {
                        cs.endText();
                        cs.close();
                        page = new PDPage(PDRectangle.LETTER);
                        doc.addPage(page);
                        cs = new PDPageContentStream(doc, page);
                        currentY = yStart;
                        cs.beginText();
                        cs.setFont(PDType1Font.HELVETICA, 11);
                        cs.newLineAtOffset(margin, currentY);
                    }
                    cs.showText("- " + e.getKey() + ": " + e.getValue());
                    cs.newLineAtOffset(0, -leading);
                    currentY -= leading;
                }

                // Sample assignments
                if (currentY - leading < margin) {
                    cs.endText();
                    cs.close();
                    page = new PDPage(PDRectangle.LETTER);
                    doc.addPage(page);
                    cs = new PDPageContentStream(doc, page);
                    currentY = yStart;
                    cs.beginText();
                    cs.setFont(PDType1Font.HELVETICA, 11);
                    cs.newLineAtOffset(margin, currentY);
                }

                cs.newLineAtOffset(0, -leading);
                currentY -= leading;
                cs.setFont(PDType1Font.HELVETICA_BOLD, 12);
                cs.showText("Sample Assignments:");
                cs.newLineAtOffset(0, -leading);
                currentY -= leading;
                cs.setFont(PDType1Font.HELVETICA, 10);

                int count = 0;
                for (CourseAssignment a : schedule.getCourseAssignments()) {
                    if (count++ > 1000) { // safety cap
                        break;
                    }
                    String timeslot = a.getTimeslot() != null ? a.getTimeslot().toString() : "UNASSIGNED";
                    String teacher = a.getTeacher() != null ? a.getTeacher().getName() : "UNASSIGNED";
                    String room = a.getRoom() != null ? a.getRoom().getName() : "UNASSIGNED";
                    String line = String.format("%s - %s - %s - %s", a.getCourse().getName(), a.getGroup().getName(),
                            timeslot, teacher + "/" + room);
                    if (line.length() > 120)
                        line = line.substring(0, 116) + "...";

                    if (currentY - leading < margin) {
                        cs.endText();
                        cs.close();
                        page = new PDPage(PDRectangle.LETTER);
                        doc.addPage(page);
                        cs = new PDPageContentStream(doc, page);
                        currentY = yStart;
                        cs.beginText();
                        cs.setFont(PDType1Font.HELVETICA, 10);
                        cs.newLineAtOffset(margin, currentY);
                    }
                    cs.showText(line);
                    cs.newLineAtOffset(0, -leading);
                    currentY -= leading;
                }

                cs.endText();
            } finally {
                if (cs != null)
                    cs.close();
            }
            doc.save(outputPath);
        }
    }

    private static void generateScheduleByTeacherPdf(SchoolSchedule schedule, String outputPath) throws IOException {
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.LETTER);
            doc.addPage(page);

            PDPageContentStream cs = new PDPageContentStream(doc, page);
            try {
                float margin = 50;
                float pageHeight = page.getMediaBox().getHeight();
                float yStart = pageHeight - margin;
                float leading = 12f;
                float currentY = yStart;

                cs.beginText();
                cs.setFont(PDType1Font.HELVETICA_BOLD, 14);
                cs.newLineAtOffset(margin, currentY);
                cs.showText("Schedule by Teacher");
                cs.newLineAtOffset(0, -leading * 1.5f);
                currentY -= leading * 1.5f;

                cs.setFont(PDType1Font.HELVETICA, 10);

                Map<String, java.util.List<CourseAssignment>> byTeacher = new java.util.TreeMap<>();
                for (CourseAssignment a : schedule.getCourseAssignments()) {
                    String teacher = a.getTeacher() != null ? a.getTeacher().getName() : "UNASSIGNED";
                    byTeacher.computeIfAbsent(teacher, k -> new java.util.ArrayList<>()).add(a);
                }

                for (Map.Entry<String, java.util.List<CourseAssignment>> e : byTeacher.entrySet()) {
                    if (currentY - leading < margin) {
                        cs.endText();
                        cs.close();
                        page = new PDPage(PDRectangle.LETTER);
                        doc.addPage(page);
                        cs = new PDPageContentStream(doc, page);
                        currentY = yStart;
                        cs.beginText();
                        cs.setFont(PDType1Font.HELVETICA, 10);
                        cs.newLineAtOffset(margin, currentY);
                    }
                    cs.setFont(PDType1Font.HELVETICA_BOLD, 12);
                    cs.showText(e.getKey());
                    cs.newLineAtOffset(0, -leading);
                    currentY -= leading;
                    cs.setFont(PDType1Font.HELVETICA, 10);
                    java.util.List<CourseAssignment> assigns = e.getValue();
                    assigns.sort(java.util.Comparator.comparing(a -> a.getTimeslot() == null ? Integer.MAX_VALUE
                            : a.getTimeslot().getDayOfWeek().getValue() * 100 + a.getTimeslot().getHour()));
                    for (CourseAssignment a : assigns) {
                        if (currentY - leading < margin) {
                            cs.endText();
                            cs.close();
                            page = new PDPage(PDRectangle.LETTER);
                            doc.addPage(page);
                            cs = new PDPageContentStream(doc, page);
                            currentY = yStart;
                            cs.beginText();
                            cs.setFont(PDType1Font.HELVETICA, 10);
                            cs.newLineAtOffset(margin, currentY);
                        }
                        String timeslot = a.getTimeslot() != null ? a.getTimeslot().toString() : "UNASSIGNED";
                        String line = String.format("  %s - %s (Group: %s) Room: %s", timeslot,
                                a.getCourse().getName(), a.getGroup().getName(),
                                a.getRoom() != null ? a.getRoom().getName() : "UNASSIGNED");
                        cs.showText(line);
                        cs.newLineAtOffset(0, -leading);
                        currentY -= leading;
                    }
                }

                cs.endText();
            } finally {
                if (cs != null)
                    cs.close();
            }
            doc.save(outputPath);
        }
    }

    private static void generateScheduleByGroupPdf(SchoolSchedule schedule, String outputPath) throws IOException {
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.LETTER);
            doc.addPage(page);

            PDPageContentStream cs = new PDPageContentStream(doc, page);
            try {
                float margin = 50;
                float pageHeight = page.getMediaBox().getHeight();
                float yStart = pageHeight - margin;
                float leading = 12f;
                float currentY = yStart;

                cs.beginText();
                cs.setFont(PDType1Font.HELVETICA_BOLD, 14);
                cs.newLineAtOffset(margin, currentY);
                cs.showText("Schedule by Group");
                cs.newLineAtOffset(0, -leading * 1.5f);
                currentY -= leading * 1.5f;

                cs.setFont(PDType1Font.HELVETICA, 10);

                Map<String, java.util.List<CourseAssignment>> byGroup = new java.util.TreeMap<>();
                for (CourseAssignment a : schedule.getCourseAssignments()) {
                    String group = a.getGroup() != null ? a.getGroup().getName() : "UNASSIGNED";
                    byGroup.computeIfAbsent(group, k -> new java.util.ArrayList<>()).add(a);
                }

                for (Map.Entry<String, java.util.List<CourseAssignment>> e : byGroup.entrySet()) {
                    if (currentY - leading < margin) {
                        cs.endText();
                        cs.close();
                        page = new PDPage(PDRectangle.LETTER);
                        doc.addPage(page);
                        cs = new PDPageContentStream(doc, page);
                        currentY = yStart;
                        cs.beginText();
                        cs.setFont(PDType1Font.HELVETICA, 10);
                        cs.newLineAtOffset(margin, currentY);
                    }
                    cs.setFont(PDType1Font.HELVETICA_BOLD, 12);
                    cs.showText(e.getKey());
                    cs.newLineAtOffset(0, -leading);
                    currentY -= leading;
                    cs.setFont(PDType1Font.HELVETICA, 10);
                    java.util.List<CourseAssignment> assigns = e.getValue();
                    assigns.sort(java.util.Comparator.comparing(a -> a.getTimeslot() == null ? Integer.MAX_VALUE
                            : a.getTimeslot().getDayOfWeek().getValue() * 100 + a.getTimeslot().getHour()));
                    for (CourseAssignment a : assigns) {
                        if (currentY - leading < margin) {
                            cs.endText();
                            cs.close();
                            page = new PDPage(PDRectangle.LETTER);
                            doc.addPage(page);
                            cs = new PDPageContentStream(doc, page);
                            currentY = yStart;
                            cs.beginText();
                            cs.setFont(PDType1Font.HELVETICA, 10);
                            cs.newLineAtOffset(margin, currentY);
                        }
                        String timeslot = a.getTimeslot() != null ? a.getTimeslot().toString() : "UNASSIGNED";
                        String line = String.format("  %s - %s (Teacher: %s) Room: %s", timeslot,
                                a.getCourse().getName(),
                                a.getTeacher() != null ? a.getTeacher().getName() : "UNASSIGNED",
                                a.getRoom() != null ? a.getRoom().getName() : "UNASSIGNED");
                        cs.showText(line);
                        cs.newLineAtOffset(0, -leading);
                        currentY -= leading;
                    }
                }

                cs.endText();
            } finally {
                if (cs != null)
                    cs.close();
            }
            doc.save(outputPath);
        }
    }
}
