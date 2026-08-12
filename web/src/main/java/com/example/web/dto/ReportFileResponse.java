package com.example.web.dto;

import java.time.LocalDateTime;

/** Metadata for one generated PDF report, for the Reports tab's file list. */
public class ReportFileResponse {

    private final String filename;
    private final long sizeBytes;
    private final LocalDateTime lastModified;

    public ReportFileResponse(String filename, long sizeBytes, LocalDateTime lastModified) {
        this.filename = filename;
        this.sizeBytes = sizeBytes;
        this.lastModified = lastModified;
    }

    public String getFilename() {
        return filename;
    }

    public long getSizeBytes() {
        return sizeBytes;
    }

    public LocalDateTime getLastModified() {
        return lastModified;
    }
}
