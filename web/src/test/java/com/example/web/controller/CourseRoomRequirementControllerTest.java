package com.example.web.controller;

import com.example.web.entity.CourseRoomRequirementEntity;
import com.example.web.repository.CourseRepository;
import com.example.web.repository.CourseRoomRequirementRepository;
import com.example.web.repository.RoomRepository;
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
 * Web-layer tests for {@link CourseRoomRequirementController}: CRUD for a
 * course's dual room requirements, nested under /api/courses/{courseId}/room-requirements.
 * Uses the MVC slice with mocked repositories so no database is required.
 */
@RunWith(SpringRunner.class)
@WebMvcTest(CourseRoomRequirementController.class)
@AutoConfigureMockMvc(addFilters = false)
public class CourseRoomRequirementControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private CourseRoomRequirementRepository requirementRepository;

    @MockBean
    private CourseRepository courseRepository;

    @MockBean
    private RoomRepository roomRepository;

    private CourseRoomRequirementEntity requirement;

    @Before
    public void setUp() {
        requirement = new CourseRoomRequirementEntity("C1", "centro de cómputo", 4, 1, "CC 1");
        requirement.setId(10L);
        when(courseRepository.existsById("C1")).thenReturn(true);
    }

    private Map<String, Object> validPayload() {
        Map<String, Object> body = new HashMap<>();
        body.put("roomType", "centro de cómputo");
        body.put("hoursRequired", 4);
        body.put("priority", 1);
        body.put("defaultPreferredRoom", "CC 1");
        return body;
    }

    private String json(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }

    // ---- GET ----

    @Test
    public void getRequirements_courseExists_returnsList() throws Exception {
        when(requirementRepository.findByCourseIdOrderByPriority("C1")).thenReturn(List.of(requirement));
        mockMvc.perform(get("/api/courses/C1/room-requirements"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].roomType").value("centro de cómputo"));
    }

    @Test
    public void getRequirements_courseNotFound_returns404() throws Exception {
        when(courseRepository.existsById("nope")).thenReturn(false);
        mockMvc.perform(get("/api/courses/nope/room-requirements"))
                .andExpect(status().isNotFound());
    }

    // ---- POST (create) ----

    @Test
    public void createRequirement_valid_returnsSaved() throws Exception {
        when(roomRepository.existsById("CC 1")).thenReturn(true);
        when(requirementRepository.save(any(CourseRoomRequirementEntity.class))).thenAnswer(inv -> inv.getArgument(0));
        mockMvc.perform(post("/api/courses/C1/room-requirements")
                .contentType(MediaType.APPLICATION_JSON).content(json(validPayload())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roomType").value("centro de cómputo"))
                .andExpect(jsonPath("$.hoursRequired").value(4));
        verify(requirementRepository).save(any(CourseRoomRequirementEntity.class));
    }

    @Test
    public void createRequirement_courseNotFound_returns404() throws Exception {
        when(courseRepository.existsById("nope")).thenReturn(false);
        mockMvc.perform(post("/api/courses/nope/room-requirements")
                .contentType(MediaType.APPLICATION_JSON).content(json(validPayload())))
                .andExpect(status().isNotFound());
        verify(requirementRepository, never()).save(any(CourseRoomRequirementEntity.class));
    }

    @Test
    public void createRequirement_blankRoomType_returnsValidationError() throws Exception {
        Map<String, Object> body = validPayload();
        body.put("roomType", "");
        mockMvc.perform(post("/api/courses/C1/room-requirements")
                .contentType(MediaType.APPLICATION_JSON).content(json(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.roomType").exists());
    }

    @Test
    public void createRequirement_hoursZero_returnsValidationError() throws Exception {
        Map<String, Object> body = validPayload();
        body.put("hoursRequired", 0);
        mockMvc.perform(post("/api/courses/C1/room-requirements")
                .contentType(MediaType.APPLICATION_JSON).content(json(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.hoursRequired").exists());
    }

    @Test
    public void createRequirement_missingHours_returnsValidationError() throws Exception {
        Map<String, Object> body = validPayload();
        body.remove("hoursRequired");
        mockMvc.perform(post("/api/courses/C1/room-requirements")
                .contentType(MediaType.APPLICATION_JSON).content(json(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.hoursRequired").exists());
    }

    @Test
    public void createRequirement_unknownPreferredRoom_returns400() throws Exception {
        when(roomRepository.existsById("CC 1")).thenReturn(false);
        mockMvc.perform(post("/api/courses/C1/room-requirements")
                .contentType(MediaType.APPLICATION_JSON).content(json(validPayload())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("does not exist")));
        verify(requirementRepository, never()).save(any(CourseRoomRequirementEntity.class));
    }

    @Test
    public void createRequirement_noPreferredRoom_skipsRoomCheck() throws Exception {
        Map<String, Object> body = validPayload();
        body.remove("defaultPreferredRoom");
        when(requirementRepository.save(any(CourseRoomRequirementEntity.class))).thenAnswer(inv -> inv.getArgument(0));
        mockMvc.perform(post("/api/courses/C1/room-requirements")
                .contentType(MediaType.APPLICATION_JSON).content(json(body)))
                .andExpect(status().isOk());
        verify(roomRepository, never()).existsById(any());
    }

    // ---- PUT (update) ----

    @Test
    public void updateRequirement_valid_returnsUpdated() throws Exception {
        when(requirementRepository.findById(10L)).thenReturn(Optional.of(requirement));
        when(roomRepository.existsById("CC 1")).thenReturn(true);
        when(requirementRepository.save(any(CourseRoomRequirementEntity.class))).thenAnswer(inv -> inv.getArgument(0));
        Map<String, Object> body = validPayload();
        body.put("hoursRequired", 5);
        mockMvc.perform(put("/api/courses/C1/room-requirements/10")
                .contentType(MediaType.APPLICATION_JSON).content(json(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hoursRequired").value(5));
    }

    @Test
    public void updateRequirement_notFound_returns404() throws Exception {
        when(requirementRepository.findById(99L)).thenReturn(Optional.empty());
        mockMvc.perform(put("/api/courses/C1/room-requirements/99")
                .contentType(MediaType.APPLICATION_JSON).content(json(validPayload())))
                .andExpect(status().isNotFound());
        verify(requirementRepository, never()).save(any(CourseRoomRequirementEntity.class));
    }

    @Test
    public void updateRequirement_belongsToDifferentCourse_returns404() throws Exception {
        CourseRoomRequirementEntity otherCoursesRequirement = new CourseRoomRequirementEntity(
                "C2", "estándar", 2, 1, null);
        otherCoursesRequirement.setId(10L);
        when(requirementRepository.findById(10L)).thenReturn(Optional.of(otherCoursesRequirement));
        mockMvc.perform(put("/api/courses/C1/room-requirements/10")
                .contentType(MediaType.APPLICATION_JSON).content(json(validPayload())))
                .andExpect(status().isNotFound());
        verify(requirementRepository, never()).save(any(CourseRoomRequirementEntity.class));
    }

    // ---- DELETE ----

    @Test
    public void deleteRequirement_existing_returns204() throws Exception {
        when(requirementRepository.findById(10L)).thenReturn(Optional.of(requirement));
        mockMvc.perform(delete("/api/courses/C1/room-requirements/10"))
                .andExpect(status().isNoContent());
        verify(requirementRepository).delete(requirement);
    }

    @Test
    public void deleteRequirement_notFound_returns404() throws Exception {
        when(requirementRepository.findById(99L)).thenReturn(Optional.empty());
        mockMvc.perform(delete("/api/courses/C1/room-requirements/99"))
                .andExpect(status().isNotFound());
        verify(requirementRepository, never()).delete(any(CourseRoomRequirementEntity.class));
    }
}
