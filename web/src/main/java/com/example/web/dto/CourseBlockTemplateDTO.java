package com.example.web.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Data Transfer Object for CourseBlockTemplate with validation. The parent
 * course comes from the URL path, not this body. Mirrors
 * course_block_template's DB CHECK constraints (block_length 1-4, preferred_day
 * 1-5) plus the same "pinned requires a timeslot" rule the generated
 * course_block_assignment row itself enforces
 * (check_block_assignment_pinned_requires_timeslot), so a template that would
 * violate it is rejected here instead of failing later when
 * BlockGenerationService tries to insert the block it produces.
 */
public class CourseBlockTemplateDTO {

    private String groupId;

    @NotNull(message = "Block index is required")
    @Min(value = 0, message = "Block index must be 0 or greater")
    private Integer blockIndex;

    @NotNull(message = "Block length is required")
    @Min(value = 1, message = "Block length must be between 1 and 4")
    @Max(value = 4, message = "Block length must be between 1 and 4")
    private Integer blockLength;

    @Size(max = 50, message = "Room type must not exceed 50 characters")
    private String roomType;

    @Size(max = 100, message = "Preferred room must not exceed 100 characters")
    private String preferredRoomName;

    @Min(value = 1, message = "Preferred day must be between 1 (Monday) and 5 (Friday)")
    @Max(value = 5, message = "Preferred day must be between 1 (Monday) and 5 (Friday)")
    private Integer preferredDay;

    private Boolean pinAssignment = false;

    private String preferredTimeslotId;

    public CourseBlockTemplateDTO() {
    }

    @AssertTrue(message = "A pinned template must specify a preferred timeslot")
    public boolean isPinnedRequiresTimeslot() {
        return !Boolean.TRUE.equals(pinAssignment) || preferredTimeslotId != null;
    }

    public String getGroupId() {
        return groupId;
    }

    public void setGroupId(String groupId) {
        this.groupId = groupId;
    }

    public Integer getBlockIndex() {
        return blockIndex;
    }

    public void setBlockIndex(Integer blockIndex) {
        this.blockIndex = blockIndex;
    }

    public Integer getBlockLength() {
        return blockLength;
    }

    public void setBlockLength(Integer blockLength) {
        this.blockLength = blockLength;
    }

    public String getRoomType() {
        return roomType;
    }

    public void setRoomType(String roomType) {
        this.roomType = roomType;
    }

    public String getPreferredRoomName() {
        return preferredRoomName;
    }

    public void setPreferredRoomName(String preferredRoomName) {
        this.preferredRoomName = preferredRoomName;
    }

    public Integer getPreferredDay() {
        return preferredDay;
    }

    public void setPreferredDay(Integer preferredDay) {
        this.preferredDay = preferredDay;
    }

    public Boolean getPinAssignment() {
        return pinAssignment;
    }

    public void setPinAssignment(Boolean pinAssignment) {
        this.pinAssignment = pinAssignment;
    }

    public String getPreferredTimeslotId() {
        return preferredTimeslotId;
    }

    public void setPreferredTimeslotId(String preferredTimeslotId) {
        this.preferredTimeslotId = preferredTimeslotId;
    }
}
