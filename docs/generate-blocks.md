# How "Generate Blocks" works

This documents the current implementation of `BlockGenerationService` (web
module) as of the availability-aware block generation feature
(`feature/availability-aware-block-generation`), including the two
generation-time heuristics ("Scenario 1" and "Scenario 2") added on top of
the original template/room-requirement decomposition, and the shared
per-teacher calendar ("Option C", added 2026-09-05 — see "Stage 0.5" below)
that lets those heuristics see a teacher's *other* pairings, not just the one
currently being decomposed. It's a companion to CLAUDE.md's
"Availability-aware block generation" summary — this file goes into the full
mechanics; CLAUDE.md keeps the short version and links here.

## Entry point and scope

`BlockGenerationService.generateBlocks()` is the whole entry point (triggered
via the web UI's "Generate Blocks" button, `BlockGenerationController`). For
every `StudentGroupEntity` and every `GroupCourseEntity` it has:

- Skip if the course can't be found, or is inactive (a `group_course` link
  can predate a course later being deactivated).
- **Skip if the (group, course) pair already has *any* `course_block_assignment`
  rows** — this is the single most important scoping fact: `generateBlocks()`
  is a **gap-filler**, never a re-shaper. A pair that already has blocks,
  however those blocks got there (an earlier generation run, a manual
  assignment, an Excel import), is left completely untouched. There is no
  "regenerate this pair with the latest logic" operation — the only way to
  apply improved generation logic to an already-populated pair is to delete
  its rows first, so it looks new to the generator again.

Before the main loop, `generateBlocks()` makes one pass over **every** group's
courses (not just the ones missing blocks) to build
`groupCourseCountByTeacher`: how many `group_course` links use each teacher
as `default_teacher_id`, across the whole dataset. This feeds the
exclusive-teacher check in Stage 4 — it has to see the *true* picture,
including pairings that don't have blocks yet in this same run, not just
what's already in `course_block_assignment`.

## Stage 0.5 — grouping pending pairs by teacher ("Option C": the shared calendar)

**The problem this solves**: `decomposeHours` (Stage 2) and `assignWindows`
(Stage 4) both reason from a teacher's *full* raw availability. That's fine
when a teacher's entire load is one (group, course) pairing, but when the
same teacher is `default_teacher_id` for *several* pairings needing
generation in the same run (e.g. one teacher teaching the same course to 5
different groups), reasoning about each pairing in isolation is wrong: it
lets every pairing believe it has the teacher's whole calendar to itself,
when in reality the first pairing's blocks are about to claim part of it.
A dataset-wide simulation confirmed this isn't a corner case — 16 pairs
(clustered around a handful of heavily-shared teachers) came out shaped
differently once this was accounted for, not just the 1-2 pairs a
same-teacher coincidence might suggest.

**The fix**: before decomposing anything, `generateBlocks()` collects every
pending (group, course) pair needing generation into a `PendingPair` list,
then groups the ones with a resolved `default_teacher_id` by that teacher.

- **A teacher with exactly one pending pairing** gets `sharedCalendar =
  null`. Nothing changes from the description below — `decomposeHours` and
  `assignWindows` build a fresh `windowsByDay(teacher)` per call, exactly as
  before this feature existed. This is deliberate: it's the common case, and
  it must be byte-for-byte unaffected (verified by running every pre-existing
  test unchanged after this feature landed).
- **A teacher with 2+ pending pairings** gets one shared, mutable calendar —
  `AvailabilityAwareBlockShaper.windowsByDay(teacher)` computed *once* — and
  their pairings are sorted **largest-hours-first** (`totalHoursFor`, summing
  `course_room_requirement` hours when present, else
  `requiredHoursPerWeek`) before being processed against it, one at a time.
  This is a bin-packing heuristic: giving the pairing with the least
  flexibility (the most hours to place) first claim on the calendar produces
  better outcomes than an arbitrary/input order would.

Each pairing in a shared group still goes through the *same* `decomposeHours`
call as any other pairing — the only difference is what `Map<Integer,
List<int[]>>` gets passed in. After a shape is chosen for a pairing,
`consumeFromCalendar` calls `AvailabilityAwareBlockShaper.assignWindows(...)`
against the shared map to actually remove those hours from it (discarding the
specific day/hour placement — a non-exclusive teacher's blocks are still left
for the solver to place; only the *hours consumed* matter to the next
pairing in line), so the *next* pairing sharing that teacher sees a
genuinely reduced calendar, not the full one.

This is also why `AvailabilityAwareBlockShaper.assignWindows(List, int, Map)`
is transactional (deep-copies the map, only commits on full success): since
the map now outlives a single call and is shared across pairings, a failed
attempt must never partially mutate it — that would corrupt what the next
pairing sees.

Only Stage 2 (shape adaptation) is affected by sharing. Stage 4
(exclusive-teacher pinning) is unreachable for any teacher in a shared group
by construction — pinning already required the teacher to have *no other*
pairing at all, which is mutually exclusive with belonging to a 2+-pairing
group here.

## Stage 1 — where a block's shape comes from (priority order)

For each (group, course) pair that needs generating:

1. **`course_block_template` rows**, if any apply to this (course, group).
   When both a group-specific and a `NULL`-group ("applies to all groups")
   template exist for the same `block_index`, the group-specific one wins
   (`resolveTemplates`). A template's fields are used verbatim: block length,
   room type, and its `preferredRoomName` is written straight onto the
   generated block's `roomName` (not just kept as a soft hint), since room is
   never solver-assigned in this system. If the template also requests
   `pinAssignment=true`, the block is pinned to `preferredTimeslotId` — but
   only if a room actually resolved; if the template wanted pinning but no
   compatible room could be found, the block is left unpinned with a warning
   rather than the whole generation batch failing or a room being fabricated.

2. **`course_room_requirement` rows** (dual/multi room-type courses, e.g. 4h
   in a computer lab + 1h in a standard room). Each requirement's hours are
   decomposed *separately* via `decomposeHours` (Stage 2), so each portion
   carries its own `satisfiesRoomType` / preferred room.

3. **Neither** (the common case for a course with no templates or dual
   requirements) — the course's `requiredHoursPerWeek` is decomposed as one
   run of `decomposeHours` under the course's single legacy `roomRequirement`
   field.

## Stage 2 — `decomposeHours`: how many blocks, how long ("Scenario 2")

This is the availability-aware shape adaptation, used for tiers 2 and 3
above (never for explicit templates, which specify their own length).

1. Look up `component_block_rule` for the course's designation (e.g. Core,
   Specialized) to get `preferredBlockSize` and `maxBlocksPerDay` — both
   default (2 and 2 respectively) when the designation has no configured
   rule.
2. Pack the required hours into blocks of exactly `preferredBlockSize`
   (`AvailabilityAwareBlockShaper.packBlocks`), with a shorter trailing
   remainder block if it doesn't divide evenly. Call this the **naive
   shape** — e.g. 5 hours at size 1 → `[1,1,1,1,1]`.
3. **No teacher resolved yet** (`group_course.default_teacher_id` unset) →
   return the naive shape unchanged. There's no availability data to reason
   from. (`decomposeHours` takes a `Map<Integer, List<int[]>>` of the
   teacher's remaining windows-by-day, not a `TeacherEntity` — `null` means
   "no teacher, or none resolvable".)
4. **Windows resolved** → check whether the naive shape's block count needs
   no more distinct days than the map currently has available, **with at
   least `AvailabilityAwareBlockShaper.DEFAULT_MARGIN_DAYS` (1) day to spare
   beyond the bare minimum** (`fitsWithinDayCap`, added 2026-09-05 — see
   "Why the margin requirement exists" below). If it already has that
   margin, the naive shape is kept. When the pairing's teacher is shared
   with other pending pairings (Stage 0.5), this map may already be partly
   consumed by an earlier pairing in the same run — the map passed in is
   whatever's left at the time this pairing's turn comes up, not necessarily
   the teacher's full raw availability.
5. If it doesn't, `AvailabilityAwareBlockShaper.tryAvailabilityAwareShape()`
   tries progressively longer block sizes — from `preferredBlockSize` up to
   `min(4, the largest single contiguous available window still in the
   map)` — and returns the first size whose block count reaches the margin.
   If no size reaches real margin, it gracefully **falls back to the first
   size that's at least bare-feasible** (zero margin) rather than giving up.
   If nothing is even bare-feasible, `decomposeHours` falls back to the
   original naive shape — a genuinely infeasible pairing that
   `PreSolveValidator.validateBlockSpreadCapacity` will still report,
   exactly as it always has.

**Critically, this stage never assigns a specific day.** It only decides
block *count* and *length*. The solver still freely places each block among
the teacher's available days — same as any other generated block — unless
Stage 4 below applies.

### Why the margin requirement exists

Live, on 2026-09-05: after regenerating blocks with the availability-aware
shaper (margin-less at the time), two (group, course) pairs came out with
block counts needing *exactly* as many distinct days as their teacher had
available — zero spare days. Both went on to violate the solver's
`maxTwoBlocksPerCoursePerGroupPerDay` hard constraint once solved: with zero
margin, any other scheduling pressure that day (the group already busy, a
room conflict) leaves the solver nowhere to go, and it has to violate
something. The margin exists to catch this *before* generation, not after a
solve fails.

This is a **probabilistic hedge, not a guarantee**. The same night, a third
pair that had a full spare day of margin was *still* violated by the
solver's actual search — margin lowers how often the solver gets squeezed
into a violation, it doesn't prove it can't happen.

## Stage 3 — room and teacher defaulting (every generated block, all tiers)

- **Teacher**: every block generated for a (group, course) pair gets
  `group_course.default_teacher_id` (if set) as its `teacherId`, since
  teacher is never solver-assigned. This is the *only* place a teacher can
  be pre-assigned before blocks exist — set via `PUT
  /api/groups/{groupId}/courses/{courseName}/default-teacher`.
- **Room** (`defaultRoomFor`), in priority order:
  1. The `default_teacher_id`'s `requiredRoomName`, if set and its type
     satisfies the block's `satisfiesRoomType` — a teacher's fixed-room
     requirement overrides the group's own range.
  2. The group's curated `group_room_range` for that `satisfiesRoomType`,
     but *only* when it resolves to exactly one type-compatible room. A
     range of 2+ rooms has no single deterministic choice, so the block is
     left roomless for the solver (or a human) to pick among them instead.
  3. Otherwise, left roomless.
  - A room already supplied more specifically — a template's own
    `preferredRoomName`, or a room requirement's `defaultPreferredRoom` —
    always wins over this default; `defaultRoomFor` only runs when nothing
    more specific was given.

## Stage 4 — exclusive-teacher auto-pinning ("Scenario 1")

After a (group, course) pair's blocks are generated via tiers 2/3 (templated
blocks are pinned or not per their own `pinAssignment` flag, independently
of this stage), `generateBlocksForGroupCourse` checks one more thing:

**Trigger** — the resolved teacher's *entire* teaching load is this one
pairing:
- `groupCourseCountByTeacher` shows exactly 1 `group_course` link uses them
  as default teacher (checked across the whole dataset, per Stage 0 above),
  **and**
- They have zero pre-existing `course_block_assignment` rows anywhere
  (`assignmentRepository.findByTeacherId(...).isEmpty()`) — this guards
  against a teacher who's been hand-assigned to something outside the
  `group_course.default_teacher_id` mechanism entirely (e.g. a block created
  directly via the assignments API or an Excel import).

When both hold, there's no real placement decision left for the solver to
make: the teacher's calendar has exactly one room for these blocks to go.
`tryPinExclusiveTeacherBlocks` handles it:

1. Greedily assigns each block a concrete `(dayOfWeek, startHour)` from the
   teacher's actual contiguous availability windows
   (`AvailabilityAwareBlockShaper.assignWindows`), respecting
   `maxBlocksPerDay`. This is all-or-nothing at the algorithm level: if any
   block length can't be placed, the whole attempt returns `null` rather
   than a partial assignment.
2. For each block, in order, checks:
   - A matching `BlockTimeslotEntity` actually exists for the computed
     `(day, hour, length)` triple.
   - A room was actually resolved in Stage 3 (non-null `roomName`).
   - The slot doesn't end after a **HARD**-severity `semester_hour_limit`
     configured for the course's semester (`violatesSemesterHourLimit`) —
     mirrors `BlockScheduleMath.violatesHardSemesterHourLimit()` in the
     engine module; duplicated here rather than shared, since `web` doesn't
     depend on `engine`.
   - The slot doesn't overlap anything this *group* already has pinned
     (`overlapsGroupsPinnedData`).
   - The slot's room isn't already pinned to a *different* assignment at an
     overlapping time, for **any** group, not just this one
     (`overlapsAnyPinnedRoomBooking`).
3. Only if every block clears every check does it commit: each block gets
   `blockTimeslotId` set and `pinned = true`, then is re-saved. **Any single
   failure at any block aborts pinning for the entire batch**, with a
   warning explaining which check failed — nothing is partially pinned.

These five checks are deliberately the same facts `PreSolveValidator`'s
pinned-data-integrity checks re-verify for any pinned row. Since a pinned
row skips the solver's own constraint checking entirely, this list is the
*only* thing standing between a pin and a silent, permanent violation that
nothing downstream would ever catch. This was learned directly: an earlier
version of this method (committed, then fixed the same day) checked only
timeslot existence, room resolution, and this-group's-own pinned conflicts —
it was missing the semester-hour-limit and cross-group-room checks, and a
real live run used it to pin a block past a HARD semester limit before the
gap was caught and closed.

## Output

`generateBlocks()` returns a `GenerationResult`:

- `blocksCreated` — total blocks actually written.
- `groupCoursesSkippedExisting` — how many (group, course) pairs were
  skipped because they already had blocks.
- `warnings` — a human-readable list covering: missing/inactive courses,
  a template that wanted pinning but got no room, and any of the five
  pin-attempt failures from Stage 4. Nothing here is fatal — a warning means
  "this one thing was left for the solver or a human," not that generation
  failed.

## Stated limitations

- **Gap-filler only, never retroactive.** Improving this logic doesn't
  retroactively improve schedules already generated under an older version —
  the affected pairs' rows have to be deleted and regenerated deliberately.
- **The margin (Stage 2) is a hedge, not a guarantee.** A pair with genuine
  margin can still be violated by the solver's actual search quality; margin
  changes the odds, not the certainty.
- **Some logic is deliberately duplicated from the engine module** — the
  semester-hour-limit check here, and the room-priority resolution in
  `defaultRoomFor`, both have engine-side equivalents
  (`BlockScheduleMath.violatesHardSemesterHourLimit()`,
  `CourseBlockAssignment.getMatchingRooms()`) that aren't reused directly,
  since the `web` module has no dependency on `engine`. This is an existing,
  accepted asymmetry in this codebase, not something introduced by this
  feature.
- **Window assignment (Stage 4) still reasons only from the teacher's own
  declared availability**, never from what the solver will eventually place
  for anyone else's commitments — but it's only ever invoked for a teacher
  with no other commitments in the first place (Stage 4's own trigger
  condition), which is what makes committing to a pin safe there. Shape
  adaptation (Stage 2) no longer has this gap for the specific case Option C
  targets — a teacher's *other pending pairings in the same run* — since
  Stage 0.5's shared calendar makes each pairing see the others' consumption.
  It still can't see commitments outside this run (a pairing that already
  has blocks, or a future run's changes), only pending pairings sharing a
  teacher within the same `generateBlocks()` call.

## Where the code lives

- `web/src/main/java/com/example/web/service/BlockGenerationService.java` —
  orchestration: the main loop (now including Stage 0.5's `PendingPair`
  grouping/sorting), template/room-requirement handling, room and teacher
  defaulting, `consumeFromCalendar`/`totalHoursFor` (Stage 0.5), and
  `tryPinExclusiveTeacherBlocks`.
- `web/src/main/java/com/example/web/service/AvailabilityAwareBlockShaper.java`
  — the pure, DB-free algorithm: `packBlocks`, `fitsWithinDayCap`,
  `tryAvailabilityAwareShape`, `assignWindows`, `distinctAvailableDayCount`,
  `largestContiguousWindow`, and the availability-window helpers they're
  built on. Deliberately free of any Spring/repository dependency so it's
  testable in complete isolation from the database. Every one of these has
  two forms: a `TeacherEntity`-based one (builds a fresh, single-use
  `windowsByDay(teacher)` internally — unchanged pre-Option-C behavior) and
  a `Map<Integer, List<int[]>>`-based one that operates directly on a
  caller-supplied, possibly-already-partly-consumed calendar — the form
  Stage 0.5's shared-calendar grouping relies on.
  `assignWindows(List, int, Map)` is transactional (deep-copies the map,
  commits only on full success) since that map can now be shared and reused
  across several pairings' calls, unlike the old single-use-per-call shape.
- Tests: `AvailabilityAwareBlockShaperTest` (the pure algorithm, every path,
  including the `Map`-based overloads and the transactional
  commit/rollback behavior) and `BlockGenerationServiceTest` (both scenarios
  end-to-end, including all the negative paths in Stage 4, and the shared
  calendar's cross-pairing effects and largest-hours-first ordering).
