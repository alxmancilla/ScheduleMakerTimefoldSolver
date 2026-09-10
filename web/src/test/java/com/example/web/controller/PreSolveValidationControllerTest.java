package com.example.web.controller;

import com.example.web.service.PreSolveValidationRunnerService;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Web-layer tests for {@link PreSolveValidationController}: the run/status
 * contract and the "already running" error response. Mirrors
 * EngineControllerTest's shape for the equivalent EngineController tests.
 */
@RunWith(SpringRunner.class)
@WebMvcTest(PreSolveValidationController.class)
@AutoConfigureMockMvc(addFilters = false)
public class PreSolveValidationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private PreSolveValidationRunnerService validationRunnerService;

    private PreSolveValidationRunnerService.Snapshot snapshot(PreSolveValidationRunnerService.State state,
            Integer exitCode) {
        return new PreSolveValidationRunnerService.Snapshot(state, LocalDateTime.now(), null, exitCode,
                List.of("line 1", "line 2"));
    }

    @Test
    public void runValidation_startsAndReturnsSnapshot() throws Exception {
        when(validationRunnerService.tryStart()).thenReturn(true);
        when(validationRunnerService.getSnapshot())
                .thenReturn(snapshot(PreSolveValidationRunnerService.State.RUNNING, null));

        mockMvc.perform(post("/api/validation/run"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("RUNNING"));

        verify(validationRunnerService).tryStart();
    }

    @Test
    public void runValidation_alreadyRunning_returns400() throws Exception {
        when(validationRunnerService.tryStart()).thenReturn(false);

        mockMvc.perform(post("/api/validation/run"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("already in progress")));
    }

    @Test
    public void getStatus_returnsSnapshot() throws Exception {
        when(validationRunnerService.getSnapshot())
                .thenReturn(snapshot(PreSolveValidationRunnerService.State.COMPLETED, 1));

        mockMvc.perform(get("/api/validation/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("COMPLETED"))
                .andExpect(jsonPath("$.exitCode").value(1))
                .andExpect(jsonPath("$.log", hasSize(2)));
    }
}
