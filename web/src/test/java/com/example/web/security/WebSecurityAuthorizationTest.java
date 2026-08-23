package com.example.web.security;

import com.example.web.controller.TeacherController;
import com.example.web.repository.CourseBlockAssignmentRepository;
import com.example.web.repository.RoomRepository;
import com.example.web.repository.TeacherRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.servlet.MockMvc;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertNotEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verifies the role-based authorization rules in {@link SecurityConfig} against
 * a representative write-protected resource (TeacherController): reads need any
 * role, writes need WRITER/ADMIN, /api/admin/** needs ADMIN, and anonymous
 * requests are rejected with 401.
 */
@RunWith(SpringRunner.class)
@WebMvcTest(TeacherController.class)
@Import(SecurityConfig.class)
@TestPropertySource(properties = "app.jwt.secret=test-secret-key-that-is-at-least-32-bytes-long")
public class WebSecurityAuthorizationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private TeacherRepository teacherRepository;

    // Required by TeacherController's constructor (applyTeacherRequiredRoom backfill).
    @MockBean
    private CourseBlockAssignmentRepository assignmentRepository;

    @MockBean
    private RoomRepository roomRepository;

    // Required by the AuthenticationManager bean declared in SecurityConfig.
    @MockBean
    private AppUserDetailsService appUserDetailsService;

    private String validTeacherJson() throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("id", "T1");
        body.put("name", "Ada");
        body.put("lastName", "Lovelace");
        body.put("maxHoursPerWeek", 40);
        return objectMapper.writeValueAsString(body);
    }

    @Test
    public void anonymousGet_isUnauthorized() throws Exception {
        mockMvc.perform(get("/api/teachers"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "READER")
    public void reader_canRead() throws Exception {
        when(teacherRepository.findAll()).thenReturn(java.util.List.of());
        mockMvc.perform(get("/api/teachers"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "READER")
    public void reader_cannotWrite() throws Exception {
        mockMvc.perform(post("/api/teachers").contentType(MediaType.APPLICATION_JSON).content(validTeacherJson()))
                .andExpect(status().isForbidden());
        verify(teacherRepository, never()).save(any());
    }

    @Test
    @WithMockUser(roles = "WRITER")
    public void writer_canRead() throws Exception {
        when(teacherRepository.findAll()).thenReturn(java.util.List.of());
        mockMvc.perform(get("/api/teachers"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "WRITER")
    public void writer_canWrite() throws Exception {
        when(teacherRepository.existsById("T1")).thenReturn(false);
        when(teacherRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        mockMvc.perform(post("/api/teachers").contentType(MediaType.APPLICATION_JSON).content(validTeacherJson()))
                .andExpect(status().isOk());
        verify(teacherRepository).save(any());
    }

    @Test
    @WithMockUser(roles = "WRITER")
    public void writer_cannotAccessAdminRoutes() throws Exception {
        mockMvc.perform(get("/api/admin/users"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "READER")
    public void reader_cannotAccessAdminRoutes() throws Exception {
        mockMvc.perform(get("/api/admin/users"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "TEACHER")
    public void teacher_cannotReadGeneralDomainData() throws Exception {
        // TEACHER is scoped to its own schedule/identity/term only (see the
        // dedicated matchers in SecurityConfig for those specific paths) - it must
        // NOT be swept into the general "any authenticated role can GET /api/**"
        // rule that READER/WRITER/ADMIN share.
        mockMvc.perform(get("/api/teachers"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    public void admin_passesAdminRouteAuthorization() throws Exception {
        // No /api/admin handler exists in this slice, so once ADMIN clears the
        // authorization rule the request fails later (missing handler). The point
        // is that it is neither 401 (unauthenticated) nor 403 (forbidden).
        int statusCode = mockMvc.perform(get("/api/admin/users"))
                .andReturn().getResponse().getStatus();
        assertNotEquals(401, statusCode);
        assertNotEquals(403, statusCode);
    }
}
