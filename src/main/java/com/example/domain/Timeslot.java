package com.example.domain;

import java.time.DayOfWeek;
import java.util.Objects;

public class Timeslot {
    private final String id;
    private final DayOfWeek dayOfWeek;
    private final int hour; // 8-15
    private final String displayName;

    public Timeslot(String id, DayOfWeek dayOfWeek, int hour, String displayName) {
        this.id = id;
        this.dayOfWeek = dayOfWeek;
        this.hour = hour;
        this.displayName = displayName;
    }

    public String getId() {
        return id;
    }

    public DayOfWeek getDayOfWeek() {
        return dayOfWeek;
    }

    public int getHour() {
        return hour;
    }

    public String getDisplayName() {
        return displayName;
    }

    public boolean isLunchTime() {
        return hour == 12; // Lunch is 12-1
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        Timeslot timeslot = (Timeslot) o;
        return Objects.equals(id, timeslot.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return displayName;
    }
}
