package com.example.domain;

import java.util.Collections;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

public class Room {
    private final String name;
    private final String building;
    private final String type; // 'estándar', 'laboratorio', 'taller', 'taller electromecánica',
                               // 'taller electrónica', 'centro de cómputo'

    // The set of room-type requirements this physical room can satisfy. Seeded by
    // convention from the primary type: a laboratorio can also serve as a plain
    // classroom (estándar), while every other type satisfies only itself. A plain
    // estándar room deliberately does NOT satisfy a laboratorio requirement.
    private final Set<String> capabilities;

    public Room(String name, String building, String type) {
        this.name = name;
        this.building = building;
        this.type = type;
        this.capabilities = capabilitiesFor(type);
    }

    private static Set<String> capabilitiesFor(String type) {
        Set<String> caps = new HashSet<>();
        if (type != null) {
            caps.add(type);
            if ("laboratorio".equals(type)) {
                // A lab is equipped with desks/board, so it can also host a regular class.
                caps.add("estándar");
            }
        }
        return Collections.unmodifiableSet(caps);
    }

    public String getName() {
        return name;
    }

    public String getBuilding() {
        return building;
    }

    public String getType() {
        return type;
    }

    public boolean satisfiesRequirement(String requirement) {
        return capabilities.contains(requirement);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        Room room = (Room) o;
        return Objects.equals(name, room.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name);
    }

    @Override
    public String toString() {
        return name;
    }
}
