// The `room_type` lookup table restricts `room.type` (and, by convention,
// course_block_assignment.satisfies_room_type) to exactly these values via
// FK. Keep in sync with database/schema_block_scheduling*.sql. Specialized -
// Workshop and Specialized - Computer Lab share a label prefix but stay
// non-interchangeable - see common/RoomTypeCompatibility.java.
export const ROOM_TYPES = [
  'Standard',
  'Mixed',
  'Specialized - Workshop',
  'Specialized - Computer Lab',
];

/** Zero-padded HH:00, e.g. formatHour(7) -> "07:00", formatHour(14) -> "14:00". */
export const formatHour = (hour) => `${String(hour).padStart(2, '0')}:00`;

// Human-readable "who/when/how this row was pinned" line, shared by the
// Assignments table, the Timetable grid card, and the move/pin editor - all
// three read the same pinnedAt/pinnedBy/pinSource fields (stamped server-side
// by CourseBlockAssignmentController.stampPinProvenance / SYSTEM pins by
// BlockGenerationService.tryPinExclusiveTeacherBlocks). A row pinned before
// provenance tracking existed (2026-09-07) has pinned=true but all three
// fields null - it still gets a label, just the "no history" one. `t` is
// passed in rather than imported so this stays a plain pure function.
export function formatPinProvenance(row, t) {
  if (!row?.pinnedAt) return t('common.pinProvenance.noHistory');
  const date = row.pinnedAt.replace('T', ' ').split('.')[0];
  return row.pinSource === 'SYSTEM'
    ? t('common.pinProvenance.bySystem', { date })
    : t('common.pinProvenance.byUser', { username: row.pinnedBy, date });
}

/**
 * The pin marker to show at a glance: 🤖 for a block the solver's
 * block-generation heuristic auto-pinned (pinSource === 'SYSTEM'), 📌 for a
 * person's pin or one predating provenance tracking. formatPinProvenance
 * still carries the who/when detail in the tooltip.
 */
export const pinIcon = (row) => (row?.pinSource === 'SYSTEM' ? '🤖' : '📌');

// Mirrors Room.satisfiesRequirement() (engine domain model, backed by
// common/RoomTypeCompatibility.java): a room satisfies a requirement of its
// own type, and a Mixed room additionally satisfies Standard and
// Specialized - Workshop (it's equipped for both), but never the reverse.
// Specialized - Computer Lab stays strictly separate - not satisfied by
// Mixed. The frontend has no way to call the Java implementation directly,
// so this is a deliberate, single mirror of that rule - shared here instead
// of re-copied per component that needs it.
export function roomMatchesType(room, requiredType) {
  if (!requiredType) return true;
  if (!room) return false;
  return room.type === requiredType
    || (room.type === 'Mixed' && (requiredType === 'Standard' || requiredType === 'Specialized - Workshop'));
}

/** True if a teacher is qualified for a course name (or no course name was given yet). */
export function teacherQualifiedFor(teacher, courseName) {
  return !courseName || teacher.qualifications.some((q) => q.qualification === courseName);
}

/**
 * Merges a single day's schedule entries into non-overlapping time windows
 * (the standard "merge overlapping intervals" sweep), so a schedule grid can
 * render one HTML table cell (rowSpan = the window's length, entries stacked
 * inside it) per window instead of one cell per entry.
 *
 * This matters because a genuine double-booking - two entries for the same
 * group/teacher whose time ranges overlap - can't each get their own
 * spanning `<td>`: HTML tables only auto-reserve a column for a `rowSpan`
 * from an *earlier* row, so a second entry starting later, mid-span, has
 * nowhere of its own to go. Emitting an extra `<td>` for it anyway (the
 * previous approach here) doesn't create a new column - it silently pushes
 * every following cell in that row one column to the right for the
 * remainder of that overlap, corrupting the rest of the row, cascading into
 * a fabricated extra column past the last real day once nothing is left to
 * absorb the shift. Merging overlapping entries into one shared window (and
 * rendering them stacked, flagged as a conflict) keeps every row's real
 * column count exactly right regardless of how many entries overlap.
 *
 * @param entries all schedule entries for ONE day (pre-filtered to a single
 *   dayOfWeek and whatever group/teacher/room scope the caller applies)
 * @returns windows sorted by startHour: [{ startHour, endHour, entries }].
 *   A window with more than one entry is a real scheduling conflict.
 */
export function buildDayWindows(entries) {
  const sorted = [...entries].sort((a, b) => a.startHour - b.startHour);
  const windows = [];
  for (const entry of sorted) {
    const entryEnd = entry.startHour + entry.lengthHours;
    const current = windows[windows.length - 1];
    if (current && entry.startHour < current.endHour) {
      current.entries.push(entry);
      current.endHour = Math.max(current.endHour, entryEnd);
    } else {
      windows.push({ startHour: entry.startHour, endHour: entryEnd, entries: [entry] });
    }
  }
  return windows;
}

/**
 * Groups a flat {constraintName, description}[] list (as returned by
 * GET /api/schedule/violations) into one entry per constraint, preserving
 * first-seen order - matching how the console/PDF report already groups the
 * same data by rule, rather than one long undifferentiated list. Used by
 * Schedule.jsx's violations panel. Each entry also keeps its own
 * assignmentIds (added 2026-09-07, alongside `descriptions` rather than
 * replacing it, so existing description-only rendering keeps working) so a
 * single description in the panel can be clicked to highlight the exact
 * grid card(s) it's about.
 *
 * @param entries [{ constraintName, description, assignmentIds }]
 * @returns [{ name, descriptions: [description, ...], items: [{ description, assignmentIds }, ...] }] in first-seen order
 */
export function groupByConstraint(entries) {
  const order = [];
  const byName = new Map();
  entries.forEach(({ constraintName, description, assignmentIds }) => {
    if (!byName.has(constraintName)) {
      byName.set(constraintName, { descriptions: [], items: [] });
      order.push(constraintName);
    }
    const bucket = byName.get(constraintName);
    bucket.descriptions.push(description);
    bucket.items.push({ description, assignmentIds: assignmentIds || [] });
  });
  return order.map((name) => ({ name, descriptions: byName.get(name).descriptions, items: byName.get(name).items }));
}

/**
 * The same flat hard/soft violation lists (GET /api/schedule/violations),
 * re-indexed by assignment id instead of by constraint - {@code assignmentId
 * -> { hardCount, softCount, items }} - so Schedule.jsx's grid can flag
 * exactly which card(s) a persisted violation involves (via each entry's own
 * assignmentIds - see ScheduleViolationsDTO.Entry) instead of only listing
 * violations separately from the grid. An assignment with no violations
 * simply has no entry in the returned map.
 *
 * @param hardEntries [{ constraintName, description, assignmentIds }]
 * @param softEntries [{ constraintName, description, assignmentIds }]
 * @returns Map<assignmentId, { hardCount, softCount, items: [{ constraintName, description, isHard }] }>
 */
export function buildViolationsByAssignment(hardEntries, softEntries) {
  const map = new Map();
  const add = (entries, isHard) => {
    (entries || []).forEach(({ constraintName, description, assignmentIds }) => {
      (assignmentIds || []).forEach((id) => {
        if (!map.has(id)) {
          map.set(id, { hardCount: 0, softCount: 0, items: [] });
        }
        const bucket = map.get(id);
        if (isHard) {
          bucket.hardCount += 1;
        } else {
          bucket.softCount += 1;
        }
        bucket.items.push({ constraintName, description, isHard });
      });
    });
  };
  add(hardEntries, true);
  add(softEntries, false);
  return map;
}
