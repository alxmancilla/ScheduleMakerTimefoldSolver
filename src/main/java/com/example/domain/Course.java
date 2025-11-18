package com.example.domain;

import java.util.Objects;

public class Course {
    private final String name;
    private final String roomRequirement; // 'standard', 'science_lab'
    private final int requiredHoursPerWeek;

    public Course(String name, String roomRequirement, int requiredHoursPerWeek) {
        this.name = name;
        this.roomRequirement = roomRequirement;
        this.requiredHoursPerWeek = requiredHoursPerWeek;
    }

    public String getName() {
        return name;
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
        return Objects.equals(name, course.name);
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
