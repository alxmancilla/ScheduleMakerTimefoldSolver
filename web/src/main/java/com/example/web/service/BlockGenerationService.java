package com.example.web.service;

import com.example.web.entity.BlockTimeslotEntity;
import com.example.web.entity.ComponentBlockRuleEntity;
import com.example.web.entity.CourseBlockAssignmentEntity;
import com.example.web.entity.CourseBlockTemplateEntity;
import com.example.web.entity.CourseEntity;
import com.example.web.entity.CourseRoomRequirementEntity;
import com.example.web.entity.GroupCourseEntity;
import com.example.web.entity.RoomEntity;
import com.example.web.entity.StudentGroupEntity;
import com.example.web.entity.TeacherEntity;
import com.example.web.repository.BlockTimeslotRepository;
import com.example.web.repository.ComponentBlockRuleRepository;
import com.example.web.repository.CourseBlockAssignmentRepository;
import com.example.web.repository.CourseBlockTemplateRepository;
import com.example.web.repository.CourseRepository;
import com.example.web.entity.GroupRoomRangeEntity;
import com.example.web.repository.CourseRoomRequirementRepository;
import com.example.web.repository.GroupRoomRangeRepository;
import com.example.web.entity.SemesterHourLimitEntity;
import com.example.web.repository.RoomRepository;
import com.example.web.repository.SemesterHourLimitRepository;
import com.example.web.repository.StudentGroupRepository;
import com.example.web.repository.TeacherRepository;
import com.example.common.RoomTypeCompatibility;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Generates the unassigned course_block_assignment rows the solver actually
 * operates on, decomposing each (group, course) pair's hours into
 * block-length (1-4h) chunks. Without this step, a freshly imported problem
 * (Teachers/Courses/Rooms/Groups/Group_Courses only) has nothing for the
 * engine to solve, since DataLoader just reads existing
 * course_block_assignment rows rather than deriving them.
 *
 * Only fills gaps: a (group, course) pair that already has any block rows is
 * left untouched, so this is safe to re-run after adding a few new
 * group-course links without disturbing an already-solved schedule.
 *
 * Three sources feed a (group, course) pair's blocks, checked in this order:
 * 1. course_block_template rows (explicit, hand-authored blocks - a group's
 *    own template for a given blockIndex wins over a NULL-group/"applies to
 *    all groups" one for the same index). A template's preferredRoomName and
 *    preferredTimeslotId are pre-assigned onto the generated block directly
 *    (roomName, not just preferredRoomName), since room is never
 *    solver-assigned in this system - matching
 *    database/datasets/load_final_dataset_blocks.sql's generate_course_blocks().
 * 2. course_room_requirement rows (dual/multi room types, e.g. 4h in a
 *    computer center + 1h in a standard room): each requirement is decomposed
 *    separately, so the generated blocks carry the right
 *    satisfiesRoomType/preferredRoomName per portion.
 * 3. Neither: the single legacy CourseEntity.roomRequirement field for every
 *    block, as before dual requirements existed.
 *
 * Within tiers 2/3, each portion's hours are decomposed by greedily packing
 * component_block_rule's preferred block size for the course's component
 * (e.g. Core=1 -> every block is 1h), falling back to DEFAULT_BLOCK_SIZE
 * for any component with no configured rule - unless the resolved teacher's
 * own availability can't fit that shape (see decomposeHours), in which case
 * AvailabilityAwareBlockShaper is asked for a longer-block shape that does,
 * without ever pinning a specific day.
 *
 * When the resolved teacher's entire teaching load is this one (group,
 * course) pairing (no other group_course link uses them as default teacher,
 * and they have no pre-existing assignments at all), there's no real
 * placement decision left for the solver to make - each block's day/hour is
 * already forced by being the only room left in their calendar. In that case
 * (see tryPinExclusiveTeacherBlocks) the generated blocks are given a
 * concrete timeslot from the teacher's actual contiguous availability and
 * pinned outright, but only when every block also resolves a single
 * deterministic room and doesn't collide with anything the group already has
 * pinned - otherwise they're left exactly as generated, for the solver or a
 * human to place instead.
 *
 * Room defaulting: whenever a generated block would otherwise have no room
 * (no block-template preferredRoomName, no room-requirement
 * defaultPreferredRoom) and the group's curated room range
 * (group_room_range) for the block's satisfiesRoomType resolves to exactly
 * one type-compatible room - the same Standard/Specialized -
 * Workshop/Mixed compatibility convention as engine's
 * Room.satisfiesRequirement() - that room is used as roomName directly,
 * since room is never solver-assigned. A range of 2+ rooms has no single
 * deterministic choice, so the block is left roomless in that case. A
 * group's range never overrides a more specific room already supplied by a
 * template or room requirement.
 *
 * Teacher defaulting: every block generated for a (group, course) pair also
 * gets group_course.default_teacher_id (if set) as its teacher_id, since
 * teacher is likewise never solver-assigned. That column is the only place a
 * teacher can be pre-assigned before blocks exist (course_block_assignment
 * rows don't exist yet) - set via PUT
 * /api/groups/{groupId}/courses/{courseName}/default-teacher.
 *
 * Effective calendar (see buildEffectiveCalendar): a resolved teacher's shape
 * decisions never reason against their raw declared availability alone -
 * only against it minus whatever their OWN pre-existing assignments (from an
 * earlier generateBlocks() run, a manual edit, or an Excel import - not just
 * sibling pairings pending in this same run, which is all the original
 * shared-calendar grouping above accounted for) already claim. A PINNED
 * existing assignment has a known day/hour, so its hours are subtracted from
 * the calendar exactly; a still-movable (non-pinned) one doesn't, so its mere
 * existence instead requires one extra margin day when deciding whether a
 * shape is safe (EXTRA_MARGIN_DAYS_FOR_EXISTING_MOVABLE_LOAD) - a coarser but
 * still meaningfully conservative signal, in place of pretending that load
 * doesn't exist at all. An exclusive teacher (see above) by definition has no
 * existing assignments, so this never changes their calendar.
 *
 * Deterministic ordering: when 2+ pairings share one teacher's calendar, they
 * are processed largest-hours-first, then - when hours tie - ascending by the
 * course's semester (so a lower-semester group gets first claim on the
 * calendar's more comfortable shapes, consistent with how this system already
 * favors semester-1 groups elsewhere), then by group id, so who gets which
 * shape is fully reproducible across reruns of identical data rather than an
 * accident of whatever order the database happens to return groups in.
 */
@Service
public class BlockGenerationService {

    private static final int DEFAULT_BLOCK_SIZE = 2;
    private static final int DEFAULT_MAX_BLOCKS_PER_DAY = 2;

    /**
     * Extra margin days required, beyond AvailabilityAwareBlockShaper's own
     * DEFAULT_MARGIN_DAYS, when a teacher already has other pre-existing but
     * still-movable (unplaced) assignments elsewhere. A movable assignment
     * has no known day yet, so its hours can't be subtracted from a specific
     * window the way a pinned one's can (see buildEffectiveCalendar) - this
     * stands in for that, a coarser but still meaningfully conservative
     * signal in place of pretending that load doesn't exist at all.
     */
    private static final int EXTRA_MARGIN_DAYS_FOR_EXISTING_MOVABLE_LOAD = 1;

    @Autowired
    private StudentGroupRepository studentGroupRepository;
    @Autowired
    private CourseRepository courseRepository;
    @Autowired
    private CourseBlockAssignmentRepository assignmentRepository;
    @Autowired
    private CourseRoomRequirementRepository roomRequirementRepository;
    @Autowired
    private CourseBlockTemplateRepository blockTemplateRepository;
    @Autowired
    private ComponentBlockRuleRepository componentBlockRuleRepository;
    @Autowired
    private RoomRepository roomRepository;
    @Autowired
    private TeacherRepository teacherRepository;
    @Autowired
    private GroupRoomRangeRepository groupRoomRangeRepository;
    @Autowired
    private BlockTimeslotRepository blockTimeslotRepository;
    @Autowired
    private SemesterHourLimitRepository semesterHourLimitRepository;

    @Transactional
    public GenerationResult generateBlocks() {
        int created = 0;
        int skippedExisting = 0;
        List<String> warnings = new ArrayList<>();

        List<StudentGroupEntity> allGroups = studentGroupRepository.findAll();
        Map<String, Integer> groupCourseCountByTeacher = new HashMap<>();
        for (StudentGroupEntity g : allGroups) {
            for (GroupCourseEntity gc : g.getCourses()) {
                if (gc.getDefaultTeacherId() != null) {
                    groupCourseCountByTeacher.merge(gc.getDefaultTeacherId(), 1, Integer::sum);
                }
            }
        }

        List<PendingPair> pending = new ArrayList<>();
        for (StudentGroupEntity group : allGroups) {
            for (GroupCourseEntity groupCourse : group.getCourses()) {
                CourseEntity course = courseRepository.findByName(groupCourse.getCourseName()).orElse(null);
                if (course == null) {
                    warnings.add("Group '" + group.getId() + "': course '" + groupCourse.getCourseName()
                            + "' not found, skipped");
                    continue;
                }
                if (Boolean.FALSE.equals(course.getActive())) {
                    // The course was active when this group_course link was created (the API
                    // rejects adding an inactive one outright) but has since been marked
                    // inactive - skip rather than generate blocks for a course nobody's
                    // supposed to be teaching right now.
                    warnings.add("Group '" + group.getId() + "': course '" + course.getName()
                            + "' is inactive, skipped");
                    continue;
                }
                if (assignmentRepository.existsByGroupIdAndCourseId(group.getId(), course.getId())) {
                    skippedExisting++;
                    continue;
                }
                pending.add(new PendingPair(group, course, groupCourse.getDefaultTeacherId()));
            }
        }

        // Group by teacher so pairings sharing one teacher are generated against a
        // single, progressively-consumed calendar instead of each independently
        // assuming they have that teacher's whole week to themselves - see
        // docs/generate-blocks.md's "shared calendar" section. A teacher appearing
        // only once (the common case) or not at all falls through with
        // sharedCalendar == null, which is exactly today's unchanged behavior.
        Map<String, List<PendingPair>> byTeacher = new LinkedHashMap<>();
        for (PendingPair p : pending) {
            if (p.defaultTeacherId() == null) {
                created += generateBlocksForGroupCourse(p.group(), p.course(), null, groupCourseCountByTeacher, null,
                        warnings);
                continue;
            }
            byTeacher.computeIfAbsent(p.defaultTeacherId(), k -> new ArrayList<>()).add(p);
        }

        for (Map.Entry<String, List<PendingPair>> entry : byTeacher.entrySet()) {
            List<PendingPair> pairs = entry.getValue();
            TeacherCalendar sharedCalendar = null;
            if (pairs.size() >= 2) {
                TeacherEntity teacher = teacherRepository.findById(entry.getKey()).orElse(null);
                if (teacher != null) {
                    sharedCalendar = buildEffectiveCalendar(teacher,
                            assignmentRepository.findByTeacherId(entry.getKey()));
                    // Largest-hours-first (bin-packing): the pairing that needs the most from
                    // this calendar gets first claim on it. Tie-broken by ascending semester
                    // so a lower-semester group gets first claim on the calendar's more
                    // comfortable shapes when hours tie - consistent with how this system
                    // already favors semester-1 groups elsewhere (earlier starts, the harder
                    // 2pm cutoff) - then by group id, so the whole ordering (and therefore
                    // who ends up with which shape) is fully reproducible across reruns of
                    // identical data, not an accident of whatever order the database happens
                    // to return groups in.
                    pairs = pairs.stream()
                            .sorted(Comparator
                                    .comparingInt((PendingPair p) -> totalHoursFor(p.course())).reversed()
                                    .thenComparingInt(p -> semesterOrMax(p.course()))
                                    .thenComparing(p -> p.group().getId()))
                            .toList();
                }
            }
            for (PendingPair p : pairs) {
                created += generateBlocksForGroupCourse(p.group(), p.course(), p.defaultTeacherId(),
                        groupCourseCountByTeacher, sharedCalendar, warnings);
            }
        }

        return new GenerationResult(created, skippedExisting, warnings);
    }

    /** A (group, course) pair queued for generation, before its teacher grouping is decided. */
    private record PendingPair(StudentGroupEntity group, CourseEntity course, String defaultTeacherId) {
    }

    /** A course's semester, or Integer.MAX_VALUE if unset - sorts an unknown semester last, never first. */
    private int semesterOrMax(CourseEntity course) {
        Integer semester = course.getSemester();
        return semester != null ? semester : Integer.MAX_VALUE;
    }

    /**
     * A teacher's calendar for shape decisions: their windows-by-day (raw
     * declared availability, minus any hours already claimed by a PINNED
     * existing assignment elsewhere - an exact subtraction, since a pinned
     * row's day/hour is known), plus how many extra margin days their other
     * pre-existing but still-movable (unplaced) assignments call for (see
     * EXTRA_MARGIN_DAYS_FOR_EXISTING_MOVABLE_LOAD).
     *
     * Built once per resolved teacher - either here for a teacher with 2+
     * pairings pending in this run (shared and progressively consumed across
     * them, see generateBlocks()), or once per call in
     * generateBlocksForGroupCourse for a teacher with only one - and always
     * from that teacher's TRUE existing assignments, not just sibling
     * pairings pending in this same run. A teacher with no pre-existing
     * assignments at all (the exclusive-teacher case, by definition, and the
     * common case generally) gets back exactly today's unmodified calendar:
     * no pinned ranges to subtract, no movable load to add margin for.
     */
    private record TeacherCalendar(Map<Integer, List<int[]>> windows, int extraMarginDays) {
    }

    private TeacherCalendar buildEffectiveCalendar(TeacherEntity teacher,
            List<CourseBlockAssignmentEntity> existingAssignments) {
        List<int[]> pinnedRanges = new ArrayList<>();
        boolean hasExistingMovable = false;
        for (CourseBlockAssignmentEntity existing : existingAssignments) {
            if (Boolean.TRUE.equals(existing.getPinned()) && existing.getBlockTimeslotId() != null) {
                BlockTimeslotEntity slot = blockTimeslotRepository.findById(existing.getBlockTimeslotId())
                        .orElse(null);
                if (slot != null) {
                    pinnedRanges.add(new int[] { slot.getDayOfWeek(), slot.getStartHour(), slot.getLengthHours() });
                }
            } else {
                hasExistingMovable = true;
            }
        }
        Map<Integer, List<int[]>> windows = AvailabilityAwareBlockShaper.windowsByDay(teacher, pinnedRanges);
        int extraMarginDays = hasExistingMovable ? EXTRA_MARGIN_DAYS_FOR_EXISTING_MOVABLE_LOAD : 0;
        return new TeacherCalendar(windows, extraMarginDays);
    }

    /**
     * The real hours this course needs, used only to order pairings sharing a
     * teacher (largest first) - not part of the actual decomposition. Dual
     * room requirements make CourseEntity.requiredHoursPerWeek meaningless
     * (existing generation logic already ignores it once requirements exist),
     * so this sums the requirements' own hours instead when any exist.
     */
    private int totalHoursFor(CourseEntity course) {
        List<CourseRoomRequirementEntity> requirements = roomRequirementRepository
                .findByCourseIdOrderByPriority(course.getId());
        if (!requirements.isEmpty()) {
            return requirements.stream().mapToInt(CourseRoomRequirementEntity::getHoursRequired).sum();
        }
        return course.getRequiredHoursPerWeek();
    }

    /**
     * Clears block_timeslot_id on every unpinned course_block_assignment row,
     * leaving pinned rows untouched - useful for discarding a solve (or a bad
     * manual edit) and starting fresh without losing pinned placements.
     * Teacher and room are untouched too, since neither is solver-assigned.
     *
     * @return how many rows were cleared
     */
    @Transactional
    public int clearUnpinnedTimeslots() {
        List<CourseBlockAssignmentEntity> unpinned = assignmentRepository.findByPinned(false);
        for (CourseBlockAssignmentEntity assignment : unpinned) {
            assignment.setBlockTimeslotId(null);
        }
        assignmentRepository.saveAll(unpinned);
        return unpinned.size();
    }

    /**
     * Decomposes and saves the blocks for one (group, course) pair; returns
     * how many blocks were created.
     *
     * @param sharedCalendar when this teacher has 2+ pairings needing
     *                       generation in the same run, the one calendar
     *                       shared and progressively consumed across all of
     *                       them (see generateBlocks()); null otherwise, in
     *                       which case this teacher's effective calendar
     *                       (see buildEffectiveCalendar) is built fresh for
     *                       this call alone.
     */
    private int generateBlocksForGroupCourse(StudentGroupEntity group, CourseEntity course, String defaultTeacherId,
            Map<String, Integer> groupCourseCountByTeacher, TeacherCalendar sharedCalendar,
            List<String> warnings) {
        List<CourseBlockTemplateEntity> templates = resolveTemplates(course.getId(), group.getId());
        if (!templates.isEmpty()) {
            for (CourseBlockTemplateEntity template : templates) {
                saveTemplateBlock(group, course, template, defaultTeacherId, warnings);
            }
            return templates.size();
        }

        TeacherEntity teacher = defaultTeacherId != null ? teacherRepository.findById(defaultTeacherId).orElse(null)
                : null;
        List<CourseBlockAssignmentEntity> existingForTeacher = teacher != null
                ? assignmentRepository.findByTeacherId(defaultTeacherId)
                : List.of();
        // "This teacher's entire load is this one pairing" - checked both against
        // every group_course row (including pairings that don't have blocks yet, so
        // a bulk generateBlocks() run sees the true picture, not just what's already
        // in course_block_assignment) and against any pre-existing assignment of
        // theirs from outside this generation flow (e.g. hand-created via the
        // assignments API, never reflected in a group_course default-teacher link).
        boolean exclusiveTeacher = teacher != null
                && groupCourseCountByTeacher.getOrDefault(defaultTeacherId, 0) == 1
                && existingForTeacher.isEmpty();

        // The calendar this pair's shape decisions reason against: the shared,
        // already-partially-consumed one when the caller supplied one, otherwise this
        // teacher's own effective calendar built fresh (their raw availability, minus
        // pinned existing hours, plus extra margin if any movable ones exist - see
        // buildEffectiveCalendar). Even a single (group, course) pair with dual room
        // requirements benefits from this being built once and consumed across its
        // own portions below - the same teacher teaches every portion, so portion 2
        // shouldn't pretend it has whatever portion 1 already used.
        TeacherCalendar calendar = sharedCalendar != null ? sharedCalendar
                : (teacher != null ? buildEffectiveCalendar(teacher, existingForTeacher) : null);

        List<CourseRoomRequirementEntity> requirements = roomRequirementRepository.findByCourseIdOrderByPriority(course.getId());
        int blockIndex = 0;
        int created = 0;
        List<CourseBlockAssignmentEntity> generated = new ArrayList<>();
        if (requirements.isEmpty()) {
            List<Integer> shape = decomposeHours(course.getRequiredHoursPerWeek(), course.getDesignation(), calendar);
            consumeFromCalendar(shape, course.getDesignation(), calendar);
            for (int length : shape) {
                generated.add(saveBlock(group, course, blockIndex, length, course.getRoomRequirement(), null, defaultTeacherId));
                blockIndex++;
                created++;
            }
        } else {
            for (CourseRoomRequirementEntity requirement : requirements) {
                List<Integer> shape = decomposeHours(requirement.getHoursRequired(), course.getDesignation(), calendar);
                consumeFromCalendar(shape, course.getDesignation(), calendar);
                for (int length : shape) {
                    generated.add(saveBlock(group, course, blockIndex, length, requirement.getRoomType(),
                            requirement.getDefaultPreferredRoom(), defaultTeacherId));
                    blockIndex++;
                    created++;
                }
            }
        }
        if (exclusiveTeacher && !generated.isEmpty()) {
            tryPinExclusiveTeacherBlocks(group, course, teacher, generated, warnings);
        }
        return created;
    }

    /**
     * Consumes this shape's hours from {@code calendar}'s windows (the
     * shared or per-call calendar - see generateBlocksForGroupCourse), so
     * whatever's generated next - another portion of this same pair's dual
     * room requirement, or another pairing sharing this teacher's calendar -
     * sees a realistically smaller remaining calendar rather than the
     * teacher's full, untouched one. A no-op when there's no calendar to
     * consume from (no teacher resolved) or nothing to consume (an empty
     * shape). If the chosen shape can't actually be placed against the
     * current window state - possible since decomposeHours' day-count
     * feasibility check is coarser than this exact placement - the windows
     * are left untouched (assignWindows is transactional) and this portion
     * is simply not tracked; a known, minor imprecision, not a correctness
     * hazard.
     */
    private void consumeFromCalendar(List<Integer> shape, String designation, TeacherCalendar calendar) {
        if (calendar == null || shape.isEmpty()) {
            return;
        }
        AvailabilityAwareBlockShaper.assignWindows(shape, maxBlocksPerDayFor(designation), calendar.windows());
    }

    /**
     * When a teacher's entire teaching load is the blocks just generated, each
     * block's day/hour is already forced - there's no real placement decision
     * left for the solver to make. Greedily assigns each block a concrete
     * timeslot from the teacher's actual contiguous availability
     * (AvailabilityAwareBlockShaper.assignWindows) and pins it, but only when
     * every block also resolves a matching BlockTimeslot, a single
     * deterministic room (already computed by saveBlock via defaultRoomFor),
     * a HARD semester_hour_limit isn't violated, and it doesn't collide with
     * anything already pinned - this group's own pinned data, or any other
     * assignment (any group) already pinned to the same room - all-or-nothing,
     * since a partial pin here would mean an assumption was wrong, not
     * something to patch over block by block. These are, deliberately, the
     * same facts PreSolveValidator's pinned-data-integrity checks re-verify
     * for any pinned row: since a pin skips the solver's own constraint
     * checking entirely, this is the only thing standing between a pin and a
     * silent, permanent violation.
     */
    private void tryPinExclusiveTeacherBlocks(StudentGroupEntity group, CourseEntity course, TeacherEntity teacher,
            List<CourseBlockAssignmentEntity> blocks, List<String> warnings) {
        int maxBlocksPerDay = maxBlocksPerDayFor(course.getDesignation());
        List<Integer> lengths = blocks.stream().map(CourseBlockAssignmentEntity::getBlockLength).toList();
        List<int[]> slots = AvailabilityAwareBlockShaper.assignWindows(lengths, maxBlocksPerDay, teacher);
        if (slots == null) {
            warnings.add(group.getId() + "/" + course.getId() + ": teacher '" + teacher.getId()
                    + "' has no other commitments, but their availability couldn't fit all " + blocks.size()
                    + " generated block(s) at once; left unpinned for the solver/manual placement.");
            return;
        }

        List<BlockTimeslotEntity> resolvedTimeslots = new ArrayList<>();
        for (int i = 0; i < blocks.size(); i++) {
            CourseBlockAssignmentEntity block = blocks.get(i);
            int day = slots.get(i)[0];
            int startHour = slots.get(i)[1];
            BlockTimeslotEntity timeslot = blockTimeslotRepository
                    .findByDayOfWeekAndStartHourAndLengthHours(day, startHour, block.getBlockLength())
                    .orElse(null);
            if (timeslot == null) {
                warnings.add(block.getId() + ": teacher '" + teacher.getId() + "' has no other commitments, but no "
                        + "timeslot exists for the computed day " + day + "/hour " + startHour + "/length "
                        + block.getBlockLength() + "; left unpinned.");
                return;
            }
            if (block.getRoomName() == null) {
                warnings.add(block.getId() + ": teacher '" + teacher.getId() + "' has no other commitments and their "
                        + "schedule is otherwise fully determined, but no single room could be resolved; left unpinned.");
                return;
            }
            if (violatesSemesterHourLimit(course, timeslot)) {
                warnings.add(block.getId() + ": the computed slot ends after semester " + course.getSemester()
                        + "'s HARD hour limit; left unpinned - pinned rows aren't re-checked against this limit by "
                        + "the solver, so a pin here would be a silent, permanent violation.");
                return;
            }
            if (overlapsGroupsPinnedData(group, timeslot)) {
                warnings.add(block.getId() + ": the computed slot conflicts with '" + group.getId()
                        + "'s existing pinned data; left unpinned.");
                return;
            }
            if (overlapsAnyPinnedRoomBooking(block.getRoomName(), timeslot)) {
                warnings.add(block.getId() + ": room '" + block.getRoomName()
                        + "' is already pinned to another assignment at the computed slot; left unpinned.");
                return;
            }
            resolvedTimeslots.add(timeslot);
        }

        for (int i = 0; i < blocks.size(); i++) {
            CourseBlockAssignmentEntity block = blocks.get(i);
            block.setBlockTimeslotId(resolvedTimeslots.get(i).getId());
            block.setPinned(true);
            assignmentRepository.save(block);
        }
    }

    /**
     * True when this course has a HARD-severity semester_hour_limit configured
     * for its semester and candidate ends after it - mirrors
     * BlockScheduleMath.violatesHardSemesterHourLimit() (engine module, not
     * reusable here directly since web doesn't depend on engine) exactly,
     * since this is the one hard constraint PreSolveValidator checks for
     * pinned rows that a freshly-computed pin isn't otherwise guaranteed to
     * satisfy - unlike teacher availability or block-length-matches-timeslot,
     * nothing else about how this slot was computed rules it out.
     */
    private boolean violatesSemesterHourLimit(CourseEntity course, BlockTimeslotEntity candidate) {
        SemesterHourLimitEntity limit = semesterHourLimitRepository.findById(course.getSemester()).orElse(null);
        if (limit == null || !"HARD".equals(limit.getSeverity())) {
            return false;
        }
        return candidate.getStartHour() + candidate.getLengthHours() > limit.getLatestEndHour();
    }

    /** True if candidate's room is already pinned to a different assignment at an overlapping time, anywhere. */
    private boolean overlapsAnyPinnedRoomBooking(String roomName, BlockTimeslotEntity candidate) {
        for (CourseBlockAssignmentEntity existing : assignmentRepository.findByRoomName(roomName)) {
            if (!Boolean.TRUE.equals(existing.getPinned()) || existing.getBlockTimeslotId() == null) {
                continue;
            }
            BlockTimeslotEntity existingSlot = blockTimeslotRepository.findById(existing.getBlockTimeslotId())
                    .orElse(null);
            if (existingSlot != null && overlaps(candidate, existingSlot)) {
                return true;
            }
        }
        return false;
    }

    /** True if candidate overlaps any timeslot this group already has pinned. */
    private boolean overlapsGroupsPinnedData(StudentGroupEntity group, BlockTimeslotEntity candidate) {
        for (CourseBlockAssignmentEntity existing : assignmentRepository.findByGroupId(group.getId())) {
            if (!Boolean.TRUE.equals(existing.getPinned()) || existing.getBlockTimeslotId() == null) {
                continue;
            }
            BlockTimeslotEntity existingSlot = blockTimeslotRepository.findById(existing.getBlockTimeslotId())
                    .orElse(null);
            if (existingSlot != null && overlaps(candidate, existingSlot)) {
                return true;
            }
        }
        return false;
    }

    private boolean overlaps(BlockTimeslotEntity a, BlockTimeslotEntity b) {
        if (!a.getDayOfWeek().equals(b.getDayOfWeek())) {
            return false;
        }
        int aStart = a.getStartHour();
        int aEnd = aStart + a.getLengthHours();
        int bStart = b.getStartHour();
        int bEnd = bStart + b.getLengthHours();
        return aStart < bEnd && bStart < aEnd;
    }

    private int maxBlocksPerDayFor(String component) {
        return componentBlockRuleRepository.findById(component)
                .map(ComponentBlockRuleEntity::getMaxBlocksPerDay)
                .orElse(DEFAULT_MAX_BLOCKS_PER_DAY);
    }

    /**
     * Templates applicable to this (course, group), one per blockIndex: when
     * both a group-specific and a NULL-group ("all groups") template exist for
     * the same index - allowed by the DB's UNIQUE (course_id, group_id,
     * block_index), since group_id differs - the group-specific one wins as the
     * more targeted override.
     */
    private List<CourseBlockTemplateEntity> resolveTemplates(String courseId, String groupId) {
        Map<Integer, CourseBlockTemplateEntity> byIndex = new LinkedHashMap<>();
        for (CourseBlockTemplateEntity template : blockTemplateRepository.findApplicableTemplates(courseId, groupId)) {
            CourseBlockTemplateEntity existing = byIndex.get(template.getBlockIndex());
            if (existing == null || (existing.getGroupId() == null && template.getGroupId() != null)) {
                byIndex.put(template.getBlockIndex(), template);
            }
        }
        return byIndex.values().stream()
                .sorted((a, b) -> Integer.compare(a.getBlockIndex(), b.getBlockIndex()))
                .toList();
    }

    private void saveTemplateBlock(StudentGroupEntity group, CourseEntity course, CourseBlockTemplateEntity template,
            String defaultTeacherId, List<String> warnings) {
        CourseBlockAssignmentEntity block = new CourseBlockAssignmentEntity();
        block.setId(group.getId() + "_" + course.getId() + "_" + template.getBlockIndex());
        block.setGroupId(group.getId());
        block.setCourseId(course.getId());
        block.setBlockLength(template.getBlockLength());
        block.setSatisfiesRoomType(template.getRoomType());
        block.setPreferredRoomHint(template.getPreferredRoomName());
        block.setRoomName(template.getPreferredRoomName() != null
                ? template.getPreferredRoomName()
                : defaultRoomFor(group, template.getRoomType(), defaultTeacherId));
        block.setTeacherId(defaultTeacherId);
        block.setBlockTimeslotId(template.getPreferredTimeslotId());
        boolean wantsPin = Boolean.TRUE.equals(template.getPinAssignment());
        if (wantsPin && block.getRoomName() == null) {
            // Pinned rows must have a room (check_block_assignment_pinned_requires_room).
            // No room could be resolved (no template preferredRoomName, no compatible
            // teacher/group default), so leave this block unpinned rather than fail the
            // whole generation batch or fabricate a room - same "leave it for manual
            // assignment" philosophy defaultRoomFor already uses for the room itself.
            warnings.add(block.getId() + ": template requested pinning but no compatible room could be resolved; "
                    + "left unpinned - assign a room manually, then pin it.");
        } else {
            block.setPinned(wantsPin);
        }
        assignmentRepository.save(block);
    }

    private CourseBlockAssignmentEntity saveBlock(StudentGroupEntity group, CourseEntity course, int blockIndex,
            int length, String satisfiesRoomType, String preferredRoomHint, String defaultTeacherId) {
        CourseBlockAssignmentEntity block = new CourseBlockAssignmentEntity();
        block.setId(group.getId() + "_" + course.getId() + "_" + blockIndex);
        block.setGroupId(group.getId());
        block.setCourseId(course.getId());
        block.setBlockLength(length);
        block.setSatisfiesRoomType(satisfiesRoomType);
        block.setPreferredRoomHint(preferredRoomHint);
        block.setRoomName(preferredRoomHint != null ? preferredRoomHint
                : defaultRoomFor(group, satisfiesRoomType, defaultTeacherId));
        block.setTeacherId(defaultTeacherId);
        block.setPinned(false);
        return assignmentRepository.save(block);
    }

    /**
     * The room a generated block should default to, in priority order: the
     * defaultTeacherId's required room (if set and type-compatible - a
     * teacher's fixed-room requirement overrides the group's range), then the
     * group's curated range for satisfiesRoomType, but only when it resolves
     * to exactly one type-compatible room - a range of 2+ rooms has no single
     * deterministic choice to default to, so the block is left roomless for
     * the solver/manual assignment to pick among them instead. Null if
     * nothing applies, leaving the room for manual assignment instead of an
     * invalid default.
     */
    private String defaultRoomFor(StudentGroupEntity group, String satisfiesRoomType, String defaultTeacherId) {
        if (defaultTeacherId != null) {
            String teacherRoom = requiredRoomFor(defaultTeacherId, satisfiesRoomType);
            if (teacherRoom != null) {
                return teacherRoom;
            }
        }
        List<GroupRoomRangeEntity> range = groupRoomRangeRepository.findByGroupIdAndRoomType(group.getId(),
                satisfiesRoomType);
        if (range.size() != 1) {
            return null;
        }
        String onlyRoomName = range.get(0).getRoomName();
        RoomEntity onlyRoom = roomRepository.findById(onlyRoomName).orElse(null);
        if (onlyRoom == null || !RoomTypeCompatibility.satisfies(onlyRoom.getType(), satisfiesRoomType)) {
            return null;
        }
        return onlyRoomName;
    }

    /** A teacher's required room, if set and its type satisfies the requirement - null otherwise. */
    private String requiredRoomFor(String teacherId, String satisfiesRoomType) {
        TeacherEntity teacher = teacherRepository.findById(teacherId).orElse(null);
        if (teacher == null || teacher.getRequiredRoomName() == null) {
            return null;
        }
        RoomEntity requiredRoom = roomRepository.findById(teacher.getRequiredRoomName()).orElse(null);
        if (requiredRoom == null || !RoomTypeCompatibility.satisfies(requiredRoom.getType(), satisfiesRoomType)) {
            return null;
        }
        return teacher.getRequiredRoomName();
    }

    /**
     * Decomposes required hours by greedily packing blocks at the component's
     * configured preferred size (component_block_rule), or DEFAULT_BLOCK_SIZE
     * if that component has no rule, with a trailing remainder block for any
     * leftover hours that don't divide evenly.
     *
     * When a teacher is already resolved, that naive shape is checked against
     * {@code windows} first: if it wouldn't leave at least
     * {@link AvailabilityAwareBlockShaper#DEFAULT_MARGIN_DAYS} spare distinct
     * days beyond the minimum needed (a shape that's only just barely
     * possible leaves the solver zero room to absorb any other scheduling
     * pressure that day - confirmed live 2026-09-05: two pairs generated with
     * exactly zero slack both went on to violate maxBlocksPerDay once
     * solved), AvailabilityAwareBlockShaper is asked for a shape (fewer,
     * longer blocks) that does have margin, without ever pinning a specific
     * day - the solver still freely places each block among the teacher's
     * available days, exactly as for any other generated block. This is a
     * probabilistic hedge, not a guarantee: it lowers how often the solver
     * gets squeezed into a violation, it doesn't prove it can't happen (a
     * third pair that night had a full spare day and still got violated).
     * Falls back to the naive shape when there's no teacher yet or no
     * availability data to reason from; falls back to a merely bare-feasible
     * (zero-margin) shape rather than the untouched naive one when margin
     * isn't reachable at any block size - a genuinely infeasible pairing
     * PreSolveValidator will still report, exactly as it does today.
     *
     * @param calendar this teacher's calendar to reason against (see
     *                  buildEffectiveCalendar) - the shared one when 2+
     *                  pairings need generation for this teacher in the same
     *                  run, a fresh one otherwise, or null when no teacher is
     *                  resolved yet at all (see generateBlocksForGroupCourse)
     */
    private List<Integer> decomposeHours(int hours, String component, TeacherCalendar calendar) {
        ComponentBlockRuleEntity rule = componentBlockRuleRepository.findById(component).orElse(null);
        int preferredSize = rule != null && rule.getPreferredBlockSize() != null ? rule.getPreferredBlockSize()
                : DEFAULT_BLOCK_SIZE;
        List<Integer> naive = AvailabilityAwareBlockShaper.packBlocks(hours, preferredSize);
        if (calendar == null) {
            return naive;
        }
        Map<Integer, List<int[]>> windows = calendar.windows();
        int availableDays = AvailabilityAwareBlockShaper.distinctAvailableDayCount(windows);
        int maxBlocksPerDay = rule != null && rule.getMaxBlocksPerDay() != null ? rule.getMaxBlocksPerDay()
                : DEFAULT_MAX_BLOCKS_PER_DAY;
        int marginDays = AvailabilityAwareBlockShaper.DEFAULT_MARGIN_DAYS + calendar.extraMarginDays();
        if (availableDays == 0
                || AvailabilityAwareBlockShaper.fitsWithinDayCap(naive.size(), maxBlocksPerDay, availableDays,
                        marginDays)) {
            return naive;
        }
        List<Integer> adapted = AvailabilityAwareBlockShaper.tryAvailabilityAwareShape(hours, preferredSize,
                maxBlocksPerDay, windows, marginDays);
        return adapted != null ? adapted : naive;
    }

    /** Outcome of a generateBlocks() run. */
    public static final class GenerationResult {
        private final int blocksCreated;
        private final int groupCoursesSkippedExisting;
        private final List<String> warnings;

        public GenerationResult(int blocksCreated, int groupCoursesSkippedExisting, List<String> warnings) {
            this.blocksCreated = blocksCreated;
            this.groupCoursesSkippedExisting = groupCoursesSkippedExisting;
            this.warnings = warnings;
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
}
