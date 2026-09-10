package com.example.common;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * The canonical test for the room-type compatibility rule. Both engine's
 * {@code Room.satisfiesRequirement()} and web's room-defaulting/override
 * logic delegate to {@link RoomTypeCompatibility#satisfies}, so this one
 * test class is the single source of truth for the rule's behavior -
 * previously this same set of cases was duplicated across engine's
 * RoomTest and indirectly re-verified across several web test classes.
 */
public class RoomTypeCompatibilityTest {

    @Test
    public void mixedSatisfiesMixedStandardAndSpecializedWorkshop() {
        assertTrue("Mixed must satisfy Mixed", RoomTypeCompatibility.satisfies("Mixed", "Mixed"));
        assertTrue("Mixed must double as a regular classroom", RoomTypeCompatibility.satisfies("Mixed", "Standard"));
        assertTrue("Mixed must double as a workshop",
                RoomTypeCompatibility.satisfies("Mixed", "Specialized - Workshop"));
    }

    @Test
    public void mixedDoesNotSatisfySpecializedComputerLab() {
        assertFalse("Mixed must NOT satisfy Specialized - Computer Lab - that stays strictly separate",
                RoomTypeCompatibility.satisfies("Mixed", "Specialized - Computer Lab"));
    }

    @Test
    public void standardSatisfiesStandardButNotMixedOrSpecializedWorkshop() {
        assertTrue(RoomTypeCompatibility.satisfies("Standard", "Standard"));
        assertFalse("Standard must NOT satisfy Mixed", RoomTypeCompatibility.satisfies("Standard", "Mixed"));
        assertFalse("Standard must NOT satisfy Specialized - Workshop",
                RoomTypeCompatibility.satisfies("Standard", "Specialized - Workshop"));
    }

    @Test
    public void specializedWorkshopSatisfiesOnlyItself() {
        assertTrue(RoomTypeCompatibility.satisfies("Specialized - Workshop", "Specialized - Workshop"));
        assertFalse("Specialized - Workshop must NOT satisfy Mixed",
                RoomTypeCompatibility.satisfies("Specialized - Workshop", "Mixed"));
        assertFalse("Specialized - Workshop must NOT satisfy Standard",
                RoomTypeCompatibility.satisfies("Specialized - Workshop", "Standard"));
    }

    @Test
    public void specializedComputerLabSatisfiesOnlyItself() {
        assertTrue(RoomTypeCompatibility.satisfies("Specialized - Computer Lab", "Specialized - Computer Lab"));
        assertFalse(RoomTypeCompatibility.satisfies("Specialized - Computer Lab", "Standard"));
        assertFalse(RoomTypeCompatibility.satisfies("Specialized - Computer Lab", "Mixed"));
        assertFalse("Specialized - Computer Lab and Specialized - Workshop stay non-interchangeable "
                + "despite sharing a label prefix",
                RoomTypeCompatibility.satisfies("Specialized - Computer Lab", "Specialized - Workshop"));
    }

    @Test
    public void unknownRoomTypeSatisfiesOnlyItself() {
        assertFalse(RoomTypeCompatibility.satisfies("Standard", "nonexistent"));
    }

    @Test
    public void nullRequirementOrRoomTypeIsTreatedAsSatisfied() {
        // "Nothing to check against" - see the class Javadoc for why this is safe:
        // every real hard-constraint caller already guards on a non-null requirement
        // before calling in, so this permissive default only ever reaches the
        // room-defaulting callers, where it's the behavior they rely on.
        assertTrue(RoomTypeCompatibility.satisfies("Standard", null));
        assertTrue(RoomTypeCompatibility.satisfies(null, "Standard"));
        assertTrue(RoomTypeCompatibility.satisfies(null, null));
    }
}
