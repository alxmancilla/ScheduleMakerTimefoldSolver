package com.example.web.controller;

import com.example.web.service.ReportRunnerService;
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
import java.util.List;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Web-layer tests for {@link ReportController}: listing report runs
 * (newest-first, each with its PDF files), status, and downloading a
 * specific run's file, including path-traversal rejection.
 */
@RunWith(SpringRunner.class)
@WebMvcTest(ReportController.class)
@AutoConfigureMockMvc(addFilters = false)
public class ReportControllerTest {

    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ReportRunnerService reportRunnerService;

    private File reportsDir;

    @Before
    public void setUp() throws Exception {
        reportsDir = tempFolder.newFolder("reports");
        when(reportRunnerService.getReportsDir()).thenReturn(reportsDir);
    }

    private File newRunDir(String runId) {
        File dir = new File(reportsDir, runId);
        dir.mkdirs();
        return dir;
    }

    @Test
    public void listRuns_returnsNewestFirstWithFiles() throws Exception {
        File older = newRunDir("2026-08-10_090000");
        Files.write(new File(older, "calendario-por-grupo.pdf").toPath(), new byte[] { 1, 2, 3 });

        File newer = newRunDir("2026-08-15_143022");
        Files.write(new File(newer, "calendario-por-grupo.pdf").toPath(), new byte[] { 1, 2 });
        Files.write(new File(newer, "calendario-por-maestro.pdf").toPath(), new byte[] { 1 });
        Files.write(new File(newer, "notes.txt").toPath(), "not a pdf".getBytes());

        mockMvc.perform(get("/api/reports"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].runId").value("2026-08-15_143022"))
                .andExpect(jsonPath("$[0].generatedAt").value("2026-08-15T14:30:22"))
                .andExpect(jsonPath("$[0].files", hasSize(2)))
                .andExpect(jsonPath("$[0].files[0].filename").value("calendario-por-grupo.pdf"))
                .andExpect(jsonPath("$[0].files[0].sizeBytes").value(2))
                .andExpect(jsonPath("$[1].runId").value("2026-08-10_090000"));
    }

    @Test
    public void listRuns_noRunsYet_returnsEmptyList() throws Exception {
        mockMvc.perform(get("/api/reports"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    public void getStatus_returnsSnapshot() throws Exception {
        when(reportRunnerService.getSnapshot())
                .thenReturn(new ReportRunnerService.Snapshot(
                        ReportRunnerService.State.COMPLETED, null, null, 0, "2026-08-15_143022", List.of("done")));

        mockMvc.perform(get("/api/reports/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("COMPLETED"))
                .andExpect(jsonPath("$.exitCode").value(0))
                .andExpect(jsonPath("$.runId").value("2026-08-15_143022"));
    }

    @Test
    public void downloadReport_existingFile_returnsPdfBytes() throws Exception {
        File runDir = newRunDir("2026-08-15_143022");
        byte[] content = "%PDF-1.4 fake content".getBytes();
        Files.write(new File(runDir, "report.pdf").toPath(), content);

        mockMvc.perform(get("/api/reports/2026-08-15_143022/report.pdf"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", MediaType.APPLICATION_PDF_VALUE));
    }

    @Test
    public void downloadReport_missingFile_returns404() throws Exception {
        newRunDir("2026-08-15_143022");
        mockMvc.perform(get("/api/reports/2026-08-15_143022/nonexistent.pdf"))
                .andExpect(status().isNotFound());
    }

    @Test
    public void downloadReport_missingRun_returns404() throws Exception {
        mockMvc.perform(get("/api/reports/nonexistent-run/report.pdf"))
                .andExpect(status().isNotFound());
    }

    @Test
    public void downloadReport_pathTraversalInFilename_returns404() throws Exception {
        newRunDir("2026-08-15_143022");
        mockMvc.perform(get("/api/reports/2026-08-15_143022/..%2F..%2F..%2Fetc%2Fpasswd"))
                .andExpect(status().isNotFound());
    }

    @Test
    public void downloadReport_pathTraversalInRunId_returns404() throws Exception {
        mockMvc.perform(get("/api/reports/..%2F..%2Fetc/passwd"))
                .andExpect(status().isNotFound());
    }
}
