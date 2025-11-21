package com.example.domain;

import java.util.Objects;
import java.util.UUID;

public class Course {
    private final String id;
    private final String name;
    private final String roomRequirement; // 'standard', 'science_lab'
    private final int requiredHoursPerWeek;

    public Course(String id, String name, String roomRequirement, int requiredHoursPerWeek) {
        this.id = id;
        this.name = name;
        this.roomRequirement = roomRequirement;
        this.requiredHoursPerWeek = requiredHoursPerWeek;
    }

    // Backwards-compatible constructor: generate an id from the name.
    public Course(String name, String roomRequirement, int requiredHoursPerWeek) {
        this(sanitizeId("c", name), name, roomRequirement, requiredHoursPerWeek);
    }

    private static String sanitizeId(String prefix, String name) {
        if (name == null)
            return prefix + "_" + UUID.randomUUID().toString();
        String s = name.replaceAll("[^A-Za-z0-9]", "_").toLowerCase();
        return prefix + "_" + s;
    }

    public String getName() {
        return name;
    }

    public String getId() {
        return id;
    }

    public String getRoomRequirement() {
        return roomRequirement;
    }

    public int getRequiredHoursPerWeek() {
        return requiredHoursPerWeek;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        Course course = (Course) o;
        return Objects.equals(id, course.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return name;
    }
}
