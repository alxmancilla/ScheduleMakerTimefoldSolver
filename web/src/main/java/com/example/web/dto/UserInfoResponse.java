package com.example.web.dto;

/**
 * The current authenticated user, returned by GET /api/auth/me.
 */
public class UserInfoResponse {

    private final String username;
    private final String role;
    private final String preferredLanguage;

    public UserInfoResponse(String username, String role, String preferredLanguage) {
        this.username = username;
        this.role = role;
        this.preferredLanguage = preferredLanguage;
    }

    public String getUsername() {
        return username;
    }

    public String getRole() {
        return role;
    }

    public String getPreferredLanguage() {
        return preferredLanguage;
    }
}
