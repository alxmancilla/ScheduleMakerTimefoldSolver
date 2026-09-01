package com.example.common;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The single, canonical list of SchoolConstraintProvider's soft constraints
 * and their default weights - previously a hardcoded {@code HardSoftScore.ofSoft(N)}
 * literal inside each constraint method, with no way to change one without
 * a code change and redeploy (evidenced by this project's own history of
 * manually retuning them: preferSemesterOneBlocksStartEarly raised from
 * weight 4 to 6, minimizeSemesterOneGroupIdleGaps likewise, several
 * constraints toggled on/off by commenting out code).
 *
 * <p>Both {@code engine} (SchoolConstraintProvider passes these as the
 * DEFAULT weight to {@code .penalize(...)}, transparently overridden per-solve
 * by a {@code ConstraintWeightOverrides} built from the {@code constraint_config}
 * table when a row exists for that name - see SchoolSchedule.getConstraintWeightOverrides())
 * and {@code web} (ConstraintWeightConfigController surfaces this same list,
 * with each entry's current override if any, so Settings can show every
 * known constraint even before it's ever been overridden) depend on this
 * module and read this one list instead of hand-maintaining two copies.
 *
 * <p>Only currently-ACTIVE soft constraints are listed (the ones wired into
 * SchoolConstraintProvider.defineConstraints()'s returned array) - a
 * TEMP-DISABLED constraint has no live weight to override yet.
 *
 * <p>Order matches SchoolConstraintProvider.defineConstraints()'s own
 * highest-weight-first ordering, preserved via LinkedHashMap so the UI list
 * doesn't need to re-sort.
 */
public final class SoftConstraintDefaults {

    private SoftConstraintDefaults() {
    }

    public static final Map<String, Integer> DEFAULTS;

    static {
        Map<String, Integer> defaults = new LinkedHashMap<>();
        defaults.put("Non-standard rooms should finish by 2pm", 10);
        defaults.put("Prefer first-semester blocks to start early", 6);
        defaults.put("Minimize first-semester group idle gaps", 6);
        defaults.put("Teacher exceeds max hours per week", 5);
        defaults.put("Room capacity should fit group size", 4);
        defaults.put("Prefer block's specified room", 3);
        defaults.put("Minimize teacher idle gaps (availability-aware)", 2);
        defaults.put("Prefer group's preferred room", 2);
        // unmodifiableMap (not Map.copyOf) so the LinkedHashMap's insertion
        // order - matching defineConstraints()'s highest-weight-first
        // ordering - survives, which Map.copyOf doesn't guarantee.
        DEFAULTS = Collections.unmodifiableMap(defaults);
    }

    /** This constraint's default weight, or null if it's not a known soft constraint. */
    public static Integer getDefault(String constraintName) {
        return DEFAULTS.get(constraintName);
    }
}
