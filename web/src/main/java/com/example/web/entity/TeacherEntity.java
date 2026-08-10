package com.example.web.entity;

import com.fasterxml.jackson.annotation.JsonManagedReference;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "teacher")
public class TeacherEntity {

    @Id
    @Column(name = "id", length = 100)
    private String id;

    @Column(name = "name", length = 200, nullable = false)
    private String name;

    @Column(name = "last_name", length = 200, nullable = false)
    private String lastName;

    @Column(name = "max_hours_per_week", nullable = false)
    private Integer maxHoursPerWeek = 40;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @OneToMany(mappedBy = "teacher", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @JsonManagedReference("teacher-qualifications")
    private Set<TeacherQualificationEntity> qualifications = new HashSet<>();

    @OneToMany(mappedBy = "teacher", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @JsonManagedReference("teacher-availability")
    private Set<TeacherAvailabilityEntity> availability = new HashSet<>();

    public TeacherEntity() {
    }

    public TeacherEntity(String id, String name, String lastName, Integer maxHoursPerWeek) {
        this.id = id;
        this.name = name;
        this.lastName = lastName;
        this.maxHoursPerWeek = maxHoursPerWeek;
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

    public String getLastName() {
        return lastName;
    }

    public void setLastName(String lastName) {
        this.lastName = lastName;
    }

    public Integer getMaxHoursPerWeek() {
        return maxHoursPerWeek;
    }

    public void setMaxHoursPerWeek(Integer maxHoursPerWeek) {
        this.maxHoursPerWeek = maxHoursPerWeek;
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

    public Set<TeacherQualificationEntity> getQualifications() {
        return qualifications;
    }

    public void setQualifications(Set<TeacherQualificationEntity> qualifications) {
        this.qualifications = qualifications;
    }

    public Set<TeacherAvailabilityEntity> getAvailability() {
        return availability;
    }

    public void setAvailability(Set<TeacherAvailabilityEntity> availability) {
        this.availability = availability;
    }

    // Helper methods

    public void addQualification(String qualification) {
        TeacherQualificationEntity entity = new TeacherQualificationEntity();
        entity.setTeacher(this);
        entity.setQualification(qualification);
        qualifications.add(entity);
    }

    public void addAvailability(Integer dayOfWeek, Integer hour) {
        TeacherAvailabilityEntity entity = new TeacherAvailabilityEntity();
        entity.setTeacher(this);
        entity.setDayOfWeek(dayOfWeek);
        entity.setHour(hour);
        availability.add(entity);
    }
}
