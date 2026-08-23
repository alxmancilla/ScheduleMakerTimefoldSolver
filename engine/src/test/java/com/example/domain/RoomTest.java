package com.example.domain;

import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Confirms Room.satisfiesRequirement() correctly delegates to the shared
 * {@link com.example.common.RoomTypeCompatibility#satisfies} rule - the
 * exhaustive matrix of room-type cases (mixto/estándar/taller/centro de
 * cómputo, null handling, ...) is tested once, in
 * common/RoomTypeCompatibilityTest, rather than duplicated here.
 */
public class RoomTest {

    @Test
    public void delegatesToSharedRoomTypeCompatibilityRule() {
        Room mixto = new Room("LQ 1", "A", "mixto");
        assertTrue("mixto must double as a regular classroom", mixto.satisfiesRequirement("estándar"));

        Room standard = new Room("AULA 1", "A", "estándar");
        assertFalse("estándar must NOT satisfy mixto", standard.satisfiesRequirement("mixto"));
    }
}
