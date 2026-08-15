package com.example.web.dto;

import com.example.web.entity.AppUserEntity;

import java.time.LocalDateTime;

/**
 * Application user info exposed by the admin user-management endpoints.
 * Never includes the password hash.
 */
public class UserSummaryResponse {

    private final String username;
    private final String role;
    private final boolean enabled;
    private final String preferredLanguage;
    private final LocalDateTime createdAt;
    private final LocalDateTime updatedAt;

    public UserSummaryResponse(AppUserEntity user) {
        this.username = user.getUsername();
        this.role = user.getRole();
        this.enabled = user.isEnabled();
        this.preferredLanguage = user.getPreferredLanguage();
        this.createdAt = user.getCreatedAt();
        this.updatedAt = user.getUpdatedAt();
    }

    public String getUsername() {
        return username;
    }

    public String getRole() {
        return role;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public String getPreferredLanguage() {
        return preferredLanguage;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}
