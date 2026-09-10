package com.example.web.dto;

import jakarta.validation.constraints.*;

/**
 * Data Transfer Object for CourseBlockAssignment with validation.
 */
public class CourseBlockAssignmentDTO {

    /**
     * Validation group applied only on creation, where the ID is supplied by the
     * client. On update the ID comes from the path and is ignored in the payload.
     */
    public interface Create {
    }

    @NotBlank(message = "Assignment ID is required", groups = Create.class)
    @Size(max = 100, message = "Assignment ID must not exceed 100 characters", groups = Create.class)
    @Pattern(regexp = "^[A-Za-z0-9_-]+$", message = "Assignment ID must contain only letters, numbers, hyphens, and underscores", groups = Create.class)
    private String id;

    @NotBlank(message = "Group ID is required")
    @Size(max = 100, message = "Group ID must not exceed 100 characters")
    private String groupId;

    @NotBlank(message = "Course ID is required")
    @Size(max = 100, message = "Course ID must not exceed 100 characters")
    private String courseId;

    @NotNull(message = "Block length is required")
    @Min(value = 1, message = "Block length must be at least 1")
    @Max(value = 4, message = "Block length must not exceed 4")
    private Integer blockLength;

    private Boolean pinned;

    @Size(max = 100, message = "Teacher ID must not exceed 100 characters")
    private String teacherId;

    @Size(max = 50, message = "Block timeslot ID must not exceed 50 characters")
    private String blockTimeslotId;

    @Size(max = 100, message = "Room name must not exceed 100 characters")
    private String roomName;

    @Size(max = 100, message = "Satisfies room type must not exceed 100 characters")
    private String satisfiesRoomType;

    @Size(max = 100, message = "Preferred room hint must not exceed 100 characters")
    private String preferredRoomHint;

    public CourseBlockAssignmentDTO() {
    }

    // Getters and Setters

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getGroupId() {
        return groupId;
    }

    public void setGroupId(String groupId) {
        this.groupId = groupId;
    }

    public String getCourseId() {
        return courseId;
    }

    public void setCourseId(String courseId) {
        this.courseId = courseId;
    }

    public Integer getBlockLength() {
        return blockLength;
    }

    public void setBlockLength(Integer blockLength) {
        this.blockLength = blockLength;
    }

    public Boolean getPinned() {
        return pinned;
    }

    public void setPinned(Boolean pinned) {
        this.pinned = pinned;
    }

    public String getTeacherId() {
        return teacherId;
    }

    public void setTeacherId(String teacherId) {
        this.teacherId = teacherId;
    }

    public String getBlockTimeslotId() {
        return blockTimeslotId;
    }

    public void setBlockTimeslotId(String blockTimeslotId) {
        this.blockTimeslotId = blockTimeslotId;
    }

    public String getRoomName() {
        return roomName;
    }

    public void setRoomName(String roomName) {
        this.roomName = roomName;
    }

    public String getSatisfiesRoomType() {
        return satisfiesRoomType;
    }

    public void setSatisfiesRoomType(String satisfiesRoomType) {
        this.satisfiesRoomType = satisfiesRoomType;
    }

    public String getPreferredRoomHint() {
        return preferredRoomHint;
    }

    public void setPreferredRoomHint(String preferredRoomHint) {
        this.preferredRoomHint = preferredRoomHint;
    }
}
