package com.example.domain;

/**
 * Represents a room requirement for a course.
 * Courses can have multiple room requirements (e.g., 4 hours in computer center + 1 hour in standard room).
 * Each requirement specifies the room type, hours needed, priority, and optional preferred room.
 */
public class RoomRequirement {
    
    private Long id;
    private String courseId;
    private String roomType;
    private int hoursRequired;
    private int priority;  // 1 = primary, 2 = secondary, etc.
    private String defaultPreferredRoom;
    
    // No-arg constructor required by Timefold
    public RoomRequirement() {
    }
    
    public RoomRequirement(Long id, String courseId, String roomType, int hoursRequired, 
                          int priority, String defaultPreferredRoom) {
        this.id = id;
        this.courseId = courseId;
        this.roomType = roomType;
        this.hoursRequired = hoursRequired;
        this.priority = priority;
        this.defaultPreferredRoom = defaultPreferredRoom;
    }
    
    // Getters and setters
    
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
    
    public int getHoursRequired() {
        return hoursRequired;
    }
    
    public void setHoursRequired(int hoursRequired) {
        this.hoursRequired = hoursRequired;
    }
    
    public int getPriority() {
        return priority;
    }
    
    public void setPriority(int priority) {
        this.priority = priority;
    }
    
    public String getDefaultPreferredRoom() {
        return defaultPreferredRoom;
    }
    
    public void setDefaultPreferredRoom(String defaultPreferredRoom) {
        this.defaultPreferredRoom = defaultPreferredRoom;
    }
    
    @Override
    public String toString() {
        return "RoomRequirement{" +
                "id=" + id +
                ", courseId='" + courseId + '\'' +
                ", roomType='" + roomType + '\'' +
                ", hoursRequired=" + hoursRequired +
                ", priority=" + priority +
                ", defaultPreferredRoom='" + defaultPreferredRoom + '\'' +
                '}';
    }
}

