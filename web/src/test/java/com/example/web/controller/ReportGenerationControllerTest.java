package com.example.web.controller;

import com.example.web.entity.AppUserEntity;
import com.example.web.repository.AppUserRepository;
import com.example.web.security.AppUserDetailsService;
import com.example.web.security.SecurityConfig;
import com.example.web.service.ReportRunnerService;
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

import java.util.List;
import java.util.Optional;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Web-layer tests for {@link ReportGenerationController}: the run/"already
 * running" contract and the requesting user's preferred-language lookup.
 * Keeps the real security filter chain active (rather than addFilters =
 * false) because generateReports() needs a real Authentication to resolve
 * the caller's username - mirrors UserControllerTest's setup for the same
 * reason.
 */
@RunWith(SpringRunner.class)
@WebMvcTest(ReportGenerationController.class)
@Import(SecurityConfig.class)
@TestPropertySource(properties = "app.jwt.secret=test-secret-key-that-is-at-least-32-bytes-long")
public class ReportGenerationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ReportRunnerService reportRunnerService;

    @MockBean
    private AppUserRepository appUserRepository;

    // Required by the AuthenticationManager bean declared in SecurityConfig.
    @MockBean
    private AppUserDetailsService appUserDetailsService;

    private ReportRunnerService.Snapshot snapshot(ReportRunnerService.State state) {
        return new ReportRunnerService.Snapshot(state, null, null, null, null, List.of());
    }

    @Test
    @WithMockUser(username = "alice", roles = "WRITER")
    public void generateReports_notAlreadyRunning_startsAndReturnsStatus() throws Exception {
        when(appUserRepository.findById("alice")).thenReturn(Optional.empty());
        when(reportRunnerService.tryStart(null, null)).thenReturn(true);
        when(reportRunnerService.getSnapshot()).thenReturn(snapshot(ReportRunnerService.State.RUNNING));

        mockMvc.perform(post("/api/reports/generate"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("RUNNING"));
    }

    @Test
    @WithMockUser(username = "alice", roles = "WRITER")
    public void generateReports_withRunId_passesItThrough() throws Exception {
        when(appUserRepository.findById("alice")).thenReturn(Optional.empty());
        when(reportRunnerService.tryStart(7, null)).thenReturn(true);
        when(reportRunnerService.getSnapshot()).thenReturn(snapshot(ReportRunnerService.State.RUNNING));

        mockMvc.perform(post("/api/reports/generate").param("runId", "7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("RUNNING"));
    }

    @Test
    @WithMockUser(username = "alice", roles = "WRITER")
    public void generateReports_userHasPreferredLanguage_passesItThrough() throws Exception {
        AppUserEntity user = new AppUserEntity();
        user.setPreferredLanguage("es");
        when(appUserRepository.findById("alice")).thenReturn(Optional.of(user));
        when(reportRunnerService.tryStart(null, "es")).thenReturn(true);
        when(reportRunnerService.getSnapshot()).thenReturn(snapshot(ReportRunnerService.State.RUNNING));

        mockMvc.perform(post("/api/reports/generate"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("RUNNING"));
    }

    @Test
    @WithMockUser(username = "alice", roles = "WRITER")
    public void generateReports_alreadyRunning_returns400() throws Exception {
        when(appUserRepository.findById("alice")).thenReturn(Optional.empty());
        when(reportRunnerService.tryStart(null, null)).thenReturn(false);

        mockMvc.perform(post("/api/reports/generate"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("already running")));
    }
}
