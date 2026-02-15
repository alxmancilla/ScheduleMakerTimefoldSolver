package com.example.domain;

import java.time.DayOfWeek;
import java.util.Set;
import java.util.Objects;
import java.util.UUID;

public class Teacher {
    private String id;
    private String name;
    private String lastName;
    private Set<String> qualifications;
    // Availability expressed as a map from DayOfWeek -> set of available hours
    // (each hour is an int)
    private java.util.Map<DayOfWeek, java.util.Set<Integer>> availabilityPerDay = new java.util.HashMap<>();
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

    public void setMaxHoursPerWeek() {
        this.maxHoursPerWeek = maxHoursPerWeek;
    }

    public void setId(String id) {
        this.id = id;
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

    public void setName(String name) {
        this.name = name;
    }

    public String getLastName() {
        return lastName;
    }

    public void setLastName(String lastName) {
        this.lastName = lastName;
    }

    public Set<String> getQualifications() {
        return qualifications;
    }

    public void setQualifications(Set<String> qualifications) {
        this.qualifications = qualifications;
    }

    /**
     * Return the set of days on which this teacher has any availability.
     */
    public java.util.Set<DayOfWeek> getAvailableDays() {
        return java.util.Collections.unmodifiableSet(availabilityPerDay.keySet());
    }

    /**
     * Get the total number of available hours across all days.
     * This is used by the difficulty comparator to prioritize teachers with low
     * availability.
     *
     * @return total number of available hours (sum of all hours across all days)
     */
    public int getTotalAvailableHours() {
        int total = 0;
        for (java.util.Set<Integer> hours : availabilityPerDay.values()) {
            total += hours.size();
        }
        return total;
    }

    /**
     * Get the availability map for this teacher.
     * This is used by the difficulty comparator to count available hours.
     *
     * @return map from DayOfWeek to set of available hours
     */
    public java.util.Map<DayOfWeek, java.util.Set<Integer>> getAvailabilityPerDay() {
        return availabilityPerDay;
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

    /**
     * Check if teacher is available at a specific day and hour.
     * This is a convenience method for checking availability.
     */
    public boolean isAvailableAt(DayOfWeek day, int hour) {
        java.util.Set<Integer> hours = availabilityPerDay.get(day);
        return hours != null && hours.contains(hour);
    }

    /**
     * Check if teacher is available for an entire block timeslot.
     * Returns true only if the teacher is available for ALL hours in the block.
     *
     * @param blockTimeslot the block timeslot to check
     * @return true if teacher is available for all hours in the block, false
     *         otherwise
     */
    public boolean isAvailableForBlock(BlockTimeslot blockTimeslot) {
        if (blockTimeslot == null) {
            return false;
        }

        // Check availability for each hour in the block
        int startHour = blockTimeslot.getStartHour();
        int endHour = startHour + blockTimeslot.getLengthHours();

        for (int hour = startHour; hour < endHour; hour++) {
            if (!isAvailableAt(blockTimeslot.getDayOfWeek(), hour)) {
                return false; // Teacher not available for this hour
            }
        }

        return true; // Teacher available for all hours in the block
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
