package com.example.web.controller;

import com.example.web.service.ReportRunnerService;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Web-layer tests for {@link ReportGenerationController}: the run/"already
 * running" contract. Uses the MVC slice with a mocked ReportRunnerService so
 * no real subprocess is spawned.
 */
@RunWith(SpringRunner.class)
@WebMvcTest(ReportGenerationController.class)
@AutoConfigureMockMvc(addFilters = false)
public class ReportGenerationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ReportRunnerService reportRunnerService;

    private ReportRunnerService.Snapshot snapshot(ReportRunnerService.State state) {
        return new ReportRunnerService.Snapshot(state, null, null, null, List.of());
    }

    @Test
    public void generateReports_notAlreadyRunning_startsAndReturnsStatus() throws Exception {
        when(reportRunnerService.tryStart()).thenReturn(true);
        when(reportRunnerService.getSnapshot()).thenReturn(snapshot(ReportRunnerService.State.RUNNING));

        mockMvc.perform(post("/api/reports/generate"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("RUNNING"));
    }

    @Test
    public void generateReports_alreadyRunning_returns400() throws Exception {
        when(reportRunnerService.tryStart()).thenReturn(false);

        mockMvc.perform(post("/api/reports/generate"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("already running")));
    }
}
