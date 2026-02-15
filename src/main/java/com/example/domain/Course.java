package com.example.domain;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

public class Course {
    private final String id;
    private final String name;
    private final String abbreviation;
    private final String semester; // 'I', 'II', 'III', 'IV', 'V', 'VI'
    private final String component; // 'BASICAS', 'TADHR','TEM'
    private final int requiredHoursPerWeek;
    private final String roomRequirement; // 'standard', 'science_lab' (legacy - use roomRequirements instead)
    private final Boolean active;

    // NEW: Support for dual room requirements and custom block decomposition
    private List<RoomRequirement> roomRequirements;
    private List<BlockTemplate> blockTemplates;

    public Course(String id, String name, String abbreviation, String semester, String component,
            String roomRequirement, int requiredHoursPerWeek, Boolean active) {
        this.id = id;
        this.name = name;
        this.abbreviation = abbreviation;
        this.semester = semester;
        this.component = component;
        this.roomRequirement = roomRequirement;
        this.requiredHoursPerWeek = requiredHoursPerWeek;
        this.active = active;
    }

    // Backwards-compatible constructor: generate an id from the name.
    public Course(String name, String roomRequirement, int requiredHoursPerWeek) {
        this(sanitizeId("c", null), name, "", "", "", roomRequirement, requiredHoursPerWeek, Boolean.TRUE);
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

    public String getSemester() {
        return semester;
    }

    public String getComponent() {
        return component;
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
