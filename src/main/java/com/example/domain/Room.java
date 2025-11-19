package com.example.domain;

import java.util.Objects;

public class Room {
    private final String name;
    private final String building;
    private final String type; // 'standard', 'lab'

    public Room(String name, String building, String type) {
        this.name = name;
        this.building = building;
        this.type = type;
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
        if ("standard".equals(requirement)) {
            return "standard".equals(type);
        } else if ("lab".equals(requirement)) {
            return "lab".equals(type);
        }
        return false;
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
