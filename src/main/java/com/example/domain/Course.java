package com.example.domain;

import java.util.Objects;
import java.util.UUID;

public class Course {
    private final String id;
    private final String name;
    private final String abbreviation;
    private final String semester; // 'I', 'II', 'III', 'IV', 'V', 'VI'
    private final String component; // 'BASICAS', 'TADHR','TEM'
    private final int requiredHoursPerWeek;
    private final String roomRequirement; // 'standard', 'science_lab'
    private final Boolean active;

    public Course(String id, String name, String abbreviation, String semester, String component,
            String roomRequirement, int requiredHoursPerWeek, Boolean active) {
        this.id = id;
        this.name = name;
        this.abbreviation = abbreviation;
        this.semester = semester;
        this.component = component;
        this.roomRequirement = roomRequirement;
        this.requiredHoursPerWeek = requiredHoursPerWeek;
        this.active = active;
    }

    // Backwards-compatible constructor: generate an id from the name.
    public Course(String name, String roomRequirement, int requiredHoursPerWeek) {
        this(sanitizeId("c", null), name, "", "", "", roomRequirement, requiredHoursPerWeek, Boolean.TRUE);
    }

    private static String sanitizeId(String prefix, String name) {
        if (name == null)
            return prefix + "_" + UUID.randomUUID().toString();
        String s = name.replaceAll("[^A-Za-z0-9]", "_").toLowerCase();
        return prefix + "_" + s;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getAbbreviation() {
        return abbreviation;
    }

    public String getSemester() {
        return semester;
    }

    public String getComponent() {
        return component;
    }

    public String getRoomRequirement() {
        return roomRequirement;
    }

    public int getRequiredHoursPerWeek() {
        return requiredHoursPerWeek;
    }

    public Boolean getActive() {
        return active;
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
