package com.example.web.security;

import com.example.web.controller.CourseBlockAssignmentController;
import com.example.web.dto.AssignmentMoveValidationResponse;
import com.example.web.entity.CourseBlockAssignmentEntity;
import com.example.web.repository.CourseBlockAssignmentRepository;
import com.example.web.repository.RoomRepository;
import com.example.web.repository.TeacherRepository;
import com.example.web.service.AssignmentExcelService;
import com.example.web.service.AssignmentMoveValidationService;
import com.example.web.service.GroupCourseDefaultTeacherSyncService;
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

import static org.junit.Assert.assertNotEquals;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verifies the SCHEDULER/ADMIN-only write exception carved out for
 * /api/assignments/** in {@link SecurityConfig}: reads are open the same as
 * everywhere else (READER/WRITER/SCHEDULER/ADMIN), but every write (the
 * general CRUD, move, and validate-move) requires SCHEDULER or ADMIN
 * specifically - WRITER does not get its usual write access here, unlike
 * every other domain-data resource (added 2026-09-07, when SCHEDULER was
 * introduced - WRITER had move/validate-move access before that).
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

    @MockBean
    private GroupCourseDefaultTeacherSyncService groupCourseDefaultTeacherSyncService;

    @MockBean
    private AssignmentMoveValidationService assignmentMoveValidationService;

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
    public void reader_canRead() throws Exception {
        when(assignmentRepository.findAll()).thenReturn(java.util.List.of());
        mockMvc.perform(get("/api/assignments"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "READER")
    public void reader_cannotWrite() throws Exception {
        mockMvc.perform(post("/api/assignments"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "WRITER")
    public void writer_canRead() throws Exception {
        when(assignmentRepository.findAll()).thenReturn(java.util.List.of());
        mockMvc.perform(get("/api/assignments"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "WRITER")
    public void writer_cannotWrite() throws Exception {
        // Unlike the general write rule (WRITER/SCHEDULER/ADMIN), the
        // assignments-specific SCHEDULER/ADMIN-only override excludes WRITER here.
        mockMvc.perform(post("/api/assignments"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "SCHEDULER")
    public void scheduler_canRead() throws Exception {
        when(assignmentRepository.findAll()).thenReturn(java.util.List.of());
        mockMvc.perform(get("/api/assignments"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "SCHEDULER")
    public void scheduler_passesWriteAuthorization() throws Exception {
        // No request body is supplied, so this may still fail validation
        // downstream - the point is only that authorization itself passes
        // (neither 401 nor 403).
        int statusCode = mockMvc.perform(post("/api/assignments"))
                .andReturn().getResponse().getStatus();
        assertNotEquals(401, statusCode);
        assertNotEquals(403, statusCode);
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

    // validate-move is a POST (it takes a request body), but computes a
    // result rather than writing anything - it exists only in service of the
    // move/pin editor's write, so it's scoped to the same SCHEDULER/ADMIN
    // audience as the write itself, not the broader READER/WRITER read
    // audience the GET rule above gets.
    @Test
    @WithMockUser(roles = "SCHEDULER")
    public void scheduler_canPostValidateMove() throws Exception {
        when(assignmentMoveValidationService.validate(anyString(), anyString(), anyBoolean()))
                .thenReturn(new AssignmentMoveValidationResponse(java.util.List.of(), java.util.List.of()));
        mockMvc.perform(post("/api/assignments/block_assignment_1/validate-move")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"blockTimeslotId\":\"block_1\",\"pinned\":false}"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "WRITER")
    public void writer_cannotPostValidateMove() throws Exception {
        // Removed 2026-09-07 when SCHEDULER was introduced - WRITER had this
        // access before that.
        mockMvc.perform(post("/api/assignments/block_assignment_1/validate-move")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"blockTimeslotId\":\"block_1\",\"pinned\":false}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "READER")
    public void reader_cannotPostValidateMove() throws Exception {
        mockMvc.perform(post("/api/assignments/block_assignment_1/validate-move")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"blockTimeslotId\":\"block_1\",\"pinned\":false}"))
                .andExpect(status().isForbidden());
    }

    // move only ever sets blockTimeslotId/pinned - narrower in scope than the
    // general full-DTO PUT above, but the same SCHEDULER/ADMIN audience as it.
    @Test
    @WithMockUser(roles = "SCHEDULER")
    public void scheduler_canPutMove() throws Exception {
        when(assignmentMoveValidationService.validate(anyString(), anyString(), anyBoolean()))
                .thenReturn(new AssignmentMoveValidationResponse(java.util.List.of(), java.util.List.of()));
        CourseBlockAssignmentEntity assignment = new CourseBlockAssignmentEntity();
        assignment.setId("block_assignment_1");
        when(assignmentRepository.findById("block_assignment_1")).thenReturn(java.util.Optional.of(assignment));
        when(assignmentRepository.save(org.mockito.ArgumentMatchers.any())).thenReturn(assignment);

        // pinned:true because this test is about ACCESS (200 vs 403), and since
        // 2026-09-08 a move that doesn't pin is rejected as incoherent (400) -
        // an unpinned block takes its position from the latest solver run, so
        // the new slot would be ignored. A 400 here would mask the authorization
        // result this test actually exists to assert.
        mockMvc.perform(put("/api/assignments/block_assignment_1/move")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"blockTimeslotId\":\"block_1\",\"pinned\":true}"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "WRITER")
    public void writer_cannotPutMove() throws Exception {
        // This is the reverse of the fix from 2026-09-06 (WRITER could open the
        // move/pin editor via canWrite() but Save 403'd because the general PUT
        // was ADMIN-only): now that SCHEDULER exists, WRITER's move access
        // (added by that fix) is intentionally removed again - schedule editing
        // has exactly one non-ADMIN owner (SCHEDULER), and Schedule.jsx's own
        // canEditSchedule() gate keeps WRITER from ever reaching this editor.
        mockMvc.perform(put("/api/assignments/block_assignment_1/move")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"blockTimeslotId\":\"block_1\",\"pinned\":false}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "READER")
    public void reader_cannotPutMove() throws Exception {
        mockMvc.perform(put("/api/assignments/block_assignment_1/move")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"blockTimeslotId\":\"block_1\",\"pinned\":false}"))
                .andExpect(status().isForbidden());
    }
}
