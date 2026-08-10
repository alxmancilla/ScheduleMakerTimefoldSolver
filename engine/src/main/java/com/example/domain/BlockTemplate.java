package com.example.domain;

/**
 * Represents a custom block template for a course.
 * Templates define explicit block decomposition instead of using the generic algorithm.
 * Each template specifies block length, room type, preferred room, and whether to pin the assignment.
 */
public class BlockTemplate {
    
    private Long id;
    private String courseId;
    private String groupId;  // NULL = applies to all groups
    private int blockIndex;
    private int blockLength;
    private String roomType;
    private String preferredRoomName;
    private Integer preferredDay;  // 1-5 (Mon-Fri), NULL = no preference
    private boolean pinAssignment;
    private String preferredTimeslotId;
    
    // No-arg constructor required by Timefold
    public BlockTemplate() {
    }
    
    public BlockTemplate(Long id, String courseId, String groupId, int blockIndex, int blockLength,
                        String roomType, String preferredRoomName, Integer preferredDay,
                        boolean pinAssignment, String preferredTimeslotId) {
        this.id = id;
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
    
    public String getGroupId() {
        return groupId;
    }
    
    public void setGroupId(String groupId) {
        this.groupId = groupId;
    }
    
    public int getBlockIndex() {
        return blockIndex;
    }
    
    public void setBlockIndex(int blockIndex) {
        this.blockIndex = blockIndex;
    }
    
    public int getBlockLength() {
        return blockLength;
    }
    
    public void setBlockLength(int blockLength) {
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
    
    public boolean isPinAssignment() {
        return pinAssignment;
    }
    
    public void setPinAssignment(boolean pinAssignment) {
        this.pinAssignment = pinAssignment;
    }
    
    public String getPreferredTimeslotId() {
        return preferredTimeslotId;
    }
    
    public void setPreferredTimeslotId(String preferredTimeslotId) {
        this.preferredTimeslotId = preferredTimeslotId;
    }
    
    @Override
    public String toString() {
        return "BlockTemplate{" +
                "id=" + id +
                ", courseId='" + courseId + '\'' +
                ", groupId='" + groupId + '\'' +
                ", blockIndex=" + blockIndex +
                ", blockLength=" + blockLength +
                ", roomType='" + roomType + '\'' +
                ", preferredRoomName='" + preferredRoomName + '\'' +
                ", preferredDay=" + preferredDay +
                ", pinAssignment=" + pinAssignment +
                ", preferredTimeslotId='" + preferredTimeslotId + '\'' +
                '}';
    }
}

