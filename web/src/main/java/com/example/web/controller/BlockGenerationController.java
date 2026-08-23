package com.example.web.controller;

import com.example.web.dto.BlockGenerationResponse;
import com.example.web.dto.ClearTimeslotsResponse;
import com.example.web.service.BlockGenerationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Admin-only trigger for generating unassigned schedule blocks from the
 * current Groups/Group_Courses/Courses data, and for bulk-clearing solved
 * timeslots. Mounted under /api/admin/**, which SecurityConfig already
 * restricts to the ADMIN role.
 */
@RestController
@RequestMapping("/api/admin/blocks")
public class BlockGenerationController {

    @Autowired
    private BlockGenerationService blockGenerationService;

    @PostMapping("/generate")
    public BlockGenerationResponse generateBlocks() {
        return new BlockGenerationResponse(blockGenerationService.generateBlocks());
    }

    @PostMapping("/clear-timeslots")
    public ClearTimeslotsResponse clearUnpinnedTimeslots() {
        return new ClearTimeslotsResponse(blockGenerationService.clearUnpinnedTimeslots());
    }
}
