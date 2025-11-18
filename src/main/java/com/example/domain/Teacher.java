package com.example.domain;

import java.time.DayOfWeek;
import java.util.Set;
import java.util.Objects;

public class Teacher {
    private final String name;
    private final Set<String> qualifications;
    private final Set<DayOfWeek> availableDays;
    private final int startHour;
    private final int endHour;

    public Teacher(String name, Set<String> qualifications, Set<DayOfWeek> availableDays,
            int startHour, int endHour) {
        this.name = name;
        this.qualifications = qualifications;
        this.availableDays = availableDays;
        this.startHour = startHour;
        this.endHour = endHour;
    }

    public String getName() {
        return name;
    }

    public Set<String> getQualifications() {
        return qualifications;
    }

    public Set<DayOfWeek> getAvailableDays() {
        return availableDays;
    }

    public int getStartHour() {
        return startHour;
    }

    public int getEndHour() {
        return endHour;
    }

    public boolean isQualifiedFor(String course) {
        return qualifications.contains(course);
    }

    public boolean isAvailableAt(Timeslot timeslot) {
        return availableDays.contains(timeslot.getDayOfWeek())
                && timeslot.getHour() >= startHour
                && timeslot.getHour() < endHour;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        Teacher teacher = (Teacher) o;
        return Objects.equals(name, teacher.name);
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
