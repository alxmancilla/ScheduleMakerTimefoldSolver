package com.example.web.security;

import com.example.web.controller.AdminReportController;
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

import java.io.File;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Confirms /api/admin/reports (the compliance-snapshot PDFs generated
 * automatically after each engine run) is ADMIN-only: READER and WRITER get
 * 403, unlike /api/reports which any authenticated role can read.
 */
@RunWith(SpringRunner.class)
@WebMvcTest(AdminReportController.class)
@Import(SecurityConfig.class)
@TestPropertySource(properties = "app.jwt.secret=test-secret-key-that-is-at-least-32-bytes-long")
public class AdminReportSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private EngineRunnerService engineRunnerService;

    @MockBean
    private AppUserDetailsService appUserDetailsService;

    @Test
    public void anonymous_isUnauthorized() throws Exception {
        mockMvc.perform(get("/api/admin/reports"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "READER")
    public void reader_isForbidden() throws Exception {
        mockMvc.perform(get("/api/admin/reports"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "WRITER")
    public void writer_isForbidden() throws Exception {
        mockMvc.perform(get("/api/admin/reports"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    public void admin_canAccess() throws Exception {
        when(engineRunnerService.getAdminReportsDir()).thenReturn(new File("/nonexistent"));
        mockMvc.perform(get("/api/admin/reports"))
                .andExpect(status().isOk());
    }
}
