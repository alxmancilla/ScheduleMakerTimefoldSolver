package com.example.web.controller;

import com.example.web.service.EngineRunnerService;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Web-layer tests for {@link EngineController}, covering the run/status
 * contract and the "already running" error response. Uses the MVC slice with
 * a mocked EngineRunnerService so no real subprocess is spawned.
 */
@RunWith(SpringRunner.class)
@WebMvcTest(EngineController.class)
@AutoConfigureMockMvc(addFilters = false)
public class EngineControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private EngineRunnerService engineRunnerService;

    private EngineRunnerService.Snapshot snapshot(EngineRunnerService.State state, Integer exitCode) {
        return new EngineRunnerService.Snapshot(state, LocalDateTime.now(), null, exitCode, List.of("line 1", "line 2"));
    }

    @Test
    public void runEngine_notAlreadyRunning_startsAndReturnsStatus() throws Exception {
        when(engineRunnerService.tryStart()).thenReturn(true);
        when(engineRunnerService.getSnapshot()).thenReturn(snapshot(EngineRunnerService.State.RUNNING, null));

        mockMvc.perform(post("/api/admin/engine/run"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("RUNNING"));

        verify(engineRunnerService).tryStart();
    }

    @Test
    public void runEngine_alreadyRunning_returns400() throws Exception {
        when(engineRunnerService.tryStart()).thenReturn(false);

        mockMvc.perform(post("/api/admin/engine/run"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("already running")));
    }

    @Test
    public void getStatus_returnsSnapshot() throws Exception {
        when(engineRunnerService.getSnapshot()).thenReturn(snapshot(EngineRunnerService.State.COMPLETED, 0));

        mockMvc.perform(get("/api/admin/engine/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("COMPLETED"))
                .andExpect(jsonPath("$.exitCode").value(0))
                .andExpect(jsonPath("$.log", org.hamcrest.Matchers.hasSize(2)));
    }
}
