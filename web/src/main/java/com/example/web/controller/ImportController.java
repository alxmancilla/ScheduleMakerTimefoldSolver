package com.example.web.controller;

import com.example.web.dto.ExcelImportResponse;
import com.example.web.service.ExcelExportService;
import com.example.web.service.ExcelImportService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

/**
 * Excel import/export of base problem data (Teachers, Courses, Rooms,
 * Groups, Group_Courses).
 *
 * Import is deliberately under /api/import (not /api/admin/**), so
 * SecurityConfig's general POST rule (WRITER or ADMIN) applies rather than
 * the ADMIN-only admin rule - WRITERs can import, same as they can
 * create/edit other domain data. READERs still cannot (POST requires
 * WRITER/ADMIN).
 *
 * Export is a read, so it follows the general GET rule instead (READER,
 * WRITER, or ADMIN - not TEACHER, which is excluded from that rule) - there's
 * no reason to further restrict downloading a snapshot of data those roles
 * can already view in the UI. Import already tolerates a workbook with only
 * some sheets present (ExcelImportService skips whatever's missing), so the
 * per-entity "Export Teachers/Courses/Rooms/Groups" buttons in the UI reuse
 * this same POST endpoint unchanged - only export needs an entity filter.
 */
@RestController
@RequestMapping("/api/import")
public class ImportController {

    @Autowired
    private ExcelImportService excelImportService;

    @Autowired
    private ExcelExportService excelExportService;

    @PostMapping("/excel")
    public ResponseEntity<ExcelImportResponse> importExcel(@RequestParam("file") MultipartFile file) throws IOException {
        if (file.isEmpty()) {
            throw new IllegalArgumentException("No file was uploaded");
        }
        ExcelImportService.ImportResult result = excelImportService.importFromExcel(file.getInputStream());
        ExcelImportResponse response = new ExcelImportResponse(result);
        HttpStatus status = result.isSuccess() ? HttpStatus.OK : HttpStatus.BAD_REQUEST;
        return ResponseEntity.status(status).body(response);
    }

    @GetMapping("/excel")
    public ResponseEntity<byte[]> exportExcel(@RequestParam(required = false) String entities) throws IOException {
        Set<String> entitySet = parseEntities(entities);
        byte[] workbook = excelExportService.exportToExcel(entitySet);
        String suffix = entitySet == null ? "" : "-" + String.join("-", new TreeSet<>(entitySet));
        String filename = "schedule-export" + suffix + "-" + LocalDate.now() + ".xlsx";
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .body(workbook);
    }

    /** Comma-separated entity names (e.g. "teachers,rooms"); null/blank means "export everything". */
    private Set<String> parseEntities(String entities) {
        if (entities == null || entities.isBlank()) {
            return null;
        }
        Set<String> parsed = Arrays.stream(entities.split(","))
                .map(String::trim)
                .map(String::toLowerCase)
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toSet());
        if (!ExcelExportService.VALID_ENTITIES.containsAll(parsed)) {
            throw new IllegalArgumentException(
                    "Unknown value in 'entities' - valid values: " + String.join(", ", new TreeSet<>(ExcelExportService.VALID_ENTITIES)));
        }
        return parsed;
    }
}
