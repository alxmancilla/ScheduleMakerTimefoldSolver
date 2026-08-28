package com.example.web.dto;

import com.example.web.service.DatabaseBackupService;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Current state of the admin-triggered database export/import subprocess:
 * which operation it was ("export" or "import"), whether it's running, when
 * it started/finished, its exit code, and a tail of its console output.
 */
public class DatabaseBackupStatusResponse {

    private final String state;
    private final String lastOperation;
    private final LocalDateTime startedAt;
    private final LocalDateTime finishedAt;
    private final Integer exitCode;
    private final List<String> log;

    public DatabaseBackupStatusResponse(DatabaseBackupService.Snapshot snapshot) {
        this.state = snapshot.state.name();
        this.lastOperation = snapshot.lastOperation != null ? snapshot.lastOperation.name() : null;
        this.startedAt = snapshot.startedAt;
        this.finishedAt = snapshot.finishedAt;
        this.exitCode = snapshot.exitCode;
        this.log = snapshot.logLines;
    }

    public String getState() {
        return state;
    }

    public String getLastOperation() {
        return lastOperation;
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
