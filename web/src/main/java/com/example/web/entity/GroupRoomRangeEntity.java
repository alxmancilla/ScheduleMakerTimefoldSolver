package com.example.web.entity;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/**
 * One acceptable room for a group, for a given room type. A group's full
 * curated range for a type is every row with that (groupId, roomType) pair -
 * a room type with no rows for a group is unrestricted (falls through to the
 * solver's full type-filtered room list). Replaces the old single
 * {@code student_group.preferred_room_name} - see
 * database/migrations/add_group_room_ranges.sql.
 */
@Entity
@Table(name = "group_room_range")
public class GroupRoomRangeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "group_id", length = 100, nullable = false)
    private String groupId;

    @Column(name = "room_type", length = 50, nullable = false)
    private String roomType;

    @Column(name = "room_name", length = 100, nullable = false)
    private String roomName;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    public GroupRoomRangeEntity() {
    }

    public GroupRoomRangeEntity(String groupId, String roomType, String roomName) {
        this.groupId = groupId;
        this.roomType = roomType;
        this.roomName = roomName;
    }

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    // Getters and Setters

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getGroupId() {
        return groupId;
    }

    public void setGroupId(String groupId) {
        this.groupId = groupId;
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

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
