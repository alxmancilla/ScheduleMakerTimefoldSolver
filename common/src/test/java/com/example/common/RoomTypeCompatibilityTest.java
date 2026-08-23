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
    public void mixtoSatisfiesMixtoEstandarAndTaller() {
        assertTrue("mixto must satisfy mixto", RoomTypeCompatibility.satisfies("mixto", "mixto"));
        assertTrue("mixto must double as a regular classroom", RoomTypeCompatibility.satisfies("mixto", "estándar"));
        assertTrue("mixto must double as a workshop", RoomTypeCompatibility.satisfies("mixto", "taller"));
    }

    @Test
    public void estandarSatisfiesEstandarButNotMixtoOrTaller() {
        assertTrue(RoomTypeCompatibility.satisfies("estándar", "estándar"));
        assertFalse("estándar must NOT satisfy mixto", RoomTypeCompatibility.satisfies("estándar", "mixto"));
        assertFalse("estándar must NOT satisfy taller", RoomTypeCompatibility.satisfies("estándar", "taller"));
    }

    @Test
    public void tallerSatisfiesTallerButNotMixtoOrEstandar() {
        assertTrue(RoomTypeCompatibility.satisfies("taller", "taller"));
        assertFalse("taller must NOT satisfy mixto", RoomTypeCompatibility.satisfies("taller", "mixto"));
        assertFalse("taller must NOT satisfy estándar", RoomTypeCompatibility.satisfies("taller", "estándar"));
    }

    @Test
    public void centroDeComputoSatisfiesOnlyItself() {
        assertTrue(RoomTypeCompatibility.satisfies("centro de cómputo", "centro de cómputo"));
        assertFalse(RoomTypeCompatibility.satisfies("centro de cómputo", "estándar"));
        assertFalse(RoomTypeCompatibility.satisfies("centro de cómputo", "mixto"));
    }

    @Test
    public void unknownRoomTypeSatisfiesOnlyItself() {
        assertFalse(RoomTypeCompatibility.satisfies("estándar", "nonexistent"));
    }

    @Test
    public void nullRequirementOrRoomTypeIsTreatedAsSatisfied() {
        // "Nothing to check against" - see the class Javadoc for why this is safe:
        // every real hard-constraint caller already guards on a non-null requirement
        // before calling in, so this permissive default only ever reaches the
        // room-defaulting callers, where it's the behavior they rely on.
        assertTrue(RoomTypeCompatibility.satisfies("estándar", null));
        assertTrue(RoomTypeCompatibility.satisfies(null, "estándar"));
        assertTrue(RoomTypeCompatibility.satisfies(null, null));
    }
}
