package com.example.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/** Body for PUT /api/admin/users/{username}: change an existing user's role, enabled status, and/or preferred language. */
public class UpdateUserRequest {

    @NotBlank(message = "Role is required")
    @Pattern(regexp = "^(ADMIN|WRITER|READER)$", message = "Role must be ADMIN, WRITER, or READER")
    private String role;

    @NotNull(message = "Enabled is required")
    private Boolean enabled;

    @NotBlank(message = "Preferred language is required")
    @Pattern(regexp = "^(en|es)$", message = "Preferred language must be 'en' or 'es'")
    private String preferredLanguage;

    public UpdateUserRequest() {
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public Boolean getEnabled() {
        return enabled;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }

    public String getPreferredLanguage() {
        return preferredLanguage;
    }

    public void setPreferredLanguage(String preferredLanguage) {
        this.preferredLanguage = preferredLanguage;
    }
}
