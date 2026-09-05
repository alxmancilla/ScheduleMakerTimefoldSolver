# How "Generate Blocks" works

This documents the current implementation of `BlockGenerationService` (web
module) as of the availability-aware block generation feature
(`feature/availability-aware-block-generation`), including the two
generation-time heuristics ("Scenario 1" and "Scenario 2") added on top of
the original template/room-requirement decomposition, the shared per-teacher
calendar ("Option C", added 2026-09-05 — see "Stage 0.5" below) that lets
those heuristics see a teacher's *other* pairings, not just the one currently
being decomposed, its "effective calendar" follow-up (same date, see "Stage
0.5" below) that extends this to a teacher's *pre-existing* assignments too,
and Core's minimal-upgrade shape preference (same date, see Stage 2) that
keeps as many small blocks as possible instead of uniformly resizing
everything. It's a companion to CLAUDE.md's "Availability-aware block
generation" summary — this file goes into the full mechanics; CLAUDE.md
keeps the short version and links here.

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
  null`. `generateBlocksForGroupCourse` builds this teacher's *effective*
  calendar fresh for that single call instead (see "Effective calendar"
  below) — this is deliberate: it's the common case, and its
  single-pairing behavior must be byte-for-byte unaffected by anything
  added here (verified by running every pre-existing test unchanged after
  each of these features landed).
- **A teacher with 2+ pending pairings** gets one shared, mutable effective
  calendar computed *once*, and their pairings are sorted
  **largest-hours-first** (`totalHoursFor`, summing `course_room_requirement`
  hours when present, else `requiredHoursPerWeek`), **tie-broken by
  ascending course semester, then by group id** (added 2026-09-05), before
  being processed against it, one at a time. Largest-hours-first is a
  bin-packing heuristic: giving the pairing with the least flexibility (the
  most hours to place) first claim on the calendar produces better outcomes
  than an arbitrary/input order would. The semester/id tie-break exists
  because the hours-only comparator left ties (a real, common case — e.g.
  six groups all needing the same 5h/week of one course) falling through to
  whatever order `studentGroupRepository.findAll()` happened to return, which
  isn't contractually stable — that incidental order was silently deciding
  which group's shape got squeezed. Breaking by ascending semester gives a
  lower-semester group first claim on the calendar's more comfortable
  shapes, consistent with how this system already privileges semester-1
  groups elsewhere (earlier starts, the harder 2pm cutoff); the group-id
  tiebreak after that makes the whole ordering fully reproducible across
  reruns of identical data.

Each pairing in a shared group still goes through the *same* `decomposeHours`
call as any other pairing — the only difference is what `TeacherCalendar`
(windows + extra margin, see below) gets passed in. After a shape is chosen
for a pairing, `consumeFromCalendar` calls
`AvailabilityAwareBlockShaper.assignWindows(...)` against the shared map to
actually remove those hours from it (discarding the specific day/hour
placement — a non-exclusive teacher's blocks are still left for the solver
to place; only the *hours consumed* matter to the next pairing in line), so
the *next* pairing sharing that teacher sees a genuinely reduced calendar,
not the full one.

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

### Effective calendar: a teacher's own existing load (added 2026-09-05)

The mechanism above only protected pairings pending *in the same
`generateBlocks()` run*. A brand-new pairing for a teacher who already has
other, previously-generated (or manually created) assignments was still
shaped against their full raw availability, blind to hours those existing
assignments already claim — the same blind spot the section above fixes,
recurring one level up across *runs* instead of within one.

`BlockGenerationService.buildEffectiveCalendar(teacher, existingAssignments)`
closes this, and runs for **every** resolved teacher now — not just when 2+
pairings are pending together:

- A **pinned** existing assignment has a known day/hour, so its hours are
  subtracted from the calendar exactly:
  `AvailabilityAwareBlockShaper.windowsByDay(teacher, consumedRanges)` (a new
  overload of the existing method) removes specific `[dayOfWeek, startHour,
  length]` ranges from the teacher's raw declared availability before any
  window is computed — removing an hour from the middle of an otherwise-open
  run naturally splits it into two windows once the remaining hours are
  re-scanned.
- A still-**movable** (non-pinned) existing assignment can't be subtracted
  the same way — it has no placed day yet — so its mere presence instead
  requires one extra margin day
  (`EXTRA_MARGIN_DAYS_FOR_EXISTING_MOVABLE_LOAD`, currently 1) on top of
  `AvailabilityAwareBlockShaper.DEFAULT_MARGIN_DAYS` for every pairing
  decomposed against that calendar. This is coarser than exact subtraction,
  but still meaningfully conservative — the alternative (ignoring that load
  entirely) is exactly the gap being closed here.

The result is a `TeacherCalendar` record (`windows` + `extraMarginDays`)
threaded through `decomposeHours` and `consumeFromCalendar` instead of a bare
`Map`. A teacher with no existing assignments at all — the common case, and
always true for an exclusive teacher (Stage 4's own trigger condition
requires zero pre-existing assignments) — gets back exactly the unmodified
raw calendar, so this is purely additive.

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
5. If it doesn't, and the designation is **not** `Core`,
   `AvailabilityAwareBlockShaper.tryAvailabilityAwareShape()` tries
   progressively longer block sizes — from `preferredBlockSize` up to
   `min(4, the largest single contiguous available window still in the
   map)` — and returns the first size whose block count reaches the margin.
   If no size reaches real margin, it gracefully **falls back to the first
   size that's at least bare-feasible** (zero margin) rather than giving up.
   If nothing is even bare-feasible, `decomposeHours` falls back to the
   original naive shape — a genuinely infeasible pairing that
   `PreSolveValidator.validateBlockSpreadCapacity` will still report,
   exactly as it always has. If the designation **is** `Core`, a different,
   more conservative path runs instead — see "Core's minimal-upgrade
   preference" below.

**Critically, this stage never assigns a specific day.** It only decides
block *count* and *length*. The solver still freely places each block among
the teacher's available days — same as any other generated block — unless
Stage 4 below applies.

### Core's minimal-upgrade preference (added 2026-09-05)

Per request, `Core` (hardcoded by this literal designation name, not driven
by whatever `preferredBlockSize` happens to be configured for any component
in general) never goes through the uniform-resize ladder above. Instead,
`AvailabilityAwareBlockShaper.tryMinimalUpgradeShape()` finds the largest
block count — fewest merges — that reaches the day-cap margin, merging only
as many *pairs* of preferred-size blocks into double-size ones as actually
needed:

- 4 hours needing to drop from 4 blocks to 3 becomes `[2, 1, 1]` (one merge)
  rather than `[2, 2]` (every block merged, what the uniform ladder would
  have produced).
- **Doubling** — not a flat "+1" size step — is the only upgrade that
  exactly preserves total hours when merging two same-size blocks into one
  (two 2h blocks merged into a 3h block would lose an hour; merged into a 4h
  block, they don't). So both the base and upgrade sizes come straight from
  `component_block_rule`'s actual configured `preferredBlockSize` for
  `Core` — an admin changing that value in Settings' Block Rules changes
  what this does (e.g. `preferredBlockSize=2` merges pairs into 4h blocks
  instead of 1h into 2h) without any code change.
- A leftover remainder block from packing at the base size (present when
  hours don't divide evenly) is never itself a merge candidate — it's
  already smaller than a full base-size block.
- **Deliberately caps out at double the base size, by explicit choice**:
  unlike the uniform ladder, this never escalates further even when
  doubling isn't enough. If `baseSize * 2` would already exceed the 4h
  structural maximum (base size 3 or 4 — nowhere to merge to at all), or
  even every full-size block merged still isn't bare-feasible,
  `tryMinimalUpgradeShape` returns null and `decomposeHours` falls straight
  back to the untouched naive shape, never trying a uniform bigger shape
  the way every other designation does.

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
  adaptation (Stage 2) no longer has this gap for either the case Option C
  targeted (a teacher's *other pending pairings in the same run*, via Stage
  0.5's shared calendar) or the case the effective-calendar fix targeted (a
  teacher's *pre-existing* assignments from earlier runs or manual edits) —
  but the movable-load side of the effective calendar is still a margin-day
  estimate, not an exact accounting, since a movable assignment's actual day
  is only decided by the solver, after generation is long done.
- **None of this generation-time reasoning guarantees the solver's actual
  placement avoids conflicts** — it only shapes blocks more safely. Confirmed
  live (2026-09-05): a teacher shared across 9 groups for one course, with
  comfortable aggregate slack (27 of 40 hours used) and margin-safe shapes,
  still ended up with 2 double-bookings after both a normal and a 30-minute
  solve with a fresh random seed — the local search settled into a similarly
  hard state each time rather than escaping it, for a densely-shared-teacher
  sub-problem this reasoning doesn't (and structurally can't, since it never
  reasons about placement) prevent.
- **`PreSolveValidator.validateBlockSpreadCapacity` has an analogous blind
  spot to the one Option C fixed here, on the validation side, that hasn't
  itself been fixed**: it checks each (group, course) pair's day-spread
  requirement against a teacher's raw availability independently, the same
  way `decomposeHours` used to before Option C — so it can't catch a case
  where several pairings sharing a teacher are each individually fine but
  collectively too tight. It hasn't been observed causing a false "clean"
  result in practice, but the structural gap is the same one this file's own
  history is about.

## Where the code lives

- `web/src/main/java/com/example/web/service/BlockGenerationService.java` —
  orchestration: the main loop (Stage 0.5's `PendingPair` grouping and
  largest-hours/semester/id sorting), `buildEffectiveCalendar` and the
  `TeacherCalendar` record (the effective-calendar section above),
  template/room-requirement handling, room and teacher defaulting,
  `consumeFromCalendar`/`totalHoursFor`/`semesterOrMax`, the `CORE_DESIGNATION`
  branch in `decomposeHours`, and `tryPinExclusiveTeacherBlocks`.
- `web/src/main/java/com/example/web/service/AvailabilityAwareBlockShaper.java`
  — the pure, DB-free algorithm: `packBlocks`, `fitsWithinDayCap`,
  `tryAvailabilityAwareShape`, `tryMinimalUpgradeShape` (Core's
  minimal-upgrade preference above), `assignWindows`,
  `distinctAvailableDayCount`, `largestContiguousWindow`, and the
  availability-window helpers they're built on. Deliberately free of any
  Spring/repository dependency so it's testable in complete isolation from
  the database. Every one of `windowsByDay`, `distinctAvailableDayCount`,
  `largestContiguousWindow`, `tryAvailabilityAwareShape`, and `assignWindows`
  has two forms: a `TeacherEntity`-based one (builds a fresh, single-use
  calendar internally — unchanged pre-Option-C behavior) and a
  `Map<Integer, List<int[]>>`-based one that operates directly on a
  caller-supplied, possibly-already-partly-consumed calendar — the form
  Stage 0.5's shared-calendar grouping relies on. `windowsByDay` additionally
  has a `(TeacherEntity, List<int[]> consumedRanges)` overload for carving
  specific pinned hours out before computing windows at all (the effective
  calendar's exact-subtraction case). `assignWindows(List, int, Map)` is
  transactional (deep-copies the map, commits only on full success) since
  that map can now be shared and reused across several pairings' calls,
  unlike the old single-use-per-call shape. `tryMinimalUpgradeShape` has only
  the primitive-argument form (`baseSize`, `maxBlocksPerDay`, `availableDays`,
  `marginDays`) — it doesn't need a calendar map at all, since it reasons
  purely about block *counts*, not specific windows.
- Tests: `AvailabilityAwareBlockShaperTest` (the pure algorithm, every path,
  including the `Map`-based overloads, the transactional commit/rollback
  behavior, the pinned-hours-carving `windowsByDay` overload, and
  `tryMinimalUpgradeShape` across several base sizes) and
  `BlockGenerationServiceTest` (both scenarios end-to-end, including all the
  negative paths in Stage 4, the shared calendar's cross-pairing effects,
  largest-hours/semester/id ordering, the effective calendar's pinned
  subtraction and movable-load margin, and Core's minimal-upgrade behavior
  including its hard cap).
