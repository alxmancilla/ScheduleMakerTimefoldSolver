package com.example.web.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "student_group")
public class StudentGroupEntity {
    
    @Id
    @Column(name = "id", length = 100)
    private String id;
    
    @Column(name = "name", length = 200, nullable = false, unique = true)
    private String name;
    
    @Column(name = "preferred_room_name", length = 100)
    private String preferredRoomName;
    
    @Column(name = "created_at")
    private LocalDateTime createdAt;
    
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
    
    @OneToMany(mappedBy = "group", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    private Set<GroupCourseEntity> courses = new HashSet<>();
    
    public StudentGroupEntity() {
    }
    
    public StudentGroupEntity(String id, String name) {
        this.id = id;
        this.name = name;
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
    
    public String getName() {
        return name;
    }
    
    public void setName(String name) {
        this.name = name;
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
    
    public Set<GroupCourseEntity> getCourses() {
        return courses;
    }
    
    public void setCourses(Set<GroupCourseEntity> courses) {
        this.courses = courses;
    }
    
    // Helper method
    public void addCourse(String courseName) {
        GroupCourseEntity entity = new GroupCourseEntity();
        entity.setGroup(this);
        entity.setCourseName(courseName);
        courses.add(entity);
    }
}

