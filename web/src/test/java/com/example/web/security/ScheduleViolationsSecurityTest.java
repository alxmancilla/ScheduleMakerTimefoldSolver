package com.example.web.security;

import com.example.web.controller.ScheduleController;
import com.example.web.repository.*;
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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Confirms GET /api/schedule/violations is narrowed to SCHEDULER/ADMIN (added
 * 2026-09-07, per explicit request: persisted constraint violations should
 * not be visible to WRITER, READER, or TEACHER) - carved out of the general
 * read-everything rule in {@link SecurityConfig}, ahead of it. Every other
 * /api/schedule/** endpoint is untouched by this and keeps its normal
 * READER+ (or, for /view/me, TEACHER-inclusive) access - only this one path
 * narrows.
 */
@RunWith(SpringRunner.class)
@WebMvcTest(ScheduleController.class)
@Import(SecurityConfig.class)
@TestPropertySource(properties = "app.jwt.secret=test-secret-key-that-is-at-least-32-bytes-long")
public class ScheduleViolationsSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private CourseBlockAssignmentRepository assignmentRepository;
    @MockBean
    private CourseBlockAssignmentCurrentRepository assignmentCurrentRepository;
    @MockBean
    private ScheduleRunRepository scheduleRunRepository;
    @MockBean
    private ScheduleRunResultRepository scheduleRunResultRepository;
    @MockBean
    private ScheduleRunViolationRepository scheduleRunViolationRepository;
    @MockBean
    private ScheduleRunViolationAssignmentRepository scheduleRunViolationAssignmentRepository;
    @MockBean
    private BlockTimeslotRepository timeslotRepository;
    @MockBean
    private CourseRepository courseRepository;
    @MockBean
    private TeacherRepository teacherRepository;
    @MockBean
    private RoomRepository roomRepository;
    @MockBean
    private StudentGroupRepository groupRepository;
    @MockBean
    private AppUserRepository appUserRepository;

    // Required by the AuthenticationManager bean declared in SecurityConfig.
    @MockBean
    private AppUserDetailsService appUserDetailsService;

    @Test
    public void anonymous_isUnauthorized() throws Exception {
        mockMvc.perform(get("/api/schedule/violations"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "READER")
    public void reader_isForbidden() throws Exception {
        mockMvc.perform(get("/api/schedule/violations"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "WRITER")
    public void writer_isForbidden() throws Exception {
        mockMvc.perform(get("/api/schedule/violations"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "TEACHER")
    public void teacher_isForbidden() throws Exception {
        mockMvc.perform(get("/api/schedule/violations"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "SCHEDULER")
    public void scheduler_canAccess() throws Exception {
        mockMvc.perform(get("/api/schedule/violations"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    public void admin_canAccess() throws Exception {
        mockMvc.perform(get("/api/schedule/violations"))
                .andExpect(status().isOk());
    }
}
