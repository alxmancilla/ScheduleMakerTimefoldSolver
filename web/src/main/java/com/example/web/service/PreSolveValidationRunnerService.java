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
import java.util.Deque;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Runs PreSolveValidator by itself - via the engine subprocess
 * (scripts/run-engine.sh) with VALIDATE_ONLY=true - and tracks its state, so
 * the "Run Validation" button on the web UI's Solver tab doesn't need a
 * terminal. Deliberately its own service rather than reusing
 * EngineRunnerService: the two represent genuinely different actions (check
 * only vs. actually solve) with their own status/log, so sharing one
 * RUNNING/COMPLETED/FAILED state would make the two actions' UI panels step
 * on each other. Mirrors EngineRunnerService's shape (state machine, log
 * capture, single-flight lock) deliberately, for the same reason
 * ReportRunnerService and DatabaseBackupService are their own classes rather
 * than folded into an existing one.
 *
 * <p>
 * The web module doesn't depend on the engine module (see CLAUDE.md - they
 * only share the database schema), so this can't call PreSolveValidator
 * in-process; running the same jar as a subprocess, like EngineRunnerService
 * already does for a real solve, is the only option. A validate-only run is
 * fast (seconds, not minutes) since it stops right after loading and
 * checking - no solving, no compliance-snapshot generation.
 * </p>
 */
@Service
public class PreSolveValidationRunnerService {

    public enum State {
        IDLE, RUNNING, COMPLETED, FAILED
    }

    private static final int MAX_LOG_LINES = 1000;

    private final String workingDir;
    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "presolve-validation-runner");
        t.setDaemon(true);
        return t;
    });

    private final Object lock = new Object();
    private volatile State state = State.IDLE;
    private volatile LocalDateTime startedAt;
    private volatile LocalDateTime finishedAt;
    private volatile Integer exitCode;
    private final Deque<String> logLines = new ArrayDeque<>();

    public PreSolveValidationRunnerService(@Value("${app.engine.working-dir:}") String configuredWorkingDir) {
        this.workingDir = RepoRootResolver.resolve(configuredWorkingDir);
    }

    /**
     * Starts a validate-only run if one isn't already running.
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
            builder.environment().put("VALIDATE_ONLY", "true");
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
            appendLog("ERROR: failed to start validation process: " + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            appendLog("ERROR: validation process wait was interrupted: " + e.getMessage());
        } finally {
            synchronized (lock) {
                exitCode = resultCode;
                finishedAt = LocalDateTime.now();
                // Exit code 1 means "ran fine, found blocking problems" (see
                // MainBlockSchedulingApp's VALIDATE_ONLY handling) - that's a
                // successful check, not a failed run, so only a genuinely
                // unexpected exit code (anything but 0 or 1) or a missing
                // exit code (process never completed) counts as FAILED.
                state = (resultCode != null && (resultCode == 0 || resultCode == 1))
                        ? State.COMPLETED
                        : State.FAILED;
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
            this.logLines = logLines;
        }
    }
}
