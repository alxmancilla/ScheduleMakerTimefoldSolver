package com.example.domain;

import com.example.common.RoomTypeCompatibility;

import java.util.Objects;

public class Room {
    private final String name;
    private final String building;
    private final String type; // 'Standard', 'Mixed', 'Specialized - Workshop', 'Specialized - Computer Lab'

    // Optional seating capacity. Null when unknown/not tracked, in which case
    // the room-capacity soft constraint is skipped for this room.
    private final Integer capacity;

    public Room(String name, String building, String type) {
        this(name, building, type, null);
    }

    public Room(String name, String building, String type, Integer capacity) {
        this.name = name;
        this.building = building;
        this.type = type;
        this.capacity = capacity;
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

    public Integer getCapacity() {
        return capacity;
    }

    /**
     * Whether this room satisfies a room-type requirement, per the shared
     * convention-seeded rule in {@link RoomTypeCompatibility} (a Mixed room
     * also satisfies Standard and Specialized - Workshop; every other type
     * satisfies only itself). Delegates rather than reimplementing so engine
     * and web can never drift on this rule again.
     */
    public boolean satisfiesRequirement(String requirement) {
        return RoomTypeCompatibility.satisfies(type, requirement);
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
