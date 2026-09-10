package com.example.web.controller;

import com.example.web.service.DatabaseBackupService;
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

import java.io.File;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Web-layer tests for {@link DatabaseBackupController}: the export/import
 * run/status contract (mirrors EngineControllerTest's shape) and the
 * path-traversal-safe backup file listing/download (mirrors
 * AdminReportControllerTest's shape, if present, for the same pattern in
 * AdminReportController). Uses the MVC slice with a mocked service so no
 * subprocess or database is required.
 */
@RunWith(SpringRunner.class)
@WebMvcTest(DatabaseBackupController.class)
@AutoConfigureMockMvc(addFilters = false)
public class DatabaseBackupControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private DatabaseBackupService databaseBackupService;

    private DatabaseBackupService.Snapshot snapshot(DatabaseBackupService.State state,
            DatabaseBackupService.Operation operation, Integer exitCode) {
        return new DatabaseBackupService.Snapshot(state, operation, LocalDateTime.now(), null, exitCode,
                List.of("line 1", "line 2"));
    }

    private String json(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }

    @Test
    public void export_starts_returnsSnapshot() throws Exception {
        when(databaseBackupService.tryStartExport()).thenReturn(true);
        when(databaseBackupService.getSnapshot())
                .thenReturn(snapshot(DatabaseBackupService.State.RUNNING, DatabaseBackupService.Operation.EXPORT, null));

        mockMvc.perform(post("/api/admin/database/export"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("RUNNING"))
                .andExpect(jsonPath("$.lastOperation").value("EXPORT"));
    }

    @Test
    public void export_alreadyRunning_returns400() throws Exception {
        when(databaseBackupService.tryStartExport()).thenReturn(false);

        mockMvc.perform(post("/api/admin/database/export"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("already running")));
    }

    @Test
    public void importDatabase_starts_returnsSnapshot() throws Exception {
        when(databaseBackupService.tryStartImport("school_schedule_20260101_000000.sql")).thenReturn(true);
        when(databaseBackupService.getSnapshot())
                .thenReturn(snapshot(DatabaseBackupService.State.RUNNING, DatabaseBackupService.Operation.IMPORT, null));

        mockMvc.perform(post("/api/admin/database/import").contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("filename", "school_schedule_20260101_000000.sql"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lastOperation").value("IMPORT"));

        verify(databaseBackupService).tryStartImport("school_schedule_20260101_000000.sql");
    }

    @Test
    public void importDatabase_blankFilename_returnsValidationError() throws Exception {
        mockMvc.perform(post("/api/admin/database/import").contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("filename", ""))))
                .andExpect(status().isBadRequest());
    }

    @Test
    public void importDatabase_invalidFilename_returns400() throws Exception {
        when(databaseBackupService.tryStartImport(eq("../etc/passwd")))
                .thenThrow(new IllegalArgumentException("Invalid backup filename: ../etc/passwd"));

        mockMvc.perform(post("/api/admin/database/import").contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("filename", "../etc/passwd"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    public void importDatabase_alreadyRunning_returns400() throws Exception {
        when(databaseBackupService.tryStartImport("x.sql")).thenReturn(false);

        mockMvc.perform(post("/api/admin/database/import").contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("filename", "x.sql"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("already running")));
    }

    @Test
    public void getStatus_returnsSnapshot() throws Exception {
        when(databaseBackupService.getSnapshot())
                .thenReturn(snapshot(DatabaseBackupService.State.COMPLETED, DatabaseBackupService.Operation.EXPORT, 0));

        mockMvc.perform(get("/api/admin/database/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value("COMPLETED"))
                .andExpect(jsonPath("$.exitCode").value(0))
                .andExpect(jsonPath("$.log", hasSize(2)));
    }

    @Test
    public void listBackups_returnsFiles() throws Exception {
        when(databaseBackupService.listBackups()).thenReturn(List.of(
                new DatabaseBackupService.BackupFile("school_schedule_20260102_000000.sql", 1024, LocalDateTime.now()),
                new DatabaseBackupService.BackupFile("school_schedule_20260101_000000.sql", 2048, LocalDateTime.now())));

        mockMvc.perform(get("/api/admin/database/backups"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].filename").value("school_schedule_20260102_000000.sql"))
                .andExpect(jsonPath("$[0].sizeBytes").value(1024));
    }

    @Test
    public void downloadBackup_notFound_returns404() throws Exception {
        when(databaseBackupService.resolveBackupFile("missing.sql")).thenReturn(null);

        mockMvc.perform(get("/api/admin/database/backups/missing.sql"))
                .andExpect(status().isNotFound());
    }

    @Test
    public void downloadBackup_found_streamsFile() throws Exception {
        File tempFile = File.createTempFile("school_schedule_test", ".sql");
        tempFile.deleteOnExit();
        java.nio.file.Files.writeString(tempFile.toPath(), "-- dump content");
        when(databaseBackupService.resolveBackupFile("school_schedule_test.sql")).thenReturn(tempFile);

        mockMvc.perform(get("/api/admin/database/backups/school_schedule_test.sql"))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .header().string("Content-Disposition", containsString("attachment")));
    }
}
