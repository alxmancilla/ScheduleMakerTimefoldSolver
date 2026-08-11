package com.example.validation;

import java.util.Collections;
import java.util.List;

/**
 * Immutable outcome of a {@link PreSolveValidator} run: the list of problems
 * found in the pinned inputs. An empty list means the pinned data is valid.
 */
public final class ValidationResult {

    private final List<String> problems;

    public ValidationResult(List<String> problems) {
        this.problems = Collections.unmodifiableList(problems);
    }

    /** @return true if no problems were found. */
    public boolean isValid() {
        return problems.isEmpty();
    }

    /** @return the (unmodifiable) list of problem descriptions. */
    public List<String> getProblems() {
        return problems;
    }

    /** @return a human-readable multi-line report. */
    public String describe() {
        if (isValid()) {
            return "Pre-solve validation passed: pinned assignments are valid.";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("Pre-solve validation found ").append(problems.size())
                .append(problems.size() == 1 ? " problem in pinned assignments:" : " problems in pinned assignments:");
        for (String p : problems) {
            sb.append(System.lineSeparator()).append("  - ").append(p);
        }
        return sb.toString();
    }
}
