package com.example.validation;

import java.util.Collections;
import java.util.List;

/**
 * Immutable outcome of a {@link PreSolveValidator} run.
 *
 * <p>
 * Two severities, matching this project's usual "blocking guardrail vs
 * advisory guardrail" split (see SemesterHourLimitController for the same
 * pattern on the web side):
 * <ul>
 * <li>{@code problems} - invalid pinned data (a pinned block the solver's
 * hard constraints can never fix, since they exclude pinned rows). An empty
 * list here means the pinned data is valid; {@link #isValid()} reflects only
 * this list, and {@code MainBlockSchedulingApp} aborts the solve when it's
 * non-empty.</li>
 * <li>{@code warnings} - structural facts about the *whole* schedule
 * (pinned and movable alike) that no amount of solving can fix, but that
 * don't represent invalid data - currently just a teacher whose total
 * assigned hours exceed their total availability. Printed, never blocking:
 * the solve still runs and does its best, producing double-bookings for
 * exactly the teachers named here.</li>
 * </ul>
 */
public final class ValidationResult {

    private final List<String> problems;
    private final List<String> warnings;

    public ValidationResult(List<String> problems) {
        this(problems, List.of());
    }

    public ValidationResult(List<String> problems, List<String> warnings) {
        this.problems = Collections.unmodifiableList(problems);
        this.warnings = Collections.unmodifiableList(warnings);
    }

    /** @return true if no blocking problems were found (warnings don't affect this). */
    public boolean isValid() {
        return problems.isEmpty();
    }

    /** @return the (unmodifiable) list of blocking problem descriptions. */
    public List<String> getProblems() {
        return problems;
    }

    /** @return the (unmodifiable) list of non-blocking warning descriptions. */
    public List<String> getWarnings() {
        return warnings;
    }

    /** @return a human-readable multi-line report covering both problems and warnings. */
    public String describe() {
        StringBuilder sb = new StringBuilder();
        if (isValid()) {
            sb.append("Pre-solve validation passed: pinned assignments are valid.");
        } else {
            sb.append("Pre-solve validation found ").append(problems.size())
                    .append(problems.size() == 1 ? " problem in pinned assignments:" : " problems in pinned assignments:");
            for (String p : problems) {
                sb.append(System.lineSeparator()).append("  - ").append(p);
            }
        }
        if (!warnings.isEmpty()) {
            sb.append(System.lineSeparator())
                    .append("Pre-solve capacity warning").append(warnings.size() == 1 ? ":" : "s (" + warnings.size() + "):");
            for (String w : warnings) {
                sb.append(System.lineSeparator()).append("  - ").append(w);
            }
        }
        return sb.toString();
    }
}
