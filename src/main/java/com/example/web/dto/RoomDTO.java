package com.example.web.dto;

import jakarta.validation.constraints.*;

/**
 * Data Transfer Object for Room with validation. The room's name is its
 * identifier and is only required/validated on creation.
 */
public class RoomDTO {

    /**
     * Validation group applied only on creation, where the name (identifier) is
     * supplied by the client. On update the name comes from the path.
     */
    public interface Create {
    }

    @NotBlank(message = "Room name is required", groups = Create.class)
    @Size(max = 100, message = "Room name must not exceed 100 characters", groups = Create.class)
    private String name;

    @NotBlank(message = "Building is required")
    @Size(max = 100, message = "Building must not exceed 100 characters")
    private String building;

    @NotBlank(message = "Room type is required")
    @Size(max = 100, message = "Room type must not exceed 100 characters")
    private String type;

    public RoomDTO() {
    }

    // Getters and Setters

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getBuilding() {
        return building;
    }

    public void setBuilding(String building) {
        this.building = building;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }
}
