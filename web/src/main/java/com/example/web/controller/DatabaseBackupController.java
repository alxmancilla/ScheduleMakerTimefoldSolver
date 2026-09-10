package com.example.web.controller;

import com.example.web.dto.BackupFileResponse;
import com.example.web.dto.DatabaseBackupStatusResponse;
import com.example.web.dto.DatabaseImportRequest;
import com.example.web.exception.ResourceNotFoundException;
import com.example.web.service.DatabaseBackupService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.File;
import java.util.List;

/**
 * Admin-only trigger for whole-database export/import (scripts/db-export.sh,
 * scripts/db-import.sh). Mounted under /api/admin/**, which SecurityConfig
 * already restricts to the ADMIN role. Mirrors EngineController's
 * run/status shape, plus AdminReportController's path-traversal-safe file
 * listing/download for the resulting .sql files.
 *
 * Import restores from a file already present under database/backups/ (this
 * feature is deliberately script-triggered rather than a browser upload -
 * see the design discussion this was built from) - use the export/download
 * endpoints first to get a file there, or copy one in directly (e.g. from
 * another machine) before importing it.
 */
@RestController
@RequestMapping("/api/admin/database")
public class DatabaseBackupController {

    @Autowired
    private DatabaseBackupService databaseBackupService;

    @PostMapping("/export")
    public DatabaseBackupStatusResponse export() {
        if (!databaseBackupService.tryStartExport()) {
            throw new IllegalArgumentException("A database export or import is already running");
        }
        return new DatabaseBackupStatusResponse(databaseBackupService.getSnapshot());
    }

    @PostMapping("/import")
    public DatabaseBackupStatusResponse importDatabase(@Valid @RequestBody DatabaseImportRequest request) {
        if (!databaseBackupService.tryStartImport(request.getFilename())) {
            throw new IllegalArgumentException("A database export or import is already running");
        }
        return new DatabaseBackupStatusResponse(databaseBackupService.getSnapshot());
    }

    @GetMapping("/status")
    public DatabaseBackupStatusResponse getStatus() {
        return new DatabaseBackupStatusResponse(databaseBackupService.getSnapshot());
    }

    @GetMapping("/backups")
    public List<BackupFileResponse> listBackups() {
        return databaseBackupService.listBackups().stream().map(BackupFileResponse::new).toList();
    }

    @GetMapping("/backups/{filename}")
    public ResponseEntity<FileSystemResource> downloadBackup(@PathVariable String filename) {
        File file = databaseBackupService.resolveBackupFile(filename);
        if (file == null) {
            throw new ResourceNotFoundException("Backup file", filename);
        }
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + file.getName() + "\"")
                .body(new FileSystemResource(file));
    }
}
