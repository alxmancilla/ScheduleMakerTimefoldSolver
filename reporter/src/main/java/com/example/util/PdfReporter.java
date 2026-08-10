package com.example.util;

import com.example.domain.CourseBlockAssignment;
import com.example.domain.SchoolSchedule;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;

import java.io.IOException;
import java.util.Map;

public class PdfReporter {

    /**
     * Generate a simple PDF report with score, constraint summaries and a short
     * listing of assignments.
     *
     * @deprecated Hour-based scheduling is no longer supported. Use
     *             {@link #generateBlockReports(SchoolSchedule, Map, Map, String)}
     *             instead.
     */
    @Deprecated
    public static void generateReport(SchoolSchedule schedule,
            Map<String, Integer> hardViolations,
            Map<String, Integer> softViolations,
            String outputPath) throws IOException {
        throw new UnsupportedOperationException(
                "Hour-based scheduling is no longer supported. " +
                        "Please use generateBlockReports() instead.");
    }

    /**
     * Generate three PDF reports:
     * - <baseName>-violations.pdf (hard/soft counts + sample)
     * - <baseName>-by-teacher.pdf (schedule grouped by teacher)
     * - <baseName>-by-group.pdf (schedule grouped by group)
     *
     * @deprecated Hour-based scheduling is no longer supported. Use
     *             {@link #generateBlockReports(SchoolSchedule, Map, Map, String)}
     *             instead.
     */
    @Deprecated
    public static void generateReports(SchoolSchedule schedule,
            Map<String, Integer> hardViolations,
            Map<String, Integer> softViolations,
            String baseName) throws IOException {
        throw new UnsupportedOperationException(
                "Hour-based scheduling is no longer supported. " +
                        "Please use generateBlockReports() instead.");
    }

    /**
     * @deprecated Hour-based scheduling is no longer supported. Use
     *             {@link #generateBlockViolationsPdf(SchoolSchedule, Map, Map, String)}
     *             instead.
     */
    @Deprecated
    private static void generateViolationsPdf(SchoolSchedule schedule,
            Map<String, Integer> hardViolations,
            Map<String, Integer> softViolations,
            String outputPath) throws IOException {
        throw new UnsupportedOperationException("Hour-based scheduling is no longer supported");
    }

    /**
     * @deprecated Hour-based scheduling is no longer supported. Use
     *             {@link #generateBlockScheduleByTeacherPdf(SchoolSchedule, String)}
     *             instead.
     */
    @Deprecated
    private static void generateScheduleByTeacherPdf(SchoolSchedule schedule, String outputPath) throws IOException {
        throw new UnsupportedOperationException("Hour-based scheduling is no longer supported");
    }

    /**
     * @deprecated Hour-based scheduling is no longer supported. Use
     *             {@link #generateBlockScheduleByGroupPdf(SchoolSchedule, String)}
     *             instead.
     */
    @Deprecated
    private static void generateScheduleByGroupPdf(SchoolSchedule schedule, String outputPath) throws IOException {
        throw new UnsupportedOperationException("Hour-based scheduling is no longer supported");
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

    // ========== BLOCK-BASED SCHEDULING REPORTS ==========

    /**
     * Generate three PDF reports for block-based scheduling:
     * - <baseName>-incumplimientos.pdf (violations)
     * - <baseName>-por-maestro.pdf (schedule by teacher)
     * - <baseName>-por-grupo.pdf (schedule by group)
     */
    public static void generateBlockReports(SchoolSchedule schedule,
            Map<String, Integer> hardViolations,
            Map<String, Integer> softViolations,
            String baseName) throws IOException {
        String violationsPath = baseName + "-incumplimientos.pdf";
        String byTeacherPath = baseName + "-por-maestro.pdf";
        String byGroupPath = baseName + "-por-grupo.pdf";

        generateBlockViolationsPdf(schedule, hardViolations, softViolations, violationsPath);
        generateBlockScheduleByTeacherPdf(schedule, byTeacherPath);
        generateBlockScheduleByGroupPdf(schedule, byGroupPath);
    }

    private static void generateBlockViolationsPdf(SchoolSchedule schedule,
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
                cs.showText("Block Schedule - Constraint Violations Report");
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

                cs.endText();
            } finally {
                if (cs != null)
                    cs.close();
            }
            doc.save(outputPath);
        }
    }

    private static void generateBlockScheduleByTeacherPdf(SchoolSchedule schedule, String outputPath)
            throws IOException {
        try (PDDocument doc = new PDDocument()) {
            Map<String, java.util.List<CourseBlockAssignment>> byTeacher = new java.util.TreeMap<>();
            for (CourseBlockAssignment a : schedule.getCourseBlockAssignments()) {
                String teacher = a.getTeacher() != null
                        ? (a.getTeacher().getName() + " " + a.getTeacher().getLastName())
                        : "UNASSIGNED";
                byTeacher.computeIfAbsent(teacher, k -> new java.util.ArrayList<>()).add(a);
            }

            for (Map.Entry<String, java.util.List<CourseBlockAssignment>> teacherEntry : byTeacher.entrySet()) {
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
                    cs.showText("Calendario (Bloques) - Maestr@: " + teacherName);
                    cs.newLineAtOffset(0, -heading * 1.5f);
                    currentY -= heading * 1.5f;
                    cs.endText();

                    // Build calendar data structure for this teacher
                    // For blocks, we need to track which hours are occupied by which block
                    Map<Integer, Map<Integer, CourseBlockAssignment>> calendar = new java.util.TreeMap<>();

                    int minHour = 7;
                    int maxHour = 15;

                    for (int day = 1; day <= 5; day++) {
                        calendar.put(day, new java.util.TreeMap<>());
                    }

                    // Populate calendar with block assignments for this teacher
                    // Store block at ALL hours it occupies so we can show info in each cell
                    for (CourseBlockAssignment a : teacherEntry.getValue()) {
                        if (a.getTimeslot() != null) {
                            int dayValue = a.getTimeslot().getDayOfWeek().getValue();
                            int startHour = a.getTimeslot().getStartHour();
                            int blockLength = a.getBlockLength();
                            if (dayValue >= 1 && dayValue <= 5 && startHour >= minHour && startHour < maxHour) {
                                // Store block at ALL hours it occupies
                                for (int h = 0; h < blockLength; h++) {
                                    int hour = startHour + h;
                                    if (hour < maxHour) {
                                        calendar.get(dayValue).put(hour, a);
                                    }
                                }
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
                            CourseBlockAssignment blockAssignment = calendar.get(day).get(hour);
                            StringBuilder cellText = new StringBuilder();

                            if (blockAssignment != null) {
                                // Show full block info in every cell it occupies
                                cellText.append(blockAssignment.getCourse().getAbbreviation());
                                cellText.append("\n");
                                cellText.append(blockAssignment.getGroup().getName());
                                if (blockAssignment.getRoom() != null) {
                                    cellText.append("\n");
                                    cellText.append(blockAssignment.getRoom().getName());
                                }
                            }

                            drawCell(cs, tableX + (day - 1) * cellWidth, tableY, cellWidth, cellHeight,
                                    cellText.toString(), 6, false);
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

    private static void generateBlockScheduleByGroupPdf(SchoolSchedule schedule, String outputPath)
            throws IOException {
        try (PDDocument doc = new PDDocument()) {
            Map<String, java.util.List<CourseBlockAssignment>> byGroup = new java.util.TreeMap<>();
            for (CourseBlockAssignment a : schedule.getCourseBlockAssignments()) {
                String group = a.getGroup() != null ? a.getGroup().getName() : "UNASSIGNED";
                byGroup.computeIfAbsent(group, k -> new java.util.ArrayList<>()).add(a);
            }

            for (Map.Entry<String, java.util.List<CourseBlockAssignment>> groupEntry : byGroup.entrySet()) {
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
                    cs.showText("Calendario (Bloques) - Grupo: " + groupName);
                    cs.newLineAtOffset(0, -heading * 1.5f);
                    currentY -= heading * 1.5f;
                    cs.endText();

                    // Build calendar data structure for this group
                    Map<Integer, Map<Integer, CourseBlockAssignment>> calendar = new java.util.TreeMap<>();

                    int minHour = 7;
                    int maxHour = 15;

                    for (int day = 1; day <= 5; day++) {
                        calendar.put(day, new java.util.TreeMap<>());
                    }

                    // Populate calendar with block assignments for this group
                    // Store block at ALL hours it occupies so we can show info in each cell
                    for (CourseBlockAssignment a : groupEntry.getValue()) {
                        if (a.getTimeslot() != null) {
                            int dayValue = a.getTimeslot().getDayOfWeek().getValue();
                            int startHour = a.getTimeslot().getStartHour();
                            int blockLength = a.getBlockLength();
                            if (dayValue >= 1 && dayValue <= 5 && startHour >= minHour && startHour < maxHour) {
                                // Store block at ALL hours it occupies
                                for (int h = 0; h < blockLength; h++) {
                                    int hour = startHour + h;
                                    if (hour < maxHour) {
                                        calendar.get(dayValue).put(hour, a);
                                    }
                                }
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
                            CourseBlockAssignment blockAssignment = calendar.get(day).get(hour);
                            StringBuilder cellText = new StringBuilder();

                            if (blockAssignment != null) {
                                // Show full block info in every cell it occupies
                                cellText.append(blockAssignment.getCourse().getAbbreviation());
                                if (blockAssignment.getTeacher() != null) {
                                    cellText.append("\n");
                                    cellText.append(blockAssignment.getTeacher().getName() + " "
                                            + blockAssignment.getTeacher().getLastName());
                                }
                                if (blockAssignment.getRoom() != null) {
                                    cellText.append("\n");
                                    cellText.append(blockAssignment.getRoom().getName());
                                }
                            }

                            drawCell(cs, tableX + (day - 1) * cellWidth, tableY, cellWidth, cellHeight,
                                    cellText.toString(), 6, false);
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
}
