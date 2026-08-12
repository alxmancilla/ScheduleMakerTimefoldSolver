package com.example.web.controller;

import com.example.web.entity.BlockTimeslotEntity;
import com.example.web.repository.BlockTimeslotRepository;
import com.example.web.repository.CourseBlockAssignmentRepository;
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
 * Web-layer tests for {@link TimeslotController}, covering DTO validation and
 * the error responses produced by {@code GlobalExceptionHandler}. Uses the MVC
 * slice with mocked repositories so no database is required.
 */
@RunWith(SpringRunner.class)
@WebMvcTest(TimeslotController.class)
@AutoConfigureMockMvc(addFilters = false)
public class TimeslotControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private BlockTimeslotRepository timeslotRepository;

    @MockBean
    private CourseBlockAssignmentRepository assignmentRepository;

    private BlockTimeslotEntity timeslot;

    @Before
    public void setUp() {
        timeslot = new BlockTimeslotEntity(1, 7, 2);
        timeslot.setId("block_abc123");
    }

    private Map<String, Object> validPayload() {
        Map<String, Object> body = new HashMap<>();
        body.put("dayOfWeek", 1);
        body.put("startHour", 7);
        body.put("lengthHours", 2);
        return body;
    }

    private String json(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }

    // ---- GET ----

    @Test
    public void getAllTimeslots_returnsList() throws Exception {
        when(timeslotRepository.findAllByOrderByDayOfWeekAscStartHourAscLengthHoursAsc())
                .thenReturn(List.of(timeslot));
        mockMvc.perform(get("/api/admin/timeslots"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value("block_abc123"));
    }

    @Test
    public void getTimeslotById_notFound_returns404() throws Exception {
        when(timeslotRepository.findById("nope")).thenReturn(Optional.empty());
        mockMvc.perform(get("/api/admin/timeslots/nope"))
                .andExpect(status().isNotFound());
    }

    // ---- POST (create) ----

    @Test
    public void createTimeslot_valid_returnsSaved() throws Exception {
        when(timeslotRepository.existsByDayOfWeekAndStartHourAndLengthHours(1, 7, 2)).thenReturn(false);
        when(timeslotRepository.save(any(BlockTimeslotEntity.class))).thenAnswer(inv -> inv.getArgument(0));
        mockMvc.perform(post("/api/admin/timeslots").contentType(MediaType.APPLICATION_JSON).content(json(validPayload())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dayOfWeek").value(1))
                .andExpect(jsonPath("$.startHour").value(7))
                .andExpect(jsonPath("$.lengthHours").value(2));
        verify(timeslotRepository).save(any(BlockTimeslotEntity.class));
    }

    @Test
    public void createTimeslot_duplicate_returns400() throws Exception {
        when(timeslotRepository.existsByDayOfWeekAndStartHourAndLengthHours(1, 7, 2)).thenReturn(true);
        mockMvc.perform(post("/api/admin/timeslots").contentType(MediaType.APPLICATION_JSON).content(json(validPayload())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("already exists")));
        verify(timeslotRepository, never()).save(any(BlockTimeslotEntity.class));
    }

    @Test
    public void createTimeslot_dayOutOfRange_returnsValidationError() throws Exception {
        Map<String, Object> body = validPayload();
        body.put("dayOfWeek", 8);
        mockMvc.perform(post("/api/admin/timeslots").contentType(MediaType.APPLICATION_JSON).content(json(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.dayOfWeek").exists());
    }

    @Test
    public void createTimeslot_exceedsEndOfDay_returnsValidationError() throws Exception {
        Map<String, Object> body = validPayload();
        body.put("startHour", 14);
        body.put("lengthHours", 4);
        mockMvc.perform(post("/api/admin/timeslots").contentType(MediaType.APPLICATION_JSON).content(json(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.withinDayBounds").exists());
    }

    @Test
    public void createTimeslot_missingFields_returnsValidationError() throws Exception {
        Map<String, Object> body = new HashMap<>();
        mockMvc.perform(post("/api/admin/timeslots").contentType(MediaType.APPLICATION_JSON).content(json(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.dayOfWeek").exists())
                .andExpect(jsonPath("$.errors.startHour").exists())
                .andExpect(jsonPath("$.errors.lengthHours").exists());
    }

    // ---- PUT (update) ----

    @Test
    public void updateTimeslot_valid_returnsUpdated() throws Exception {
        when(timeslotRepository.findById("block_abc123")).thenReturn(Optional.of(timeslot));
        when(timeslotRepository.existsByDayOfWeekAndStartHourAndLengthHoursAndIdNot(2, 8, 3, "block_abc123"))
                .thenReturn(false);
        when(timeslotRepository.save(any(BlockTimeslotEntity.class))).thenAnswer(inv -> inv.getArgument(0));
        Map<String, Object> body = new HashMap<>();
        body.put("dayOfWeek", 2);
        body.put("startHour", 8);
        body.put("lengthHours", 3);
        mockMvc.perform(put("/api/admin/timeslots/block_abc123").contentType(MediaType.APPLICATION_JSON).content(json(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dayOfWeek").value(2));
        verify(timeslotRepository).save(any(BlockTimeslotEntity.class));
    }

    @Test
    public void updateTimeslot_notFound_returns404() throws Exception {
        when(timeslotRepository.findById("nope")).thenReturn(Optional.empty());
        mockMvc.perform(put("/api/admin/timeslots/nope").contentType(MediaType.APPLICATION_JSON).content(json(validPayload())))
                .andExpect(status().isNotFound());
        verify(timeslotRepository, never()).save(any(BlockTimeslotEntity.class));
    }

    // ---- DELETE ----

    @Test
    public void deleteTimeslot_unused_returns204() throws Exception {
        when(timeslotRepository.findById("block_abc123")).thenReturn(Optional.of(timeslot));
        when(assignmentRepository.countByBlockTimeslotId("block_abc123")).thenReturn(0L);
        mockMvc.perform(delete("/api/admin/timeslots/block_abc123"))
                .andExpect(status().isNoContent());
        verify(timeslotRepository).delete(timeslot);
    }

    @Test
    public void deleteTimeslot_inUse_returns400() throws Exception {
        when(timeslotRepository.findById("block_abc123")).thenReturn(Optional.of(timeslot));
        when(assignmentRepository.countByBlockTimeslotId("block_abc123")).thenReturn(3L);
        mockMvc.perform(delete("/api/admin/timeslots/block_abc123"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("3 assignment")));
        verify(timeslotRepository, never()).delete(any(BlockTimeslotEntity.class));
    }

    @Test
    public void deleteTimeslot_notFound_returns404() throws Exception {
        when(timeslotRepository.findById("nope")).thenReturn(Optional.empty());
        mockMvc.perform(delete("/api/admin/timeslots/nope"))
                .andExpect(status().isNotFound());
        verify(timeslotRepository, never()).delete(any(BlockTimeslotEntity.class));
    }
}
