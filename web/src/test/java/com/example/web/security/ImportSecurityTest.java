package com.example.web.security;

import com.example.web.controller.ImportController;
import com.example.web.service.ExcelImportService;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Confirms /api/import/excel follows the general POST rule (WRITER or
 * ADMIN), not the ADMIN-only /api/admin/** rule: READER gets 403, WRITER and
 * ADMIN both get through to the controller.
 */
@RunWith(SpringRunner.class)
@WebMvcTest(ImportController.class)
@Import(SecurityConfig.class)
@TestPropertySource(properties = "app.jwt.secret=test-secret-key-that-is-at-least-32-bytes-long")
public class ImportSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ExcelImportService excelImportService;

    // Required by the AuthenticationManager bean declared in SecurityConfig.
    @MockBean
    private AppUserDetailsService appUserDetailsService;

    private MockMultipartFile file() {
        return new MockMultipartFile("file", "data.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", new byte[] { 1, 2, 3 });
    }

    @Test
    public void anonymous_isUnauthorized() throws Exception {
        mockMvc.perform(multipart("/api/import/excel").file(file()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "READER")
    public void reader_isForbidden() throws Exception {
        mockMvc.perform(multipart("/api/import/excel").file(file()))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "WRITER")
    public void writer_canAccess() throws Exception {
        when(excelImportService.importFromExcel(any()))
                .thenReturn(ExcelImportService.ImportResult.success(0, 0, 0, 0, 0));
        mockMvc.perform(multipart("/api/import/excel").file(file()))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    public void admin_canAccess() throws Exception {
        when(excelImportService.importFromExcel(any()))
                .thenReturn(ExcelImportService.ImportResult.success(0, 0, 0, 0, 0));
        mockMvc.perform(multipart("/api/import/excel").file(file()))
                .andExpect(status().isOk());
    }
}
