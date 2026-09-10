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

    /**
     * No body at all - the shape every caller predating BlockGenerationRequest
     * used, and what the UI sends with the pinning box unticked. Must route to
     * the pinning-off overload.
     */
    @Test
    public void generateBlocks_withNoBody_doesNotOptIntoPinning() throws Exception {
        when(blockGenerationService.generateBlocks(false))
                .thenReturn(new BlockGenerationService.GenerationResult(4, 0, List.of()));

        mockMvc.perform(post("/api/admin/blocks/generate"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.blocksCreated").value(4));

        org.mockito.Mockito.verify(blockGenerationService).generateBlocks(false);
    }

    @Test
    public void generateBlocks_pinExclusiveTeacherBlocksTrue_isPassedThrough() throws Exception {
        when(blockGenerationService.generateBlocks(true))
                .thenReturn(new BlockGenerationService.GenerationResult(4, 0, List.of()));

        mockMvc.perform(post("/api/admin/blocks/generate")
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .content("{\"pinExclusiveTeacherBlocks\":true}"))
                .andExpect(status().isOk());

        org.mockito.Mockito.verify(blockGenerationService).generateBlocks(true);
    }

    /** An explicit false, and a body that omits the field, both mean off. */
    @Test
    public void generateBlocks_explicitFalseOrOmittedField_bothMeanOff() throws Exception {
        when(blockGenerationService.generateBlocks(false))
                .thenReturn(new BlockGenerationService.GenerationResult(0, 0, List.of()));

        mockMvc.perform(post("/api/admin/blocks/generate")
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .content("{\"pinExclusiveTeacherBlocks\":false}"))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/admin/blocks/generate")
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .content("{}"))
                .andExpect(status().isOk());

        org.mockito.Mockito.verify(blockGenerationService, org.mockito.Mockito.times(2)).generateBlocks(false);
    }

    @Test
    public void generateBlocks_returnsCounts() throws Exception {
        when(blockGenerationService.generateBlocks(false))
                .thenReturn(new BlockGenerationService.GenerationResult(12, 3, List.of()));

        mockMvc.perform(post("/api/admin/blocks/generate"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.blocksCreated").value(12))
                .andExpect(jsonPath("$.groupCoursesSkippedExisting").value(3))
                .andExpect(jsonPath("$.warnings").isEmpty());
    }

    @Test
    public void generateBlocks_withWarnings_returnsThem() throws Exception {
        when(blockGenerationService.generateBlocks(false))
                .thenReturn(new BlockGenerationService.GenerationResult(0, 0,
                        List.of("Group 'G1': course 'Ghost' not found, skipped")));

        mockMvc.perform(post("/api/admin/blocks/generate"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.warnings[0]").value("Group 'G1': course 'Ghost' not found, skipped"));
    }

    @Test
    public void clearUnpinnedTimeslots_returnsClearedCount() throws Exception {
        when(blockGenerationService.clearUnpinnedTimeslots()).thenReturn(42);

        mockMvc.perform(post("/api/admin/blocks/clear-timeslots"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.clearedCount").value(42));
    }
}
