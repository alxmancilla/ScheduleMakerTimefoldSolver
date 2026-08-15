package com.example.web.controller;

import com.example.web.entity.AppUserEntity;
import com.example.web.repository.AppUserRepository;
import com.example.web.security.AppUserDetailsService;
import com.example.web.security.SecurityConfig;
import com.example.web.security.TokenService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Tests the authentication endpoints: successful login issues a JWT with the
 * user's role and preferred language, bad credentials return 401, /me
 * reflects the current user, and /preferred-language lets any authenticated
 * role (including READER) update their own language choice.
 * Imports the real {@link SecurityConfig} and {@link TokenService}.
 */
@RunWith(SpringRunner.class)
@WebMvcTest(AuthController.class)
@Import({ SecurityConfig.class, TokenService.class })
@TestPropertySource(properties = "app.jwt.secret=test-secret-key-that-is-at-least-32-bytes-long")
public class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private AppUserDetailsService appUserDetailsService;

    @MockBean
    private AppUserRepository userRepository;

    private String json(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }

    private void stubUser(String username, String rawPassword, String role) {
        String hash = new BCryptPasswordEncoder().encode(rawPassword);
        when(appUserDetailsService.loadUserByUsername(username)).thenReturn(
                User.withUsername(username)
                        .password(hash)
                        .authorities(new SimpleGrantedAuthority("ROLE_" + role))
                        .build());
    }

    @Test
    public void login_validCredentials_returnsTokenAndRole() throws Exception {
        stubUser("alice", "secret", "WRITER");
        AppUserEntity entity = new AppUserEntity("alice", "hash", "WRITER", true);
        entity.setPreferredLanguage("es");
        when(userRepository.findById("alice")).thenReturn(Optional.of(entity));

        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("username", "alice", "password", "secret"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.username").value("alice"))
                .andExpect(jsonPath("$.role").value("WRITER"))
                .andExpect(jsonPath("$.expiresInSeconds").isNumber())
                .andExpect(jsonPath("$.preferredLanguage").value("es"));
    }

    @Test
    public void login_noStoredUserRow_defaultsLanguageToEn() throws Exception {
        stubUser("alice", "secret", "WRITER");
        when(userRepository.findById("alice")).thenReturn(Optional.empty());

        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("username", "alice", "password", "secret"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.preferredLanguage").value("en"));
    }

    @Test
    public void login_badPassword_returns401() throws Exception {
        stubUser("alice", "secret", "WRITER");
        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("username", "alice", "password", "wrong"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid username or password"));
    }

    @Test
    public void login_blankUsername_returnsValidationError() throws Exception {
        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("username", "", "password", "secret"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.username").exists());
    }

    @Test
    public void me_anonymous_isUnauthorized() throws Exception {
        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(username = "bob", roles = "ADMIN")
    public void me_authenticated_returnsUserAndRole() throws Exception {
        AppUserEntity entity = new AppUserEntity("bob", "hash", "ADMIN", true);
        entity.setPreferredLanguage("es");
        when(userRepository.findById("bob")).thenReturn(Optional.of(entity));

        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("bob"))
                .andExpect(jsonPath("$.role").value("ADMIN"))
                .andExpect(jsonPath("$.preferredLanguage").value("es"));
    }

    @Test
    public void updateLanguage_anonymous_isUnauthorized() throws Exception {
        mockMvc.perform(put("/api/auth/preferred-language").contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("language", "es"))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(username = "carol", roles = "READER")
    public void updateLanguage_readerCanUpdateOwnLanguage() throws Exception {
        AppUserEntity entity = new AppUserEntity("carol", "hash", "READER", true);
        when(userRepository.findById("carol")).thenReturn(Optional.of(entity));
        when(userRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        mockMvc.perform(put("/api/auth/preferred-language").contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("language", "es"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("carol"))
                .andExpect(jsonPath("$.preferredLanguage").value("es"));

        verify(userRepository).save(org.mockito.ArgumentMatchers.argThat(u -> "es".equals(u.getPreferredLanguage())));
    }

    @Test
    @WithMockUser(username = "dave", roles = "WRITER")
    public void updateLanguage_invalidValue_returnsValidationError() throws Exception {
        mockMvc.perform(put("/api/auth/preferred-language").contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("language", "fr"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.language").exists());
    }

    @Test
    @WithMockUser(username = "unknown", roles = "ADMIN")
    public void updateLanguage_userRowMissing_returns404() throws Exception {
        when(userRepository.findById("unknown")).thenReturn(Optional.empty());
        mockMvc.perform(put("/api/auth/preferred-language").contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("language", "es"))))
                .andExpect(status().isNotFound());
    }
}
