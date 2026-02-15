package com.example.web.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "course_block_assignment")
public class CourseBlockAssignmentEntity {

    @Id
    @Column(name = "id", length = 100)
    private String id;

    @Column(name = "group_id", length = 100, nullable = false)
    private String groupId;

    @Column(name = "course_id", length = 100, nullable = false)
    private String courseId;

    @Column(name = "block_length", nullable = false)
    private Integer blockLength;

    @Column(name = "pinned")
    private Boolean pinned = false;

    @Column(name = "teacher_id", length = 100)
    private String teacherId;

    @Column(name = "block_timeslot_id", length = 50)
    private String blockTimeslotId;

    @Column(name = "room_name", length = 100)
    private String roomName;

    @Column(name = "satisfies_room_type", length = 100)
    private String satisfiesRoomType;

    @Column(name = "preferred_room_name", length = 100)
    private String preferredRoomName;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public CourseBlockAssignmentEntity() {
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

    public String getPreferredRoomName() {
        return preferredRoomName;
    }

    public void setPreferredRoomName(String preferredRoomName) {
        this.preferredRoomName = preferredRoomName;
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
