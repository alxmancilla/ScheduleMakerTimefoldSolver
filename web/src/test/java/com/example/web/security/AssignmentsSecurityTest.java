package com.example.web.security;

import com.example.web.controller.CourseBlockAssignmentController;
import com.example.web.repository.CourseBlockAssignmentRepository;
import com.example.web.repository.RoomRepository;
import com.example.web.repository.TeacherRepository;
import com.example.web.service.AssignmentExcelService;
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

import static org.junit.Assert.assertNotEquals;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verifies the ADMIN-only exception carved out for /api/assignments/** in
 * {@link SecurityConfig}: unlike the general domain-data rules (any role can
 * read, WRITER/ADMIN can write), course block assignments are ADMIN-only for
 * every method - READER and WRITER are excluded from reading them too, not
 * just from writing.
 */
@RunWith(SpringRunner.class)
@WebMvcTest(CourseBlockAssignmentController.class)
@Import(SecurityConfig.class)
@TestPropertySource(properties = "app.jwt.secret=test-secret-key-that-is-at-least-32-bytes-long")
public class AssignmentsSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private CourseBlockAssignmentRepository assignmentRepository;

    @MockBean
    private TeacherRepository teacherRepository;

    @MockBean
    private RoomRepository roomRepository;

    // Required by CourseBlockAssignmentController's export/import endpoints.
    @MockBean
    private AssignmentExcelService assignmentExcelService;

    // Required by the AuthenticationManager bean declared in SecurityConfig.
    @MockBean
    private AppUserDetailsService appUserDetailsService;

    @Test
    public void anonymousGet_isUnauthorized() throws Exception {
        mockMvc.perform(get("/api/assignments"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "READER")
    public void reader_cannotRead() throws Exception {
        // Unlike the general GET rule (any role can read), assignments carve
        // out an ADMIN-only exception for every method.
        mockMvc.perform(get("/api/assignments"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "WRITER")
    public void writer_cannotRead() throws Exception {
        mockMvc.perform(get("/api/assignments"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "WRITER")
    public void writer_cannotWrite() throws Exception {
        // Previously WRITER could POST here under the general write rule;
        // the assignments-specific ADMIN-only override now excludes it.
        mockMvc.perform(post("/api/assignments"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    public void admin_canRead() throws Exception {
        when(assignmentRepository.findAll()).thenReturn(java.util.List.of());
        mockMvc.perform(get("/api/assignments"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    public void admin_passesWriteAuthorization() throws Exception {
        // No request body is supplied, so this may still fail validation
        // downstream - the point is only that authorization itself passes
        // (neither 401 nor 403).
        int statusCode = mockMvc.perform(post("/api/assignments"))
                .andReturn().getResponse().getStatus();
        assertNotEquals(401, statusCode);
        assertNotEquals(403, statusCode);
    }
}
