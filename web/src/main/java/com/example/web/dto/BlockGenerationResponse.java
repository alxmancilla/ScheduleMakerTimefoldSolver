package com.example.web.dto;

import com.example.web.service.BlockGenerationService;

import java.util.List;

/**
 * Result of an admin-triggered "Generate Blocks" run: how many new unassigned
 * course_block_assignment rows were created, how many (group, course) pairs
 * were skipped because they already had blocks, and any courses referenced
 * by a group that couldn't be found.
 */
public class BlockGenerationResponse {

    private final int blocksCreated;
    private final int groupCoursesSkippedExisting;
    private final List<String> warnings;

    public BlockGenerationResponse(BlockGenerationService.GenerationResult result) {
        this.blocksCreated = result.getBlocksCreated();
        this.groupCoursesSkippedExisting = result.getGroupCoursesSkippedExisting();
        this.warnings = result.getWarnings();
    }

    public int getBlocksCreated() {
        return blocksCreated;
    }

    public int getGroupCoursesSkippedExisting() {
        return groupCoursesSkippedExisting;
    }

    public List<String> getWarnings() {
        return warnings;
    }
}
