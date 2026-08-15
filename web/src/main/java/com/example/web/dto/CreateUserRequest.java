package com.example.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Body for POST /api/admin/users: create a new application user. */
public class CreateUserRequest {

    @NotBlank(message = "Username is required")
    @Pattern(regexp = "^[A-Za-z0-9_.-]{3,100}$",
            message = "Username must be 3-100 characters: letters, digits, underscore, dot, or hyphen")
    private String username;

    @NotBlank(message = "Password is required")
    @Size(min = 8, max = 72, message = "Password must be between 8 and 72 characters")
    private String password;

    @NotBlank(message = "Role is required")
    @Pattern(regexp = "^(ADMIN|WRITER|READER)$", message = "Role must be ADMIN, WRITER, or READER")
    private String role;

    public CreateUserRequest() {
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }
}
