package com.example.domain;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Objects;

public class Group {
    private final String id;
    private final String name;
    private final Set<String> courseNames;
    // This group's curated set of acceptable rooms, keyed by room type (e.g.
    // "Standard" -> a handful of adjacent classrooms this group can move
    // between for its Core courses). A room type with no entry here is
    // unrestricted for this group - see
    // CourseBlockAssignment.getMatchingRooms(), which falls through to the
    // full type-filtered room list in that case. A room type mapped to a
    // single-room list behaves exactly like this group being "fixed" to that
    // one room; 2+ rooms is a narrowed but still movable set.
    private final Map<String, List<Room>> roomRangesByType;

    // Optional student headcount. Null when unknown/not tracked, in which case
    // the room-capacity soft constraint is skipped for this group.
    private final Integer studentCount;

    public Group(String id, String name, Set<String> courseNames) {
        this(id, name, courseNames, null);
    }

    public Group(String id, String name, Set<String> courseNames, Map<String, List<Room>> roomRangesByType) {
        this(id, name, courseNames, roomRangesByType, null);
    }

    public Group(String id, String name, Set<String> courseNames, Map<String, List<Room>> roomRangesByType,
            Integer studentCount) {
        this.id = id;
        this.name = name;
        this.courseNames = courseNames;
        this.roomRangesByType = roomRangesByType;
        this.studentCount = studentCount;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public Set<String> getCourseNames() {
        return courseNames;
    }

    /**
     * This group's curated acceptable rooms for the given room type, or
     * {@code null} when the group hasn't curated a range for that type (as
     * opposed to an empty list, which would mean "curated but resolved to
     * nothing" - callers use the {@code null} case to fall through to the
     * unrestricted, full type-filtered room list instead). A block with no
     * satisfiesRoomType of its own (roomType == null) can never match a
     * curated range, since ranges are always stored under a specific type
     * key - guarded explicitly here rather than relying on Map#get(null),
     * which throws for the immutable maps (Map.of(...)) some callers use.
     */
    public List<Room> getAcceptableRooms(String roomType) {
        return (roomRangesByType == null || roomType == null) ? null : roomRangesByType.get(roomType);
    }

    public Integer getStudentCount() {
        return studentCount;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        Group group = (Group) o;
        return Objects.equals(id, group.id);
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
