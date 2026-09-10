package com.example.web.dto;

import java.time.LocalDateTime;
import java.util.List;

public class ScheduleViewDTO {
    private List<ScheduleEntry> entries;
    private Integer totalAssignments;
    private Integer assignedCount;
    private Integer unassignedCount;

    public static class ScheduleEntry {
        private String id;
        private Integer dayOfWeek;
        private Integer startHour;
        private Integer lengthHours;
        private String courseName;
        private String teacherName;
        private String teacherId;
        private String roomName;
        private String groupName;
        private String groupId;
        private Boolean pinned;
        private LocalDateTime pinnedAt;
        private String pinnedBy;
        private String pinSource;
        private String courseId;
        private String satisfiesRoomType;

        // Getters and Setters

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public Integer getDayOfWeek() {
            return dayOfWeek;
        }

        public void setDayOfWeek(Integer dayOfWeek) {
            this.dayOfWeek = dayOfWeek;
        }

        public Integer getStartHour() {
            return startHour;
        }

        public void setStartHour(Integer startHour) {
            this.startHour = startHour;
        }

        public Integer getLengthHours() {
            return lengthHours;
        }

        public void setLengthHours(Integer lengthHours) {
            this.lengthHours = lengthHours;
        }

        public String getCourseName() {
            return courseName;
        }

        public void setCourseName(String courseName) {
            this.courseName = courseName;
        }

        public String getTeacherName() {
            return teacherName;
        }

        public void setTeacherName(String teacherName) {
            this.teacherName = teacherName;
        }

        public String getTeacherId() {
            return teacherId;
        }

        public void setTeacherId(String teacherId) {
            this.teacherId = teacherId;
        }

        public String getRoomName() {
            return roomName;
        }

        public void setRoomName(String roomName) {
            this.roomName = roomName;
        }

        public String getGroupName() {
            return groupName;
        }

        public void setGroupName(String groupName) {
            this.groupName = groupName;
        }

        public String getGroupId() {
            return groupId;
        }

        public void setGroupId(String groupId) {
            this.groupId = groupId;
        }

        public Boolean getPinned() {
            return pinned;
        }

        public void setPinned(Boolean pinned) {
            this.pinned = pinned;
        }

        public LocalDateTime getPinnedAt() {
            return pinnedAt;
        }

        public void setPinnedAt(LocalDateTime pinnedAt) {
            this.pinnedAt = pinnedAt;
        }

        public String getPinnedBy() {
            return pinnedBy;
        }

        public void setPinnedBy(String pinnedBy) {
            this.pinnedBy = pinnedBy;
        }

        public String getPinSource() {
            return pinSource;
        }

        public void setPinSource(String pinSource) {
            this.pinSource = pinSource;
        }

        public String getCourseId() {
            return courseId;
        }

        public void setCourseId(String courseId) {
            this.courseId = courseId;
        }

        public String getSatisfiesRoomType() {
            return satisfiesRoomType;
        }

        public void setSatisfiesRoomType(String satisfiesRoomType) {
            this.satisfiesRoomType = satisfiesRoomType;
        }
    }

    // Getters and Setters

    public List<ScheduleEntry> getEntries() {
        return entries;
    }

    public void setEntries(List<ScheduleEntry> entries) {
        this.entries = entries;
    }

    public Integer getTotalAssignments() {
        return totalAssignments;
    }

    public void setTotalAssignments(Integer totalAssignments) {
        this.totalAssignments = totalAssignments;
    }

    public Integer getAssignedCount() {
        return assignedCount;
    }

    public void setAssignedCount(Integer assignedCount) {
        this.assignedCount = assignedCount;
    }

    public Integer getUnassignedCount() {
        return unassignedCount;
    }

    public void setUnassignedCount(Integer unassignedCount) {
        this.unassignedCount = unassignedCount;
    }
}
