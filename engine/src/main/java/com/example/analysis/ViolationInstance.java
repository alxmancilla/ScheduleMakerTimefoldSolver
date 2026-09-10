package com.example.analysis;

import java.util.List;

/**
 * One violation instance from BlockScheduleAnalyzer's detailed analysis:
 * the same human-readable description the console/PDF report already
 * showed, plus the actual course_block_assignment id(s) involved - added
 * 2026-09-07 so persisted violation data (schedule_run_violation) can link
 * back to the specific grid card(s) it's about (Schedule.jsx) instead of
 * only being readable as free text. A violation involving a pair
 * (double-booking, a non-consecutive chain link, an idle-gap pair) carries
 * both ids; a single-assignment violation (room capacity, a semester hour
 * limit overrun) carries one; an aggregate violation across a teacher's
 * whole week (teacher exceeds max hours) carries every one of that
 * teacher's block ids, since any of them could be the one moved to fix it.
 */
public record ViolationInstance(List<String> assignmentIds, String description) {

    public static ViolationInstance of(String description, String... assignmentIds) {
        return new ViolationInstance(List.of(assignmentIds), description);
    }
}
