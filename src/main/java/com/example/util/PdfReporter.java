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
            Map<String, java.util.List<CourseAssignment>> byTeacher = new java.util.TreeMap<>();
            for (CourseAssignment a : schedule.getCourseAssignments()) {
                String teacher = a.getTeacher() != null ? a.getTeacher().getName() : "UNASSIGNED";
                byTeacher.computeIfAbsent(teacher, k -> new java.util.ArrayList<>()).add(a);
            }

            for (Map.Entry<String, java.util.List<CourseAssignment>> teacherEntry : byTeacher.entrySet()) {
                String teacherName = teacherEntry.getKey();

                PDPage page = new PDPage(PDRectangle.LETTER);
                doc.addPage(page);

                PDPageContentStream cs = new PDPageContentStream(doc, page);
                try {
                    float margin = 40;
                    float pageHeight = page.getMediaBox().getHeight();
                    float pageWidth = page.getMediaBox().getWidth();
                    float yStart = pageHeight - margin;
                    float heading = 14f;
                    float currentY = yStart;

                    // Title
                    cs.beginText();
                    cs.setFont(PDType1Font.HELVETICA_BOLD, 15);
                    cs.newLineAtOffset(margin, currentY);
                    cs.showText("Calendario - Maestr@: " + teacherName);
                    cs.newLineAtOffset(0, -heading * 1.5f);
                    currentY -= heading * 1.5f;
                    cs.endText();

                    // Build calendar data structure for this teacher
                    Map<Integer, Map<Integer, java.util.List<CourseAssignment>>> calendar = new java.util.TreeMap<>();

                    int minHour = 8;
                    int maxHour = 15;

                    for (int day = 1; day <= 5; day++) {
                        calendar.put(day, new java.util.TreeMap<>());
                        for (int hour = minHour; hour < maxHour; hour++) {
                            calendar.get(day).put(hour, new java.util.ArrayList<>());
                        }
                    }

                    // Populate calendar with assignments for this teacher
                    for (CourseAssignment a : teacherEntry.getValue()) {
                        if (a.getTimeslot() != null) {
                            int dayValue = a.getTimeslot().getDayOfWeek().getValue();
                            int hour = a.getTimeslot().getHour();
                            if (dayValue >= 1 && dayValue <= 5 && hour >= minHour && hour < maxHour) {
                                calendar.get(dayValue).get(hour).add(a);
                            }
                        }
                    }

                    // Draw table
                    String[] daysOfWeek = { "Lunes", "Martes", "Miércoles", "Jueves", "Viernes" };
                    float cellWidth = (pageWidth - 2 * margin - 50) / 5;
                    float cellHeight = 40;
                    float tableX = margin + 50;
                    float tableY = currentY - cellHeight - 5;

                    // Header row with days
                    drawCell(cs, tableX - 50, tableY, 50, cellHeight, "Hora", 8, true);
                    for (int i = 0; i < 5; i++) {
                        drawCell(cs, tableX + i * cellWidth, tableY, cellWidth, cellHeight, daysOfWeek[i], 8, true);
                    }
                    tableY -= cellHeight;

                    // Data rows with hours
                    for (int hour = minHour; hour < maxHour; hour++) {
                        // Hour label
                        String hourLabel = hour + ":00-" + (hour + 1) + ":00";
                        drawCell(cs, tableX - 50, tableY, 50, cellHeight, hourLabel, 8, false);

                        // Assignments for each day
                        for (int day = 1; day <= 5; day++) {
                            java.util.List<CourseAssignment> assignments = calendar.get(day).get(hour);
                            StringBuilder cellText = new StringBuilder();
                            for (CourseAssignment a : assignments) {
                                if (cellText.length() > 0) {
                                    cellText.append("\n");
                                }
                                cellText.append(a.getCourse().getName());
                                cellText.append("\n");
                                cellText.append(a.getGroup().getName());
                                if (a.getRoom() != null) {
                                    cellText.append("\n");
                                    cellText.append(a.getRoom().getName());
                                }
                            }
                            drawCell(cs, tableX + (day - 1) * cellWidth, tableY, cellWidth, cellHeight,
                                    cellText.toString(), 8, false);
                        }
                        tableY -= cellHeight;
                    }

                    cs.close();
                } catch (IOException e) {
                    cs.close();
                    throw e;
                }
            }

            doc.save(outputPath);
        }
    }

    private static void generateScheduleByGroupPdf(SchoolSchedule schedule, String outputPath) throws IOException {
        try (PDDocument doc = new PDDocument()) {
            Map<String, java.util.List<CourseAssignment>> byGroup = new java.util.TreeMap<>();
            for (CourseAssignment a : schedule.getCourseAssignments()) {
                String group = a.getGroup() != null ? a.getGroup().getName() : "UNASSIGNED";
                byGroup.computeIfAbsent(group, k -> new java.util.ArrayList<>()).add(a);
            }

            boolean firstPage = true;
            for (Map.Entry<String, java.util.List<CourseAssignment>> groupEntry : byGroup.entrySet()) {
                String groupName = groupEntry.getKey();

                PDPage page = new PDPage(PDRectangle.LETTER);
                doc.addPage(page);

                PDPageContentStream cs = new PDPageContentStream(doc, page);
                try {
                    float margin = 40;
                    float pageHeight = page.getMediaBox().getHeight();
                    float pageWidth = page.getMediaBox().getWidth();
                    float yStart = pageHeight - margin;
                    float heading = 14f;
                    float currentY = yStart;

                    // Title
                    cs.beginText();
                    cs.setFont(PDType1Font.HELVETICA_BOLD, 15);
                    cs.newLineAtOffset(margin, currentY);
                    cs.showText("Calendario - Grupo: " + groupName);
                    cs.newLineAtOffset(0, -heading * 1.5f);
                    currentY -= heading * 1.5f;
                    cs.endText();

                    // Build calendar data structure for this group
                    Map<Integer, Map<Integer, java.util.List<CourseAssignment>>> calendar = new java.util.TreeMap<>();

                    int minHour = 8;
                    int maxHour = 15;

                    for (int day = 1; day <= 5; day++) {
                        calendar.put(day, new java.util.TreeMap<>());
                        for (int hour = minHour; hour < maxHour; hour++) {
                            calendar.get(day).put(hour, new java.util.ArrayList<>());
                        }
                    }

                    // Populate calendar with assignments for this group
                    for (CourseAssignment a : groupEntry.getValue()) {
                        if (a.getTimeslot() != null) {
                            int dayValue = a.getTimeslot().getDayOfWeek().getValue();
                            int hour = a.getTimeslot().getHour();
                            if (dayValue >= 1 && dayValue <= 5 && hour >= minHour && hour < maxHour) {
                                calendar.get(dayValue).get(hour).add(a);
                            }
                        }
                    }

                    // Draw table
                    String[] daysOfWeek = { "Lunes", "Martes", "Miércoles", "Jueves", "Viernes" };
                    float cellWidth = (pageWidth - 2 * margin - 50) / 5;
                    float cellHeight = 40;
                    float tableX = margin + 50;
                    float tableY = currentY - cellHeight - 5;

                    // Header row with days
                    drawCell(cs, tableX - 50, tableY, 50, cellHeight, "Hora", 8, true);
                    for (int i = 0; i < 5; i++) {
                        drawCell(cs, tableX + i * cellWidth, tableY, cellWidth, cellHeight, daysOfWeek[i], 8, true);
                    }
                    tableY -= cellHeight;

                    // Data rows with hours
                    for (int hour = minHour; hour < maxHour; hour++) {
                        // Hour label
                        String hourLabel = hour + ":00-" + (hour + 1) + ":00";
                        drawCell(cs, tableX - 50, tableY, 50, cellHeight, hourLabel, 8, false);

                        // Assignments for each day
                        for (int day = 1; day <= 5; day++) {
                            java.util.List<CourseAssignment> assignments = calendar.get(day).get(hour);
                            StringBuilder cellText = new StringBuilder();
                            for (CourseAssignment a : assignments) {
                                if (cellText.length() > 0) {
                                    cellText.append("\n");
                                }
                                cellText.append(a.getCourse().getName());
                                if (a.getTeacher() != null) {
                                    cellText.append("\n");
                                    cellText.append(a.getTeacher().getName());
                                }
                                if (a.getRoom() != null) {
                                    cellText.append("\n");
                                    cellText.append(a.getRoom().getName());
                                }
                            }
                            drawCell(cs, tableX + (day - 1) * cellWidth, tableY, cellWidth, cellHeight,
                                    cellText.toString(), 8, false);
                        }
                        tableY -= cellHeight;
                    }

                    cs.close();
                } catch (IOException e) {
                    cs.close();
                    throw e;
                }
            }

            doc.save(outputPath);
        }
    }

    /**
     * Helper method to draw a table cell with text
     */
    private static void drawCell(PDPageContentStream cs, float x, float y, float width, float height,
            String text, int fontSize, boolean isBold) throws IOException {
        // Draw cell border
        cs.setStrokingColor(0, 0, 0);
        cs.setLineWidth(1);
        cs.addRect(x, y, width, height);
        cs.stroke();

        if (text == null || text.isEmpty()) {
            return;
        }

        // Draw text inside cell
        cs.beginText();
        if (isBold) {
            cs.setFont(PDType1Font.HELVETICA_BOLD, fontSize);
        } else {
            cs.setFont(PDType1Font.HELVETICA, fontSize);
        }

        // Position text at top-left of cell with padding
        float textX = x + 2;
        float textY = y + height - fontSize - 2;

        cs.newLineAtOffset(textX, textY);

        // Handle multiline text
        String[] lines = text.split("\n");
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];

            // Truncate line if too long for cell
            if (line.length() > 20) {
                line = line.substring(0, 17) + "...";
            }

            if (i > 0) {
                cs.newLineAtOffset(0, -(fontSize + 1));
            }
            cs.showText(line);
        }

        cs.endText();
    }
}
