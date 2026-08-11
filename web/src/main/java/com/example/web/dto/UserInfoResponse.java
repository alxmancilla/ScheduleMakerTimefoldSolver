package com.example.web.dto;

/**
 * The current authenticated user, returned by GET /api/auth/me.
 */
public class UserInfoResponse {

    private final String username;
    private final String role;

    public UserInfoResponse(String username, String role) {
        this.username = username;
        this.role = role;
    }

    public String getUsername() {
        return username;
    }

    public String getRole() {
        return role;
    }
}
