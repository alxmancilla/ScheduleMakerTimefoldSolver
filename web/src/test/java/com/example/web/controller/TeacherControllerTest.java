package com.example.web.controller;

import com.example.web.entity.TeacherEntity;
import com.example.web.repository.TeacherRepository;
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
 * Web-layer tests for {@link TeacherController}, covering DTO validation and
 * the
 * error responses produced by {@code GlobalExceptionHandler}. Uses the MVC
 * slice
 * with a mocked repository so no database is required.
 */
@RunWith(SpringRunner.class)
@WebMvcTest(TeacherController.class)
@AutoConfigureMockMvc(addFilters = false)
public class TeacherControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private TeacherRepository teacherRepository;

    private TeacherEntity teacher;

    @Before
    public void setUp() {
        teacher = new TeacherEntity("T1", "Ada", "Lovelace", 40);
    }

    private Map<String, Object> validPayload() {
        Map<String, Object> body = new HashMap<>();
        body.put("id", "T1");
        body.put("name", "Ada");
        body.put("lastName", "Lovelace");
        body.put("maxHoursPerWeek", 40);
        return body;
    }

    private String json(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }

    // ---- GET ----

    @Test
    public void getAllTeachers_returnsList() throws Exception {
        when(teacherRepository.findAll()).thenReturn(List.of(teacher));
        mockMvc.perform(get("/api/teachers"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value("T1"))
                .andExpect(jsonPath("$[0].name").value("Ada"));
    }

    @Test
    public void getTeacherById_found_returnsTeacher() throws Exception {
        when(teacherRepository.findById("T1")).thenReturn(Optional.of(teacher));
        mockMvc.perform(get("/api/teachers/T1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("T1"))
                .andExpect(jsonPath("$.lastName").value("Lovelace"));
    }

    @Test
    public void getTeacherById_notFound_returns404() throws Exception {
        when(teacherRepository.findById("nope")).thenReturn(Optional.empty());
        mockMvc.perform(get("/api/teachers/nope"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").value("Teacher with ID 'nope' not found"));
    }

    @Test
    public void searchTeachers_returnsMatches() throws Exception {
        when(teacherRepository.findByNameContainingIgnoreCaseOrLastNameContainingIgnoreCase("ada", "ada"))
                .thenReturn(List.of(teacher));
        mockMvc.perform(get("/api/teachers/search").param("query", "ada"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("T1"));
    }

    // ---- POST (create) ----

    @Test
    public void createTeacher_valid_returnsSaved() throws Exception {
        when(teacherRepository.existsById("T1")).thenReturn(false);
        when(teacherRepository.save(any(TeacherEntity.class))).thenAnswer(inv -> inv.getArgument(0));
        mockMvc.perform(post("/api/teachers").contentType(MediaType.APPLICATION_JSON).content(json(validPayload())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("T1"))
                .andExpect(jsonPath("$.name").value("Ada"));
        verify(teacherRepository).save(any(TeacherEntity.class));
    }

    @Test
    public void createTeacher_duplicateId_returns400() throws Exception {
        when(teacherRepository.existsById("T1")).thenReturn(true);
        mockMvc.perform(post("/api/teachers").contentType(MediaType.APPLICATION_JSON).content(json(validPayload())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("already exists")));
        verify(teacherRepository, never()).save(any(TeacherEntity.class));
    }

    @Test
    public void createTeacher_blankName_returnsValidationError() throws Exception {
        Map<String, Object> body = validPayload();
        body.put("name", "");
        mockMvc.perform(post("/api/teachers").contentType(MediaType.APPLICATION_JSON).content(json(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Validation failed"))
                .andExpect(jsonPath("$.errors.name").exists());
    }

    @Test
    public void createTeacher_missingId_returnsValidationError() throws Exception {
        Map<String, Object> body = validPayload();
        body.remove("id");
        mockMvc.perform(post("/api/teachers").contentType(MediaType.APPLICATION_JSON).content(json(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.id").exists());
    }

    @Test
    public void createTeacher_invalidIdPattern_returnsValidationError() throws Exception {
        Map<String, Object> body = validPayload();
        body.put("id", "bad id!");
        mockMvc.perform(post("/api/teachers").contentType(MediaType.APPLICATION_JSON).content(json(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.id").exists());
    }

    @Test
    public void createTeacher_hoursOutOfRange_returnsValidationError() throws Exception {
        Map<String, Object> body = validPayload();
        body.put("maxHoursPerWeek", 100);
        mockMvc.perform(post("/api/teachers").contentType(MediaType.APPLICATION_JSON).content(json(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.maxHoursPerWeek").exists());
    }

    @Test
    public void createTeacher_invalidNestedAvailability_returnsValidationError() throws Exception {
        Map<String, Object> body = validPayload();
        body.put("availability", List.of(Map.of("dayOfWeek", 9, "hour", 8)));
        mockMvc.perform(post("/api/teachers").contentType(MediaType.APPLICATION_JSON).content(json(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Validation failed"));
    }

    // ---- PUT (update) ----

    @Test
    public void updateTeacher_valid_returnsUpdated() throws Exception {
        when(teacherRepository.findById("T1")).thenReturn(Optional.of(teacher));
        when(teacherRepository.save(any(TeacherEntity.class))).thenAnswer(inv -> inv.getArgument(0));
        Map<String, Object> body = validPayload();
        body.remove("id");
        body.put("name", "Grace");
        mockMvc.perform(put("/api/teachers/T1").contentType(MediaType.APPLICATION_JSON).content(json(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("T1"))
                .andExpect(jsonPath("$.name").value("Grace"));
        verify(teacherRepository).save(any(TeacherEntity.class));
    }

    @Test
    public void updateTeacher_notFound_returns404() throws Exception {
        when(teacherRepository.findById("nope")).thenReturn(Optional.empty());
        Map<String, Object> body = validPayload();
        body.remove("id");
        mockMvc.perform(put("/api/teachers/nope").contentType(MediaType.APPLICATION_JSON).content(json(body)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
        verify(teacherRepository, never()).save(any(TeacherEntity.class));
    }

    @Test
    public void updateTeacher_blankName_returnsValidationError() throws Exception {
        Map<String, Object> body = validPayload();
        body.remove("id");
        body.put("name", "");
        mockMvc.perform(put("/api/teachers/T1").contentType(MediaType.APPLICATION_JSON).content(json(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.name").exists());
    }

    // ---- DELETE ----

    @Test
    public void deleteTeacher_existing_returns204() throws Exception {
        when(teacherRepository.findById("T1")).thenReturn(Optional.of(teacher));
        mockMvc.perform(delete("/api/teachers/T1"))
                .andExpect(status().isNoContent());
        verify(teacherRepository).delete(teacher);
    }

    @Test
    public void deleteTeacher_notFound_returns404() throws Exception {
        when(teacherRepository.findById("nope")).thenReturn(Optional.empty());
        mockMvc.perform(delete("/api/teachers/nope"))
                .andExpect(status().isNotFound());
        verify(teacherRepository, never()).delete(any(TeacherEntity.class));
    }
}
