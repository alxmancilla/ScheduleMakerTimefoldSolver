package com.example.web.controller;

import com.example.web.service.ExcelExportService;
import com.example.web.service.ExcelImportService;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Web-layer tests for {@link ImportController}, covering the multipart
 * upload contract and success/failure response shapes. Uses the MVC slice
 * with a mocked ExcelImportService so no real workbook parsing happens here
 * (see ExcelImportServiceTest for that).
 */
@RunWith(SpringRunner.class)
@WebMvcTest(ImportController.class)
@AutoConfigureMockMvc(addFilters = false)
public class ImportControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ExcelImportService excelImportService;

    @MockBean
    private ExcelExportService excelExportService;

    @Test
    public void importExcel_success_returns200() throws Exception {
        when(excelImportService.importFromExcel(any()))
                .thenReturn(ExcelImportService.ImportResult.success(3, 2, 1, 1, 1));
        MockMultipartFile file = new MockMultipartFile("file", "data.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", new byte[] { 1, 2, 3 });

        mockMvc.perform(multipart("/api/import/excel").file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.teachersImported").value(3))
                .andExpect(jsonPath("$.coursesImported").value(2));
    }

    @Test
    public void importExcel_validationFailure_returns400WithErrors() throws Exception {
        when(excelImportService.importFromExcel(any()))
                .thenReturn(ExcelImportService.ImportResult.failure(List.of("Courses row 3: semester must be between 1 and 12")));
        MockMultipartFile file = new MockMultipartFile("file", "data.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", new byte[] { 1, 2, 3 });

        mockMvc.perform(multipart("/api/import/excel").file(file))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errors[0]").value("Courses row 3: semester must be between 1 and 12"));
    }

    @Test
    public void importExcel_emptyFile_returns400() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "empty.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", new byte[0]);

        mockMvc.perform(multipart("/api/import/excel").file(file))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", org.hamcrest.Matchers.containsString("No file")));
    }

    @Test
    public void exportExcel_returnsWorkbookAsAttachment() throws Exception {
        byte[] fakeWorkbookBytes = new byte[] { 1, 2, 3, 4 };
        when(excelExportService.exportToExcel(isNull())).thenReturn(fakeWorkbookBytes);

        mockMvc.perform(get("/api/import/excel"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE,
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION,
                        org.hamcrest.Matchers.containsString("attachment; filename=\"schedule-export-")))
                .andExpect(content().bytes(fakeWorkbookBytes));
    }

    @Test
    public void exportExcel_withEntitiesParam_passesParsedSetAndSuffixesFilename() throws Exception {
        byte[] fakeWorkbookBytes = new byte[] { 5, 6 };
        when(excelExportService.exportToExcel(eq(Set.of("teachers", "rooms")))).thenReturn(fakeWorkbookBytes);

        mockMvc.perform(get("/api/import/excel").param("entities", "Teachers, rooms"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION,
                        org.hamcrest.Matchers.containsString("schedule-export-rooms-teachers-")))
                .andExpect(content().bytes(fakeWorkbookBytes));
    }

    @Test
    public void exportExcel_withUnknownEntity_returns400() throws Exception {
        mockMvc.perform(get("/api/import/excel").param("entities", "assignments"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", org.hamcrest.Matchers.containsString("Unknown value")));
    }
}
