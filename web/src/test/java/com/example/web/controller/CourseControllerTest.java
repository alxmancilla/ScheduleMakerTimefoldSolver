package com.example.web.controller;

import com.example.web.entity.CourseEntity;
import com.example.web.repository.CourseRepository;
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
 * Web-layer tests for {@link CourseController}, covering DTO validation and the
 * error responses produced by {@code GlobalExceptionHandler}. Uses the MVC
 * slice
 * with a mocked repository so no database is required.
 */
@RunWith(SpringRunner.class)
@WebMvcTest(CourseController.class)
@AutoConfigureMockMvc(addFilters = false)
public class CourseControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private CourseRepository courseRepository;

    private CourseEntity course;

    @Before
    public void setUp() {
        course = new CourseEntity("C1", "Mathematics", "estandar", 5);
    }

    private Map<String, Object> validPayload() {
        Map<String, Object> body = new HashMap<>();
        body.put("id", "C1");
        body.put("name", "Mathematics");
        body.put("requiredHoursPerWeek", 5);
        return body;
    }

    private String json(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }

    // ---- GET ----

    @Test
    public void getAllCourses_returnsList() throws Exception {
        when(courseRepository.findAll()).thenReturn(List.of(course));
        mockMvc.perform(get("/api/courses"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value("C1"));
    }

    @Test
    public void getCourseById_found_returnsCourse() throws Exception {
        when(courseRepository.findById("C1")).thenReturn(Optional.of(course));
        mockMvc.perform(get("/api/courses/C1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Mathematics"));
    }

    @Test
    public void getCourseById_notFound_returns404() throws Exception {
        when(courseRepository.findById("nope")).thenReturn(Optional.empty());
        mockMvc.perform(get("/api/courses/nope"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").value("Course with ID 'nope' not found"));
    }

    // ---- POST (create) ----

    @Test
    public void createCourse_valid_returnsSaved() throws Exception {
        when(courseRepository.existsById("C1")).thenReturn(false);
        when(courseRepository.save(any(CourseEntity.class))).thenAnswer(inv -> inv.getArgument(0));
        mockMvc.perform(post("/api/courses").contentType(MediaType.APPLICATION_JSON).content(json(validPayload())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("C1"));
        verify(courseRepository).save(any(CourseEntity.class));
    }

    @Test
    public void createCourse_duplicateId_returns400() throws Exception {
        when(courseRepository.existsById("C1")).thenReturn(true);
        mockMvc.perform(post("/api/courses").contentType(MediaType.APPLICATION_JSON).content(json(validPayload())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("already exists")));
        verify(courseRepository, never()).save(any(CourseEntity.class));
    }

    @Test
    public void createCourse_blankName_returnsValidationError() throws Exception {
        Map<String, Object> body = validPayload();
        body.put("name", "");
        mockMvc.perform(post("/api/courses").contentType(MediaType.APPLICATION_JSON).content(json(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Validation failed"))
                .andExpect(jsonPath("$.errors.name").exists());
    }

    @Test
    public void createCourse_missingHours_returnsValidationError() throws Exception {
        Map<String, Object> body = validPayload();
        body.remove("requiredHoursPerWeek");
        mockMvc.perform(post("/api/courses").contentType(MediaType.APPLICATION_JSON).content(json(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.requiredHoursPerWeek").exists());
    }

    @Test
    public void createCourse_invalidIdPattern_returnsValidationError() throws Exception {
        Map<String, Object> body = validPayload();
        body.put("id", "bad id!");
        mockMvc.perform(post("/api/courses").contentType(MediaType.APPLICATION_JSON).content(json(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.id").exists());
    }

    @Test
    public void createCourse_hoursOutOfRange_returnsValidationError() throws Exception {
        Map<String, Object> body = validPayload();
        body.put("requiredHoursPerWeek", 100);
        mockMvc.perform(post("/api/courses").contentType(MediaType.APPLICATION_JSON).content(json(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.requiredHoursPerWeek").exists());
    }

    // ---- PUT (update) ----

    @Test
    public void updateCourse_valid_returnsUpdated() throws Exception {
        when(courseRepository.findById("C1")).thenReturn(Optional.of(course));
        when(courseRepository.save(any(CourseEntity.class))).thenAnswer(inv -> inv.getArgument(0));
        Map<String, Object> body = validPayload();
        body.remove("id");
        body.put("name", "Physics");
        mockMvc.perform(put("/api/courses/C1").contentType(MediaType.APPLICATION_JSON).content(json(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Physics"));
        verify(courseRepository).save(any(CourseEntity.class));
    }

    @Test
    public void updateCourse_notFound_returns404() throws Exception {
        when(courseRepository.findById("nope")).thenReturn(Optional.empty());
        Map<String, Object> body = validPayload();
        body.remove("id");
        mockMvc.perform(put("/api/courses/nope").contentType(MediaType.APPLICATION_JSON).content(json(body)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
        verify(courseRepository, never()).save(any(CourseEntity.class));
    }

    @Test
    public void updateCourse_blankName_returnsValidationError() throws Exception {
        Map<String, Object> body = validPayload();
        body.remove("id");
        body.put("name", "");
        mockMvc.perform(put("/api/courses/C1").contentType(MediaType.APPLICATION_JSON).content(json(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.name").exists());
    }

    // ---- DELETE ----

    @Test
    public void deleteCourse_existing_returns204() throws Exception {
        when(courseRepository.findById("C1")).thenReturn(Optional.of(course));
        mockMvc.perform(delete("/api/courses/C1"))
                .andExpect(status().isNoContent());
        verify(courseRepository).delete(course);
    }

    @Test
    public void deleteCourse_notFound_returns404() throws Exception {
        when(courseRepository.findById("nope")).thenReturn(Optional.empty());
        mockMvc.perform(delete("/api/courses/nope"))
                .andExpect(status().isNotFound());
        verify(courseRepository, never()).delete(any(CourseEntity.class));
    }
}
