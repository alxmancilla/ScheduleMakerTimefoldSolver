package com.example.web.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Runs the engine module's one-shot solver (scripts/run-engine.sh) as a
 * subprocess and tracks its state, so the admin-only "Start Engine" button in
 * the web UI doesn't need a terminal. Deliberately a separate OS process
 * (mirroring the docker-compose "engine" service) rather than an in-process
 * call: the solver is CPU/memory-hungry and this keeps it isolated from the
 * web app's own JVM.
 *
 * Local-dev oriented: assumes the process working directory is (or
 * app.engine.working-dir is set to) the repository root, and that
 * engine/target/scheduler-engine-*.jar has already been built (the script
 * fails fast with a clear message if it hasn't).
 *
 * Right after a successful solve, this service also generates an admin-only
 * compliance snapshot: the violations PDF (calendario-incumplimientos.pdf)
 * captured at that exact moment, written into its own timestamped subdirectory
 * of <repo root>/admin-reports (distinct from the reports/ directory the
 * WRITER-triggered "Generate PDFs" button uses, and readable only by ADMIN -
 * see AdminReportController). Capturing the PDF at solve time means the data
 * behind it doesn't need to be separately persisted for later regeneration:
 * the PDF itself is the durable record of that run's compliance state.
 */
@Service
public class EngineRunnerService {

    public enum State {
        IDLE, RUNNING, COMPLETED, FAILED
    }

    private static final int MAX_LOG_LINES = 1000;
    private static final String ADMIN_REPORTS_SUBDIR = "admin-reports";

    private final String workingDir;
    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "engine-runner");
        t.setDaemon(true);
        return t;
    });

    private final Object lock = new Object();
    private volatile State state = State.IDLE;
    private volatile LocalDateTime startedAt;
    private volatile LocalDateTime finishedAt;
    private volatile Integer exitCode;
    private final Deque<String> logLines = new ArrayDeque<>();

    public EngineRunnerService(@Value("${app.engine.working-dir:}") String configuredWorkingDir) {
        this.workingDir = RepoRootResolver.resolve(configuredWorkingDir);
    }

    /** Admin-only compliance-snapshot directory; see {@link com.example.web.controller.AdminReportController}. */
    public File getAdminReportsDir() {
        return new File(workingDir, ADMIN_REPORTS_SUBDIR);
    }

    /**
     * Starts the engine subprocess if one isn't already running.
     *
     * @return true if this call started a run, false if one was already in progress
     */
    public boolean tryStart() {
        synchronized (lock) {
            if (state == State.RUNNING) {
                return false;
            }
            state = State.RUNNING;
            startedAt = LocalDateTime.now();
            finishedAt = null;
            exitCode = null;
            logLines.clear();
        }
        executor.submit(this::runProcess);
        return true;
    }

    private void runProcess() {
        Integer resultCode = null;
        try {
            ProcessBuilder builder = new ProcessBuilder("bash", "scripts/run-engine.sh");
            builder.directory(new File(workingDir));
            builder.redirectErrorStream(true);
            Process process = builder.start();

            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    appendLog(line);
                }
            }
            resultCode = process.waitFor();
            if (resultCode == 0) {
                generateComplianceSnapshot();
            }
        } catch (IOException e) {
            appendLog("ERROR: failed to start engine process: " + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            appendLog("ERROR: engine process wait was interrupted: " + e.getMessage());
        } finally {
            synchronized (lock) {
                exitCode = resultCode;
                finishedAt = LocalDateTime.now();
                state = (resultCode != null && resultCode == 0) ? State.COMPLETED : State.FAILED;
            }
        }
    }

    /**
     * Generates the admin-only violations-PDF snapshot immediately after a
     * successful solve, into its own run directory under admin-reports/. Runs
     * scripts/run-reporter.sh with REPORT_TARGET=violations so only
     * calendario-incumplimientos.pdf is produced (the by-teacher/by-group
     * reports aren't needed for this snapshot and are the more expensive part of
     * a reporter run). Failures here are logged but deliberately don't flip the
     * engine run's own state to FAILED - the solve itself already succeeded and
     * was persisted; only the compliance snapshot is missing.
     */
    private void generateComplianceSnapshot() {
        String runId = ReportRunnerService.RUN_ID_FORMAT.format(LocalDateTime.now());
        File runDir = new File(getAdminReportsDir(), runId);
        appendLog("Generating admin-only compliance snapshot...");
        Integer reportExit = null;
        try {
            runDir.mkdirs();
            ProcessBuilder builder = new ProcessBuilder("bash", "scripts/run-reporter.sh");
            builder.directory(new File(workingDir));
            builder.environment().put("REPORTER_OUTPUT_DIR", runDir.getAbsolutePath());
            builder.environment().put("REPORT_TARGET", "violations");
            builder.redirectErrorStream(true);
            Process process = builder.start();

            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    appendLog("[compliance-report] " + line);
                }
            }
            reportExit = process.waitFor();
        } catch (IOException e) {
            appendLog("WARNING: failed to generate compliance snapshot: " + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            appendLog("WARNING: compliance snapshot generation was interrupted: " + e.getMessage());
        }
        if (reportExit != null && reportExit == 0) {
            appendLog("Compliance snapshot written to admin-reports/" + runId);
        } else if (reportExit != null) {
            appendLog("WARNING: compliance snapshot generation failed with exit code " + reportExit);
        }
    }

    private void appendLog(String line) {
        synchronized (lock) {
            logLines.addLast(line);
            while (logLines.size() > MAX_LOG_LINES) {
                logLines.removeFirst();
            }
        }
    }

    public Snapshot getSnapshot() {
        synchronized (lock) {
            return new Snapshot(state, startedAt, finishedAt, exitCode, List.copyOf(logLines));
        }
    }

    /** Immutable point-in-time view of the runner's state for the status endpoint. */
    public static final class Snapshot {
        public final State state;
        public final LocalDateTime startedAt;
        public final LocalDateTime finishedAt;
        public final Integer exitCode;
        public final List<String> logLines;

        public Snapshot(State state, LocalDateTime startedAt, LocalDateTime finishedAt, Integer exitCode,
                List<String> logLines) {
            this.state = state;
            this.startedAt = startedAt;
            this.finishedAt = finishedAt;
            this.exitCode = exitCode;
            this.logLines = Collections.unmodifiableList(logLines);
        }
    }
}
