package com.example.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Data Transfer Object for GroupRoomRange with validation. The parent group
 * comes from the URL path, not this body. room_type is free text (no CHECK
 * constraint, matching room.type/course_room_requirement.room_type elsewhere
 * in this codebase).
 */
public class GroupRoomRangeDTO {

    @NotBlank(message = "Room type is required")
    @Size(max = 50, message = "Room type must not exceed 50 characters")
    private String roomType;

    @NotBlank(message = "Room name is required")
    @Size(max = 100, message = "Room name must not exceed 100 characters")
    private String roomName;

    public GroupRoomRangeDTO() {
    }

    public String getRoomType() {
        return roomType;
    }

    public void setRoomType(String roomType) {
        this.roomType = roomType;
    }

    public String getRoomName() {
        return roomName;
    }

    public void setRoomName(String roomName) {
        this.roomName = roomName;
    }
}
