package com.example.web.controller;

import com.example.web.dto.ReportFileResponse;
import com.example.web.dto.ReportStatusResponse;
import com.example.web.exception.ResourceNotFoundException;
import com.example.web.service.ReportRunnerService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

/**
 * Read-only access to generated PDF reports for any authenticated role
 * (READER/WRITER/ADMIN) - the Reports tab. Generating new reports is
 * admin-only; see ReportGenerationController at /api/admin/reports/generate.
 */
@RestController
@RequestMapping("/api/reports")
public class ReportController {

    @Autowired
    private ReportRunnerService reportRunnerService;

    @GetMapping
    public List<ReportFileResponse> listReports() {
        File dir = reportRunnerService.getReportsDir();
        File[] files = dir.isDirectory() ? dir.listFiles((d, name) -> name.toLowerCase().endsWith(".pdf")) : null;
        if (files == null) {
            return List.of();
        }
        return Arrays.stream(files)
                .sorted(Comparator.comparing(File::getName))
                .map(f -> new ReportFileResponse(f.getName(), f.length(), lastModified(f)))
                .toList();
    }

    @GetMapping("/status")
    public ReportStatusResponse getStatus() {
        return new ReportStatusResponse(reportRunnerService.getSnapshot());
    }

    @GetMapping("/{filename}")
    public ResponseEntity<FileSystemResource> downloadReport(@PathVariable String filename) {
        // Only allow a bare filename (no path separators/traversal), and only one
        // that actually exists as a direct child of the reports directory.
        if (filename.contains("/") || filename.contains("\\") || filename.contains("..")) {
            throw new ResourceNotFoundException("Report", filename);
        }
        File file = new File(reportRunnerService.getReportsDir(), filename);
        if (!file.isFile()) {
            throw new ResourceNotFoundException("Report", filename);
        }
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + file.getName() + "\"")
                .body(new FileSystemResource(file));
    }

    private LocalDateTime lastModified(File file) {
        try {
            return LocalDateTime.ofInstant(Files.getLastModifiedTime(file.toPath()).toInstant(),
                    ZoneId.systemDefault());
        } catch (IOException e) {
            return null;
        }
    }
}
