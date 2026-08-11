package com.example.domain;

import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Verifies the convention-seeded capability rules of {@link Room#satisfiesRequirement}.
 * A laboratorio room may also serve as a plain classroom (estándar), while every
 * other type satisfies only itself; a plain estándar room must NOT satisfy a
 * laboratorio requirement.
 */
public class RoomTest {

    @Test
    public void laboratorioRoomSatisfiesLaboratorioAndEstandar() {
        Room lab = new Room("LQ 1", "A", "laboratorio");
        assertTrue("lab must satisfy laboratorio", lab.satisfiesRequirement("laboratorio"));
        assertTrue("lab must double as a regular classroom", lab.satisfiesRequirement("estándar"));
    }

    @Test
    public void estandarRoomSatisfiesEstandarButNotLaboratorio() {
        Room standard = new Room("AULA 1", "A", "estándar");
        assertTrue("estándar room must satisfy estándar", standard.satisfiesRequirement("estándar"));
        assertFalse("estándar room must NOT satisfy laboratorio",
                standard.satisfiesRequirement("laboratorio"));
    }

    @Test
    public void tallerRoomsSatisfyOnlyThemselves() {
        Room taller = new Room("AULA 4", "A", "taller");
        assertTrue(taller.satisfiesRequirement("taller"));
        assertFalse(taller.satisfiesRequirement("estándar"));
        assertFalse(taller.satisfiesRequirement("laboratorio"));

        Room tem = new Room("TEM 1", "B", "taller electromecánica");
        assertTrue(tem.satisfiesRequirement("taller electromecánica"));
        assertFalse(tem.satisfiesRequirement("estándar"));
        assertFalse(tem.satisfiesRequirement("taller"));

        Room te = new Room("TE 1", "B", "taller electrónica");
        assertTrue(te.satisfiesRequirement("taller electrónica"));
        assertFalse(te.satisfiesRequirement("estándar"));
    }

    @Test
    public void centroDeComputoSatisfiesOnlyItself() {
        Room cc = new Room("CC 1", "C", "centro de cómputo");
        assertTrue(cc.satisfiesRequirement("centro de cómputo"));
        assertFalse(cc.satisfiesRequirement("estándar"));
        assertFalse(cc.satisfiesRequirement("laboratorio"));
    }

    @Test
    public void unknownOrNullRequirementIsNotSatisfied() {
        Room lab = new Room("LQ 1", "A", "laboratorio");
        assertFalse(lab.satisfiesRequirement("nonexistent"));
        assertFalse(lab.satisfiesRequirement(null));
    }
}
