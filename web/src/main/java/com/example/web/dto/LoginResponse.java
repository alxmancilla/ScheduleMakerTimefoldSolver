package com.example.web.dto;

/**
 * Successful login result: the signed JWT plus the metadata the SPA needs to
 * store it and reflect the current user's role.
 */
public class LoginResponse {

    private final String token;
    private final String tokenType = "Bearer";
    private final String username;
    private final String role;
    private final long expiresInSeconds;
    private final String preferredLanguage;

    public LoginResponse(String token, String username, String role, long expiresInSeconds, String preferredLanguage) {
        this.token = token;
        this.username = username;
        this.role = role;
        this.expiresInSeconds = expiresInSeconds;
        this.preferredLanguage = preferredLanguage;
    }

    public String getToken() {
        return token;
    }

    public String getTokenType() {
        return tokenType;
    }

    public String getUsername() {
        return username;
    }

    public String getRole() {
        return role;
    }

    public long getExpiresInSeconds() {
        return expiresInSeconds;
    }

    public String getPreferredLanguage() {
        return preferredLanguage;
    }
}
