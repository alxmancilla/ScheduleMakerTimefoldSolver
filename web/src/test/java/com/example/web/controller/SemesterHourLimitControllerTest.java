package com.example.web.controller;

import com.example.web.entity.SemesterHourLimitEntity;
import com.example.web.repository.SemesterHourLimitRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Web-layer tests for {@link SemesterHourLimitController}. Uses the MVC
 * slice with a mocked repository so no database is required.
 */
@RunWith(SpringRunner.class)
@WebMvcTest(SemesterHourLimitController.class)
@AutoConfigureMockMvc(addFilters = false)
public class SemesterHourLimitControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private SemesterHourLimitRepository limitRepository;

    private String json(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }

    @Test
    public void getAllLimits_returnsList() throws Exception {
        when(limitRepository.findAll()).thenReturn(
                List.of(new SemesterHourLimitEntity(1, 14, "HARD")));

        mockMvc.perform(get("/api/admin/semester-hour-limits"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].semester").value(1))
                .andExpect(jsonPath("$[0].latestEndHour").value(14))
                .andExpect(jsonPath("$[0].severity").value("HARD"));
    }

    @Test
    public void upsertLimit_valid_returnsSaved() throws Exception {
        when(limitRepository.findById(5)).thenReturn(Optional.empty());
        when(limitRepository.save(any(SemesterHourLimitEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        Map<String, Object> body = new HashMap<>();
        body.put("latestEndHour", 14);
        body.put("severity", "SOFT");

        mockMvc.perform(put("/api/admin/semester-hour-limits/{semester}", 5)
                        .contentType(MediaType.APPLICATION_JSON).content(json(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.semester").value(5))
                .andExpect(jsonPath("$.latestEndHour").value(14))
                .andExpect(jsonPath("$.severity").value("SOFT"));
        verify(limitRepository).save(any(SemesterHourLimitEntity.class));
    }

    @Test
    public void upsertLimit_invalidSeverity_returnsValidationError() throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("latestEndHour", 14);
        body.put("severity", "MEDIUM");

        mockMvc.perform(put("/api/admin/semester-hour-limits/{semester}", 5)
                        .contentType(MediaType.APPLICATION_JSON).content(json(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.severity").exists());
        verify(limitRepository, never()).save(any(SemesterHourLimitEntity.class));
    }

    @Test
    public void upsertLimit_hourOutOfRange_returnsValidationError() throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("latestEndHour", 30);
        body.put("severity", "HARD");

        mockMvc.perform(put("/api/admin/semester-hour-limits/{semester}", 5)
                        .contentType(MediaType.APPLICATION_JSON).content(json(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.latestEndHour").exists());
    }

    @Test
    public void deleteLimit_existing_returns204() throws Exception {
        when(limitRepository.existsById(5)).thenReturn(true);
        mockMvc.perform(delete("/api/admin/semester-hour-limits/{semester}", 5))
                .andExpect(status().isNoContent());
        verify(limitRepository).deleteById(5);
    }

    @Test
    public void deleteLimit_notExisting_isIdempotent() throws Exception {
        when(limitRepository.existsById(5)).thenReturn(false);
        mockMvc.perform(delete("/api/admin/semester-hour-limits/{semester}", 5))
                .andExpect(status().isNoContent());
        verify(limitRepository, never()).deleteById(any());
    }
}
