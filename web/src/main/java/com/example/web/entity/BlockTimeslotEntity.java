package com.example.web.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "block_timeslot")
public class BlockTimeslotEntity {

    @Id
    @Column(name = "id", length = 50)
    private String id;

    @Column(name = "day_of_week", nullable = false)
    private Integer dayOfWeek;

    @Column(name = "start_hour", nullable = false)
    private Integer startHour;

    @Column(name = "length_hours", nullable = false)
    private Integer lengthHours;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    public BlockTimeslotEntity() {
    }

    public BlockTimeslotEntity(Integer dayOfWeek, Integer startHour, Integer lengthHours) {
        this.dayOfWeek = dayOfWeek;
        this.startHour = startHour;
        this.lengthHours = lengthHours;
    }

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

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

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
