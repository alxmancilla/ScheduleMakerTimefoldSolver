package com.example.web.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "course")
public class CourseEntity {
    
    @Id
    @Column(name = "id", length = 100)
    private String id;
    
    @Column(name = "name", length = 200, nullable = false)
    private String name;
    
    @Column(name = "abbreviation", length = 50)
    private String abbreviation;
    
    @Column(name = "semester", length = 10)
    private String semester;
    
    @Column(name = "component", length = 50)
    private String component;
    
    @Column(name = "room_requirement", length = 100)
    private String roomRequirement;
    
    @Column(name = "required_hours_per_week")
    private Integer requiredHoursPerWeek;
    
    @Column(name = "active")
    private Boolean active = true;
    
    @Column(name = "created_at")
    private LocalDateTime createdAt;
    
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
    
    public CourseEntity() {
    }
    
    public CourseEntity(String id, String name, String roomRequirement, Integer requiredHoursPerWeek) {
        this.id = id;
        this.name = name;
        this.roomRequirement = roomRequirement;
        this.requiredHoursPerWeek = requiredHoursPerWeek;
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
    
    public String getAbbreviation() {
        return abbreviation;
    }
    
    public void setAbbreviation(String abbreviation) {
        this.abbreviation = abbreviation;
    }
    
    public String getSemester() {
        return semester;
    }
    
    public void setSemester(String semester) {
        this.semester = semester;
    }
    
    public String getComponent() {
        return component;
    }
    
    public void setComponent(String component) {
        this.component = component;
    }
    
    public String getRoomRequirement() {
        return roomRequirement;
    }
    
    public void setRoomRequirement(String roomRequirement) {
        this.roomRequirement = roomRequirement;
    }
    
    public Integer getRequiredHoursPerWeek() {
        return requiredHoursPerWeek;
    }
    
    public void setRequiredHoursPerWeek(Integer requiredHoursPerWeek) {
        this.requiredHoursPerWeek = requiredHoursPerWeek;
    }
    
    public Boolean getActive() {
        return active;
    }
    
    public void setActive(Boolean active) {
        this.active = active;
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

