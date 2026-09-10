package com.example.web.dto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * One past "Generate PDFs" run: its timestamped run id, when it was
 * generated, and the PDF files it produced. The Reports tab lists these
 * newest-first so past runs stay browsable for comparison instead of being
 * overwritten by the next generation.
 */
public class ReportRunResponse {

    private final String runId;
    private final LocalDateTime generatedAt;
    private final List<ReportFileResponse> files;

    public ReportRunResponse(String runId, LocalDateTime generatedAt, List<ReportFileResponse> files) {
        this.runId = runId;
        this.generatedAt = generatedAt;
        this.files = files;
    }

    public String getRunId() {
        return runId;
    }

    public LocalDateTime getGeneratedAt() {
        return generatedAt;
    }

    public List<ReportFileResponse> getFiles() {
        return files;
    }
}
