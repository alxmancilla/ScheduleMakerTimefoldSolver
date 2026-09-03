package com.example.web.controller;

import com.example.web.entity.AppUserEntity;
import com.example.web.repository.AppUserRepository;
import com.example.web.security.AppUserDetailsService;
import com.example.web.security.SecurityConfig;
import com.example.web.service.EngineRunnerService;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Web-layer tests for {@link EngineController}, covering the run/status
 * contract, the "already running" error response, and the requesting user's
 * preferred-language lookup (used for the auto-generated compliance
 * snapshot's report chrome). Keeps the real security filter chain active
 * (rather than addFilters = false) because runEngine() needs a real
 * Authentication to resolve the caller's username - mirrors
 * UserControllerTest's setup for the same reason.
 */
@RunWith(SpringRunner.class)
@WebMvcTest(EngineController.class)
@Import(SecurityConfig.class)
@TestPropertySource(properties = "app.jwt.secret=test-secret-key-that-is-at-least-32-bytes-long")
public class EngineControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private EngineRunnerService engineRunnerService;

    @MockBean
    private AppUserRepository appUserRepository;

    // Required by the AuthenticationManager bean declared in SecurityConfig.
    @MockBean
    private AppUserDetailsService appUserDetailsService;

    private EngineRunnerService.Snapshot snapshot(EngineRunnerService.State state, Integer exitCode) {
        return new EngineRunnerService.Snapshot(state, LocalDateTime.now(), null, exitCode, List.of("line 1", "line 2"));
    }

    @Test
    @WithMockUser(username = "alice", roles = "ADMIN")
    public void runEngine_noBody_startsWithNoOverrides() throws Exception {
        when(appUserRepository.findById("alice")).thenReturn(Optional.empty());
        when(engineRunnerService.tryStart(null, null, null, null, null)).thenReturn(true);
        when(engineRunnerService.getSnapshot()).thenReturn(snapshot(EngineRunnerService.State.RUNNING, null));

        mockMvc.perform(post("/api/admin/engine/run"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("RUNNING"));

        verify(engineRunnerService).tryStart(null, null, null, null, null);
    }

    @Test
    @WithMockUser(username = "alice", roles = "ADMIN")
    public void runEngine_withOverrides_passesThemThrough() throws Exception {
        when(appUserRepository.findById("alice")).thenReturn(Optional.empty());
        when(engineRunnerService.tryStart(10, 4, null, null, null)).thenReturn(true);
        when(engineRunnerService.getSnapshot()).thenReturn(snapshot(EngineRunnerService.State.RUNNING, null));

        mockMvc.perform(post("/api/admin/engine/run")
                        .contentType(APPLICATION_JSON)
                        .content("{\"minutesSpentLimit\":10,\"unimprovedMinutesSpentLimit\":4}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("RUNNING"));

        verify(engineRunnerService).tryStart(10, 4, null, null, null);
    }

    @Test
    @WithMockUser(username = "alice", roles = "ADMIN")
    public void runEngine_skipValidation_passesItThrough() throws Exception {
        when(appUserRepository.findById("alice")).thenReturn(Optional.empty());
        when(engineRunnerService.tryStart(null, null, null, true, null)).thenReturn(true);
        when(engineRunnerService.getSnapshot()).thenReturn(snapshot(EngineRunnerService.State.RUNNING, null));

        mockMvc.perform(post("/api/admin/engine/run")
                        .contentType(APPLICATION_JSON)
                        .content("{\"skipValidation\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("RUNNING"));

        verify(engineRunnerService).tryStart(null, null, null, true, null);
    }

    @Test
    @WithMockUser(username = "alice", roles = "ADMIN")
    public void runEngine_userHasPreferredLanguage_passesItThrough() throws Exception {
        AppUserEntity user = new AppUserEntity();
        user.setPreferredLanguage("es");
        when(appUserRepository.findById("alice")).thenReturn(Optional.of(user));
        when(engineRunnerService.tryStart(null, null, "es", null, null)).thenReturn(true);
        when(engineRunnerService.getSnapshot()).thenReturn(snapshot(EngineRunnerService.State.RUNNING, null));

        mockMvc.perform(post("/api/admin/engine/run"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("RUNNING"));

        verify(engineRunnerService).tryStart(null, null, "es", null, null);
    }

    @Test
    @WithMockUser(username = "alice", roles = "ADMIN")
    public void runEngine_randomSeed_passesItThrough() throws Exception {
        when(appUserRepository.findById("alice")).thenReturn(Optional.empty());
        when(engineRunnerService.tryStart(null, null, null, null, "random")).thenReturn(true);
        when(engineRunnerService.getSnapshot()).thenReturn(snapshot(EngineRunnerService.State.RUNNING, null));

        mockMvc.perform(post("/api/admin/engine/run")
                        .contentType(APPLICATION_JSON)
                        .content("{\"randomSeed\":\"random\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("RUNNING"));

        verify(engineRunnerService).tryStart(null, null, null, null, "random");
    }

    @Test
    @WithMockUser(username = "alice", roles = "ADMIN")
    public void runEngine_malformedRandomSeed_returns400() throws Exception {
        mockMvc.perform(post("/api/admin/engine/run")
                        .contentType(APPLICATION_JSON)
                        .content("{\"randomSeed\":\"not-a-seed\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(username = "alice", roles = "ADMIN")
    public void runEngine_outOfBoundsOverride_returns400() throws Exception {
        mockMvc.perform(post("/api/admin/engine/run")
                        .contentType(APPLICATION_JSON)
                        .content("{\"minutesSpentLimit\":150}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(username = "alice", roles = "ADMIN")
    public void runEngine_alreadyRunning_returns400() throws Exception {
        when(appUserRepository.findById("alice")).thenReturn(Optional.empty());
        when(engineRunnerService.tryStart(null, null, null, null, null)).thenReturn(false);

        mockMvc.perform(post("/api/admin/engine/run"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("already running")));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    public void getStatus_returnsSnapshot() throws Exception {
        when(engineRunnerService.getSnapshot()).thenReturn(snapshot(EngineRunnerService.State.COMPLETED, 0));

        mockMvc.perform(get("/api/admin/engine/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("COMPLETED"))
                .andExpect(jsonPath("$.exitCode").value(0))
                .andExpect(jsonPath("$.log", org.hamcrest.Matchers.hasSize(2)));
    }
}
