package com.example.web.dto;

import com.example.web.service.ReportRunnerService;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Current state of the admin-triggered PDF report generation subprocess:
 * whether it's running, when it started/finished, its exit code, and a tail
 * of its console output.
 */
public class ReportStatusResponse {

    private final String state;
    private final LocalDateTime startedAt;
    private final LocalDateTime finishedAt;
    private final Integer exitCode;
    private final String runId;
    private final List<String> log;

    public ReportStatusResponse(ReportRunnerService.Snapshot snapshot) {
        this.state = snapshot.state.name();
        this.startedAt = snapshot.startedAt;
        this.finishedAt = snapshot.finishedAt;
        this.exitCode = snapshot.exitCode;
        this.runId = snapshot.runId;
        this.log = snapshot.logLines;
    }

    public String getState() {
        return state;
    }

    public LocalDateTime getStartedAt() {
        return startedAt;
    }

    public LocalDateTime getFinishedAt() {
        return finishedAt;
    }

    public Integer getExitCode() {
        return exitCode;
    }

    public String getRunId() {
        return runId;
    }

    public List<String> getLog() {
        return log;
    }
}
