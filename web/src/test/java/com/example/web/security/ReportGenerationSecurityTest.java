package com.example.web.security;

import com.example.web.controller.ReportGenerationController;
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

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Confirms /api/reports/generate follows the general POST rule (WRITER or
 * ADMIN), not the ADMIN-only /api/admin/** rule: READER gets 403, WRITER and
 * ADMIN both get through to the controller.
 */
@RunWith(SpringRunner.class)
@WebMvcTest(ReportGenerationController.class)
@Import(SecurityConfig.class)
@TestPropertySource(properties = "app.jwt.secret=test-secret-key-that-is-at-least-32-bytes-long")
public class ReportGenerationSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ReportRunnerService reportRunnerService;

    // Required by the AuthenticationManager bean declared in SecurityConfig.
    @MockBean
    private AppUserDetailsService appUserDetailsService;

    private void stubRunningStart() {
        when(reportRunnerService.tryStart()).thenReturn(true);
        when(reportRunnerService.getSnapshot())
                .thenReturn(new ReportRunnerService.Snapshot(ReportRunnerService.State.RUNNING, null, null, null, List.of()));
    }

    @Test
    public void anonymous_isUnauthorized() throws Exception {
        mockMvc.perform(post("/api/reports/generate"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "READER")
    public void reader_isForbidden() throws Exception {
        mockMvc.perform(post("/api/reports/generate"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "WRITER")
    public void writer_canAccess() throws Exception {
        stubRunningStart();
        mockMvc.perform(post("/api/reports/generate"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    public void admin_canAccess() throws Exception {
        stubRunningStart();
        mockMvc.perform(post("/api/reports/generate"))
                .andExpect(status().isOk());
    }
}
