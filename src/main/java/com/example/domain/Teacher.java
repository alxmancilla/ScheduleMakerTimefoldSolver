package com.example.domain;

import java.time.DayOfWeek;
import java.util.Set;
import java.util.Objects;
import java.util.UUID;

public class Teacher {
    private final String id;
    private final String name;
    private final String lastName;
    private final Set<String> qualifications;
    // Availability expressed as a map from DayOfWeek -> set of available hours
    // (each hour is an int)
    private final java.util.Map<DayOfWeek, java.util.Set<Integer>> availabilityPerDay = new java.util.HashMap<>();
    // Maximum teaching hours per week for this teacher. Default will be 20.
    private int maxHoursPerWeek = 40;

    public Teacher(String id, String name, String lastName, Set<String> qualifications, Set<DayOfWeek> availableDays,
            int startHour, int endHour) {
        this.id = id;
        this.name = name;
        this.lastName = lastName;
        this.qualifications = qualifications;
        // Build availability map from provided days and hour range [startHour, endHour)
        for (DayOfWeek d : availableDays) {
            java.util.Set<Integer> hours = new java.util.HashSet<>();
            for (int h = startHour; h < endHour; h++) {
                hours.add(h);
            }
            availabilityPerDay.put(d, hours);
        }
    }

    // Backwards-compatible constructor that generates an id from the name
    public Teacher(String name, Set<String> qualifications, Set<DayOfWeek> availableDays,
            int startHour, int endHour) {
        this(sanitizeId("t", name), name, "", qualifications, availableDays, startHour, endHour);
    }

    /**
     * Constructor that also sets the maximum teaching hours per week.
     */
    public Teacher(String id, String name, String lastName, Set<String> qualifications, Set<DayOfWeek> availableDays,
            int startHour, int endHour, int maxHoursPerWeek) {
        this(id, name, lastName, qualifications, availableDays, startHour, endHour);
        this.maxHoursPerWeek = maxHoursPerWeek;
    }

    // Backwards-compatible constructor with maxHoursPerWeek parameter
    public Teacher(String name, Set<String> qualifications, Set<DayOfWeek> availableDays,
            int startHour, int endHour, int maxHoursPerWeek) {
        this(sanitizeId("t", name), name, "", qualifications, availableDays, startHour, endHour, maxHoursPerWeek);
    }

    /**
     * Construct a teacher with explicit availability per day.
     */
    public Teacher(String id, String name, String lastName, Set<String> qualifications,
            java.util.Map<DayOfWeek, java.util.Set<Integer>> availabilityPerDay,
            int maxHoursPerWeek) {
        this.id = id;
        this.name = name;
        this.lastName = lastName;
        this.qualifications = qualifications;
        if (availabilityPerDay != null) {
            // copy to internal map
            for (java.util.Map.Entry<DayOfWeek, java.util.Set<Integer>> e : availabilityPerDay.entrySet()) {
                this.availabilityPerDay.put(e.getKey(), new java.util.HashSet<>(e.getValue()));
            }
        }
        this.maxHoursPerWeek = maxHoursPerWeek;
    }

    // Backwards-compatible explicit-availability constructor without id
    public Teacher(String name, String lastName, Set<String> qualifications,
            java.util.Map<DayOfWeek, java.util.Set<Integer>> availabilityPerDay,
            int maxHoursPerWeek) {
        this(sanitizeId("t", lastName), name, lastName, qualifications, availabilityPerDay, maxHoursPerWeek);
    }

    /**
     * Get the maximum allowed teaching hours per week for this teacher.
     */
    public int getMaxHoursPerWeek() {
        return maxHoursPerWeek;
    }

    public String getId() {
        return id;
    }

    /**
     * Set the maximum allowed teaching hours per week for this teacher.
     */
    public void setMaxHoursPerWeek(int maxHoursPerWeek) {
        this.maxHoursPerWeek = maxHoursPerWeek;
    }

    public String getName() {
        return name;
    }

    public String getLastName() {
        return lastName;
    }

    public Set<String> getQualifications() {
        return qualifications;
    }

    /**
     * Return the set of days on which this teacher has any availability.
     */
    public java.util.Set<DayOfWeek> getAvailableDays() {
        return java.util.Collections.unmodifiableSet(availabilityPerDay.keySet());
    }

    /**
     * Derived: return the earliest hour the teacher is available across all days,
     * or 0 if none.
     */
    public int getStartHour() {
        return availabilityPerDay.values().stream().flatMapToInt(s -> s.stream().mapToInt(Integer::intValue))
                .min().orElse(0);
    }

    /**
     * Derived: return one past the latest hour the teacher is available across all
     * days, or 0 if none.
     */
    public int getEndHour() {
        java.util.OptionalInt max = availabilityPerDay.values().stream()
                .flatMapToInt(s -> s.stream().mapToInt(Integer::intValue)).max();
        return max.isPresent() ? max.getAsInt() + 1 : 0;
    }

    public boolean isQualifiedFor(String course) {
        return qualifications.contains(course);
    }

    public boolean isAvailableAt(Timeslot timeslot) {
        if (timeslot == null) {
            return false;
        }
        java.util.Set<Integer> hours = availabilityPerDay.get(timeslot.getDayOfWeek());
        return hours != null && hours.contains(timeslot.getHour());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        Teacher teacher = (Teacher) o;
        return Objects.equals(id, teacher.id);
    }

    private static String sanitizeId(String prefix, String name) {
        if (name == null)
            return prefix + "_" + UUID.randomUUID().toString();
        String s = name.replaceAll("[^A-Za-z0-9]", "_").toLowerCase();
        return prefix + "_" + s;
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
