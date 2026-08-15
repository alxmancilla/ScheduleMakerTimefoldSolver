package com.example.web.controller;

import com.example.web.dto.CreateUserRequest;
import com.example.web.dto.ResetPasswordRequest;
import com.example.web.dto.UpdateUserRequest;
import com.example.web.dto.UserSummaryResponse;
import com.example.web.entity.AppUserEntity;
import com.example.web.exception.ResourceNotFoundException;
import com.example.web.repository.AppUserRepository;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.Comparator;
import java.util.List;

/**
 * Admin-only CRUD for application users (app_user table): who can sign in and
 * with which role. Mounted under /api/admin/**, which SecurityConfig already
 * restricts to the ADMIN role.
 *
 * Two safety guards prevent locking every admin out of user management:
 * an ADMIN can't delete their own account, and the last remaining
 * enabled ADMIN can't be deleted, demoted, or disabled.
 */
@RestController
@RequestMapping("/api/admin/users")
public class UserController {

    @Autowired
    private AppUserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @GetMapping
    public List<UserSummaryResponse> getAllUsers() {
        return userRepository.findAll().stream()
                .sorted(Comparator.comparing(AppUserEntity::getUsername))
                .map(UserSummaryResponse::new)
                .toList();
    }

    @PostMapping
    public UserSummaryResponse createUser(@Valid @RequestBody CreateUserRequest request) {
        if (userRepository.existsById(request.getUsername())) {
            throw new IllegalArgumentException("Username already exists");
        }
        AppUserEntity user = new AppUserEntity(
                request.getUsername(),
                passwordEncoder.encode(request.getPassword()),
                request.getRole(),
                true);
        return new UserSummaryResponse(userRepository.save(user));
    }

    @PutMapping("/{username}")
    public UserSummaryResponse updateUser(@PathVariable String username, @Valid @RequestBody UpdateUserRequest request) {
        AppUserEntity user = userRepository.findById(username)
                .orElseThrow(() -> new ResourceNotFoundException("User", username));
        boolean staysAdmin = "ADMIN".equals(request.getRole()) && Boolean.TRUE.equals(request.getEnabled());
        if (!staysAdmin) {
            guardLastAdmin(user, username);
        }
        user.setRole(request.getRole());
        user.setEnabled(request.getEnabled());
        user.setPreferredLanguage(request.getPreferredLanguage());
        return new UserSummaryResponse(userRepository.save(user));
    }

    @PutMapping("/{username}/password")
    public UserSummaryResponse resetPassword(@PathVariable String username, @Valid @RequestBody ResetPasswordRequest request) {
        AppUserEntity user = userRepository.findById(username)
                .orElseThrow(() -> new ResourceNotFoundException("User", username));
        user.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        return new UserSummaryResponse(userRepository.save(user));
    }

    @DeleteMapping("/{username}")
    public ResponseEntity<Void> deleteUser(@PathVariable String username, Authentication authentication) {
        AppUserEntity user = userRepository.findById(username)
                .orElseThrow(() -> new ResourceNotFoundException("User", username));
        if (username.equals(authentication.getName())) {
            throw new IllegalArgumentException("Cannot delete your own account");
        }
        guardLastAdmin(user, username);
        userRepository.delete(user);
        return ResponseEntity.noContent().build();
    }

    /** Blocks removing ADMIN capability from the only remaining enabled ADMIN account. */
    private void guardLastAdmin(AppUserEntity user, String username) {
        if (!"ADMIN".equals(user.getRole()) || !user.isEnabled()) {
            return;
        }
        boolean anotherEnabledAdminExists = userRepository.findAll().stream()
                .anyMatch(u -> !u.getUsername().equals(username) && "ADMIN".equals(u.getRole()) && u.isEnabled());
        if (!anotherEnabledAdminExists) {
            throw new IllegalArgumentException("Cannot remove the last remaining ADMIN account");
        }
    }
}
