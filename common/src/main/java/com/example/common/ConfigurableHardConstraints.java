package com.example.common;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The canonical list of HARD constraints whose severity is admin-configurable
 * to SOFT - a deliberately small subset of SchoolConstraintProvider's HARD
 * constraints, chosen because each is a genuine business/scheduling
 * preference with no value-range embedding and no physical-impossibility
 * risk if relaxed (unlike noTeacherDoubleBooking/noRoomDoubleBooking/
 * groupCannotHaveTwoCoursesAtSameTime, which encode outcomes that can't
 * actually be executed in reality, or blockLengthMustMatchTimeslotLength/
 * teacherRequiredRoomMustBeUsed, which are data-integrity checks with no
 * meaningful "soft" version).
 *
 * <p>Unlike SoftConstraintDefaults (constraints whose DEFAULT is already
 * soft, this map's value being the weight actually in effect until
 * overridden), these 4 constraints default to HARD in code
 * ({@code .penalize(HardSoftScore.ONE_HARD)}, unconditionally, in
 * SchoolConstraintProvider) - no engine code change was needed to make them
 * configurable, since a {@code constraint_config} row for any constraint
 * name (not just ones already known to be soft) transparently replaces
 * whatever HardSoftScore literal that constraint passes to {@code .penalize(...)}
 * - confirmed empirically that overriding a HARD constraint's weight to
 * {@code HardSoftScore.ofSoft(N)} removes the hard violation entirely and
 * applies the soft one instead, via Timefold's own ConstraintWeightOverrides
 * mechanism, the same one SoftConstraintDefaults-listed constraints use.
 *
 * <p>This map's value is only a SUGGESTED starting weight to pre-fill the UI
 * with when an admin first switches one of these to SOFT - there's no
 * historical "current" weight the way a real soft constraint has, since it
 * has never been soft before. The admin can retune it immediately via the
 * same constraint_config row.
 *
 * <p>A constraint in this list with NO constraint_config row stays HARD -
 * the code's own {@code .penalize(HardSoftScore.ONE_HARD)} default applies,
 * completely unaffected by this map's existence.
 */
public final class ConfigurableHardConstraints {

    private ConfigurableHardConstraints() {
    }

    public static final Map<String, Integer> SUGGESTED_SOFT_WEIGHT;

    static {
        Map<String, Integer> suggested = new LinkedHashMap<>();
        suggested.put("Teacher must be qualified", 8);
        suggested.put("Teacher must be available for entire block", 8);
        suggested.put("Maximum blocks per course per group per day", 5);
        suggested.put("Course blocks must be consecutive", 5);
        // unmodifiableMap (not Map.copyOf) so insertion order survives - see
        // SoftConstraintDefaults for why Map.copyOf doesn't guarantee it.
        SUGGESTED_SOFT_WEIGHT = Collections.unmodifiableMap(suggested);
    }

    /** True when this constraint's severity is admin-configurable (it's in this list). */
    public static boolean isConfigurable(String constraintName) {
        return SUGGESTED_SOFT_WEIGHT.containsKey(constraintName);
    }
}
