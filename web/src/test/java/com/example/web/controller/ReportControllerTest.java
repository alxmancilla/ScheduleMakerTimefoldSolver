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
 * Web-layer tests for {@link ReportController}: listing, status, and
 * downloading generated PDF reports, including path-traversal rejection.
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

    @Test
    public void listReports_returnsPdfFilesOnly() throws Exception {
        Files.write(new File(reportsDir, "calendario-bloques-por-grupo.pdf").toPath(), new byte[] { 1, 2, 3 });
        Files.write(new File(reportsDir, "calendario-bloques-por-maestro.pdf").toPath(), new byte[] { 1, 2 });
        Files.write(new File(reportsDir, "notes.txt").toPath(), "not a pdf".getBytes());

        mockMvc.perform(get("/api/reports"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].filename").value("calendario-bloques-por-grupo.pdf"))
                .andExpect(jsonPath("$[0].sizeBytes").value(3));
    }

    @Test
    public void listReports_emptyDir_returnsEmptyList() throws Exception {
        mockMvc.perform(get("/api/reports"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    public void getStatus_returnsSnapshot() throws Exception {
        when(reportRunnerService.getSnapshot())
                .thenReturn(new ReportRunnerService.Snapshot(ReportRunnerService.State.COMPLETED, null, null, 0, List.of("done")));

        mockMvc.perform(get("/api/reports/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("COMPLETED"))
                .andExpect(jsonPath("$.exitCode").value(0));
    }

    @Test
    public void downloadReport_existingFile_returnsPdfBytes() throws Exception {
        byte[] content = "%PDF-1.4 fake content".getBytes();
        Files.write(new File(reportsDir, "report.pdf").toPath(), content);

        mockMvc.perform(get("/api/reports/report.pdf"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", MediaType.APPLICATION_PDF_VALUE));
    }

    @Test
    public void downloadReport_missingFile_returns404() throws Exception {
        mockMvc.perform(get("/api/reports/nonexistent.pdf"))
                .andExpect(status().isNotFound());
    }

    @Test
    public void downloadReport_pathTraversalAttempt_returns404() throws Exception {
        mockMvc.perform(get("/api/reports/..%2F..%2F..%2Fetc%2Fpasswd"))
                .andExpect(status().isNotFound());
    }
}
