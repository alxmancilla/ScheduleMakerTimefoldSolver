package com.example.web.controller;

import com.example.web.dto.ReportFileResponse;
import com.example.web.dto.ReportRunResponse;
import com.example.web.exception.ResourceNotFoundException;
import com.example.web.service.EngineRunnerService;
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
import java.time.format.DateTimeParseException;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

/**
 * Admin-only access to the compliance-snapshot PDFs (just
 * calendario-incumplimientos.pdf) that EngineRunnerService generates
 * automatically right after each successful engine run. Mounted under
 * /api/admin/**, which SecurityConfig already restricts to the ADMIN role -
 * unlike /api/reports (the WRITER-triggered "Generate PDFs" runs), which any
 * authenticated role can read. Mirrors ReportController's run/file listing
 * shape and path-traversal-safe download handling.
 */
@RestController
@RequestMapping("/api/admin/reports")
public class AdminReportController {

    @Autowired
    private EngineRunnerService engineRunnerService;

    @GetMapping
    public List<ReportRunResponse> listRuns() {
        File reportsDir = engineRunnerService.getAdminReportsDir();
        File[] runDirs = reportsDir.isDirectory() ? reportsDir.listFiles(File::isDirectory) : null;
        if (runDirs == null) {
            return List.of();
        }
        return Arrays.stream(runDirs)
                .sorted(Comparator.comparing(File::getName).reversed())
                .map(this::toRunResponse)
                .toList();
    }

    @GetMapping("/{runId}/{filename}")
    public ResponseEntity<FileSystemResource> downloadReport(@PathVariable String runId, @PathVariable String filename) {
        File file = resolveReportFile(runId, filename);
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + file.getName() + "\"")
                .body(new FileSystemResource(file));
    }

    private ReportRunResponse toRunResponse(File runDir) {
        File[] pdfFiles = runDir.listFiles((d, name) -> name.toLowerCase().endsWith(".pdf"));
        List<ReportFileResponse> files = pdfFiles == null ? List.of()
                : Arrays.stream(pdfFiles)
                        .sorted(Comparator.comparing(File::getName))
                        .map(f -> new ReportFileResponse(f.getName(), f.length()))
                        .toList();
        return new ReportRunResponse(runDir.getName(), parseRunTimestamp(runDir), files);
    }

    /** Prefers parsing the timestamp out of the run id itself; falls back to the directory's mtime. */
    private LocalDateTime parseRunTimestamp(File runDir) {
        try {
            return LocalDateTime.parse(runDir.getName(), ReportRunnerService.RUN_ID_FORMAT);
        } catch (DateTimeParseException e) {
            try {
                return LocalDateTime.ofInstant(Files.getLastModifiedTime(runDir.toPath()).toInstant(),
                        ZoneId.systemDefault());
            } catch (IOException ioException) {
                return null;
            }
        }
    }

    /** Only allows a bare runId/filename (no path separators/traversal) that actually exists on disk. */
    private File resolveReportFile(String runId, String filename) {
        if (isUnsafeSegment(runId) || isUnsafeSegment(filename)) {
            throw new ResourceNotFoundException("Report", runId + "/" + filename);
        }
        File file = new File(new File(engineRunnerService.getAdminReportsDir(), runId), filename);
        if (!file.isFile()) {
            throw new ResourceNotFoundException("Report", runId + "/" + filename);
        }
        return file;
    }

    private boolean isUnsafeSegment(String segment) {
        return segment.contains("/") || segment.contains("\\") || segment.contains("..");
    }
}
