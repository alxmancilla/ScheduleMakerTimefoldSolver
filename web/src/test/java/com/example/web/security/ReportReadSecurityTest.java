package com.example.web.security;

import com.example.web.controller.ReportController;
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

import java.io.File;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Confirms /api/reports (unlike /api/admin/reports/generate) is readable by
 * any authenticated role, since the Reports tab is open to READER/WRITER/ADMIN.
 */
@RunWith(SpringRunner.class)
@WebMvcTest(ReportController.class)
@Import(SecurityConfig.class)
@TestPropertySource(properties = "app.jwt.secret=test-secret-key-that-is-at-least-32-bytes-long")
public class ReportReadSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ReportRunnerService reportRunnerService;

    @MockBean
    private AppUserDetailsService appUserDetailsService;

    @Test
    public void anonymous_isUnauthorized() throws Exception {
        mockMvc.perform(get("/api/reports"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "READER")
    public void reader_canRead() throws Exception {
        org.mockito.Mockito.when(reportRunnerService.getReportsDir()).thenReturn(new File("/nonexistent"));
        mockMvc.perform(get("/api/reports"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "WRITER")
    public void writer_canRead() throws Exception {
        org.mockito.Mockito.when(reportRunnerService.getReportsDir()).thenReturn(new File("/nonexistent"));
        mockMvc.perform(get("/api/reports"))
                .andExpect(status().isOk());
    }
}
