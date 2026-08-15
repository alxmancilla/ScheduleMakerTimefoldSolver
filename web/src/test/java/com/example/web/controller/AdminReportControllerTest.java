package com.example.web.controller;

import com.example.web.service.EngineRunnerService;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.servlet.MockMvc;

import java.io.File;
import java.nio.file.Files;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Web-layer tests for {@link AdminReportController}: listing compliance-snapshot
 * runs (newest-first) and downloading a specific run's file, including
 * path-traversal rejection. Mirrors ReportControllerTest's coverage shape.
 */
@RunWith(SpringRunner.class)
@WebMvcTest(AdminReportController.class)
@AutoConfigureMockMvc(addFilters = false)
public class AdminReportControllerTest {

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private EngineRunnerService engineRunnerService;

    private File adminReportsDir;

    @Before
    public void setUp() throws Exception {
        adminReportsDir = tempFolder.newFolder("admin-reports");
        when(engineRunnerService.getAdminReportsDir()).thenReturn(adminReportsDir);
    }

    private File newRunDir(String runId) {
        File dir = new File(adminReportsDir, runId);
        dir.mkdirs();
        return dir;
    }

    @Test
    public void listRuns_returnsNewestFirstWithFiles() throws Exception {
        File older = newRunDir("2026-08-10_090000");
        Files.write(new File(older, "calendario-incumplimientos.pdf").toPath(), new byte[] { 1, 2, 3 });

        File newer = newRunDir("2026-08-15_143022");
        Files.write(new File(newer, "calendario-incumplimientos.pdf").toPath(), new byte[] { 1, 2 });

        mockMvc.perform(get("/api/admin/reports"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].runId").value("2026-08-15_143022"))
                .andExpect(jsonPath("$[0].generatedAt").value("2026-08-15T14:30:22"))
                .andExpect(jsonPath("$[0].files", hasSize(1)))
                .andExpect(jsonPath("$[0].files[0].filename").value("calendario-incumplimientos.pdf"))
                .andExpect(jsonPath("$[0].files[0].sizeBytes").value(2))
                .andExpect(jsonPath("$[1].runId").value("2026-08-10_090000"));
    }

    @Test
    public void listRuns_noRunsYet_returnsEmptyList() throws Exception {
        mockMvc.perform(get("/api/admin/reports"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    public void downloadReport_existingFile_returnsPdfBytes() throws Exception {
        File runDir = newRunDir("2026-08-15_143022");
        byte[] content = "%PDF-1.4 fake content".getBytes();
        Files.write(new File(runDir, "calendario-incumplimientos.pdf").toPath(), content);

        mockMvc.perform(get("/api/admin/reports/2026-08-15_143022/calendario-incumplimientos.pdf"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", MediaType.APPLICATION_PDF_VALUE));
    }

    @Test
    public void downloadReport_missingFile_returns404() throws Exception {
        newRunDir("2026-08-15_143022");
        mockMvc.perform(get("/api/admin/reports/2026-08-15_143022/nonexistent.pdf"))
                .andExpect(status().isNotFound());
    }

    @Test
    public void downloadReport_missingRun_returns404() throws Exception {
        mockMvc.perform(get("/api/admin/reports/nonexistent-run/report.pdf"))
                .andExpect(status().isNotFound());
    }

    @Test
    public void downloadReport_pathTraversalInFilename_returns404() throws Exception {
        newRunDir("2026-08-15_143022");
        mockMvc.perform(get("/api/admin/reports/2026-08-15_143022/..%2F..%2F..%2Fetc%2Fpasswd"))
                .andExpect(status().isNotFound());
    }

    @Test
    public void downloadReport_pathTraversalInRunId_returns404() throws Exception {
        mockMvc.perform(get("/api/admin/reports/..%2F..%2Fetc/passwd"))
                .andExpect(status().isNotFound());
    }
}
