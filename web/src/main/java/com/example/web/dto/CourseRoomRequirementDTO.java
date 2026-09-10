package com.example.web.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Data Transfer Object for CourseRoomRequirement with validation. The parent
 * course comes from the URL path, not this body. Mirrors
 * course_room_requirement's DB constraints: room_type is free text (no CHECK
 * constraint, same as room.type and course.room_requirement elsewhere in this
 * codebase), hours_required must be positive.
 */
public class CourseRoomRequirementDTO {

    @NotBlank(message = "Room type is required")
    @Size(max = 50, message = "Room type must not exceed 50 characters")
    private String roomType;

    @NotNull(message = "Hours required is required")
    @Min(value = 1, message = "Hours required must be at least 1")
    private Integer hoursRequired;

    @Min(value = 1, message = "Priority must be at least 1")
    private Integer priority;

    @Size(max = 100, message = "Default preferred room must not exceed 100 characters")
    private String defaultPreferredRoom;

    public CourseRoomRequirementDTO() {
    }

    public String getRoomType() {
        return roomType;
    }

    public void setRoomType(String roomType) {
        this.roomType = roomType;
    }

    public Integer getHoursRequired() {
        return hoursRequired;
    }

    public void setHoursRequired(Integer hoursRequired) {
        this.hoursRequired = hoursRequired;
    }

    public Integer getPriority() {
        return priority;
    }

    public void setPriority(Integer priority) {
        this.priority = priority;
    }

    public String getDefaultPreferredRoom() {
        return defaultPreferredRoom;
    }

    public void setDefaultPreferredRoom(String defaultPreferredRoom) {
        this.defaultPreferredRoom = defaultPreferredRoom;
    }
}
