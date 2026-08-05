package com.example.web.dto;

import jakarta.validation.constraints.*;

/**
 * Data Transfer Object for Course with validation.
 */
public class CourseDTO {

    /**
     * Validation group applied only on creation, where the ID is supplied by the
     * client. On update the ID comes from the path and is ignored in the payload.
     */
    public interface Create {
    }

    @NotBlank(message = "Course ID is required", groups = Create.class)
    @Size(max = 100, message = "Course ID must not exceed 100 characters", groups = Create.class)
    @Pattern(regexp = "^[A-Za-z0-9_-]+$", message = "Course ID must contain only letters, numbers, hyphens, and underscores", groups = Create.class)
    private String id;

    @NotBlank(message = "Course name is required")
    @Size(min = 2, max = 200, message = "Course name must be between 2 and 200 characters")
    private String name;

    @Size(max = 50, message = "Abbreviation must not exceed 50 characters")
    private String abbreviation;

    @Size(max = 10, message = "Semester must not exceed 10 characters")
    private String semester;

    @Size(max = 50, message = "Component must not exceed 50 characters")
    private String component;

    @Size(max = 100, message = "Room requirement must not exceed 100 characters")
    private String roomRequirement;

    @NotNull(message = "Required hours per week is required")
    @Min(value = 1, message = "Required hours per week must be at least 1")
    @Max(value = 40, message = "Required hours per week must not exceed 40")
    private Integer requiredHoursPerWeek;

    private Boolean active;

    public CourseDTO() {
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

    public String getAbbreviation() {
        return abbreviation;
    }

    public void setAbbreviation(String abbreviation) {
        this.abbreviation = abbreviation;
    }

    public String getSemester() {
        return semester;
    }

    public void setSemester(String semester) {
        this.semester = semester;
    }

    public String getComponent() {
        return component;
    }

    public void setComponent(String component) {
        this.component = component;
    }

    public String getRoomRequirement() {
        return roomRequirement;
    }

    public void setRoomRequirement(String roomRequirement) {
        this.roomRequirement = roomRequirement;
    }

    public Integer getRequiredHoursPerWeek() {
        return requiredHoursPerWeek;
    }

    public void setRequiredHoursPerWeek(Integer requiredHoursPerWeek) {
        this.requiredHoursPerWeek = requiredHoursPerWeek;
    }

    public Boolean getActive() {
        return active;
    }

    public void setActive(Boolean active) {
        this.active = active;
    }
}
