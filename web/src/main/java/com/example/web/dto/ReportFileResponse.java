package com.example.web.dto;

/** Metadata for one generated PDF within a report run, for the Reports tab. */
public class ReportFileResponse {

    private final String filename;
    private final long sizeBytes;

    public ReportFileResponse(String filename, long sizeBytes) {
        this.filename = filename;
        this.sizeBytes = sizeBytes;
    }

    public String getFilename() {
        return filename;
    }

    public long getSizeBytes() {
        return sizeBytes;
    }
}
