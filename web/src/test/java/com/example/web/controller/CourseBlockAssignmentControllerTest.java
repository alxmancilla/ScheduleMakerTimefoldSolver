package com.example.web.controller;

import com.example.web.entity.CourseBlockAssignmentEntity;
import com.example.web.entity.RoomEntity;
import com.example.web.entity.TeacherEntity;
import com.example.web.repository.CourseBlockAssignmentRepository;
import com.example.web.repository.RoomRepository;
import com.example.web.repository.TeacherRepository;
import com.example.web.service.AssignmentExcelService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
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
    public void createAssignment_pinnedWithoutRoom_returns400() throws Exception {
        Map<String, Object> body = validPayload();
        body.put("pinned", true);
        mockMvc.perform(post("/api/assignments").contentType(MediaType.APPLICATION_JSON).content(json(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("without a room")));
        verify(assignmentRepository, never()).save(any(CourseBlockAssignmentEntity.class));
    }

    @Test
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

    // ---- PUT (update) ----

    @Test
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
}
