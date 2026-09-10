package com.example.web.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Runs the whole-database export/import scripts (scripts/db-export.sh,
 * scripts/db-import.sh) as a subprocess and tracks their state, mirroring
 * {@link EngineRunnerService}'s exact pattern (own state machine, own log
 * buffer - this codebase's established convention is one self-contained
 * runner per script rather than a shared base class).
 *
 * db-import.sh always takes its own fresh "pre_restore_*" safety backup
 * before touching anything, so a bad restore is itself recoverable - that
 * happens inside the script, not here.
 *
 * Local-dev oriented, same as EngineRunnerService: assumes the process
 * working directory is (or app.engine.working-dir is set to) the repository
 * root.
 */
@Service
public class DatabaseBackupService {

    public enum State {
        IDLE, RUNNING, COMPLETED, FAILED
    }

    public enum Operation {
        EXPORT, IMPORT
    }

    private static final int MAX_LOG_LINES = 1000;
    private static final String BACKUP_SUBDIR = "database/backups";

    private final String workingDir;
    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "database-backup-runner");
        t.setDaemon(true);
        return t;
    });

    private final Object lock = new Object();
    private volatile State state = State.IDLE;
    private volatile Operation lastOperation;
    private volatile LocalDateTime startedAt;
    private volatile LocalDateTime finishedAt;
    private volatile Integer exitCode;
    private final Deque<String> logLines = new ArrayDeque<>();

    public DatabaseBackupService(@Value("${app.engine.working-dir:}") String configuredWorkingDir) {
        this.workingDir = RepoRootResolver.resolve(configuredWorkingDir);
    }

    public File getBackupDir() {
        return new File(workingDir, BACKUP_SUBDIR);
    }

    /** @return true if this call started the export, false if a run was already in progress */
    public boolean tryStartExport() {
        if (!claimRun(Operation.EXPORT)) {
            return false;
        }
        executor.submit(() -> runProcess(List.of("bash", "scripts/db-export.sh")));
        return true;
    }

    /**
     * @param filename an existing file under database/backups/ (validated - no path
     *                 separators or ".." segments, must already exist on disk)
     * @return true if this call started the import, false if a run was already in progress
     */
    public boolean tryStartImport(String filename) {
        if (isUnsafeSegment(filename)) {
            throw new IllegalArgumentException("Invalid backup filename: " + filename);
        }
        File target = new File(getBackupDir(), filename);
        if (!target.isFile()) {
            throw new IllegalArgumentException("Backup file not found: " + filename);
        }
        if (!claimRun(Operation.IMPORT)) {
            return false;
        }
        executor.submit(() -> runProcess(List.of("bash", "scripts/db-import.sh", filename)));
        return true;
    }

    private boolean claimRun(Operation operation) {
        synchronized (lock) {
            if (state == State.RUNNING) {
                return false;
            }
            state = State.RUNNING;
            lastOperation = operation;
            startedAt = LocalDateTime.now();
            finishedAt = null;
            exitCode = null;
            logLines.clear();
        }
        return true;
    }

    private void runProcess(List<String> command) {
        Integer resultCode = null;
        try {
            ProcessBuilder builder = new ProcessBuilder(command);
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
        } catch (IOException e) {
            appendLog("ERROR: failed to start process: " + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            appendLog("ERROR: process wait was interrupted: " + e.getMessage());
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
            return new Snapshot(state, lastOperation, startedAt, finishedAt, exitCode, List.copyOf(logLines));
        }
    }

    /** Every *.sql file under database/backups/, newest first. */
    public List<BackupFile> listBackups() {
        File dir = getBackupDir();
        File[] files = dir.isDirectory() ? dir.listFiles((d, name) -> name.toLowerCase().endsWith(".sql")) : null;
        if (files == null) {
            return List.of();
        }
        return Arrays.stream(files)
                .map(f -> new BackupFile(f.getName(), f.length(), lastModified(f)))
                .sorted(Comparator.comparing(BackupFile::filename).reversed())
                .toList();
    }

    /** Only allows a bare filename (no path separators/traversal) that actually exists on disk under database/backups/. */
    public File resolveBackupFile(String filename) {
        if (isUnsafeSegment(filename)) {
            return null;
        }
        File file = new File(getBackupDir(), filename);
        return file.isFile() ? file : null;
    }

    private boolean isUnsafeSegment(String segment) {
        return segment == null || segment.isBlank()
                || segment.contains("/") || segment.contains("\\") || segment.contains("..");
    }

    private LocalDateTime lastModified(File f) {
        try {
            return LocalDateTime.ofInstant(Files.getLastModifiedTime(f.toPath()).toInstant(), ZoneId.systemDefault());
        } catch (IOException e) {
            return null;
        }
    }

    public record BackupFile(String filename, long sizeBytes, LocalDateTime modifiedAt) {
    }

    /** Immutable point-in-time view of the runner's state for the status endpoint. */
    public static final class Snapshot {
        public final State state;
        public final Operation lastOperation;
        public final LocalDateTime startedAt;
        public final LocalDateTime finishedAt;
        public final Integer exitCode;
        public final List<String> logLines;

        public Snapshot(State state, Operation lastOperation, LocalDateTime startedAt, LocalDateTime finishedAt,
                Integer exitCode, List<String> logLines) {
            this.state = state;
            this.lastOperation = lastOperation;
            this.startedAt = startedAt;
            this.finishedAt = finishedAt;
            this.exitCode = exitCode;
            this.logLines = Collections.unmodifiableList(logLines);
        }
    }
}
