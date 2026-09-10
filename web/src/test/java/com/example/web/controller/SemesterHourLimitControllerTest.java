package com.example.web.controller;

import com.example.web.entity.SemesterHourLimitEntity;
import com.example.web.repository.CourseBlockAssignmentRepository;
import com.example.web.repository.CourseBlockAssignmentRepository.GroupSemesterDemand;
import com.example.web.repository.CourseBlockAssignmentRepository.PinnedHourLimitViolation;
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

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Web-layer tests for {@link SemesterHourLimitController}, including its 3
 * guardrails against an infeasible config (see the controller's own
 * javadoc). Uses the MVC slice with mocked repositories so no database is
 * required.
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

    @MockBean
    private CourseBlockAssignmentRepository assignmentRepository;

    private String json(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }

    private void stubNoPinnedViolationsOrDemand() {
        when(assignmentRepository.findPinnedHourLimitViolations(anyInt(), anyInt())).thenReturn(List.of());
        when(assignmentRepository.findGroupWeeklyDemandForSemester(anyInt())).thenReturn(List.of());
    }

    private PinnedHourLimitViolation violation(String groupName, String courseName, int dayOfWeek, int startHour,
            int lengthHours) {
        return new PinnedHourLimitViolation() {
            public String getGroupName() {
                return groupName;
            }

            public String getCourseName() {
                return courseName;
            }

            public Integer getDayOfWeek() {
                return dayOfWeek;
            }

            public Integer getStartHour() {
                return startHour;
            }

            public Integer getLengthHours() {
                return lengthHours;
            }
        };
    }

    private GroupSemesterDemand demand(String groupId, int totalHours) {
        return new GroupSemesterDemand() {
            public String getGroupId() {
                return groupId;
            }

            public Integer getTotalHours() {
                return totalHours;
            }
        };
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
    public void upsertLimit_valid_returnsSavedWithNoWarnings() throws Exception {
        stubNoPinnedViolationsOrDemand();
        when(limitRepository.findById(5)).thenReturn(Optional.empty());
        when(limitRepository.save(any(SemesterHourLimitEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        Map<String, Object> body = new HashMap<>();
        body.put("latestEndHour", 14);
        body.put("severity", "SOFT");

        mockMvc.perform(put("/api/admin/semester-hour-limits/{semester}", 5)
                        .contentType(MediaType.APPLICATION_JSON).content(json(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.limit.semester").value(5))
                .andExpect(jsonPath("$.limit.latestEndHour").value(14))
                .andExpect(jsonPath("$.limit.severity").value("SOFT"))
                .andExpect(jsonPath("$.warnings").isEmpty());
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

    // ---- Guardrail #1: bounds are the school's real hours, not 1-24 ----

    @Test
    public void upsertLimit_hourAboveRange_returnsValidationError() throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("latestEndHour", 30);
        body.put("severity", "HARD");

        mockMvc.perform(put("/api/admin/semester-hour-limits/{semester}", 5)
                        .contentType(MediaType.APPLICATION_JSON).content(json(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.latestEndHour").exists());
    }

    @Test
    public void upsertLimit_hourBelowRange_returnsValidationError() throws Exception {
        // 7 leaves zero valid timeslots - even a 1h block starting at the
        // earliest possible hour (7:00) wouldn't finish until 8:00.
        Map<String, Object> body = new HashMap<>();
        body.put("latestEndHour", 7);
        body.put("severity", "HARD");

        mockMvc.perform(put("/api/admin/semester-hour-limits/{semester}", 5)
                        .contentType(MediaType.APPLICATION_JSON).content(json(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.latestEndHour").exists());
    }

    // ---- Guardrail #2: blocking, HARD only, exact pinned-data conflict ----

    @Test
    public void upsertLimit_hardWithPinnedViolations_isRejected() throws Exception {
        when(assignmentRepository.findPinnedHourLimitViolations(5, 14)).thenReturn(
                List.of(violation("5A-PRO", "Ingles V", 1, 13, 2)));

        Map<String, Object> body = new HashMap<>();
        body.put("latestEndHour", 14);
        body.put("severity", "HARD");

        mockMvc.perform(put("/api/admin/semester-hour-limits/{semester}", 5)
                        .contentType(MediaType.APPLICATION_JSON).content(json(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("1 pinned block")))
                .andExpect(jsonPath("$.message", containsString("5A-PRO")));
        verify(limitRepository, never()).save(any(SemesterHourLimitEntity.class));
    }

    @Test
    public void upsertLimit_softWithWouldBeHardViolations_isNotBlocked() throws Exception {
        // The exact same conflicting pinned data exists, but severity is
        // SOFT this time - guardrail #2 only applies to HARD, since a SOFT
        // limit was never going to structurally exclude that timeslot anyway.
        when(assignmentRepository.findGroupWeeklyDemandForSemester(anyInt())).thenReturn(List.of());
        when(limitRepository.findById(5)).thenReturn(Optional.empty());
        when(limitRepository.save(any(SemesterHourLimitEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        Map<String, Object> body = new HashMap<>();
        body.put("latestEndHour", 14);
        body.put("severity", "SOFT");

        mockMvc.perform(put("/api/admin/semester-hour-limits/{semester}", 5)
                        .contentType(MediaType.APPLICATION_JSON).content(json(body)))
                .andExpect(status().isOk());
        verify(assignmentRepository, never()).findPinnedHourLimitViolations(anyInt(), anyInt());
        verify(limitRepository).save(any(SemesterHourLimitEntity.class));
    }

    // ---- Guardrail #3: non-blocking capacity warning ----

    @Test
    public void upsertLimit_groupDemandExceedsWindow_returnsWarningButStillSaves() throws Exception {
        when(assignmentRepository.findPinnedHourLimitViolations(anyInt(), anyInt())).thenReturn(List.of());
        // Window: 5 days x (14 - 7) = 35h/week. 40h demand exceeds it.
        when(assignmentRepository.findGroupWeeklyDemandForSemester(5)).thenReturn(List.of(demand("5A-PRO", 40)));
        when(limitRepository.findById(5)).thenReturn(Optional.empty());
        when(limitRepository.save(any(SemesterHourLimitEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        Map<String, Object> body = new HashMap<>();
        body.put("latestEndHour", 14);
        body.put("severity", "HARD");

        mockMvc.perform(put("/api/admin/semester-hour-limits/{semester}", 5)
                        .contentType(MediaType.APPLICATION_JSON).content(json(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.warnings.length()").value(1))
                .andExpect(jsonPath("$.warnings[0]", containsString("5A-PRO")))
                .andExpect(jsonPath("$.warnings[0]", containsString("40h")));
        verify(limitRepository).save(any(SemesterHourLimitEntity.class));
    }

    @Test
    public void upsertLimit_groupDemandWithinWindow_noWarning() throws Exception {
        stubNoPinnedViolationsOrDemand();
        when(assignmentRepository.findGroupWeeklyDemandForSemester(5)).thenReturn(List.of(demand("1A-ARH", 24)));
        when(limitRepository.findById(5)).thenReturn(Optional.empty());
        when(limitRepository.save(any(SemesterHourLimitEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        Map<String, Object> body = new HashMap<>();
        body.put("latestEndHour", 14);
        body.put("severity", "HARD");

        mockMvc.perform(put("/api/admin/semester-hour-limits/{semester}", 5)
                        .contentType(MediaType.APPLICATION_JSON).content(json(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.warnings").isEmpty());
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
