package com.example.web.controller;

import com.example.web.entity.AppUserEntity;
import com.example.web.repository.AppUserRepository;
import com.example.web.security.AppUserDetailsService;
import com.example.web.security.SecurityConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.servlet.MockMvc;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Web-layer tests for {@link UserController}: user CRUD, password reset, and
 * the last-admin / self-delete safety guards. Uses the MVC slice with a
 * mocked repository so no database is required. Unlike most controller
 * tests, this one keeps the real security filter chain active (rather than
 * addFilters = false) because deleteUser() needs a real Authentication to
 * check "is this the caller's own account" - mirrors AuthControllerTest's
 * setup for the same reason.
 */
@RunWith(SpringRunner.class)
@WebMvcTest(UserController.class)
@Import(SecurityConfig.class)
@TestPropertySource(properties = "app.jwt.secret=test-secret-key-that-is-at-least-32-bytes-long")
public class UserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private AppUserRepository userRepository;

    @MockBean
    private PasswordEncoder passwordEncoder;

    // Required by the AuthenticationManager bean declared in SecurityConfig.
    @MockBean
    private AppUserDetailsService appUserDetailsService;

    private String json(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }

    private AppUserEntity user(String username, String role, boolean enabled) {
        return new AppUserEntity(username, "hash", role, enabled);
    }

    // ---- GET ----

    @Test
    @WithMockUser(roles = "ADMIN")
    public void getAllUsers_returnsSortedList() throws Exception {
        when(userRepository.findAll()).thenReturn(List.of(
                user("writer1", "WRITER", true), user("admin", "ADMIN", true)));
        mockMvc.perform(get("/api/admin/users"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].username").value("admin"))
                .andExpect(jsonPath("$[1].username").value("writer1"));
    }

    // ---- POST (create) ----

    @Test
    @WithMockUser(roles = "ADMIN")
    public void createUser_valid_returnsSaved() throws Exception {
        when(userRepository.existsById("newwriter")).thenReturn(false);
        when(passwordEncoder.encode("supersecret")).thenReturn("hashed");
        when(userRepository.save(any(AppUserEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        Map<String, Object> body = Map.of("username", "newwriter", "password", "supersecret", "role", "WRITER");
        mockMvc.perform(post("/api/admin/users").contentType(MediaType.APPLICATION_JSON).content(json(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("newwriter"))
                .andExpect(jsonPath("$.role").value("WRITER"))
                .andExpect(jsonPath("$.enabled").value(true));
        verify(userRepository).save(any(AppUserEntity.class));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    public void createUser_duplicateUsername_returns400() throws Exception {
        when(userRepository.existsById("admin")).thenReturn(true);
        Map<String, Object> body = Map.of("username", "admin", "password", "supersecret", "role", "WRITER");
        mockMvc.perform(post("/api/admin/users").contentType(MediaType.APPLICATION_JSON).content(json(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("already exists")));
        verify(userRepository, never()).save(any(AppUserEntity.class));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    public void createUser_shortPassword_returnsValidationError() throws Exception {
        Map<String, Object> body = Map.of("username", "newwriter", "password", "short", "role", "WRITER");
        mockMvc.perform(post("/api/admin/users").contentType(MediaType.APPLICATION_JSON).content(json(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.password").exists());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    public void createUser_invalidRole_returnsValidationError() throws Exception {
        Map<String, Object> body = Map.of("username", "newwriter", "password", "supersecret", "role", "SUPERUSER");
        mockMvc.perform(post("/api/admin/users").contentType(MediaType.APPLICATION_JSON).content(json(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.role").exists());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    public void createUser_missingFields_returnsValidationError() throws Exception {
        mockMvc.perform(post("/api/admin/users").contentType(MediaType.APPLICATION_JSON).content(json(new HashMap<>())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.username").exists())
                .andExpect(jsonPath("$.errors.password").exists())
                .andExpect(jsonPath("$.errors.role").exists());
    }

    // ---- PUT (update role/enabled) ----

    @Test
    @WithMockUser(roles = "ADMIN")
    public void updateUser_roleChange_returnsUpdated() throws Exception {
        when(userRepository.findById("writer1")).thenReturn(Optional.of(user("writer1", "WRITER", true)));
        when(userRepository.save(any(AppUserEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        Map<String, Object> body = Map.of("role", "READER", "enabled", true, "preferredLanguage", "en");
        mockMvc.perform(put("/api/admin/users/writer1").contentType(MediaType.APPLICATION_JSON).content(json(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("READER"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    public void updateUser_notFound_returns404() throws Exception {
        when(userRepository.findById("nope")).thenReturn(Optional.empty());
        Map<String, Object> body = Map.of("role", "READER", "enabled", true, "preferredLanguage", "en");
        mockMvc.perform(put("/api/admin/users/nope").contentType(MediaType.APPLICATION_JSON).content(json(body)))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    public void updateUser_demotingLastAdmin_returns400() throws Exception {
        when(userRepository.findById("admin")).thenReturn(Optional.of(user("admin", "ADMIN", true)));
        when(userRepository.findAll()).thenReturn(List.of(user("admin", "ADMIN", true)));

        Map<String, Object> body = Map.of("role", "WRITER", "enabled", true, "preferredLanguage", "en");
        mockMvc.perform(put("/api/admin/users/admin").contentType(MediaType.APPLICATION_JSON).content(json(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("last remaining ADMIN")));
        verify(userRepository, never()).save(any(AppUserEntity.class));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    public void updateUser_disablingLastAdmin_returns400() throws Exception {
        when(userRepository.findById("admin")).thenReturn(Optional.of(user("admin", "ADMIN", true)));
        when(userRepository.findAll()).thenReturn(List.of(user("admin", "ADMIN", true)));

        Map<String, Object> body = Map.of("role", "ADMIN", "enabled", false, "preferredLanguage", "en");
        mockMvc.perform(put("/api/admin/users/admin").contentType(MediaType.APPLICATION_JSON).content(json(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("last remaining ADMIN")));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    public void updateUser_demotingAdminWithAnotherAdminPresent_succeeds() throws Exception {
        when(userRepository.findById("admin")).thenReturn(Optional.of(user("admin", "ADMIN", true)));
        when(userRepository.findAll()).thenReturn(List.of(
                user("admin", "ADMIN", true), user("admin2", "ADMIN", true)));
        when(userRepository.save(any(AppUserEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        Map<String, Object> body = Map.of("role", "WRITER", "enabled", true, "preferredLanguage", "en");
        mockMvc.perform(put("/api/admin/users/admin").contentType(MediaType.APPLICATION_JSON).content(json(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("WRITER"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    public void updateUser_languageChange_returnsUpdated() throws Exception {
        when(userRepository.findById("writer1")).thenReturn(Optional.of(user("writer1", "WRITER", true)));
        when(userRepository.save(any(AppUserEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        Map<String, Object> body = Map.of("role", "WRITER", "enabled", true, "preferredLanguage", "es");
        mockMvc.perform(put("/api/admin/users/writer1").contentType(MediaType.APPLICATION_JSON).content(json(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.preferredLanguage").value("es"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    public void updateUser_invalidLanguage_returnsValidationError() throws Exception {
        Map<String, Object> body = Map.of("role", "WRITER", "enabled", true, "preferredLanguage", "fr");
        mockMvc.perform(put("/api/admin/users/writer1").contentType(MediaType.APPLICATION_JSON).content(json(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.preferredLanguage").exists());
        verify(userRepository, never()).save(any(AppUserEntity.class));
    }

    // ---- PUT (password reset) ----

    @Test
    @WithMockUser(roles = "ADMIN")
    public void resetPassword_valid_returnsUpdated() throws Exception {
        when(userRepository.findById("writer1")).thenReturn(Optional.of(user("writer1", "WRITER", true)));
        when(passwordEncoder.encode("brandnewpassword")).thenReturn("hashed2");
        when(userRepository.save(any(AppUserEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        Map<String, Object> body = Map.of("newPassword", "brandnewpassword");
        mockMvc.perform(put("/api/admin/users/writer1/password").contentType(MediaType.APPLICATION_JSON).content(json(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("writer1"));
        verify(passwordEncoder).encode("brandnewpassword");
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    public void resetPassword_tooShort_returnsValidationError() throws Exception {
        Map<String, Object> body = Map.of("newPassword", "short");
        mockMvc.perform(put("/api/admin/users/writer1/password").contentType(MediaType.APPLICATION_JSON).content(json(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.newPassword").exists());
        verify(passwordEncoder, never()).encode(anyString());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    public void resetPassword_userNotFound_returns404() throws Exception {
        when(userRepository.findById("nope")).thenReturn(Optional.empty());
        Map<String, Object> body = Map.of("newPassword", "brandnewpassword");
        mockMvc.perform(put("/api/admin/users/nope/password").contentType(MediaType.APPLICATION_JSON).content(json(body)))
                .andExpect(status().isNotFound());
    }

    // ---- DELETE ----

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    public void deleteUser_otherUser_returns204() throws Exception {
        when(userRepository.findById("writer1")).thenReturn(Optional.of(user("writer1", "WRITER", true)));
        mockMvc.perform(delete("/api/admin/users/writer1"))
                .andExpect(status().isNoContent());
        verify(userRepository).delete(any(AppUserEntity.class));
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    public void deleteUser_self_returns400() throws Exception {
        when(userRepository.findById("admin")).thenReturn(Optional.of(user("admin", "ADMIN", true)));
        mockMvc.perform(delete("/api/admin/users/admin"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("own account")));
        verify(userRepository, never()).delete(any(AppUserEntity.class));
    }

    @Test
    @WithMockUser(username = "admin2", roles = "ADMIN")
    public void deleteUser_lastAdmin_returns400() throws Exception {
        when(userRepository.findById("admin")).thenReturn(Optional.of(user("admin", "ADMIN", true)));
        when(userRepository.findAll()).thenReturn(List.of(user("admin", "ADMIN", true)));
        mockMvc.perform(delete("/api/admin/users/admin"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("last remaining ADMIN")));
        verify(userRepository, never()).delete(any(AppUserEntity.class));
    }

    @Test
    @WithMockUser(username = "admin2", roles = "ADMIN")
    public void deleteUser_notFound_returns404() throws Exception {
        when(userRepository.findById("nope")).thenReturn(Optional.empty());
        mockMvc.perform(delete("/api/admin/users/nope"))
                .andExpect(status().isNotFound());
    }
}
