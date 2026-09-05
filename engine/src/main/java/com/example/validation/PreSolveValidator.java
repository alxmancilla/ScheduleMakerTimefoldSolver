package com.example.validation;

import com.example.common.CalendarPacking;
import com.example.common.SchoolCalendarConstants;
import com.example.domain.BlockScheduleMath;
import com.example.domain.BlockTimeslot;
import com.example.domain.Course;
import com.example.domain.CourseBlockAssignment;
import com.example.domain.Group;
import com.example.domain.Room;
import com.example.domain.SchoolSchedule;
import com.example.domain.Teacher;

import java.time.DayOfWeek;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Validates pinned {@link CourseBlockAssignment}s, plus whole-schedule
 * capacity facts, before solving.
 *
 * <p>
 * Pinned blocks are fixed inputs (their timeslot/teacher/room come from the
 * database and never change). The solver's hard constraints deliberately
 * <em>exclude</em> pinned assignments, so an invalid pinned row loaded from the
 * database is silently accepted and never surfaces in the score. This validator
 * closes that gap by re-applying the same checks the solver skips - block
 * length, room type, teacher qualification/availability, the teacher's
 * required room (mirrors {@code teacherRequiredRoomMustBeUsed}), and a
 * HARD-severity semester hour limit (mirrors
 * {@code semesterHourLimitsMustBeRespected}) - plus conflict detection among
 * pinned blocks themselves, and fails fast with a clear report.
 * </p>
 * <p>
 * It also blocks on assignments belonging to an inactive course (see
 * {@link #validateNoInactiveCourses}), a teacher not qualified for their
 * assigned course regardless of pinned status (see
 * {@link #validateTeacherQualifications}), several whole-schedule capacity
 * facts (see {@link #validateCapacity}, {@link #validateRoomFixedCapacity},
 * {@link #validateBlockSpreadCapacity}), and a non-pinned assignment whose
 * timeslot value range has been narrowed down to nothing (see
 * {@link #validateNonEmptyTimeslotRanges} - this can happen once
 * {@link CourseBlockAssignment#getMatchingBlockTimeslots()} excludes a
 * teacher's/group's pinned-occupied timeslots from a movable block's own
 * candidates, so the solver never even attempts a slot that's guaranteed to
 * double-book a teacher or clash a group against fixed, unmovable data).
 * </p>
 * <p>
 * The capacity checks in particular aren't invalid data the way the pinned
 * checks above are, but they're just as fatal to the solve: each is a
 * proven mathematical fact (pigeonhole - every teaching hour occupies
 * exactly one of the teacher's available hour-slots, and two blocks can never
 * share one) that at least one double-booking is unavoidable, so a solve
 * attempt can only ever end in a hard violation. Blocking here - rather than
 * warning and burning the full solve budget to confirm what's already known
 * - mirrors this project's other "exact, not a heuristic" guardrail
 * (SemesterHourLimitController's guardrail #2).
 * </p>
 */
public final class PreSolveValidator {

    private PreSolveValidator() {
    }

    /**
     * Validate all pinned assignments in the schedule, plus whole-schedule
     * capacity facts.
     *
     * @param schedule the problem to validate (before solving)
     * @return a {@link ValidationResult} listing every problem found
     */
    public static ValidationResult validate(SchoolSchedule schedule) {
        List<String> problems = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        if (schedule == null || schedule.getCourseBlockAssignments() == null) {
            return new ValidationResult(problems, warnings);
        }

        List<CourseBlockAssignment> pinned = new ArrayList<>();
        for (CourseBlockAssignment a : schedule.getCourseBlockAssignments()) {
            if (a.isPinned()) {
                pinned.add(a);
            }
        }

        for (CourseBlockAssignment a : pinned) {
            validateSingle(a, problems);
        }
        validateConflicts(pinned, problems);
        validateNoInactiveCourses(schedule.getCourseBlockAssignments(), problems);
        validateTeacherQualifications(schedule.getCourseBlockAssignments(), problems);
        validateCapacity(schedule.getCourseBlockAssignments(), problems);
        validateRoomFixedCapacity(schedule.getCourseBlockAssignments(), problems);
        validateBlockSpreadCapacity(schedule.getCourseBlockAssignments(), problems);
        validateNonEmptyTimeslotRanges(schedule.getCourseBlockAssignments(), problems);
        validateSharedTeacherLoad(schedule.getCourseBlockAssignments(), warnings);

        return new ValidationResult(problems, warnings);
    }

    /**
     * Blocks when a non-pinned assignment's own timeslot value range
     * ({@link CourseBlockAssignment#getMatchingBlockTimeslots()}) is empty -
     * there is literally nowhere left the solver could ever place it. This
     * is deliberately a direct emptiness check rather than an aggregate
     * hours comparison: {@link #validateCapacity} already catches the pure
     * "not enough total hours" case (mathematically identical to comparing
     * assigned hours against availability), but excluding a teacher's/
     * group's pinned-occupied timeslots from this value range (see
     * getMatchingBlockTimeslots()'s doc) can fail for a *shape* reason
     * capacity math can't see - e.g. a teacher with plenty of hours left in
     * total, but none of it in one contiguous window as long as a 3-hour
     * block still needs. Checking the value range itself, after every
     * exclusion has been applied, catches that exactly rather than
     * approximating it.
     */
    private static void validateNonEmptyTimeslotRanges(List<CourseBlockAssignment> assignments,
            List<String> problems) {
        for (CourseBlockAssignment a : assignments) {
            // allTimeslots == null means the timeslot catalog was never wired
            // up at all (only ever true for a hand-built schedule that skips
            // it, e.g. a unit test not exercising this) - a different,
            // uninteresting state from "wired up and genuinely empty", which
            // is the only thing this check means to catch.
            if (a.isPinned() || a.getAllTimeslots() == null) {
                continue;
            }
            if (a.getMatchingBlockTimeslots().isEmpty()) {
                problems.add(String.format(
                        "%s has no valid timeslot left: every %dh slot either doesn't exist, runs past a HARD "
                                + "semester hour limit, or would double-book its teacher/group against a pinned "
                                + "block. Widen availability, move a conflicting pinned block, or reassign this block.",
                        describe(a), a.getBlockLength()));
            }
        }
    }

    /**
     * Blocks when any assignment - pinned or movable - belongs to a course
     * marked inactive. Nothing else in the solve path checks this:
     * DataLoader loads {@code course.active} into memory, but nothing
     * downstream ever reads it again, so a course deactivated after its
     * blocks were generated would otherwise be scheduled/optimized exactly
     * like any active course's. The two existing active-flag guards
     * (GroupCourseController.addCourse(), BlockGenerationService) only stop
     * *new* commitments from being created for an inactive course - neither
     * retroactively excludes rows that already exist from a solve, and per
     * BlockGenerationService's own comment, an inactive course means
     * "nobody's supposed to be teaching this right now" - present tense,
     * not "grandfather in what's already there." Grouped per course (one
     * problem per inactive course, not one per block) to avoid flooding the
     * report when a course has many leftover blocks.
     */
    private static void validateNoInactiveCourses(List<CourseBlockAssignment> assignments, List<String> problems) {
        Map<Course, List<CourseBlockAssignment>> byInactiveCourse = new LinkedHashMap<>();
        for (CourseBlockAssignment a : assignments) {
            Course course = a.getCourse();
            if (course != null && Boolean.FALSE.equals(course.getActive())) {
                byInactiveCourse.computeIfAbsent(course, k -> new ArrayList<>()).add(a);
            }
        }
        for (Map.Entry<Course, List<CourseBlockAssignment>> entry : byInactiveCourse.entrySet()) {
            Course course = entry.getKey();
            List<CourseBlockAssignment> blocks = entry.getValue();
            Set<String> groupIds = new TreeSet<>();
            for (CourseBlockAssignment a : blocks) {
                if (a.getGroup() != null) {
                    groupIds.add(a.getGroup().getId());
                }
            }
            problems.add(String.format(
                    "Course '%s' is inactive but still has %d block assignment(s) across group(s) %s - "
                            + "remove or reassign them, or reactivate the course, before solving.",
                    course.getName(), blocks.size(), groupIds));
        }
    }

    /**
     * Blocks when a teacher isn't qualified for a course they're assigned to
     * - for every assignment, pinned or not (unlike the other per-block
     * checks in {@link #validateSingle}, which are pinned-only because a
     * movable block's timeslot/room aren't fixed yet). Teacher is fixed
     * input, never a planning variable, so qualification doesn't depend on
     * where the solver places anything: an unqualified pairing is guaranteed
     * to violate {@code teacherMustBeQualified} no matter what.
     */
    private static void validateTeacherQualifications(List<CourseBlockAssignment> assignments,
            List<String> problems) {
        for (CourseBlockAssignment a : assignments) {
            Teacher teacher = a.getTeacher();
            Course course = a.getCourse();
            if (teacher == null || course == null) {
                continue;
            }
            String courseName = course.getName();
            if (courseName != null && !teacher.isQualifiedFor(courseName)) {
                problems.add(String.format("%s teacher '%s' is not qualified for course '%s'.",
                        describe(a), teacher.getId(), courseName));
            }
        }
    }

    /**
     * Blocks when a single room that multiple blocks are structurally fixed
     * to (see {@link CourseBlockAssignment#isRoomFixed()} - a teacher's
     * required room, or a group's single-room curated range) is asked to
     * host more hours than exist in the school week. The mirror of
     * {@link #validateCapacity} for rooms instead of teachers: a room has no
     * per-day availability of its own, so its ceiling is simply the whole
     * week ({@link SchoolCalendarConstants}). Deliberately scoped to
     * room-fixed blocks only - a movable block can use any type-compatible
     * room, so there's no single room whose capacity to check; that
     * aggregate-demand-vs-supply question is a real one (see
     * roomTypeMustSatisfyRequirement's demand across ALL rooms of a type),
     * but genuinely heuristic given type substitutability (Mixed satisfies
     * Standard/Workshop too) - not attempted here.
     */
    private static void validateRoomFixedCapacity(List<CourseBlockAssignment> assignments, List<String> problems) {
        int totalWeeklyHours = (SchoolCalendarConstants.LATEST_HOUR - SchoolCalendarConstants.EARLIEST_START_HOUR)
                * SchoolCalendarConstants.SCHOOL_DAYS_PER_WEEK;
        Map<Room, Integer> assignedHoursByFixedRoom = new LinkedHashMap<>();
        for (CourseBlockAssignment a : assignments) {
            if (!a.isRoomFixed()) {
                continue;
            }
            List<Room> matching = a.getMatchingRooms();
            if (matching.size() != 1) {
                continue; // defensive - isRoomFixed() already guarantees this
            }
            assignedHoursByFixedRoom.merge(matching.get(0), a.getBlockLength(), Integer::sum);
        }
        for (Map.Entry<Room, Integer> entry : assignedHoursByFixedRoom.entrySet()) {
            Room room = entry.getKey();
            int assignedHours = entry.getValue();
            if (assignedHours > totalWeeklyHours) {
                problems.add(String.format(
                        "Room '%s' is fixed (via a teacher's required room or a group's single-room range) for "
                                + "%dh/week of blocks, but the school week only has %dh/week - short by %dh. "
                                + "At least one double-booking is unavoidable.",
                        room.getName(), assignedHours, totalWeeklyHours, assignedHours - totalWeeklyHours));
            }
        }
    }

    /**
     * Blocks when a (group, course) pair's blocks - all taught by the same
     * teacher - need more distinct days than that teacher has available, to
     * respect {@code maxTwoBlocksPerCoursePerGroupPerDay}'s per-day cap
     * ({@link BlockScheduleMath#maxBlocksPerDay}). E.g. 5 required 1-hour
     * blocks at 1/day need 5 distinct days; a teacher missing one weekday
     * entirely can never spread them without exceeding the cap somewhere,
     * regardless of which day. Deliberately skipped when a (group, course)
     * pair's blocks span more than one teacher - which teacher's
     * availability would govern is ambiguous, and none of the real cases
     * this check was built for (a single teacher covering all of a course's
     * blocks for a group) involve that anyway, so skipping avoids a false
     * positive rather than guessing.
     */
    private static void validateBlockSpreadCapacity(List<CourseBlockAssignment> assignments, List<String> problems) {
        Map<List<Object>, List<CourseBlockAssignment>> byGroupAndCourse = new LinkedHashMap<>();
        for (CourseBlockAssignment a : assignments) {
            if (a.getGroup() == null || a.getCourse() == null) {
                continue;
            }
            byGroupAndCourse.computeIfAbsent(List.of(a.getGroup(), a.getCourse()), k -> new ArrayList<>()).add(a);
        }

        for (Map.Entry<List<Object>, List<CourseBlockAssignment>> entry : byGroupAndCourse.entrySet()) {
            List<CourseBlockAssignment> blocks = entry.getValue();
            Teacher teacher = blocks.get(0).getTeacher();
            if (teacher == null || blocks.stream().anyMatch(a -> !teacher.equals(a.getTeacher()))) {
                continue;
            }
            Course course = (Course) entry.getKey().get(1);
            int maxPerDay = BlockScheduleMath.maxBlocksPerDay(course);
            int neededDays = (int) Math.ceil(blocks.size() / (double) maxPerDay);
            int availableDays = teacher.getAvailableDays().size();
            if (neededDays > availableDays) {
                Group group = (Group) entry.getKey().get(0);
                problems.add(String.format(
                        "Group '%s' course '%s' needs %d block(s) spread across at least %d distinct day(s) "
                                + "(max %d/day), but teacher '%s' is only available %d day(s)/week. At least one "
                                + "day will exceed the per-day limit.",
                        group.getId(), course.getName(), blocks.size(), neededDays, maxPerDay, teacher.getId(),
                        availableDays));
            }
        }
    }

    /**
     * ADVISORY (warning, not blocking): when a teacher has 2+ distinct
     * (group, course) pairings, simulates whether all of their MOVABLE
     * blocks (already-shaped lengths, decided at generation time) can be
     * greedily packed into the teacher's actual per-day hour windows -
     * after subtracting hours already committed to that teacher's PINNED
     * assignments - without any pairing exceeding its own
     * {@link BlockScheduleMath#maxBlocksPerDay} cap or the teacher being
     * double-booked.
     *
     * <p>
     * This is the validation-side mirror of the exact blind spot
     * {@code BlockGenerationService}'s shape adaptation used to have (fixed
     * 2026-09-05, see docs/generate-blocks.md's "Option C" and "Effective
     * calendar" sections): {@link #validateBlockSpreadCapacity} above checks
     * each (group, course) pair's day requirement against the teacher's FULL
     * raw day count in isolation, never accounting for what the teacher's
     * OTHER pairings also need. A teacher shared across many groups for one
     * course - confirmed live (2026-09-05): 9 groups, comfortable aggregate
     * hours - can still leave the solver unable to avoid a double-booking,
     * even though every pairing looks individually fine and the aggregate
     * hours check ({@link #validateCapacity}) passes too.
     * </p>
     * <p>
     * Deliberately a WARNING, not a blocking problem, unlike every other
     * check in this validator: this is a GREEDY simulation
     * (largest-remaining-movable-hours-first, mirroring
     * {@code BlockGenerationService}'s own bin-packing heuristic), not a
     * mathematical proof - a greedy failure means "this particular
     * placement order didn't work," not "no arrangement can ever work."
     * Flagging it as a hard problem would risk blocking solves that are
     * actually perfectly feasible via a smarter placement order than this
     * simulation tried - exactly the "advisory rather than provably fatal"
     * case {@link ValidationResult}'s own javadoc reserves {@code warnings}
     * for.
     * </p>
     */
    private static void validateSharedTeacherLoad(List<CourseBlockAssignment> assignments, List<String> warnings) {
        // Group by (group, course) first, exactly like validateBlockSpreadCapacity, so only
        // pairings unambiguously taught by one teacher are ever considered.
        Map<List<Object>, List<CourseBlockAssignment>> byGroupAndCourse = new LinkedHashMap<>();
        for (CourseBlockAssignment a : assignments) {
            if (a.getGroup() == null || a.getCourse() == null) {
                continue;
            }
            byGroupAndCourse.computeIfAbsent(List.of(a.getGroup(), a.getCourse()), k -> new ArrayList<>()).add(a);
        }

        Map<Teacher, List<Map.Entry<List<Object>, List<CourseBlockAssignment>>>> pairingsByTeacher =
                new LinkedHashMap<>();
        for (Map.Entry<List<Object>, List<CourseBlockAssignment>> entry : byGroupAndCourse.entrySet()) {
            List<CourseBlockAssignment> blocks = entry.getValue();
            Teacher teacher = blocks.get(0).getTeacher();
            if (teacher == null || blocks.stream().anyMatch(a -> !teacher.equals(a.getTeacher()))) {
                continue; // ambiguous/no single teacher - skip, same reasoning as validateBlockSpreadCapacity
            }
            pairingsByTeacher.computeIfAbsent(teacher, k -> new ArrayList<>()).add(entry);
        }

        for (Map.Entry<Teacher, List<Map.Entry<List<Object>, List<CourseBlockAssignment>>>> teacherEntry
                : pairingsByTeacher.entrySet()) {
            Teacher teacher = teacherEntry.getKey();
            List<Map.Entry<List<Object>, List<CourseBlockAssignment>>> pairings = teacherEntry.getValue();
            if (pairings.size() < 2) {
                continue; // single pairing: nothing to share; validateBlockSpreadCapacity already covers this exactly
            }

            // Every one of this teacher's PINNED hours, across every pairing, is a fixed fact -
            // subtract them from the calendar before simulating anything movable.
            List<CourseBlockAssignment> allTeacherBlocks = new ArrayList<>();
            for (Map.Entry<List<Object>, List<CourseBlockAssignment>> pairing : pairings) {
                allTeacherBlocks.addAll(pairing.getValue());
            }
            Map<DayOfWeek, List<int[]>> windows = windowsMinusPinned(teacher, allTeacherBlocks);

            List<Map.Entry<List<Object>, List<CourseBlockAssignment>>> ordered = pairings.stream()
                    .sorted(Comparator.comparingInt(
                            (Map.Entry<List<Object>, List<CourseBlockAssignment>> e) -> movableHours(e.getValue()))
                            .reversed())
                    .toList();

            for (Map.Entry<List<Object>, List<CourseBlockAssignment>> pairing : ordered) {
                List<CourseBlockAssignment> movable = pairing.getValue().stream()
                        .filter(a -> !a.isPinned())
                        .toList();
                if (movable.isEmpty()) {
                    continue; // this pairing is already fully pinned - no placement decision left
                }
                Group group = (Group) pairing.getKey().get(0);
                Course course = (Course) pairing.getKey().get(1);
                int maxPerDay = BlockScheduleMath.maxBlocksPerDay(course);
                List<Integer> lengths = movable.stream().map(CourseBlockAssignment::getBlockLength).toList();

                if (!greedyAssign(windows, maxPerDay, lengths)) {
                    warnings.add(String.format(
                            "Teacher '%s' teaches %d different (group, course) pairings sharing this calendar; "
                                    + "group '%s' course '%s' (%d movable block(s)) couldn't be greedily fit in "
                                    + "once the other pairings' likely hours are accounted for. Each pairing looks "
                                    + "fine checked alone, but placing all of them together may not be - the "
                                    + "solver could end up leaving a conflict here. This is a heuristic warning, "
                                    + "not a proof: a smarter placement order might still succeed.",
                            teacher.getId(), pairings.size(), group.getId(), course.getName(), movable.size()));
                }
            }
        }
    }

    /** Sum of block lengths across this pairing's MOVABLE (non-pinned) blocks only. */
    private static int movableHours(List<CourseBlockAssignment> pairingBlocks) {
        return pairingBlocks.stream().filter(a -> !a.isPinned()).mapToInt(CourseBlockAssignment::getBlockLength).sum();
    }

    /**
     * This teacher's contiguous available hour-windows per day, with any
     * hours already claimed by one of {@code teacherBlocks}' PINNED entries
     * removed first - the engine-side mirror of the web module's
     * {@code AvailabilityAwareBlockShaper.windowsByDay(TeacherEntity, List)}.
     */
    private static Map<DayOfWeek, List<int[]>> windowsMinusPinned(Teacher teacher,
            List<CourseBlockAssignment> teacherBlocks) {
        Map<DayOfWeek, SortedSet<Integer>> hours = new TreeMap<>();
        for (Map.Entry<DayOfWeek, Set<Integer>> e : teacher.getAvailabilityPerDay().entrySet()) {
            hours.put(e.getKey(), new TreeSet<>(e.getValue()));
        }
        for (CourseBlockAssignment a : teacherBlocks) {
            if (!a.isPinned() || a.getTimeslot() == null) {
                continue;
            }
            BlockTimeslot slot = a.getTimeslot();
            SortedSet<Integer> dayHours = hours.get(slot.getDayOfWeek());
            if (dayHours == null) {
                continue;
            }
            for (int h = slot.getStartHour(); h < slot.getStartHour() + slot.getLengthHours(); h++) {
                dayHours.remove(h);
            }
        }
        Map<DayOfWeek, List<int[]>> windows = new TreeMap<>();
        for (Map.Entry<DayOfWeek, SortedSet<Integer>> e : hours.entrySet()) {
            windows.put(e.getKey(), CalendarPacking.contiguousWindows(e.getValue()));
        }
        return windows;
    }

    /**
     * Greedily places each length into {@code windows}, respecting
     * maxPerDay, mutating windows only on full success (transactional - a
     * failed attempt must leave the calendar untouched for whichever
     * pairing is tried next). Delegates to {@link CalendarPacking#assignWindows}
     * (shared with the web module's {@code AvailabilityAwareBlockShaper},
     * which needs the identical reasoning over its own {@code Integer}-keyed
     * calendar) and discards the actual placements - this check only needs
     * to know whether every length placed successfully, never where.
     */
    private static boolean greedyAssign(Map<DayOfWeek, List<int[]>> windows, int maxPerDay, List<Integer> lengths) {
        return CalendarPacking.assignWindows(lengths, maxPerDay, windows) != null;
    }

    /**
     * Blocks when a teacher's total assigned hours - summed across every
     * assignment for them, pinned or not, the same basis
     * TeacherController.buildCapacityWarning() uses - exceed their total
     * weekly availability ({@link Teacher#getTotalAvailableHours()}). Unlike
     * that web-layer check (which only warns, since an admin might be mid-fix
     * when saving), this runs right before every solve regardless of how the
     * data got there (hand-edited via the API, bulk Excel import, or a direct
     * SQL fix) and regardless of how the solve was triggered (CLI or the web
     * "Run Solver" button, since both funnel through
     * MainBlockSchedulingApp) - so there's no good reason left to let the
     * solve run anyway: the outcome (at least one double-booking) is already
     * certain.
     */
    private static void validateCapacity(List<CourseBlockAssignment> assignments, List<String> problems) {
        Map<Teacher, Integer> assignedHoursByTeacher = new LinkedHashMap<>();
        for (CourseBlockAssignment a : assignments) {
            Teacher teacher = a.getTeacher();
            if (teacher == null) {
                continue;
            }
            assignedHoursByTeacher.merge(teacher, a.getBlockLength(), Integer::sum);
        }
        for (Map.Entry<Teacher, Integer> entry : assignedHoursByTeacher.entrySet()) {
            Teacher teacher = entry.getKey();
            int assignedHours = entry.getValue();
            int availableHours = teacher.getTotalAvailableHours();
            if (assignedHours > availableHours) {
                problems.add(String.format(
                        "Teacher '%s' is assigned %dh/week of courses but only has %dh/week of availability - "
                                + "short by %dh. At least one double-booking is unavoidable.",
                        teacher.getId(), assignedHours, availableHours, assignedHours - availableHours));
            }
        }
    }

    /** Per-block checks that mirror the solver's (pinned-excluded) hard rules. */
    private static void validateSingle(CourseBlockAssignment a, List<String> problems) {
        BlockTimeslot slot = a.getTimeslot();
        if (slot == null) {
            problems.add(describe(a) + " is pinned but has no timeslot assigned.");
            return;
        }

        // Room must be assigned (data-integrity rule, mirrors the DB's
        // check_block_assignment_pinned_requires_room constraint - a pinned
        // row without a room can still be loaded from a database that
        // predates that constraint, so this check stays even though the
        // constraint should normally prevent it at the source).
        if (a.getRoom() == null) {
            problems.add(describe(a) + " is pinned but has no room assigned.");
        }

        // Block length must match timeslot length (data-integrity rule).
        if (a.getBlockLength() != slot.getLengthHours()) {
            problems.add(String.format("%s block length %d does not match timeslot length %d (%s).",
                    describe(a), a.getBlockLength(), slot.getLengthHours(), slot.getDisplayName()));
        }

        // Room type must satisfy the block's required room type.
        if (a.getRoom() != null && a.getSatisfiesRoomType() != null
                && !a.getRoom().satisfiesRequirement(a.getSatisfiesRoomType())) {
            problems.add(String.format("%s room '%s' (type '%s') does not satisfy required room type '%s'.",
                    describe(a), a.getRoom().getName(), a.getRoom().getType(), a.getSatisfiesRoomType()));
        }

        // Semester hour limit (HARD severity only - mirrors
        // SchoolConstraintProvider.semesterHourLimitsMustBeRespected(), which
        // is likewise not excluded for pinned rows: a non-pinned block of a
        // HARD-limited course can never reach a violating timeslot in the
        // first place (excluded from its own entity-scoped value range), so
        // this can only ever fire for a pinned row whose timeslot predates
        // the limit. Reuses the constraint's own check rather than
        // re-deriving the math, so the two can't silently drift apart.
        if (BlockScheduleMath.violatesHardSemesterHourLimit(a)) {
            Course course = a.getCourse();
            problems.add(String.format(
                    "%s ends at %d:00, past semester %d's HARD limit of %d:00 (%s).",
                    describe(a), slot.getStartHour() + slot.getLengthHours(), course.getSemester(),
                    course.getLatestEndHour(), slot.getDisplayName()));
        }

        // Teacher must be available for the whole block. (Qualification is
        // checked separately, for every assignment - see
        // validateTeacherQualifications() - since unlike availability it
        // doesn't depend on where the solver places anything.)
        if (a.getTeacher() != null) {
            if (!a.getTeacher().isAvailableForBlock(slot)) {
                problems.add(String.format("%s teacher '%s' is not available for the entire block (%s).",
                        describe(a), a.getTeacher().getId(), slot.getDisplayName()));
            }
        }

        // Teacher's required room must be used, when it applies to this block
        // (mirrors SchoolConstraintProvider.teacherRequiredRoomMustBeUsed(),
        // also not excluded for pinned rows: a non-pinned block's room is
        // already structurally guaranteed correct by
        // CourseBlockAssignment.getMatchingRooms(), so this can only ever
        // fire for a pinned row whose room drifted out of sync with its
        // teacher's current requirement - e.g. TeacherController.
        // backfillRequiredRoom() explicitly skips pinned blocks when the
        // requirement changes).
        if (a.isTeacherRequiredRoomApplicable() && a.getRoom() != null
                && !a.getTeacher().getRequiredRoomName().equals(a.getRoom().getName())) {
            problems.add(String.format("%s room '%s' does not match teacher '%s''s required room '%s'.",
                    describe(a), a.getRoom().getName(), a.getTeacher().getId(),
                    a.getTeacher().getRequiredRoomName()));
        }
    }

    /** Pairwise conflict checks among pinned blocks that share teacher/group/room. */
    private static void validateConflicts(List<CourseBlockAssignment> pinned, List<String> problems) {
        for (int i = 0; i < pinned.size(); i++) {
            for (int j = i + 1; j < pinned.size(); j++) {
                CourseBlockAssignment a1 = pinned.get(i);
                CourseBlockAssignment a2 = pinned.get(j);
                if (!blocksOverlap(a1.getTimeslot(), a2.getTimeslot())) {
                    continue;
                }
                if (a1.getTeacher() != null && a1.getTeacher().equals(a2.getTeacher())) {
                    problems.add(String.format("Pinned teacher double-booking: %s and %s share teacher '%s'.",
                            describe(a1), describe(a2), a1.getTeacher().getId()));
                }
                if (a1.getGroup() != null && a1.getGroup().equals(a2.getGroup())) {
                    problems.add(String.format("Pinned group clash: %s and %s share group '%s' at the same time.",
                            describe(a1), describe(a2), a1.getGroup().getId()));
                }
                if (a1.getRoom() != null && a1.getRoom().equals(a2.getRoom())) {
                    problems.add(String.format("Pinned room double-booking: %s and %s share room '%s'.",
                            describe(a1), describe(a2), a1.getRoom().getName()));
                }
            }
        }
    }

    /** Same overlap semantics as {@code SchoolConstraintProvider.blocksOverlap}. */
    private static boolean blocksOverlap(BlockTimeslot b1, BlockTimeslot b2) {
        if (b1 == null || b2 == null) {
            return false;
        }
        if (!b1.getDayOfWeek().equals(b2.getDayOfWeek())) {
            return false;
        }
        int start1 = b1.getStartHour();
        int end1 = start1 + b1.getLengthHours();
        int start2 = b2.getStartHour();
        int end2 = start2 + b2.getLengthHours();
        return start1 < end2 && start2 < end1;
    }

    private static String describe(CourseBlockAssignment a) {
        String group = a.getGroup() != null ? a.getGroup().getId() : "?";
        String course = a.getCourse() != null ? a.getCourse().getId() : "?";
        return String.format("Assignment '%s' (group %s, course %s)", a.getId(), group, course);
    }
}
