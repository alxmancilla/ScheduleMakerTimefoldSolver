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
