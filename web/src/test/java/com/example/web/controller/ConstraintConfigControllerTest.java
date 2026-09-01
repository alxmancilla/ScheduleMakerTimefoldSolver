package com.example.web.controller;

import com.example.web.entity.ConstraintConfigEntity;
import com.example.web.repository.ConstraintConfigRepository;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Web-layer tests for {@link ConstraintConfigController}. Uses the MVC
 * slice with a mocked repository so no database is required.
 */
@RunWith(SpringRunner.class)
@WebMvcTest(ConstraintConfigController.class)
@AutoConfigureMockMvc(addFilters = false)
public class ConstraintConfigControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private ConstraintConfigRepository configRepository;

    private String json(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }

    @Test
    public void getAllWeights_mergesDefaultsWithOverrides() throws Exception {
        when(configRepository.findAll()).thenReturn(
                List.of(new ConstraintConfigEntity("Prefer group's preferred room", 25)));

        mockMvc.perform(get("/api/admin/constraint-config"))
                .andExpect(status().isOk())
                // 9 SOFT-by-default + 4 severity-configurable HARD constraints,
                // regardless of how many have an override.
                .andExpect(jsonPath("$.length()").value(13))
                .andExpect(jsonPath("$[0].constraintName").value("Non-standard rooms should finish by 2pm"))
                .andExpect(jsonPath("$[0].defaultSeverity").value("SOFT"))
                .andExpect(jsonPath("$[0].defaultWeight").value(10))
                .andExpect(jsonPath("$[0].overrideWeight").doesNotExist())
                .andExpect(jsonPath("$[0].effectiveSeverity").value("SOFT"))
                .andExpect(jsonPath("$[0].effectiveWeight").value(10));
    }

    @Test
    public void getAllWeights_includesConfigurableHardConstraintsAsHardByDefault() throws Exception {
        when(configRepository.findAll()).thenReturn(List.of());

        mockMvc.perform(get("/api/admin/constraint-config"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.constraintName=='Teacher must be qualified')].defaultSeverity")
                        .value("HARD"))
                .andExpect(jsonPath("$[?(@.constraintName=='Teacher must be qualified')].overrideWeight")
                        .value(org.hamcrest.Matchers.contains(org.hamcrest.Matchers.nullValue())))
                .andExpect(jsonPath("$[?(@.constraintName=='Teacher must be qualified')].effectiveSeverity")
                        .value("HARD"));
    }

    @Test
    public void getAllWeights_overriddenConfigurableHardConstraintBecomesSoft() throws Exception {
        when(configRepository.findAll()).thenReturn(
                List.of(new ConstraintConfigEntity("Teacher must be qualified", 8)));

        mockMvc.perform(get("/api/admin/constraint-config"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.constraintName=='Teacher must be qualified')].defaultSeverity")
                        .value("HARD"))
                .andExpect(jsonPath("$[?(@.constraintName=='Teacher must be qualified')].overrideWeight")
                        .value(8))
                .andExpect(jsonPath("$[?(@.constraintName=='Teacher must be qualified')].effectiveSeverity")
                        .value("SOFT"))
                .andExpect(jsonPath("$[?(@.constraintName=='Teacher must be qualified')].effectiveWeight")
                        .value(8));
    }

    @Test
    public void getAllWeights_overriddenConstraintShowsBothValues() throws Exception {
        when(configRepository.findAll()).thenReturn(
                List.of(new ConstraintConfigEntity("Prefer group's preferred room", 25)));

        mockMvc.perform(get("/api/admin/constraint-config"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.constraintName=='Prefer group\\'s preferred room')].defaultWeight")
                        .value(2))
                .andExpect(jsonPath("$[?(@.constraintName=='Prefer group\\'s preferred room')].overrideWeight")
                        .value(25))
                .andExpect(jsonPath("$[?(@.constraintName=='Prefer group\\'s preferred room')].effectiveWeight")
                        .value(25));
    }

    @Test
    public void upsertWeight_knownConstraint_returnsSaved() throws Exception {
        when(configRepository.findById("Prefer group's preferred room")).thenReturn(Optional.empty());
        when(configRepository.save(any(ConstraintConfigEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        Map<String, Object> body = new HashMap<>();
        body.put("weightSoft", 15);

        mockMvc.perform(put("/api/admin/constraint-config/{name}", "Prefer group's preferred room")
                        .contentType(MediaType.APPLICATION_JSON).content(json(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.constraintName").value("Prefer group's preferred room"))
                .andExpect(jsonPath("$.weightSoft").value(15));
        verify(configRepository).save(any(ConstraintConfigEntity.class));
    }

    @Test
    public void upsertWeight_unknownConstraint_returns400() throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("weightSoft", 15);

        mockMvc.perform(put("/api/admin/constraint-config/{name}", "Not A Real Constraint")
                        .contentType(MediaType.APPLICATION_JSON).content(json(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("not a known constraint")));
        verify(configRepository, never()).save(any(ConstraintConfigEntity.class));
    }

    @Test
    public void upsertWeight_configurableHardConstraint_returnsSaved() throws Exception {
        // A HARD-by-default constraint (ConfigurableHardConstraints, not
        // SoftConstraintDefaults) must also be accepted by the upsert.
        when(configRepository.findById("Course blocks must be consecutive")).thenReturn(Optional.empty());
        when(configRepository.save(any(ConstraintConfigEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        Map<String, Object> body = new HashMap<>();
        body.put("weightSoft", 5);

        mockMvc.perform(put("/api/admin/constraint-config/{name}", "Course blocks must be consecutive")
                        .contentType(MediaType.APPLICATION_JSON).content(json(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.constraintName").value("Course blocks must be consecutive"))
                .andExpect(jsonPath("$.weightSoft").value(5));
        verify(configRepository).save(any(ConstraintConfigEntity.class));
    }

    @Test
    public void upsertWeight_negativeWeight_returnsValidationError() throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("weightSoft", -1);

        mockMvc.perform(put("/api/admin/constraint-config/{name}", "Prefer group's preferred room")
                        .contentType(MediaType.APPLICATION_JSON).content(json(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.weightSoft").exists());
    }

    @Test
    public void deleteWeight_existing_returns204() throws Exception {
        when(configRepository.existsById("Prefer group's preferred room")).thenReturn(true);
        mockMvc.perform(delete("/api/admin/constraint-config/{name}", "Prefer group's preferred room"))
                .andExpect(status().isNoContent());
        verify(configRepository).deleteById("Prefer group's preferred room");
    }

    @Test
    public void deleteWeight_notExisting_isIdempotent() throws Exception {
        when(configRepository.existsById("Prefer group's preferred room")).thenReturn(false);
        mockMvc.perform(delete("/api/admin/constraint-config/{name}", "Prefer group's preferred room"))
                .andExpect(status().isNoContent());
        verify(configRepository, never()).deleteById(any());
    }
}
