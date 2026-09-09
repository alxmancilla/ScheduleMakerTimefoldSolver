package com.example.web.controller;

import com.example.web.entity.CourseBlockAssignmentEntity;
import com.example.web.entity.RoomEntity;
import com.example.web.entity.TeacherEntity;
import com.example.web.repository.CourseBlockAssignmentRepository;
import com.example.web.repository.RoomRepository;
import com.example.web.repository.TeacherRepository;
import com.example.web.service.AssignmentExcelService;
import com.example.web.service.AssignmentMoveValidationService;
import com.example.web.service.GroupCourseDefaultTeacherSyncService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.servlet.MockMvc;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Web-layer tests for {@link CourseBlockAssignmentController}, covering DTO
 * validation and the error responses produced by
 * {@code GlobalExceptionHandler}.
 * Uses the MVC slice with a mocked repository so no database is required.
 */
@RunWith(SpringRunner.class)
@WebMvcTest(CourseBlockAssignmentController.class)
@AutoConfigureMockMvc(addFilters = false)
public class CourseBlockAssignmentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private CourseBlockAssignmentRepository assignmentRepository;

    @MockBean
    private TeacherRepository teacherRepository;

    @MockBean
    private RoomRepository roomRepository;

    @MockBean
    private AssignmentExcelService assignmentExcelService;

    @MockBean
    private GroupCourseDefaultTeacherSyncService groupCourseDefaultTeacherSyncService;

    @MockBean
    private AssignmentMoveValidationService assignmentMoveValidationService;

    private CourseBlockAssignmentEntity assignment;

    @Before
    public void setUp() {
        assignment = new CourseBlockAssignmentEntity();
        assignment.setId("A1");
        assignment.setGroupId("G1");
        assignment.setCourseId("C1");
        assignment.setBlockLength(2);
    }

    private Map<String, Object> validPayload() {
        Map<String, Object> body = new HashMap<>();
        body.put("id", "A1");
        body.put("groupId", "G1");
        body.put("courseId", "C1");
        body.put("blockLength", 2);
        return body;
    }

    private String json(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }

    // ---- GET ----

    @Test
    public void getAllAssignments_returnsList() throws Exception {
        when(assignmentRepository.findAll()).thenReturn(List.of(assignment));
        mockMvc.perform(get("/api/assignments"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value("A1"));
    }

    @Test
    public void getAssignmentById_found_returnsAssignment() throws Exception {
        when(assignmentRepository.findById("A1")).thenReturn(Optional.of(assignment));
        mockMvc.perform(get("/api/assignments/A1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.courseId").value("C1"));
    }

    @Test
    public void getAssignmentById_notFound_returns404() throws Exception {
        when(assignmentRepository.findById("nope")).thenReturn(Optional.empty());
        mockMvc.perform(get("/api/assignments/nope"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").value("Assignment with ID 'nope' not found"));
    }

    // ---- POST (create) ----

    @Test
    @WithMockUser(username = "scheduler_test", roles = "SCHEDULER")
    public void createAssignment_valid_returnsSaved() throws Exception {
        when(assignmentRepository.existsById("A1")).thenReturn(false);
        when(assignmentRepository.save(any(CourseBlockAssignmentEntity.class))).thenAnswer(inv -> inv.getArgument(0));
        mockMvc.perform(post("/api/assignments").contentType(MediaType.APPLICATION_JSON).content(json(validPayload())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("A1"));
        verify(assignmentRepository).save(any(CourseBlockAssignmentEntity.class));
    }

    @Test
    public void createAssignment_duplicateId_returns400() throws Exception {
        when(assignmentRepository.existsById("A1")).thenReturn(true);
        mockMvc.perform(post("/api/assignments").contentType(MediaType.APPLICATION_JSON).content(json(validPayload())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("already exists")));
        verify(assignmentRepository, never()).save(any(CourseBlockAssignmentEntity.class));
    }

    @Test
    public void createAssignment_blankGroupId_returnsValidationError() throws Exception {
        Map<String, Object> body = validPayload();
        body.put("groupId", "");
        mockMvc.perform(post("/api/assignments").contentType(MediaType.APPLICATION_JSON).content(json(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Validation failed"))
                .andExpect(jsonPath("$.errors.groupId").exists());
    }

    @Test
    public void createAssignment_missingBlockLength_returnsValidationError() throws Exception {
        Map<String, Object> body = validPayload();
        body.remove("blockLength");
        mockMvc.perform(post("/api/assignments").contentType(MediaType.APPLICATION_JSON).content(json(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.blockLength").exists());
    }

    @Test
    public void createAssignment_invalidIdPattern_returnsValidationError() throws Exception {
        Map<String, Object> body = validPayload();
        body.put("id", "bad id!");
        mockMvc.perform(post("/api/assignments").contentType(MediaType.APPLICATION_JSON).content(json(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.id").exists());
    }

    @Test
    public void createAssignment_blockLengthOutOfRange_returnsValidationError() throws Exception {
        Map<String, Object> body = validPayload();
        body.put("blockLength", 5);
        mockMvc.perform(post("/api/assignments").contentType(MediaType.APPLICATION_JSON).content(json(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.blockLength").exists());
    }

    @Test
    @WithMockUser(username = "scheduler_test", roles = "SCHEDULER")
    public void createAssignment_pinnedWithoutRoom_returns400() throws Exception {
        Map<String, Object> body = validPayload();
        body.put("pinned", true);
        mockMvc.perform(post("/api/assignments").contentType(MediaType.APPLICATION_JSON).content(json(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("without a room")));
        verify(assignmentRepository, never()).save(any(CourseBlockAssignmentEntity.class));
    }

    @Test
    @WithMockUser(username = "scheduler_test", roles = "SCHEDULER")
    public void createAssignment_pinnedWithRoom_succeeds() throws Exception {
        when(assignmentRepository.existsById("A1")).thenReturn(false);
        when(assignmentRepository.save(any(CourseBlockAssignmentEntity.class))).thenAnswer(inv -> inv.getArgument(0));
        Map<String, Object> body = validPayload();
        body.put("pinned", true);
        body.put("roomName", "AULA 1");
        mockMvc.perform(post("/api/assignments").contentType(MediaType.APPLICATION_JSON).content(json(body)))
                .andExpect(status().isOk());
        verify(assignmentRepository).save(any(CourseBlockAssignmentEntity.class));
    }

    @Test
    @WithMockUser(username = "scheduler_test", roles = "SCHEDULER")
    public void createAssignment_pinned_stampsUserProvenance() throws Exception {
        when(assignmentRepository.existsById("A1")).thenReturn(false);
        when(assignmentRepository.save(any(CourseBlockAssignmentEntity.class))).thenAnswer(inv -> inv.getArgument(0));
        Map<String, Object> body = validPayload();
        body.put("pinned", true);
        body.put("roomName", "AULA 1");
        mockMvc.perform(post("/api/assignments").contentType(MediaType.APPLICATION_JSON).content(json(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pinSource").value("USER"))
                .andExpect(jsonPath("$.pinnedBy").value("scheduler_test"))
                .andExpect(jsonPath("$.pinnedAt").exists());
    }

    @Test
    @WithMockUser(username = "scheduler_test", roles = "SCHEDULER")
    public void createAssignment_teacherHasCompatibleRequiredRoom_overridesSubmittedRoom() throws Exception {
        when(assignmentRepository.existsById("A1")).thenReturn(false);
        when(assignmentRepository.save(any(CourseBlockAssignmentEntity.class))).thenAnswer(inv -> inv.getArgument(0));
        TeacherEntity teacher = new TeacherEntity("T1", "Ada", "Lovelace", 40);
        teacher.setRequiredRoomName("ROOM1");
        when(teacherRepository.findById("T1")).thenReturn(Optional.of(teacher));
        when(roomRepository.findById("ROOM1")).thenReturn(Optional.of(new RoomEntity("ROOM1", "Building A", "estándar")));

        Map<String, Object> body = validPayload();
        body.put("teacherId", "T1");
        body.put("roomName", "AULA-CHOSEN-BY-GROUP-PREFERENCE");
        body.put("satisfiesRoomType", "estándar");
        mockMvc.perform(post("/api/assignments").contentType(MediaType.APPLICATION_JSON).content(json(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roomName").value("ROOM1"));
    }

    @Test
    @WithMockUser(username = "scheduler_test", roles = "SCHEDULER")
    public void createAssignment_teacherRequiredRoomIncompatibleType_keepsSubmittedRoom() throws Exception {
        when(assignmentRepository.existsById("A1")).thenReturn(false);
        when(assignmentRepository.save(any(CourseBlockAssignmentEntity.class))).thenAnswer(inv -> inv.getArgument(0));
        TeacherEntity teacher = new TeacherEntity("T1", "Ada", "Lovelace", 40);
        teacher.setRequiredRoomName("ROOM1");
        when(teacherRepository.findById("T1")).thenReturn(Optional.of(teacher));
        when(roomRepository.findById("ROOM1")).thenReturn(Optional.of(new RoomEntity("ROOM1", "Building A", "estándar")));

        // A mixto-required block can't be forced into a plain estándar room.
        Map<String, Object> body = validPayload();
        body.put("teacherId", "T1");
        body.put("roomName", "LAB1");
        body.put("satisfiesRoomType", "mixto");
        mockMvc.perform(post("/api/assignments").contentType(MediaType.APPLICATION_JSON).content(json(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roomName").value("LAB1"));
    }

    @Test
    @WithMockUser(username = "scheduler_test", roles = "SCHEDULER")
    public void createAssignment_teacherWithoutRequiredRoom_keepsSubmittedRoom() throws Exception {
        when(assignmentRepository.existsById("A1")).thenReturn(false);
        when(assignmentRepository.save(any(CourseBlockAssignmentEntity.class))).thenAnswer(inv -> inv.getArgument(0));
        TeacherEntity teacher = new TeacherEntity("T1", "Ada", "Lovelace", 40);
        when(teacherRepository.findById("T1")).thenReturn(Optional.of(teacher));

        Map<String, Object> body = validPayload();
        body.put("teacherId", "T1");
        body.put("roomName", "AULA1");
        mockMvc.perform(post("/api/assignments").contentType(MediaType.APPLICATION_JSON).content(json(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roomName").value("AULA1"));
    }

    @Test
    @WithMockUser(username = "scheduler_test", roles = "SCHEDULER")
    public void createAssignment_withTeacher_syncsGroupCourseDefaultTeacher() throws Exception {
        when(assignmentRepository.existsById("A1")).thenReturn(false);
        when(assignmentRepository.save(any(CourseBlockAssignmentEntity.class))).thenAnswer(inv -> inv.getArgument(0));
        TeacherEntity teacher = new TeacherEntity("T1", "Ada", "Lovelace", 40);
        when(teacherRepository.findById("T1")).thenReturn(Optional.of(teacher));

        Map<String, Object> body = validPayload();
        body.put("teacherId", "T1");
        mockMvc.perform(post("/api/assignments").contentType(MediaType.APPLICATION_JSON).content(json(body)))
                .andExpect(status().isOk());

        verify(groupCourseDefaultTeacherSyncService).sync("G1", "C1", "T1");
    }

    @Test
    @WithMockUser(username = "scheduler_test", roles = "SCHEDULER")
    public void createAssignment_withoutTeacher_stillSyncsWithNullTeacher() throws Exception {
        when(assignmentRepository.existsById("A1")).thenReturn(false);
        when(assignmentRepository.save(any(CourseBlockAssignmentEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        mockMvc.perform(post("/api/assignments").contentType(MediaType.APPLICATION_JSON).content(json(validPayload())))
                .andExpect(status().isOk());

        // The sync service itself is responsible for no-op'ing on a null teacherId
        // (see GroupCourseDefaultTeacherSyncServiceTest) - the controller always
        // calls through unconditionally.
        verify(groupCourseDefaultTeacherSyncService).sync("G1", "C1", null);
    }

    // ---- PUT (update) ----

    @Test
    @WithMockUser(username = "scheduler_test", roles = "SCHEDULER")
    public void updateAssignment_valid_returnsUpdated() throws Exception {
        when(assignmentRepository.findById("A1")).thenReturn(Optional.of(assignment));
        when(assignmentRepository.save(any(CourseBlockAssignmentEntity.class))).thenAnswer(inv -> inv.getArgument(0));
        Map<String, Object> body = validPayload();
        body.remove("id");
        body.put("blockLength", 3);
        mockMvc.perform(put("/api/assignments/A1").contentType(MediaType.APPLICATION_JSON).content(json(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.blockLength").value(3));
        verify(assignmentRepository).save(any(CourseBlockAssignmentEntity.class));
    }

    @Test
    @WithMockUser(username = "scheduler_test", roles = "SCHEDULER")
    public void updateAssignment_withTeacher_syncsGroupCourseDefaultTeacher() throws Exception {
        when(assignmentRepository.findById("A1")).thenReturn(Optional.of(assignment));
        when(assignmentRepository.save(any(CourseBlockAssignmentEntity.class))).thenAnswer(inv -> inv.getArgument(0));
        TeacherEntity teacher = new TeacherEntity("T1", "Ada", "Lovelace", 40);
        when(teacherRepository.findById("T1")).thenReturn(Optional.of(teacher));

        Map<String, Object> body = validPayload();
        body.remove("id");
        body.put("teacherId", "T1");
        mockMvc.perform(put("/api/assignments/A1").contentType(MediaType.APPLICATION_JSON).content(json(body)))
                .andExpect(status().isOk());

        verify(groupCourseDefaultTeacherSyncService).sync("G1", "C1", "T1");
    }

    @Test
    @WithMockUser(username = "scheduler_test", roles = "SCHEDULER")
    public void updateAssignment_pinningPreviouslyUnpinned_stampsUserProvenance() throws Exception {
        // fixture `assignment` starts unpinned (setUp() never sets pinned/pinnedAt/etc).
        when(assignmentRepository.findById("A1")).thenReturn(Optional.of(assignment));
        when(assignmentRepository.save(any(CourseBlockAssignmentEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        Map<String, Object> body = validPayload();
        body.remove("id");
        body.put("pinned", true);
        body.put("roomName", "AULA 1");
        mockMvc.perform(put("/api/assignments/A1").contentType(MediaType.APPLICATION_JSON).content(json(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pinSource").value("USER"))
                .andExpect(jsonPath("$.pinnedBy").value("scheduler_test"))
                .andExpect(jsonPath("$.pinnedAt").exists());
    }

    @Test
    @WithMockUser(username = "scheduler_test", roles = "SCHEDULER")
    public void updateAssignment_unpinningPreviouslyPinned_clearsProvenance() throws Exception {
        assignment.setPinned(true);
        assignment.setRoomName("AULA 1");
        assignment.setPinnedAt(java.time.LocalDateTime.of(2026, 1, 1, 9, 0));
        assignment.setPinnedBy("someone_else");
        assignment.setPinSource("USER");
        when(assignmentRepository.findById("A1")).thenReturn(Optional.of(assignment));
        when(assignmentRepository.save(any(CourseBlockAssignmentEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        Map<String, Object> body = validPayload();
        body.remove("id");
        body.put("pinned", false);
        mockMvc.perform(put("/api/assignments/A1").contentType(MediaType.APPLICATION_JSON).content(json(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pinSource").doesNotExist())
                .andExpect(jsonPath("$.pinnedBy").doesNotExist())
                .andExpect(jsonPath("$.pinnedAt").doesNotExist())
                // The timeslot goes with the provenance: block_timeslot_id is only
                // meaningful for a pinned row, so leaving it behind on unpin left
                // dead data (found in production on 1A-ARH_1001_0 - see
                // stampPinProvenance).
                .andExpect(jsonPath("$.blockTimeslotId").doesNotExist());
    }

    @Test
    @WithMockUser(username = "scheduler_test", roles = "SCHEDULER")
    public void updateAssignment_editingAlreadyPinnedAssignment_leavesProvenanceUntouched() throws Exception {
        // Changing an unrelated field (blockLength) on a row that's already
        // pinned must not reset "when was this pinned" - only a genuine
        // false->true transition should.
        java.time.LocalDateTime originalPinnedAt = java.time.LocalDateTime.of(2026, 1, 1, 9, 0);
        assignment.setPinned(true);
        assignment.setRoomName("AULA 1");
        assignment.setPinnedAt(originalPinnedAt);
        assignment.setPinnedBy("original_pinner");
        assignment.setPinSource("USER");
        when(assignmentRepository.findById("A1")).thenReturn(Optional.of(assignment));
        when(assignmentRepository.save(any(CourseBlockAssignmentEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        Map<String, Object> body = validPayload();
        body.remove("id");
        body.put("blockLength", 3);
        body.put("pinned", true);
        body.put("roomName", "AULA 1");
        mockMvc.perform(put("/api/assignments/A1").contentType(MediaType.APPLICATION_JSON).content(json(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pinnedBy").value("original_pinner"))
                .andExpect(jsonPath("$.pinSource").value("USER"));
        // pinnedAt itself is asserted via the entity, not JSON, to avoid depending
        // on the exact serialized datetime shape.
        org.junit.Assert.assertEquals(originalPinnedAt, assignment.getPinnedAt());
    }

    @Test
    public void updateAssignment_notFound_returns404() throws Exception {
        when(assignmentRepository.findById("nope")).thenReturn(Optional.empty());
        Map<String, Object> body = validPayload();
        body.remove("id");
        mockMvc.perform(put("/api/assignments/nope").contentType(MediaType.APPLICATION_JSON).content(json(body)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
        verify(assignmentRepository, never()).save(any(CourseBlockAssignmentEntity.class));
    }

    @Test
    public void updateAssignment_blankCourseId_returnsValidationError() throws Exception {
        Map<String, Object> body = validPayload();
        body.remove("id");
        body.put("courseId", "");
        mockMvc.perform(put("/api/assignments/A1").contentType(MediaType.APPLICATION_JSON).content(json(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.courseId").exists());
    }

    // ---- DELETE ----

    @Test
    public void deleteAssignment_existing_returns204() throws Exception {
        when(assignmentRepository.findById("A1")).thenReturn(Optional.of(assignment));
        mockMvc.perform(delete("/api/assignments/A1"))
                .andExpect(status().isNoContent());
        verify(assignmentRepository).delete(assignment);
    }

    @Test
    public void deleteAssignment_notFound_returns404() throws Exception {
        when(assignmentRepository.findById("nope")).thenReturn(Optional.empty());
        mockMvc.perform(delete("/api/assignments/nope"))
                .andExpect(status().isNotFound());
        verify(assignmentRepository, never()).delete(any(CourseBlockAssignmentEntity.class));
    }

    // ---- PUT /{id}/move ----

    @Test
    @WithMockUser(username = "scheduler_test", roles = "SCHEDULER")
    public void moveAssignment_noViolations_savesOnlyBlockTimeslotIdAndPinned() throws Exception {
        when(assignmentMoveValidationService.validate("A1", "TS1", true))
                .thenReturn(new com.example.web.dto.AssignmentMoveValidationResponse(List.of(), List.of()));
        when(assignmentRepository.findById("A1")).thenReturn(Optional.of(assignment));
        when(assignmentRepository.save(any(CourseBlockAssignmentEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        mockMvc.perform(put("/api/assignments/A1/move")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("blockTimeslotId", "TS1", "pinned", true))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.blockTimeslotId").value("TS1"))
                .andExpect(jsonPath("$.pinned").value(true))
                // Untouched fields from setUp() survive - this endpoint never
                // touches group/course/room/teacher.
                .andExpect(jsonPath("$.groupId").value("G1"))
                .andExpect(jsonPath("$.courseId").value("C1"));
    }

    @Test
    @WithMockUser(username = "scheduler_test", roles = "SCHEDULER")
    public void moveAssignment_pinningPreviouslyUnpinned_stampsUserProvenance() throws Exception {
        when(assignmentMoveValidationService.validate("A1", "TS1", true))
                .thenReturn(new com.example.web.dto.AssignmentMoveValidationResponse(List.of(), List.of()));
        when(assignmentRepository.findById("A1")).thenReturn(Optional.of(assignment));
        when(assignmentRepository.save(any(CourseBlockAssignmentEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        mockMvc.perform(put("/api/assignments/A1/move")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("blockTimeslotId", "TS1", "pinned", true))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pinSource").value("USER"))
                .andExpect(jsonPath("$.pinnedBy").value("scheduler_test"))
                .andExpect(jsonPath("$.pinnedAt").exists());
    }

    @Test
    public void moveAssignment_serverSideViolation_returns400AndDoesNotSave() throws Exception {
        // The frontend already blocks Save on a violation, but the server
        // re-validates independently - e.g. a constraint that was SOFT when
        // the client last checked could have been switched back to HARD
        // since.
        when(assignmentMoveValidationService.validate("A1", "TS1", false))
                .thenReturn(new com.example.web.dto.AssignmentMoveValidationResponse(
                        List.of("Teacher double-booking with A2 at that time"), List.of()));

        mockMvc.perform(put("/api/assignments/A1/move")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("blockTimeslotId", "TS1", "pinned", false))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("Teacher double-booking")));

        verify(assignmentRepository, never()).save(any(CourseBlockAssignmentEntity.class));
    }

    @Test
    public void moveAssignment_notFound_returns404() throws Exception {
        when(assignmentMoveValidationService.validate("nope", "TS1", false))
                .thenReturn(new com.example.web.dto.AssignmentMoveValidationResponse(List.of(), List.of()));
        when(assignmentRepository.findById("nope")).thenReturn(Optional.empty());

        mockMvc.perform(put("/api/assignments/nope/move")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("blockTimeslotId", "TS1", "pinned", false))))
                .andExpect(status().isNotFound());
    }
}
