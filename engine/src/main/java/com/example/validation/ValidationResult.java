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
 * <li>{@code problems} - anything {@code PreSolveValidator} can *prove*
 * will keep the solve from succeeding: invalid pinned data (a pinned block
 * the solver's hard constraints can never fix, since they exclude pinned
 * rows) and whole-schedule capacity facts (a teacher assigned more hours
 * than they have availability for - see {@code validateCapacity}, an exact
 * mathematical guarantee, not a heuristic). An empty list means nothing
 * proven-fatal was found; {@link #isValid()} reflects only this list, and
 * {@code MainBlockSchedulingApp} aborts the solve when it's non-empty.</li>
 * <li>{@code warnings} - reserved for a future check that's advisory rather
 * than provably fatal (e.g. a room-capacity heuristic with enough moving
 * parts - multiple types, multiple rooms, no single "the room's
 * availability" - that it wouldn't be as airtight as the teacher-capacity
 * check above). {@code PreSolveValidator} doesn't currently populate this;
 * printed, never blocking, when something eventually does.</li>
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
            sb.append("Pre-solve validation passed: no blocking problems found.");
        } else {
            sb.append("Pre-solve validation found ").append(problems.size())
                    .append(problems.size() == 1 ? " problem:" : " problems:");
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
