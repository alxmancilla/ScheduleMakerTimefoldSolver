package com.example.domain;

import ai.timefold.solver.core.api.domain.entity.PlanningEntity;
import ai.timefold.solver.core.api.domain.entity.PlanningPin;
import ai.timefold.solver.core.api.domain.valuerange.ValueRangeProvider;
import ai.timefold.solver.core.api.domain.variable.PlanningVariable;
import ai.timefold.solver.core.api.domain.lookup.PlanningId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@PlanningEntity(difficultyComparatorClass = com.example.solver.BlockLengthDifficultyComparator.class)
public class CourseBlockAssignment {
    @PlanningId
    private String id;
    private Group group;
    private Course course;

    private int blockLength;

    @PlanningPin
    private boolean pinned;

    // @PlanningVariable(valueRangeProviderRefs = { "teacherRange" })
    private Teacher teacher;

    @PlanningVariable(valueRangeProviderRefs = { "matchingBlockTimeslotRange" })
    private BlockTimeslot timeslot;

    // Not a planning variable or shadow variable - just a reference to the
    // solution's full timeslot list, set once by the data-loading path
    // (DataLoader/DemoDataGenerator) so getMatchingBlockTimeslots() below can
    // filter it. This is the standard Timefold pattern for a planning
    // variable whose valid values depend on another field of the same
    // entity: the value range lives on the entity, not the solution, so a
    // 1h block's ChangeMove candidates can never include a 3h timeslot in
    // the first place - no move filter or pre-fill phase has to reject it
    // after the fact.
    private List<BlockTimeslot> allTimeslots;

    @PlanningVariable(valueRangeProviderRefs = { "matchingRoomRange" }, allowsUnassigned = true)
    private Room room;

    // Not a planning variable or shadow variable - the full room pool, set once
    // by the data-loading path (DataLoader/DemoDataGenerator), mirroring
    // allTimeslots above. getMatchingRooms() below filters it down per entity:
    // to a singleton (this entity's own current room) when the room is "fixed"
    // (see isRoomFixed()), or to the full type-compatible list otherwise - so a
    // fixed entity's room is structurally unreachable to change, the same way
    // a block-length mismatch is structurally unreachable for timeslot.
    private List<Room> allRooms;

    // NEW: Support for dual room requirements and custom block decomposition
    private String satisfiesRoomType; // Which room requirement this block satisfies
    private String preferredRoomHint; // Preferred room for soft constraint optimization

    // Not planning variables - shared per-teacher/per-group maps of the
    // timeslots already committed by PINNED blocks, built once by DataLoader
    // in a second pass after every row is read (which rows are pinned isn't
    // known until the whole table has been scanned) and set identically on
    // every assignment, mirroring allTimeslots/allRooms above. Used by
    // getMatchingBlockTimeslots() to exclude, from a non-pinned assignment's
    // own value range, any timeslot that would double-book its own teacher
    // or clash its own group against a DIFFERENT, fixed pinned block - the
    // same "structurally unreachable, not just scored" treatment already
    // given to block-length mismatches and the HARD semester hour limit
    // below. MatchingLengthSwapFilter also consults getPinnedOccupiedTimeslots()
    // directly: SwapMove exchanges values without consulting either entity's
    // own value range (see that filter's class doc), so this exclusion needs
    // enforcing there too, not just here.
    private Map<String, List<BlockTimeslot>> pinnedTimeslotsByTeacherId;
    private Map<String, List<BlockTimeslot>> pinnedTimeslotsByGroupId;

    public CourseBlockAssignment() {
        // No-arg constructor required by Timefold
    }

    public CourseBlockAssignment(String id, Group group, Course course, int blockLength) {
        this.id = id;
        this.group = group;
        this.course = course;
        this.blockLength = blockLength;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public Group getGroup() {
        return group;
    }

    public void setGroup(Group group) {
        this.group = group;
    }

    public Course getCourse() {
        return course;
    }

    public void setCourse(Course course) {
        this.course = course;
    }

    public int getBlockLength() {
        return blockLength;
    }

    public void setBlockLength(int blockLength) {
        this.blockLength = blockLength;
    }

    public Teacher getTeacher() {
        return teacher;
    }

    public void setTeacher(Teacher teacher) {
        this.teacher = teacher;
    }

    public BlockTimeslot getTimeslot() {
        return timeslot;
    }

    public void setTimeslot(BlockTimeslot timeslot) {
        this.timeslot = timeslot;
    }

    public List<BlockTimeslot> getAllTimeslots() {
        return allTimeslots;
    }

    public void setAllTimeslots(List<BlockTimeslot> allTimeslots) {
        this.allTimeslots = allTimeslots;
    }

    public void setPinnedTimeslotsByTeacherId(Map<String, List<BlockTimeslot>> pinnedTimeslotsByTeacherId) {
        this.pinnedTimeslotsByTeacherId = pinnedTimeslotsByTeacherId;
    }

    public void setPinnedTimeslotsByGroupId(Map<String, List<BlockTimeslot>> pinnedTimeslotsByGroupId) {
        this.pinnedTimeslotsByGroupId = pinnedTimeslotsByGroupId;
    }

    /**
     * The timeslots this (non-pinned) assignment's own teacher and/or group
     * already has committed to via a DIFFERENT pinned block - the set
     * getMatchingBlockTimeslots() below excludes from this assignment's own
     * value range. Public (not just used internally) because
     * MatchingLengthSwapFilter needs the same list to reject a SwapMove that
     * would hand this assignment one of these timeslots - SwapMove exchanges
     * values directly without consulting either entity's own value range, so
     * the exclusion below alone doesn't reach it.
     *
     * <p>
     * Always empty for a pinned assignment: Timefold's {@code @PlanningPin}
     * means this assignment's own timeslot/room variables are never
     * reassigned by the solver in the first place, so there is nothing to
     * exclude from an irrelevant value range.
     */
    public List<BlockTimeslot> getPinnedOccupiedTimeslots() {
        if (pinned) {
            return Collections.emptyList();
        }
        List<BlockTimeslot> occupied = new ArrayList<>();
        if (teacher != null && pinnedTimeslotsByTeacherId != null) {
            occupied.addAll(pinnedTimeslotsByTeacherId.getOrDefault(teacher.getId(), Collections.emptyList()));
        }
        if (group != null && pinnedTimeslotsByGroupId != null) {
            occupied.addAll(pinnedTimeslotsByGroupId.getOrDefault(group.getId(), Collections.emptyList()));
        }
        return occupied;
    }

    /**
     * This block's valid timeslot candidates: every timeslot whose length
     * matches blockLength; that doesn't overlap a timeslot this block's own
     * teacher or group already has pinned elsewhere (see
     * getPinnedOccupiedTimeslots()); and - if this block's course has a
     * HARD-severity semester_hour_limit configured (see Course.getLatestEndHour()/
     * getLatestEndHourSeverity(), sourced from the semester_hour_limit
     * table) - whose end hour doesn't run past that limit. A SOFT-severity
     * limit does NOT filter here; the solver may still place such a block
     * past its limit, penalized instead by the soft constraint
     * preferSemesterHourLimits (see BlockScheduleMath.softSemesterHourLimitExcess()).
     * Entity-scoped value range, so a HARD limit's mismatch is something the
     * solver can never pick in the first place: a 1h block's candidates can
     * never include a 3h timeslot (that mismatch is also the HARD constraint
     * blockLengthMustMatchTimeslotLength), and a HARD-limited course's block
     * candidates can never include a slot ending after its limit (mirrored
     * by the HARD constraint semesterHourLimitsMustBeRespected, which exists
     * only to catch a pinned row whose timeslot predates the limit - see
     * that constraint's doc). The pinned-occupancy exclusion is the same
     * treatment for a different fact: a slot that's guaranteed to
     * double-book this block's teacher or clash its group against fixed,
     * unmovable data is exactly as unreachable as a length mismatch.
     */
    @ValueRangeProvider(id = "matchingBlockTimeslotRange")
    public List<BlockTimeslot> getMatchingBlockTimeslots() {
        if (allTimeslots == null) {
            return Collections.emptyList();
        }
        Integer hardLatestEndHour = course != null && "HARD".equals(course.getLatestEndHourSeverity())
                ? course.getLatestEndHour()
                : null;
        List<BlockTimeslot> pinnedOccupied = getPinnedOccupiedTimeslots();
        List<BlockTimeslot> matching = new ArrayList<>();
        for (BlockTimeslot candidate : allTimeslots) {
            if (candidate.getLengthHours() != blockLength) {
                continue;
            }
            if (hardLatestEndHour != null
                    && candidate.getStartHour() + candidate.getLengthHours() > hardLatestEndHour) {
                continue;
            }
            if (overlapsAny(candidate, pinnedOccupied)) {
                continue;
            }
            matching.add(candidate);
        }
        return matching;
    }

    private static boolean overlapsAny(BlockTimeslot candidate, List<BlockTimeslot> others) {
        for (BlockTimeslot other : others) {
            if (BlockScheduleMath.blocksOverlap(candidate, other)) {
                return true;
            }
        }
        return false;
    }

    /** True when this block belongs to a first-semester (semester == 1) course. */
    public boolean isSemesterOne() {
        return course != null && Integer.valueOf(1).equals(course.getSemester());
    }

    public Room getRoom() {
        return room;
    }

    public void setRoom(Room room) {
        this.room = room;
    }

    public List<Room> getAllRooms() {
        return allRooms;
    }

    public void setAllRooms(List<Room> allRooms) {
        this.allRooms = allRooms;
    }

    /**
     * Whether this block's room has already been decided outside the solver:
     * true when the teacher has a required room, or the group's curated
     * range for this block's satisfiesRoomType resolves to exactly one
     * compatible room (the teacher's requirement overrides the group's range
     * - see BlockGenerationService.defaultRoomFor() in the web module for the
     * same priority order) - but only when that fixed room's type actually
     * satisfies this block's own satisfiesRoomType. A teacher who mostly
     * teaches one room type but also has an occasional course of a different
     * type (e.g. a workshop teacher who also covers one computer-lab course)
     * shouldn't have their requiredRoomName blanket-lock every block they
     * teach; when it doesn't apply here, this falls through to the group's
     * range exactly as if the teacher had no requirement at all (and that, in
     * turn, falls through to "not fixed" if the range is undefined for this
     * type, empty, incompatible, or has more than one acceptable room - see
     * getMatchingRooms()). Used both by getMatchingRooms() below (to
     * collapse the room value range to a singleton) and by
     * BlockLengthDifficultyComparator (to schedule fixed-room blocks before
     * movable ones).
     */
    public boolean isRoomFixed() {
        if (teacher != null && teacher.getRequiredRoomName() != null && teacherRequiredRoomAppliesHere()) {
            return true;
        }
        List<Room> groupRange = resolveGroupAcceptableRooms();
        return groupRange != null && groupRange.size() == 1;
    }

    /**
     * The group's curated range for this block's satisfiesRoomType, filtered
     * to rooms that actually satisfy it, or {@code null} when the group has
     * no range for this type (falls through to the unrestricted list),
     * curated a range that resolves to nothing after filtering, or has no
     * group at all.
     */
    private List<Room> resolveGroupAcceptableRooms() {
        if (group == null) {
            return null;
        }
        List<Room> range = group.getAcceptableRooms(satisfiesRoomType);
        if (range == null || range.isEmpty()) {
            return null;
        }
        List<Room> compatible = new ArrayList<>();
        for (Room candidate : range) {
            if (roomSatisfiesType(candidate)) {
                compatible.add(candidate);
            }
        }
        return compatible.isEmpty() ? null : compatible;
    }

    /**
     * True when the teacher's required room should be treated as fixing this
     * block: either it satisfies satisfiesRoomType, or compatibility can't be
     * verified yet (allRooms not wired up - e.g. a plain domain-object test -
     * or the name is a dangling reference to no known room, a data error
     * that should surface as "fixed to an empty range" via getMatchingRooms()
     * rather than silently falling back to the group's preference).
     */
    private boolean teacherRequiredRoomAppliesHere() {
        Room required = resolveTeacherRequiredRoomObject();
        return required == null || roomSatisfiesType(required);
    }

    private Room resolveTeacherRequiredRoomObject() {
        if (allRooms == null || teacher == null || teacher.getRequiredRoomName() == null) {
            return null;
        }
        for (Room candidate : allRooms) {
            if (candidate.getName().equals(teacher.getRequiredRoomName())) {
                return candidate;
            }
        }
        return null;
    }

    private boolean roomSatisfiesType(Room room) {
        return satisfiesRoomType == null || room.satisfiesRequirement(satisfiesRoomType);
    }

    /**
     * True when the teacher has a required room AND it genuinely governs
     * this block (satisfies satisfiesRoomType, or compatibility can't be
     * determined) - as opposed to a blanket requirement the compatibility
     * fallback would skip for this particular course (see isRoomFixed()).
     * Used by SchoolConstraintProvider.teacherRequiredRoomMustBeUsed and its
     * BlockScheduleAnalyzer mirror to only flag a room that disagrees with a
     * requirement that actually applies here - not one the fallback would
     * have ignored anyway.
     */
    public boolean isTeacherRequiredRoomApplicable() {
        return teacher != null && teacher.getRequiredRoomName() != null && teacherRequiredRoomAppliesHere();
    }

    /**
     * This block's valid room candidates, in priority order: the teacher's
     * required room (singleton) if it applies here; otherwise the group's
     * curated range for this block's satisfiesRoomType (singleton if it
     * resolves to exactly one compatible room, or the whole filtered set if
     * more than one - a narrowed but still movable range); otherwise every
     * room whose type satisfies this block's requirement (the unrestricted
     * fallback, used whenever the group hasn't curated a range for this
     * type). Room objects come from stable, solver-untouched facts -
     * group.getAcceptableRooms() (already resolved Room references) or
     * teacher.getRequiredRoomName() (resolved against allRooms by name) -
     * deliberately NOT this entity's own current room field. Timefold's
     * construction heuristic nulls a variable before consulting its own
     * value-range provider to decide what to assign it, so a fixed-branch
     * that read `room` here would always see null and return an empty range,
     * permanently unassigning every fixed block (confirmed empirically: an
     * earlier version of this method did exactly that and left ~400 fixed
     * blocks roomless after one real solve).
     */
    @ValueRangeProvider(id = "matchingRoomRange")
    public List<Room> getMatchingRooms() {
        if (allRooms == null) {
            return Collections.emptyList();
        }
        // Teacher's required room takes precedence over the group's range
        // (matches BlockGenerationService.defaultRoomFor()'s priority in the
        // web module: teacher's required room > group's range) - but only
        // when it actually satisfies this block's satisfiesRoomType. When it
        // doesn't, fall through to the group's range below instead of
        // locking this block to a room of the wrong type.
        if (teacher != null && teacher.getRequiredRoomName() != null) {
            Room required = null;
            boolean found = false;
            for (Room candidate : allRooms) {
                if (candidate.getName().equals(teacher.getRequiredRoomName())) {
                    required = candidate;
                    found = true;
                    break;
                }
            }
            if (!found) {
                // Dangling required-room reference (data error) - surface it
                // as permanently unassigned rather than silently falling
                // back to the group's range, which would mask the bad data.
                return Collections.emptyList();
            }
            if (roomSatisfiesType(required)) {
                return Collections.singletonList(required);
            }
            // else: wrong type for this block - fall through below, exactly
            // as if the teacher had no requirement at all.
        }
        List<Room> groupRange = resolveGroupAcceptableRooms();
        if (groupRange != null) {
            return groupRange.size() == 1 ? Collections.singletonList(groupRange.get(0)) : groupRange;
        }
        if (satisfiesRoomType == null) {
            return allRooms;
        }
        List<Room> matching = new ArrayList<>();
        for (Room candidate : allRooms) {
            if (candidate.satisfiesRequirement(satisfiesRoomType)) {
                matching.add(candidate);
            }
        }
        return matching;
    }

    public boolean isPinned() {
        return pinned;
    }

    public void setPinned(boolean pinned) {
        this.pinned = pinned;
    }

    // NEW: Getters and setters for room requirement fields

    public String getSatisfiesRoomType() {
        return satisfiesRoomType;
    }

    public void setSatisfiesRoomType(String satisfiesRoomType) {
        this.satisfiesRoomType = satisfiesRoomType;
    }

    public String getPreferredRoomHint() {
        return preferredRoomHint;
    }

    public void setPreferredRoomHint(String preferredRoomHint) {
        this.preferredRoomHint = preferredRoomHint;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        CourseBlockAssignment that = (CourseBlockAssignment) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return String.format("%s [%s] slot=%s teacher=%s room=%s %s blockLength=%d",
                id, course, timeslot, teacher, room, pinned ? "PINNED" : "UNPINNED", blockLength);
    }
}
