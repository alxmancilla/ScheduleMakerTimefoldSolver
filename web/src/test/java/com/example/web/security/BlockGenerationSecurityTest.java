package com.example.web.security;

import com.example.web.controller.BlockGenerationController;
import com.example.web.service.BlockGenerationService;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Confirms /api/admin/blocks is gated to ADMIN: READER and WRITER get 403,
 * ADMIN gets through to the controller.
 */
@RunWith(SpringRunner.class)
@WebMvcTest(BlockGenerationController.class)
@Import(SecurityConfig.class)
@TestPropertySource(properties = "app.jwt.secret=test-secret-key-that-is-at-least-32-bytes-long")
public class BlockGenerationSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private BlockGenerationService blockGenerationService;

    // Required by the AuthenticationManager bean declared in SecurityConfig.
    @MockBean
    private AppUserDetailsService appUserDetailsService;

    @Test
    public void anonymous_isUnauthorized() throws Exception {
        mockMvc.perform(post("/api/admin/blocks/generate"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "READER")
    public void reader_isForbidden() throws Exception {
        mockMvc.perform(post("/api/admin/blocks/generate"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "WRITER")
    public void writer_isForbidden() throws Exception {
        mockMvc.perform(post("/api/admin/blocks/generate"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    public void admin_canAccess() throws Exception {
        when(blockGenerationService.generateBlocks())
                .thenReturn(new BlockGenerationService.GenerationResult(0, 0, List.of()));
        mockMvc.perform(post("/api/admin/blocks/generate"))
                .andExpect(status().isOk());
    }
}
