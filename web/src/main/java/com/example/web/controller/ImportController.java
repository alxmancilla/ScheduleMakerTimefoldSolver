package com.example.web.controller;

import com.example.web.dto.ExcelImportResponse;
import com.example.web.service.ExcelImportService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

/**
 * Excel import of base problem data (Teachers, Courses, Rooms, Groups,
 * Group_Courses). Deliberately under /api/import (not /api/admin/**), so
 * SecurityConfig's general POST rule (WRITER or ADMIN) applies rather than
 * the ADMIN-only admin rule - WRITERs can import, same as they can
 * create/edit other domain data. READERs still cannot (POST requires
 * WRITER/ADMIN).
 */
@RestController
@RequestMapping("/api/import")
public class ImportController {

    @Autowired
    private ExcelImportService excelImportService;

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
}
