package com.example.domain;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

public class Course {
    private final String id;
    private final String name;
    private final String abbreviation;
    private final Integer semester; // 1-12
    private final String designation; // 'Core', 'Elective', 'TEM'
    private final int requiredHoursPerWeek;
    private final String roomRequirement; // 'standard', 'science_lab' (legacy - use roomRequirements instead)
    private final Boolean active;

    // NEW: Support for dual room requirements and custom block decomposition
    private List<RoomRequirement> roomRequirements;
    private List<BlockTemplate> blockTemplates;

    // Per-designation HARD limit on how many blocks of this course may land on the
    // same day for the same group, sourced from component_block_rule. Null until
    // DataLoader populates it; the solver falls back to a code default when null.
    private Integer maxBlocksPerDay;

    // Per-semester "must/should finish by this hour" limit, sourced from
    // semester_hour_limit (keyed by course.semester, not course.id - every
    // course of the same semester shares the same limit). Null until
    // DataLoader populates it, meaning "no limit for this semester" - the
    // same "absent row = unrestricted" convention as maxBlocksPerDay above.
    // latestEndHourSeverity is "HARD" (structurally excluded from this
    // course's blocks' timeslot value range - see
    // CourseBlockAssignment.getMatchingBlockTimeslots()) or "SOFT" (blocks
    // may still land past the limit, but SchoolConstraintProvider's soft
    // constraint penalizes it) - see BlockScheduleMath's
    // violatesHardSemesterHourLimit()/softSemesterHourLimitExcess().
    private Integer latestEndHour;
    private String latestEndHourSeverity;

    public Course(String id, String name, String abbreviation, Integer semester, String designation,
            String roomRequirement, int requiredHoursPerWeek, Boolean active) {
        this.id = id;
        this.name = name;
        this.abbreviation = abbreviation;
        this.semester = semester;
        this.designation = designation;
        this.roomRequirement = roomRequirement;
        this.requiredHoursPerWeek = requiredHoursPerWeek;
        this.active = active;
    }

    // Backwards-compatible constructor: generate an id from the name.
    public Course(String name, String roomRequirement, int requiredHoursPerWeek) {
        this(sanitizeId("c", null), name, "", null, "", roomRequirement, requiredHoursPerWeek, Boolean.TRUE);
    }

    private static String sanitizeId(String prefix, String name) {
        if (name == null)
            return prefix + "_" + UUID.randomUUID().toString();
        String s = name.replaceAll("[^A-Za-z0-9]", "_").toLowerCase();
        return prefix + "_" + s;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getAbbreviation() {
        return abbreviation;
    }

    public Integer getSemester() {
        return semester;
    }

    public String getDesignation() {
        return designation;
    }

    public String getRoomRequirement() {
        return roomRequirement;
    }

    public int getRequiredHoursPerWeek() {
        return requiredHoursPerWeek;
    }

    public Boolean getActive() {
        return active;
    }

    // NEW: Getters and setters for room requirements and block templates

    public List<RoomRequirement> getRoomRequirements() {
        return roomRequirements;
    }

    public void setRoomRequirements(List<RoomRequirement> roomRequirements) {
        this.roomRequirements = roomRequirements;
    }

    public List<BlockTemplate> getBlockTemplates() {
        return blockTemplates;
    }

    public void setBlockTemplates(List<BlockTemplate> blockTemplates) {
        this.blockTemplates = blockTemplates;
    }

    public Integer getMaxBlocksPerDay() {
        return maxBlocksPerDay;
    }

    public void setMaxBlocksPerDay(Integer maxBlocksPerDay) {
        this.maxBlocksPerDay = maxBlocksPerDay;
    }

    public Integer getLatestEndHour() {
        return latestEndHour;
    }

    public void setLatestEndHour(Integer latestEndHour) {
        this.latestEndHour = latestEndHour;
    }

    public String getLatestEndHourSeverity() {
        return latestEndHourSeverity;
    }

    public void setLatestEndHourSeverity(String latestEndHourSeverity) {
        this.latestEndHourSeverity = latestEndHourSeverity;
    }

    // NEW: Helper methods

    /**
     * Check if this course has custom block decomposition defined.
     * 
     * @return true if custom templates exist, false otherwise
     */
    public boolean hasCustomDecomposition() {
        return blockTemplates != null && !blockTemplates.isEmpty();
    }

    /**
     * Get the preferred room for a specific room type.
     * 
     * @param roomType the room type to search for
     * @return the preferred room name, or null if not found
     */
    public String getPreferredRoomForType(String roomType) {
        if (roomRequirements == null) {
            return null;
        }
        return roomRequirements.stream()
                .filter(r -> r.getRoomType().equals(roomType))
                .findFirst()
                .map(RoomRequirement::getDefaultPreferredRoom)
                .orElse(null);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        Course course = (Course) o;
        return Objects.equals(id, course.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return name;
    }
}
