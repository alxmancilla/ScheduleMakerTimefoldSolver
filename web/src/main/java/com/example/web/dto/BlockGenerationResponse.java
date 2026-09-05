package com.example.web.dto;

import com.example.web.service.BlockGenerationService;

import java.util.List;

/**
 * Result of an admin-triggered "Generate Blocks" run: how many new unassigned
 * course_block_assignment rows were created, how many (group, course) pairs
 * were skipped because they already had blocks, any courses referenced by a
 * group that couldn't be found, and which portions got a shape other than
 * their naive one (margin adaptation or Core's minimal-upgrade preference
 * actually did something) - added 2026-09-05 so a scheduler can see what was
 * reshaped without reconstructing it from the database by hand.
 */
public class BlockGenerationResponse {

    private final int blocksCreated;
    private final int groupCoursesSkippedExisting;
    private final List<String> warnings;
    private final List<String> adjustments;

    public BlockGenerationResponse(BlockGenerationService.GenerationResult result) {
        this.blocksCreated = result.getBlocksCreated();
        this.groupCoursesSkippedExisting = result.getGroupCoursesSkippedExisting();
        this.warnings = result.getWarnings();
        this.adjustments = result.getAdjustments();
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

    public List<String> getAdjustments() {
        return adjustments;
    }
}
