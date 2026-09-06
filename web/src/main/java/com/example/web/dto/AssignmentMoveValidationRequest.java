package com.example.web.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * A candidate "move this block" or "pin it here" action for
 * {@code POST /api/assignments/{id}/validate-move} - the Schedule grid's
 * move/pin editor calls this before Save to check whether the target
 * timeslot would violate a hard constraint, without actually committing
 * anything. Only the two fields the editor actually offers changing;
 * everything else about the assignment (teacher, room, group, course) is
 * read from the existing row.
 */
public class AssignmentMoveValidationRequest {

    @NotBlank(message = "Block timeslot ID is required")
    private String blockTimeslotId;

    private boolean pinned;

    public String getBlockTimeslotId() {
        return blockTimeslotId;
    }

    public void setBlockTimeslotId(String blockTimeslotId) {
        this.blockTimeslotId = blockTimeslotId;
    }

    public boolean isPinned() {
        return pinned;
    }

    public void setPinned(boolean pinned) {
        this.pinned = pinned;
    }
}
