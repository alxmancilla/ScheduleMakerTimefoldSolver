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
 * Runs the reporter module's one-shot PDF generation (scripts/run-reporter.sh)
 * as a subprocess and tracks its state, so the admin-only "Generate PDFs"
 * button doesn't need a terminal. Deliberately a separate OS process (same
 * reasoning as EngineRunnerService): the reporter reads the already-solved
 * schedule straight from the database, so it doesn't need to run inside the
 * web app's own JVM.
 *
 * PDFs are written to <repo root>/reports, which ReportController serves to
 * any authenticated role.
 */
@Service
public class ReportRunnerService {

    public enum State {
        IDLE, RUNNING, COMPLETED, FAILED
    }

    private static final int MAX_LOG_LINES = 1000;
    private static final String REPORTS_SUBDIR = "reports";

    private final String workingDir;
    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "report-runner");
        t.setDaemon(true);
        return t;
    });

    private final Object lock = new Object();
    private volatile State state = State.IDLE;
    private volatile LocalDateTime startedAt;
    private volatile LocalDateTime finishedAt;
    private volatile Integer exitCode;
    private final Deque<String> logLines = new ArrayDeque<>();

    public ReportRunnerService(@Value("${app.engine.working-dir:}") String configuredWorkingDir) {
        // Shares the same repo-root config/detection as EngineRunnerService: both
        // scripts/*.sh live in the same directory, so one working-dir setting
        // (and the same auto-detection fallback) covers both.
        this.workingDir = RepoRootResolver.resolve(configuredWorkingDir);
    }

    public File getReportsDir() {
        return new File(workingDir, REPORTS_SUBDIR);
    }

    /**
     * Starts the reporter subprocess if one isn't already running.
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
            File reportsDir = getReportsDir();
            reportsDir.mkdirs();

            ProcessBuilder builder = new ProcessBuilder("bash", "scripts/run-reporter.sh");
            builder.directory(new File(workingDir));
            builder.environment().put("REPORTER_OUTPUT_DIR", reportsDir.getAbsolutePath());
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
        } catch (IOException e) {
            appendLog("ERROR: failed to start reporter process: " + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            appendLog("ERROR: reporter process wait was interrupted: " + e.getMessage());
        } finally {
            synchronized (lock) {
                exitCode = resultCode;
                finishedAt = LocalDateTime.now();
                state = (resultCode != null && resultCode == 0) ? State.COMPLETED : State.FAILED;
            }
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
