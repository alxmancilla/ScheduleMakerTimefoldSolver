package com.example.web.entity;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/**
 * One room-type requirement for a course that needs hours split across
 * multiple room types (e.g. 4h in a computer center + 1h in a standard room).
 * A course with no rows here just uses its single legacy
 * {@code CourseEntity.roomRequirement} field instead.
 */
@Entity
@Table(name = "course_room_requirement")
public class CourseRoomRequirementEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "course_id", length = 10, nullable = false)
    private String courseId;

    @Column(name = "room_type", length = 50, nullable = false)
    private String roomType;

    @Column(name = "hours_required", nullable = false)
    private Integer hoursRequired;

    @Column(name = "priority")
    private Integer priority = 1;

    @Column(name = "default_preferred_room", length = 100)
    private String defaultPreferredRoom;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public CourseRoomRequirementEntity() {
    }

    public CourseRoomRequirementEntity(String courseId, String roomType, Integer hoursRequired, Integer priority,
            String defaultPreferredRoom) {
        this.courseId = courseId;
        this.roomType = roomType;
        this.hoursRequired = hoursRequired;
        this.priority = priority;
        this.defaultPreferredRoom = defaultPreferredRoom;
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
