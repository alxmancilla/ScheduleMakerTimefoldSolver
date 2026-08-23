package com.example.web.dto;

/**
 * Result of an admin-triggered "Clear Unpinned Timeslots" run: how many
 * course_block_assignment rows had their block_timeslot_id cleared. Pinned
 * rows are never touched.
 */
public class ClearTimeslotsResponse {

    private final int clearedCount;

    public ClearTimeslotsResponse(int clearedCount) {
        this.clearedCount = clearedCount;
    }

    public int getClearedCount() {
        return clearedCount;
    }
}
