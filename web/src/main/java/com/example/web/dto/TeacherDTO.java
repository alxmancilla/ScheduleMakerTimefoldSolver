package com.example.web.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.Set;

/**
 * Data Transfer Object for Teacher with validation.
 */
public class TeacherDTO {

    /**
     * Validation group applied only on creation, where the ID is supplied by the
     * client. On update the ID comes from the path and is ignored in the payload.
     */
    public interface Create {
    }

    @NotBlank(message = "Teacher ID is required", groups = Create.class)
    @Size(max = 100, message = "Teacher ID must not exceed 100 characters", groups = Create.class)
    @Pattern(regexp = "^[A-Za-z0-9_-]+$", message = "Teacher ID must contain only letters, numbers, hyphens, and underscores", groups = Create.class)
    private String id;

    @NotBlank(message = "Teacher name is required")
    @Size(min = 2, max = 200, message = "Teacher name must be between 2 and 200 characters")
    private String name;

    @NotBlank(message = "Teacher last name is required")
    @Size(min = 2, max = 200, message = "Teacher last name must be between 2 and 200 characters")
    private String lastName;

    @NotNull(message = "Max hours per week is required")
    @Min(value = 1, message = "Max hours per week must be at least 1")
    @Max(value = 60, message = "Max hours per week must not exceed 60")
    private Integer maxHoursPerWeek;

    private Set<@NotBlank(message = "Qualification must not be blank") String> qualifications;

    @Valid
    private Set<AvailabilityDTO> availability;

    public TeacherDTO() {
    }

    public TeacherDTO(String id, String name, String lastName, Integer maxHoursPerWeek) {
        this.id = id;
        this.name = name;
        this.lastName = lastName;
        this.maxHoursPerWeek = maxHoursPerWeek;
    }

    // Getters and Setters

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
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

    public Integer getMaxHoursPerWeek() {
        return maxHoursPerWeek;
    }

    public void setMaxHoursPerWeek(Integer maxHoursPerWeek) {
        this.maxHoursPerWeek = maxHoursPerWeek;
    }

    public Set<String> getQualifications() {
        return qualifications;
    }

    public void setQualifications(Set<String> qualifications) {
        this.qualifications = qualifications;
    }

    public Set<AvailabilityDTO> getAvailability() {
        return availability;
    }

    public void setAvailability(Set<AvailabilityDTO> availability) {
        this.availability = availability;
    }

    /**
     * Nested DTO for teacher availability.
     */
    public static class AvailabilityDTO {

        @NotNull(message = "Day of week is required")
        @Min(value = 1, message = "Day of week must be between 1 (Monday) and 5 (Friday)")
        @Max(value = 5, message = "Day of week must be between 1 (Monday) and 5 (Friday)")
        private Integer dayOfWeek;

        // 14 (not 15): the school day runs 7:00-15:00, and availability represents the start
        // hour of a 1-hour slot a block could occupy - a block starting at 14 (the latest
        // possible) still only occupies the 14:00-15:00 hour, so 15 itself is never checked.
        @NotNull(message = "Hour is required")
        @Min(value = 7, message = "Hour must be between 7 and 14")
        @Max(value = 14, message = "Hour must be between 7 and 14")
        private Integer hour;

        public AvailabilityDTO() {
        }

        public AvailabilityDTO(Integer dayOfWeek, Integer hour) {
            this.dayOfWeek = dayOfWeek;
            this.hour = hour;
        }

        public Integer getDayOfWeek() {
            return dayOfWeek;
        }

        public void setDayOfWeek(Integer dayOfWeek) {
            this.dayOfWeek = dayOfWeek;
        }

        public Integer getHour() {
            return hour;
        }

        public void setHour(Integer hour) {
            this.hour = hour;
        }
    }
}
