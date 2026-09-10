package com.example.web.dto;

import com.example.web.service.PreSolveValidationRunnerService;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Current state of the validate-only engine subprocess: whether it's
 * running, when it started/finished, its exit code (0 = no blocking
 * problems, 1 = found some, anything else = the run itself failed), and its
 * console output (PreSolveValidator's full report).
 */
public class PreSolveValidationStatusResponse {

    private final String state;
    private final LocalDateTime startedAt;
    private final LocalDateTime finishedAt;
    private final Integer exitCode;
    private final List<String> log;

    public PreSolveValidationStatusResponse(PreSolveValidationRunnerService.Snapshot snapshot) {
        this.state = snapshot.state.name();
        this.startedAt = snapshot.startedAt;
        this.finishedAt = snapshot.finishedAt;
        this.exitCode = snapshot.exitCode;
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

    public List<String> getLog() {
        return log;
    }
}
