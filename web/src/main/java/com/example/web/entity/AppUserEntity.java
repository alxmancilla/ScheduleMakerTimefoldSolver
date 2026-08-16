package com.example.web.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Application user backing authentication and role-based authorization.
 * Each user has exactly one role: ADMIN, WRITER or READER.
 */
@Entity
@Table(name = "app_user")
public class AppUserEntity {

    @Id
    @Column(name = "username", length = 100)
    private String username;

    /** BCrypt hash of the password (never the plaintext). */
    @Column(name = "password_hash", length = 100, nullable = false)
    private String passwordHash;

    @Column(name = "role", length = 20, nullable = false)
    private String role;

    @Column(name = "enabled", nullable = false)
    private boolean enabled = true;

    @Column(name = "preferred_language", length = 5, nullable = false)
    private String preferredLanguage = "en";

    /** Optional link to a teacher record. Meaningful only for TEACHER-role accounts. */
    @Column(name = "teacher_id", length = 100)
    private String teacherId;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public AppUserEntity() {
    }

    public AppUserEntity(String username, String passwordHash, String role, boolean enabled) {
        this.username = username;
        this.passwordHash = passwordHash;
        this.role = role;
        this.enabled = enabled;
    }

    @PrePersist
    void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getPreferredLanguage() {
        return preferredLanguage;
    }

    public void setPreferredLanguage(String preferredLanguage) {
        this.preferredLanguage = preferredLanguage;
    }

    public String getTeacherId() {
        return teacherId;
    }

    public void setTeacherId(String teacherId) {
        this.teacherId = teacherId;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}
