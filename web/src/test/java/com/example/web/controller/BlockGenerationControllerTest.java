package com.example.web.controller;

import com.example.web.service.BlockGenerationService;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Web-layer tests for {@link BlockGenerationController}. Uses the MVC slice
 * with a mocked BlockGenerationService (see BlockGenerationServiceTest for
 * the decomposition logic itself).
 */
@RunWith(SpringRunner.class)
@WebMvcTest(BlockGenerationController.class)
@AutoConfigureMockMvc(addFilters = false)
public class BlockGenerationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private BlockGenerationService blockGenerationService;

    @Test
    public void generateBlocks_returnsCounts() throws Exception {
        when(blockGenerationService.generateBlocks())
                .thenReturn(new BlockGenerationService.GenerationResult(12, 3, List.of()));

        mockMvc.perform(post("/api/admin/blocks/generate"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.blocksCreated").value(12))
                .andExpect(jsonPath("$.groupCoursesSkippedExisting").value(3))
                .andExpect(jsonPath("$.warnings").isEmpty());
    }

    @Test
    public void generateBlocks_withWarnings_returnsThem() throws Exception {
        when(blockGenerationService.generateBlocks())
                .thenReturn(new BlockGenerationService.GenerationResult(0, 0,
                        List.of("Group 'G1': course 'Ghost' not found, skipped")));

        mockMvc.perform(post("/api/admin/blocks/generate"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.warnings[0]").value("Group 'G1': course 'Ghost' not found, skipped"));
    }
}
