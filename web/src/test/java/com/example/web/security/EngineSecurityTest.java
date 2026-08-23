package com.example.web.security;

import com.example.web.controller.EngineController;
import com.example.web.repository.AppUserRepository;
import com.example.web.service.EngineRunnerService;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Confirms /api/admin/engine is gated to ADMIN: READER and WRITER get 403,
 * ADMIN gets through to the controller.
 */
@RunWith(SpringRunner.class)
@WebMvcTest(EngineController.class)
@Import(SecurityConfig.class)
@TestPropertySource(properties = "app.jwt.secret=test-secret-key-that-is-at-least-32-bytes-long")
public class EngineSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private EngineRunnerService engineRunnerService;

    @MockBean
    private AppUserRepository appUserRepository;

    // Required by the AuthenticationManager bean declared in SecurityConfig.
    @MockBean
    private AppUserDetailsService appUserDetailsService;

    @Test
    public void anonymous_isUnauthorized() throws Exception {
        mockMvc.perform(get("/api/admin/engine/status"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "READER")
    public void reader_isForbidden() throws Exception {
        mockMvc.perform(post("/api/admin/engine/run"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "WRITER")
    public void writer_isForbidden() throws Exception {
        mockMvc.perform(post("/api/admin/engine/run"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    public void admin_canAccess() throws Exception {
        when(engineRunnerService.getSnapshot()).thenReturn(
                new EngineRunnerService.Snapshot(EngineRunnerService.State.IDLE, null, null, null, List.of()));
        mockMvc.perform(get("/api/admin/engine/status"))
                .andExpect(status().isOk());
    }
}
