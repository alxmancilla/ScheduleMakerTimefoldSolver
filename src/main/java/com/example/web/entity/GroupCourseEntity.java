package com.example.web.entity;

import jakarta.persistence.*;
import java.io.Serializable;
import java.time.LocalDateTime;

@Entity
@Table(name = "group_course")
@IdClass(GroupCourseEntity.GroupCourseId.class)
public class GroupCourseEntity {
    
    @Id
    @Column(name = "group_id", length = 100)
    private String groupId;
    
    @Id
    @Column(name = "course_name", length = 200)
    private String courseName;
    
    @Column(name = "created_at")
    private LocalDateTime createdAt;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "group_id", insertable = false, updatable = false)
    private StudentGroupEntity group;
    
    public GroupCourseEntity() {
    }
    
    public GroupCourseEntity(String groupId, String courseName) {
        this.groupId = groupId;
        this.courseName = courseName;
    }
    
    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
    
    // Getters and Setters
    
    public String getGroupId() {
        return groupId;
    }
    
    public void setGroupId(String groupId) {
        this.groupId = groupId;
    }
    
    public String getCourseName() {
        return courseName;
    }
    
    public void setCourseName(String courseName) {
        this.courseName = courseName;
    }
    
    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
    
    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
    
    public StudentGroupEntity getGroup() {
        return group;
    }
    
    public void setGroup(StudentGroupEntity group) {
        this.group = group;
        if (group != null) {
            this.groupId = group.getId();
        }
    }
    
    // Composite Key Class
    public static class GroupCourseId implements Serializable {
        private String groupId;
        private String courseName;
        
        public GroupCourseId() {
        }
        
        public GroupCourseId(String groupId, String courseName) {
            this.groupId = groupId;
            this.courseName = courseName;
        }
        
        public String getGroupId() {
            return groupId;
        }
        
        public void setGroupId(String groupId) {
            this.groupId = groupId;
        }
        
        public String getCourseName() {
            return courseName;
        }
        
        public void setCourseName(String courseName) {
            this.courseName = courseName;
        }
        
        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            GroupCourseId that = (GroupCourseId) o;
            return groupId.equals(that.groupId) && courseName.equals(that.courseName);
        }
        
        @Override
        public int hashCode() {
            return groupId.hashCode() + courseName.hashCode();
        }
    }
}

