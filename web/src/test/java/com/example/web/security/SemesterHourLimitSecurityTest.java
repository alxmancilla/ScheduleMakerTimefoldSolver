package com.example.web.security;

import com.example.web.controller.SemesterHourLimitController;
import com.example.web.repository.CourseBlockAssignmentRepository;
import com.example.web.repository.SemesterHourLimitRepository;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Confirms /api/admin/semester-hour-limits is ADMIN-only (falls under the
 * blanket /api/admin/** rule): READER and WRITER get 403, matching
 * AdminReportSecurityTest/ConstraintConfigSecurityTest's convention for the
 * same rule.
 */
@RunWith(SpringRunner.class)
@WebMvcTest(SemesterHourLimitController.class)
@Import(SecurityConfig.class)
@TestPropertySource(properties = "app.jwt.secret=test-secret-key-that-is-at-least-32-bytes-long")
public class SemesterHourLimitSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private SemesterHourLimitRepository limitRepository;

    @MockBean
    private CourseBlockAssignmentRepository assignmentRepository;

    @MockBean
    private AppUserDetailsService appUserDetailsService;

    @Test
    public void anonymous_isUnauthorized() throws Exception {
        mockMvc.perform(get("/api/admin/semester-hour-limits"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "READER")
    public void reader_isForbidden() throws Exception {
        mockMvc.perform(get("/api/admin/semester-hour-limits"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "WRITER")
    public void writer_isForbidden() throws Exception {
        mockMvc.perform(get("/api/admin/semester-hour-limits"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    public void admin_canAccess() throws Exception {
        when(limitRepository.findAll()).thenReturn(List.of());
        mockMvc.perform(get("/api/admin/semester-hour-limits"))
                .andExpect(status().isOk());
    }
}
