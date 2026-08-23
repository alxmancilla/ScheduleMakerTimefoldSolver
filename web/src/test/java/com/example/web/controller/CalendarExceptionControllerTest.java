package com.example.web.controller;

import com.example.web.entity.CalendarExceptionEntity;
import com.example.web.repository.CalendarExceptionRepository;
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

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Web-layer tests for {@link CalendarExceptionController}. Uses the MVC
 * slice with a mocked repository so no database is required; role
 * enforcement for /api/admin/** is covered separately by the security
 * package's tests, not here (security filters disabled below).
 */
@RunWith(SpringRunner.class)
@WebMvcTest(CalendarExceptionController.class)
@AutoConfigureMockMvc(addFilters = false)
public class CalendarExceptionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private CalendarExceptionRepository exceptionRepository;

    private String json(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }

    @Before
    public void setUp() {
    }

    @Test
    public void getAllExceptions_returnsListSortedByDate() throws Exception {
        CalendarExceptionEntity e1 = new CalendarExceptionEntity(LocalDate.of(2026, 9, 16), "HOLIDAY",
                "Día de la Independencia", null);
        when(exceptionRepository.findAllByOrderByExceptionDateAsc()).thenReturn(List.of(e1));

        mockMvc.perform(get("/api/admin/calendar-exceptions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].exceptionDate").value("2026-09-16"))
                .andExpect(jsonPath("$[0].type").value("HOLIDAY"));
    }

    @Test
    public void upsertException_createsHoliday() throws Exception {
        when(exceptionRepository.findById(LocalDate.of(2026, 12, 25))).thenReturn(Optional.empty());
        when(exceptionRepository.save(org.mockito.ArgumentMatchers.any()))
                .thenAnswer(inv -> inv.getArgument(0));

        Map<String, Object> body = new HashMap<>();
        body.put("type", "HOLIDAY");
        body.put("label", "Navidad");

        mockMvc.perform(put("/api/admin/calendar-exceptions/2026-12-25")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("HOLIDAY"))
                .andExpect(jsonPath("$.label").value("Navidad"));
    }

    @Test
    public void upsertException_halfDayWithoutEndHour_returns400() throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("type", "HALF_DAY");

        mockMvc.perform(put("/api/admin/calendar-exceptions/2026-12-19")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("End hour is required for a half-day exception"));
    }

    @Test
    public void upsertException_halfDayWithEndHour_succeeds() throws Exception {
        when(exceptionRepository.findById(LocalDate.of(2026, 12, 19))).thenReturn(Optional.empty());
        when(exceptionRepository.save(org.mockito.ArgumentMatchers.any()))
                .thenAnswer(inv -> inv.getArgument(0));

        Map<String, Object> body = new HashMap<>();
        body.put("type", "HALF_DAY");
        body.put("endHour", 11);

        mockMvc.perform(put("/api/admin/calendar-exceptions/2026-12-19")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.endHour").value(11));
    }

    @Test
    public void upsertException_invalidType_returns400() throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("type", "NOT_A_REAL_TYPE");

        mockMvc.perform(put("/api/admin/calendar-exceptions/2026-12-19")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(body)))
                .andExpect(status().isBadRequest());
    }

    @Test
    public void deleteException_existing_returns204() throws Exception {
        LocalDate date = LocalDate.of(2026, 12, 25);
        when(exceptionRepository.existsById(date)).thenReturn(true);

        mockMvc.perform(delete("/api/admin/calendar-exceptions/2026-12-25"))
                .andExpect(status().isNoContent());
    }

    @Test
    public void deleteException_nonExistent_isIdempotent() throws Exception {
        LocalDate date = LocalDate.of(2026, 12, 25);
        when(exceptionRepository.existsById(date)).thenReturn(false);

        mockMvc.perform(delete("/api/admin/calendar-exceptions/2026-12-25"))
                .andExpect(status().isNoContent());
    }
}
