package com.example.domain;

import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Confirms Room.satisfiesRequirement() correctly delegates to the shared
 * {@link com.example.common.RoomTypeCompatibility#satisfies} rule - the
 * exhaustive matrix of room-type cases (Mixed/Standard/Specialized -
 * Workshop/Specialized - Computer Lab, null handling, ...) is tested once,
 * in common/RoomTypeCompatibilityTest, rather than duplicated here.
 */
public class RoomTest {

    @Test
    public void delegatesToSharedRoomTypeCompatibilityRule() {
        Room mixed = new Room("LQ 1", "A", "Mixed");
        assertTrue("Mixed must double as a regular classroom", mixed.satisfiesRequirement("Standard"));

        Room standard = new Room("AULA 1", "A", "Standard");
        assertFalse("Standard must NOT satisfy Mixed", standard.satisfiesRequirement("Mixed"));
    }
}
