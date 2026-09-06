package com.example.web.dto;

import java.util.List;

/**
 * Result of validating a candidate assignment move (see
 * AssignmentMoveValidationRequest). {@code violations} are constraints that
 * are CURRENTLY configured HARD (see AssignmentMoveValidationService) -
 * the UI should block Save while any are present, same posture as
 * PreSolveValidator's blocking problems. {@code warnings} are the same
 * checks' SOFT-configured counterpart (a constraint an admin has switched
 * to SOFT via Settings > Constraint Weights, or a course/semester's
 * SOFT-severity semester_hour_limit) - real facts worth showing, but not
 * something the solver would actually reject, so Save stays enabled.
 */
public class AssignmentMoveValidationResponse {

    private final List<String> violations;
    private final List<String> warnings;

    public AssignmentMoveValidationResponse(List<String> violations, List<String> warnings) {
        this.violations = violations;
        this.warnings = warnings;
    }

    public List<String> getViolations() {
        return violations;
    }

    public List<String> getWarnings() {
        return warnings;
    }
}
