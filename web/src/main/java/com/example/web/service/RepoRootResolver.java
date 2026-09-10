package com.example.web.service;

import java.io.File;

/**
 * Locates the repository root that scripts/run-engine.sh and
 * scripts/run-reporter.sh are launched from (shared by EngineRunnerService
 * and ReportRunnerService).
 */
final class RepoRootResolver {

    private RepoRootResolver() {
    }

    /**
     * If a working directory is explicitly configured, trust it. Otherwise,
     * search upward from the JVM's own working directory for a scripts/
     * directory: `mvn -pl web spring-boot:run` sets the process's cwd to the
     * web module's own directory (not the multi-module repo root), so a plain
     * "." default would silently fail to find either script depending on how
     * the app was launched.
     */
    static String resolve(String configuredWorkingDir) {
        if (configuredWorkingDir != null && !configuredWorkingDir.isBlank()) {
            return configuredWorkingDir;
        }
        File dir = new File(System.getProperty("user.dir", "."));
        for (int i = 0; i < 4 && dir != null; i++) {
            if (new File(dir, "scripts/run-engine.sh").isFile()) {
                return dir.getPath();
            }
            dir = dir.getParentFile();
        }
        // Fall back to the JVM's own working directory; the process will fail
        // fast with a clear "No such file or directory" message if this is wrong.
        return System.getProperty("user.dir", ".");
    }
}
