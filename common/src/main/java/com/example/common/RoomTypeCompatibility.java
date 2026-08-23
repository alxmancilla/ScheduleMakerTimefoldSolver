package com.example.common;

/**
 * The single, canonical implementation of "does this room type satisfy this
 * room-type requirement" - previously hand-maintained as two separate
 * copies (engine's {@code Room.capabilitiesFor()} and web's own
 * {@code RoomTypeCompatibility}), which had to be edited in lockstep on
 * every change to the rule (three times, across the "laboratorio" ->
 * "dual" -> "mixto" -> "Mixed" renames alone). Both {@code engine} and
 * {@code web} depend on this module and call this one method instead.
 *
 * <p>Convention: a room satisfies a requirement of its own type, and a
 * {@code Mixed} room additionally satisfies {@code Standard} and
 * {@code Specialized - Workshop} (it's equipped for both a regular class
 * and a workshop), but never the reverse - a plain {@code Standard} or
 * {@code Specialized - Workshop} room does not satisfy a {@code Mixed}
 * requirement. Notably, {@code Mixed} does NOT satisfy
 * {@code Specialized - Computer Lab} - that one stays a strictly separate,
 * non-interchangeable room type (deliberately not merged with
 * {@code Specialized - Workshop} despite the shared "Specialized" label
 * prefix; that prefix is a UI grouping convenience only).
 *
 * <p>A {@code null} requirement or a {@code null} room type is treated as
 * satisfied ("nothing to check against"): every real caller that has an
 * actual requirement to enforce (e.g. the engine's HARD
 * {@code roomTypeMustSatisfyRequirement} constraint) already guards on
 * {@code satisfiesRoomType != null} before calling this method, so the
 * permissive null behavior only matters to callers doing room defaulting
 * (web's {@code BlockGenerationService}/{@code CourseBlockAssignmentController}),
 * where "no requirement" should never block a default from being applied.
 */
public final class RoomTypeCompatibility {

    private RoomTypeCompatibility() {
    }

    public static boolean satisfies(String roomType, String requirement) {
        if (requirement == null || roomType == null) {
            return true;
        }
        return roomType.equals(requirement)
                || ("Mixed".equals(roomType)
                        && ("Standard".equals(requirement) || "Specialized - Workshop".equals(requirement)));
    }
}
