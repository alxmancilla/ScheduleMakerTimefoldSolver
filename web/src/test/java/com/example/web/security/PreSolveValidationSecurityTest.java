package com.example.web.security;

import com.example.web.controller.PreSolveValidationController;
import com.example.web.service.PreSolveValidationRunnerService;
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

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Confirms /api/validation falls under the general rule (POST needs WRITER
 * or ADMIN, not the /api/admin/** ADMIN-only gate EngineController uses) -
 * deliberate, since running the check by itself is lower-risk than actually
 * triggering a solve, and the feature was explicitly requested for both
 * roles.
 */
@RunWith(SpringRunner.class)
@WebMvcTest(PreSolveValidationController.class)
@Import(SecurityConfig.class)
@TestPropertySource(properties = "app.jwt.secret=test-secret-key-that-is-at-least-32-bytes-long")
public class PreSolveValidationSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private PreSolveValidationRunnerService validationRunnerService;

    // Required by the AuthenticationManager bean declared in SecurityConfig.
    @MockBean
    private AppUserDetailsService appUserDetailsService;

    @Test
    public void anonymous_isUnauthorized() throws Exception {
        mockMvc.perform(get("/api/validation/status"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "READER")
    public void reader_run_isForbidden() throws Exception {
        mockMvc.perform(post("/api/validation/run"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "TEACHER")
    public void teacher_run_isForbidden() throws Exception {
        mockMvc.perform(post("/api/validation/run"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "WRITER")
    public void writer_canRun() throws Exception {
        when(validationRunnerService.tryStart()).thenReturn(true);
        when(validationRunnerService.getSnapshot()).thenReturn(new PreSolveValidationRunnerService.Snapshot(
                PreSolveValidationRunnerService.State.RUNNING, null, null, null, List.of()));
        mockMvc.perform(post("/api/validation/run"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    public void admin_canRun() throws Exception {
        when(validationRunnerService.tryStart()).thenReturn(true);
        when(validationRunnerService.getSnapshot()).thenReturn(new PreSolveValidationRunnerService.Snapshot(
                PreSolveValidationRunnerService.State.RUNNING, null, null, null, List.of()));
        mockMvc.perform(post("/api/validation/run"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "READER")
    public void reader_canViewStatus() throws Exception {
        when(validationRunnerService.getSnapshot()).thenReturn(new PreSolveValidationRunnerService.Snapshot(
                PreSolveValidationRunnerService.State.IDLE, null, null, null, List.of()));
        mockMvc.perform(get("/api/validation/status"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "TEACHER")
    public void teacher_statusIsForbidden() throws Exception {
        mockMvc.perform(get("/api/validation/status"))
                .andExpect(status().isForbidden());
    }
}
