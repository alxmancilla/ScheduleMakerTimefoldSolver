package com.example.util;

import com.example.domain.CourseBlockAssignment;
import com.example.domain.SchoolSchedule;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;

import java.io.IOException;
import java.util.Locale;
import java.util.Map;

public class PdfReporter {

    /**
     * Report chrome text (titles, labels, day names) in English and Spanish -
     * a separate store from web-ui's en.json/es.json, since this module has no
     * access to React/i18next or those files. Only the report's fixed chrome is
     * covered here; violation detail sentences (built in BlockScheduleAnalyzer,
     * shared with the web UI's own violation displays) are a separate, larger
     * localization effort and are not covered by this map.
     */
    private static final Map<String, String> TEXT_EN = Map.ofEntries(
            Map.entry("coverTeacherTitle", "Teacher Schedules"),
            Map.entry("coverGroupTitle", "Group Schedules"),
            Map.entry("term", "Term"),
            Map.entry("generated", "Generated"),
            Map.entry("scheduleVersion", "Schedule Version"),
            Map.entry("teacherLabel", "Teacher"),
            Map.entry("groupLabel", "Group"),
            Map.entry("hour", "Hour"),
            Map.entry("dayMonday", "Monday"),
            Map.entry("dayTuesday", "Tuesday"),
            Map.entry("dayWednesday", "Wednesday"),
            Map.entry("dayThursday", "Thursday"),
            Map.entry("dayFriday", "Friday"),
            Map.entry("violationsTitle", "Block Schedule - Constraint Violations Report"),
            Map.entry("score", "Score"),
            Map.entry("hardViolations", "Hard Constraint Violations:"),
            Map.entry("softViolations", "Soft Constraint Violations:"),
            Map.entry("page", "Page"),
            Map.entry("pageOf", "of"));

    private static final Map<String, String> TEXT_ES = Map.ofEntries(
            Map.entry("coverTeacherTitle", "Horarios de Maestros"),
            Map.entry("coverGroupTitle", "Horarios de Grupos"),
            Map.entry("term", "Periodo"),
            Map.entry("generated", "Generado"),
            Map.entry("scheduleVersion", "Versión del Horario"),
            Map.entry("teacherLabel", "Maestro"),
            Map.entry("groupLabel", "Grupo"),
            Map.entry("hour", "Hora"),
            Map.entry("dayMonday", "Lunes"),
            Map.entry("dayTuesday", "Martes"),
            Map.entry("dayWednesday", "Miércoles"),
            Map.entry("dayThursday", "Jueves"),
            Map.entry("dayFriday", "Viernes"),
            Map.entry("violationsTitle", "Reporte de Violaciones de Restricciones - Horario por Bloques"),
            Map.entry("score", "Puntuación"),
            Map.entry("hardViolations", "Violaciones de Restricciones Estrictas:"),
            Map.entry("softViolations", "Violaciones de Restricciones Suaves:"),
            Map.entry("page", "Página"),
            Map.entry("pageOf", "de"));

    /** Report chrome text for the given key, in the given locale ("es" or anything else -> "en"). */
    private static String t(String key, String locale) {
        Map<String, String> table = "es".equalsIgnoreCase(locale) ? TEXT_ES : TEXT_EN;
        return table.get(key);
    }

    private static String[] dayNames(String locale) {
        return new String[] { t("dayMonday", locale), t("dayTuesday", locale), t("dayWednesday", locale),
                t("dayThursday", locale), t("dayFriday", locale) };
    }

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
     * A cover page (title, term label if set, schedule version timestamp if
     * known, generation date) added as page 1 before the per-teacher/per-group
     * schedule pages, centered on a LETTER page.
     *
     * @param scheduleRunTimestamp when the schedule_run backing this report's
     *                             content was solved - the specific run if one
     *                             was explicitly requested, otherwise the latest
     *                             one, matching whichever DataLoader actually
     *                             read. Null (e.g. no schedule_run exists yet)
     *                             omits the line entirely.
     */
    private static void addCoverPage(PDDocument doc, String title, String termLabel,
            java.time.LocalDateTime scheduleRunTimestamp, String locale) throws IOException {
        PDPage page = new PDPage(PDRectangle.LETTER);
        doc.addPage(page);

        float pageWidth = page.getMediaBox().getWidth();
        float pageHeight = page.getMediaBox().getHeight();
        Locale javaLocale = "es".equalsIgnoreCase(locale) ? new Locale("es") : Locale.ENGLISH;

        try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
            float titleFontSize = 26f;
            float titleWidth = PDType1Font.HELVETICA_BOLD.getStringWidth(title) / 1000 * titleFontSize;
            float titleY = pageHeight * 0.55f;

            cs.beginText();
            cs.setFont(PDType1Font.HELVETICA_BOLD, titleFontSize);
            cs.newLineAtOffset((pageWidth - titleWidth) / 2, titleY);
            cs.showText(title);
            cs.endText();

            float detailFontSize = 12f;
            float detailY = titleY - 40;

            if (termLabel != null && !termLabel.isBlank()) {
                detailY = drawCenteredDetailLine(cs, pageWidth, detailY, detailFontSize,
                        t("term", locale) + ": " + termLabel);
            }

            if (scheduleRunTimestamp != null) {
                String versionText = scheduleRunTimestamp
                        .format(java.time.format.DateTimeFormatter.ofPattern("MMMM d, yyyy, h:mm a", javaLocale));
                detailY = drawCenteredDetailLine(cs, pageWidth, detailY, detailFontSize,
                        t("scheduleVersion", locale) + ": " + versionText);
            }

            String dateLine = t("generated", locale) + ": " + java.time.LocalDate.now()
                    .format(java.time.format.DateTimeFormatter.ofPattern("MMMM d, yyyy", javaLocale));
            drawCenteredDetailLine(cs, pageWidth, detailY, detailFontSize, dateLine);
        }
    }

    /** Draws one centered line of cover-page detail text; returns the Y for the next line. */
    private static float drawCenteredDetailLine(PDPageContentStream cs, float pageWidth, float y, float fontSize,
            String text) throws IOException {
        float width = PDType1Font.HELVETICA.getStringWidth(text) / 1000 * fontSize;
        cs.beginText();
        cs.setFont(PDType1Font.HELVETICA, fontSize);
        cs.newLineAtOffset((pageWidth - width) / 2, y);
        cs.showText(text);
        cs.endText();
        return y - 18;
    }

    /**
     * Shortens a course name/abbreviation longer than 20 characters to its
     * first 10 characters, "...", and its last 7 characters, so a long name
     * stays recognizable by both its start and its end in the narrow
     * schedule-grid cells.
     */
    private static String truncateCourseName(String name) {
        if (name == null || name.length() <= 20) {
            return name;
        }
        return name.substring(0, 10) + "..." + name.substring(name.length() - 7);
    }

    /**
     * Draws a small centered footer (generation date, schedule version) on
     * every page of the finished document except the first {@code skipPages}
     * (1 for the schedule PDFs, to skip a cover page that already shows this
     * prominently; 0 for the violations PDF, which has no cover page). Runs
     * once after all content is drawn, using an APPEND content stream so it
     * doesn't have to be threaded through each page-break point in the
     * content-generation loops above - those already have several exit points
     * (see generateBlockViolationsPdf's mid-loop page breaks) and retrofitting
     * a footer into each one individually would be fragile.
     */
    private static void addFooters(PDDocument doc, java.time.LocalDateTime scheduleRunTimestamp, String locale,
            int skipPages) throws IOException {
        Locale javaLocale = "es".equalsIgnoreCase(locale) ? new Locale("es") : Locale.ENGLISH;
        String footerText = t("generated", locale) + ": " + java.time.LocalDate.now()
                .format(java.time.format.DateTimeFormatter.ofPattern("MMMM d, yyyy", javaLocale));
        if (scheduleRunTimestamp != null) {
            String versionText = scheduleRunTimestamp
                    .format(java.time.format.DateTimeFormatter.ofPattern("MMMM d, yyyy, h:mm a", javaLocale));
            footerText += "   |   " + t("scheduleVersion", locale) + ": " + versionText;
        }

        float fontSize = 8f;
        int totalPages = doc.getNumberOfPages();
        int pageIndex = 0;
        for (PDPage page : doc.getPages()) {
            int pageNumber = ++pageIndex;
            float pageWidth = page.getMediaBox().getWidth();
            String pageText = t("page", locale) + " " + pageNumber + " " + t("pageOf", locale) + " " + totalPages;
            boolean drawFooterText = pageIndex > skipPages;

            try (PDPageContentStream cs = new PDPageContentStream(doc, page,
                    PDPageContentStream.AppendMode.APPEND, true)) {
                if (drawFooterText) {
                    float textWidth = PDType1Font.HELVETICA.getStringWidth(footerText) / 1000 * fontSize;
                    cs.beginText();
                    cs.setFont(PDType1Font.HELVETICA, fontSize);
                    cs.newLineAtOffset((pageWidth - textWidth) / 2, 20);
                    cs.showText(footerText);
                    cs.endText();
                }

                // Page number: centered, every page (including the cover), below the
                // generated/version line above so both stay independently readable.
                float pageTextWidth = PDType1Font.HELVETICA.getStringWidth(pageText) / 1000 * fontSize;
                cs.beginText();
                cs.setFont(PDType1Font.HELVETICA, fontSize);
                cs.newLineAtOffset((pageWidth - pageTextWidth) / 2, 8);
                cs.showText(pageText);
                cs.endText();
            }
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
            String baseName,
            String termLabel,
            java.time.LocalDateTime scheduleRunTimestamp,
            String locale) throws IOException {
        String violationsPath = baseName + "-incumplimientos.pdf";
        String byTeacherPath = baseName + "-por-maestro.pdf";
        String byGroupPath = baseName + "-por-grupo.pdf";

        generateBlockViolationsPdf(schedule, hardViolations, softViolations, violationsPath, scheduleRunTimestamp,
                locale);
        generateBlockScheduleByTeacherPdf(schedule, byTeacherPath, termLabel, scheduleRunTimestamp, locale);
        generateBlockScheduleByGroupPdf(schedule, byGroupPath, termLabel, scheduleRunTimestamp, locale);
    }

    /**
     * Generate just the by-teacher and by-group schedule PDFs, without the
     * violations report. Used by the WRITER-triggered "Generate PDFs" button:
     * the violations report (calendario-incumplimientos.pdf) is generated only
     * automatically right after each engine run (see EngineRunnerService), not
     * on demand here, so it always reflects a specific solve rather than
     * whatever the schedule happens to look like when someone clicks the button.
     */
    public static void generateBlockSchedulePdfs(SchoolSchedule schedule, String baseName, String termLabel,
            java.time.LocalDateTime scheduleRunTimestamp, String locale) throws IOException {
        String byTeacherPath = baseName + "-por-maestro.pdf";
        String byGroupPath = baseName + "-por-grupo.pdf";

        generateBlockScheduleByTeacherPdf(schedule, byTeacherPath, termLabel, scheduleRunTimestamp, locale);
        generateBlockScheduleByGroupPdf(schedule, byGroupPath, termLabel, scheduleRunTimestamp, locale);
    }

    /**
     * Generate just the violations PDF (<outputPath>), without the
     * by-teacher/by-group schedule reports. Used for the admin-only
     * compliance snapshot generated automatically right after each engine
     * run, where only the incumplimientos report is needed.
     */
    public static void generateBlockViolationsPdf(SchoolSchedule schedule,
            Map<String, Integer> hardViolations,
            Map<String, Integer> softViolations,
            String outputPath,
            java.time.LocalDateTime scheduleRunTimestamp,
            String locale) throws IOException {
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.LETTER);
            doc.addPage(page);

            Locale javaLocale = "es".equalsIgnoreCase(locale) ? new Locale("es") : Locale.ENGLISH;

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
                cs.showText(t("violationsTitle", locale));
                cs.newLineAtOffset(0, -leading * 1.5f);
                currentY -= leading * 1.5f;

                cs.setFont(PDType1Font.HELVETICA, 11);
                // When the schedule was actually solved (schedule_run.created_at) - not
                // to be confused with the small "Generated: <today>" footer line, which
                // is when this PDF file itself was rendered, possibly long after the
                // solve. Omitted when unknown (e.g. no schedule_run exists yet), same
                // null-safety convention as the cover page / footer's own use of this
                // value.
                if (scheduleRunTimestamp != null) {
                    String versionText = scheduleRunTimestamp.format(
                            java.time.format.DateTimeFormatter.ofPattern("MMMM d, yyyy, h:mm a", javaLocale));
                    cs.showText(t("scheduleVersion", locale) + ": " + versionText);
                    cs.newLineAtOffset(0, -leading);
                    currentY -= leading;
                }

                cs.showText(t("score", locale) + ": " + schedule.getScore());
                cs.newLineAtOffset(0, -leading);
                currentY -= leading;

                cs.setFont(PDType1Font.HELVETICA_BOLD, 12);
                cs.showText(t("hardViolations", locale));
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
                cs.showText(t("softViolations", locale));
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
            addFooters(doc, scheduleRunTimestamp, locale, 0);
            doc.save(outputPath);
        }
    }

    private static void generateBlockScheduleByTeacherPdf(SchoolSchedule schedule, String outputPath,
            String termLabel, java.time.LocalDateTime scheduleRunTimestamp, String locale) throws IOException {
        try (PDDocument doc = new PDDocument()) {
            addCoverPage(doc, t("coverTeacherTitle", locale), termLabel, scheduleRunTimestamp, locale);

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
                    cs.showText(t("teacherLabel", locale) + ": " + teacherName);
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
                    String[] daysOfWeek = dayNames(locale);
                    float cellWidth = (pageWidth - 2 * margin - 50) / 5;
                    float cellHeight = 40;
                    float tableX = margin + 50;
                    float tableY = currentY - cellHeight - 5;

                    // Header row with days
                    drawCell(cs, tableX - 50, tableY, 50, cellHeight, t("hour", locale), 8, true);
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
                                cellText.append(truncateCourseName(blockAssignment.getCourse().getAbbreviation()));
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

            addFooters(doc, scheduleRunTimestamp, locale, 1);
            doc.save(outputPath);
        }
    }

    private static void generateBlockScheduleByGroupPdf(SchoolSchedule schedule, String outputPath,
            String termLabel, java.time.LocalDateTime scheduleRunTimestamp, String locale) throws IOException {
        try (PDDocument doc = new PDDocument()) {
            addCoverPage(doc, t("coverGroupTitle", locale), termLabel, scheduleRunTimestamp, locale);

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
                    cs.showText(t("groupLabel", locale) + ": " + groupName);
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
                    String[] daysOfWeek = dayNames(locale);
                    float cellWidth = (pageWidth - 2 * margin - 50) / 5;
                    float cellHeight = 40;
                    float tableX = margin + 50;
                    float tableY = currentY - cellHeight - 5;

                    // Header row with days
                    drawCell(cs, tableX - 50, tableY, 50, cellHeight, t("hour", locale), 8, true);
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
                                cellText.append(truncateCourseName(blockAssignment.getCourse().getAbbreviation()));
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

            addFooters(doc, scheduleRunTimestamp, locale, 1);
            doc.save(outputPath);
        }
    }
}
