package com.example.domain;

import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.Objects;

public class BlockTimeslot {
    private final String id;
    private final DayOfWeek dayOfWeek;
    private int startHour; // 8-15

    private final int lengthHours; // 1,2,3,4

    public BlockTimeslot(String id, DayOfWeek dayOfWeek, int startHour, int lengthHours) {
        this.id = id;
        this.dayOfWeek = dayOfWeek;
        this.startHour = startHour;
        this.lengthHours = lengthHours;
    }

    public String getId() {
        return id;
    }

    public int getStartHour() {
        return startHour;
    }

    public DayOfWeek getDayOfWeek() {
        return dayOfWeek;
    }

    public int getLengthHours() {
        return lengthHours;
    }

    public LocalTime getStartTime() {
        return LocalTime.of(startHour, 0);
    }

    public String getDisplayName() {
        return dayOfWeek.toString() + " " + getStartTime().toString() + "-" + getEndTime().toString();
    }

    public boolean isLunchTime() {
        return startHour == 12; // Lunch is 12-1
    }

    public LocalTime getEndTime() {
        return getStartTime().plusHours(lengthHours);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        BlockTimeslot timeslot = (BlockTimeslot) o;
        return Objects.equals(id, timeslot.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return getDisplayName();
    }
}
