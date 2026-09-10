package com.example.web.dto;

import com.example.web.service.DatabaseBackupService;

import java.time.LocalDateTime;

/** Metadata for one .sql file under database/backups/, for the Database Backups tab. */
public class BackupFileResponse {

    private final String filename;
    private final long sizeBytes;
    private final LocalDateTime modifiedAt;

    public BackupFileResponse(DatabaseBackupService.BackupFile file) {
        this.filename = file.filename();
        this.sizeBytes = file.sizeBytes();
        this.modifiedAt = file.modifiedAt();
    }

    public String getFilename() {
        return filename;
    }

    public long getSizeBytes() {
        return sizeBytes;
    }

    public LocalDateTime getModifiedAt() {
        return modifiedAt;
    }
}
