package com.example.web.dto;

/**
 * Optional per-run options for POST /api/admin/blocks/generate. The whole body
 * may be omitted, in which case every option takes its default - so the
 * endpoint stays callable with no body at all, as it was before this existed.
 */
public class BlockGenerationRequest {

    /**
     * true also places and PINS the blocks of any (group, course) pairing whose
     * teacher has no other load at all (see
     * BlockGenerationService.tryPinExclusiveTeacherBlocks). Defaults to
     * false/null - off.
     *
     * <p>Opt-in because it is the only part of block generation that makes an
     * irreversible commitment. A pin bypasses the solver's constraint checking
     * entirely, and the placement is picked greedily from the TEACHER's
     * calendar with no awareness of the GROUP's other blocks - observed live on
     * 5A-TEC, where blocks pinned Monday 11:00-15:00 left a 10:00-11:00 hole
     * the solver can neither move nor even score, since a pinned block is
     * excluded from the idle-gap constraints. It also buys little: teacher
     * availability, room type and the HARD semester hour limit are all
     * enforced by the solver anyway, so this only removes blocks from the
     * search (7 of 551 on the live dataset).
     *
     * <p>Availability-aware block SHAPING and the shared per-teacher calendar
     * are unconditional and unaffected by this flag - it gates placement and
     * pinning only.
     */
    private Boolean pinExclusiveTeacherBlocks;

    public Boolean getPinExclusiveTeacherBlocks() {
        return pinExclusiveTeacherBlocks;
    }

    public void setPinExclusiveTeacherBlocks(Boolean pinExclusiveTeacherBlocks) {
        this.pinExclusiveTeacherBlocks = pinExclusiveTeacherBlocks;
    }
}
