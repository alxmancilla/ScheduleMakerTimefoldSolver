package com.example.web.entity;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/**
 * One explicit block for a course, instead of letting BlockGenerationService's
 * generic decomposition (or dual room requirements) derive one. A NULL
 * {@code groupId} means the template applies to every group taking this
 * course; a specific group's own template for the same {@code blockIndex}
 * takes precedence over a NULL-group one (see BlockGenerationService).
 * {@code preferredRoomName}/{@code preferredTimeslotId} are pre-assigned onto
 * the generated block directly (not just a soft hint) since room and timeslot
 * are not solver-assigned in this system except timeslot, which the solver
 * still optimizes unless {@code pinAssignment} is set.
 */
@Entity
@Table(name = "course_block_template")
public class CourseBlockTemplateEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "course_id", length = 10, nullable = false)
    private String courseId;

    @Column(name = "group_id", length = 10)
    private String groupId;

    @Column(name = "block_index", nullable = false)
    private Integer blockIndex;

    @Column(name = "block_length", nullable = false)
    private Integer blockLength;

    @Column(name = "room_type", length = 50)
    private String roomType;

    @Column(name = "preferred_room_name", length = 100)
    private String preferredRoomName;

    @Column(name = "preferred_day")
    private Integer preferredDay;

    @Column(name = "pin_assignment")
    private Boolean pinAssignment = false;

    @Column(name = "preferred_timeslot_id", length = 20)
    private String preferredTimeslotId;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public CourseBlockTemplateEntity() {
    }

    public CourseBlockTemplateEntity(String courseId, String groupId, Integer blockIndex, Integer blockLength,
            String roomType, String preferredRoomName, Integer preferredDay, Boolean pinAssignment,
            String preferredTimeslotId) {
        this.courseId = courseId;
        this.groupId = groupId;
        this.blockIndex = blockIndex;
        this.blockLength = blockLength;
        this.roomType = roomType;
        this.preferredRoomName = preferredRoomName;
        this.preferredDay = preferredDay;
        this.pinAssignment = pinAssignment;
        this.preferredTimeslotId = preferredTimeslotId;
    }

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    // Getters and Setters

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getCourseId() {
        return courseId;
    }

    public void setCourseId(String courseId) {
        this.courseId = courseId;
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

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
