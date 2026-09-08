import { describe, expect, test } from 'vitest';
import { formatHour, formatPinProvenance, pinIcon, buildDayWindows, groupByConstraint, buildViolationsByAssignment, roomMatchesType, teacherQualifiedFor } from './constants';

describe('formatHour', () => {
  test('zero-pads a single-digit hour', () => {
    expect(formatHour(7)).toBe('07:00');
  });

  test('leaves a two-digit hour as-is', () => {
    expect(formatHour(14)).toBe('14:00');
  });
});

describe('formatPinProvenance', () => {
  // Echo the interpolation so the assertions don't depend on the actual copy.
  const t = (key, opts) => (opts ? `${key}:${JSON.stringify(opts)}` : key);

  test('a USER pin names the user and the date', () => {
    expect(formatPinProvenance({ pinnedAt: '2026-09-07T14:18:49.123', pinnedBy: 'scheduler1', pinSource: 'USER' }, t))
      .toBe('common.pinProvenance.byUser:{"username":"scheduler1","date":"2026-09-07 14:18:49"}');
  });

  test('a SYSTEM pin uses the system wording with no username', () => {
    expect(formatPinProvenance({ pinnedAt: '2026-09-07T14:18:49', pinnedBy: null, pinSource: 'SYSTEM' }, t))
      .toBe('common.pinProvenance.bySystem:{"date":"2026-09-07 14:18:49"}');
  });

  test('a row pinned before provenance tracking (no pinnedAt) falls back to the no-history label', () => {
    expect(formatPinProvenance({ pinned: true, pinnedAt: null, pinnedBy: null, pinSource: null }, t))
      .toBe('common.pinProvenance.noHistory');
  });

  test('tolerates a null/undefined row', () => {
    expect(formatPinProvenance(null, t)).toBe('common.pinProvenance.noHistory');
  });
});

describe('pinIcon', () => {
  test('a SYSTEM pin gets the robot marker', () => {
    expect(pinIcon({ pinSource: 'SYSTEM' })).toBe('🤖');
  });

  test('a USER pin, a legacy pin (no pinSource), and a null row all get the pushpin', () => {
    expect(pinIcon({ pinSource: 'USER' })).toBe('📌');
    expect(pinIcon({ pinned: true })).toBe('📌');
    expect(pinIcon(null)).toBe('📌');
  });
});

describe('buildDayWindows', () => {
  const entry = (id, startHour, lengthHours) => ({ id, startHour, lengthHours });

  test('empty input produces no windows', () => {
    expect(buildDayWindows([])).toEqual([]);
  });

  test('a single entry becomes its own window', () => {
    const windows = buildDayWindows([entry('a', 8, 1)]);
    expect(windows).toEqual([{ startHour: 8, endHour: 9, entries: [entry('a', 8, 1)] }]);
  });

  test('non-overlapping entries each get their own window, sorted by start hour', () => {
    const windows = buildDayWindows([entry('b', 10, 1), entry('a', 8, 1)]);
    expect(windows.map((w) => w.startHour)).toEqual([8, 10]);
    expect(windows.every((w) => w.entries.length === 1)).toBe(true);
  });

  test('overlapping entries merge into one shared window - a real double-booking', () => {
    // 8-9 and 8-10 overlap.
    const windows = buildDayWindows([entry('a', 8, 1), entry('b', 8, 2)]);
    expect(windows).toHaveLength(1);
    expect(windows[0]).toMatchObject({ startHour: 8, endHour: 10 });
    expect(windows[0].entries).toHaveLength(2);
  });

  test('a later entry starting exactly when an earlier one ends does NOT merge (touching, not overlapping)', () => {
    const windows = buildDayWindows([entry('a', 8, 1), entry('b', 9, 1)]);
    expect(windows).toHaveLength(2);
  });

  test('a long block absorbs a shorter one nested entirely inside its span', () => {
    // 8-12 (4h) and 9-10 (1h): the second starts inside the first's window.
    const windows = buildDayWindows([entry('a', 8, 4), entry('b', 9, 1)]);
    expect(windows).toHaveLength(1);
    expect(windows[0]).toMatchObject({ startHour: 8, endHour: 12 });
  });

  test('a merged window\'s end hour is the max of its entries, not just the last one processed', () => {
    // 8-9 (1h), 8-11 (3h), 9-10 (1h) - all overlap into one window ending at 11,
    // even though the 3h entry isn't sorted last.
    const windows = buildDayWindows([entry('a', 8, 1), entry('b', 8, 3), entry('c', 9, 1)]);
    expect(windows).toHaveLength(1);
    expect(windows[0].endHour).toBe(11);
  });

  test('three separate groups on the same day stay separate windows', () => {
    const windows = buildDayWindows([
      entry('a', 7, 1),
      entry('b', 9, 1), entry('c', 9, 1), // overlap -> one window
      entry('d', 13, 1),
    ]);
    expect(windows).toHaveLength(3);
    expect(windows.map((w) => w.entries.length)).toEqual([1, 2, 1]);
  });

  test('does not mutate the input array', () => {
    const input = [entry('b', 10, 1), entry('a', 8, 1)];
    const copy = [...input];
    buildDayWindows(input);
    expect(input).toEqual(copy);
  });
});

describe('groupByConstraint', () => {
  test('empty input produces no groups', () => {
    expect(groupByConstraint([])).toEqual([]);
  });

  test('groups multiple descriptions under the same constraint name', () => {
    const result = groupByConstraint([
      { constraintName: 'No teacher double-booking', description: 'A <-> B' },
      { constraintName: 'No teacher double-booking', description: 'C <-> D' },
    ]);
    expect(result).toEqual([
      {
        name: 'No teacher double-booking',
        descriptions: ['A <-> B', 'C <-> D'],
        items: [
          { description: 'A <-> B', assignmentIds: [] },
          { description: 'C <-> D', assignmentIds: [] },
        ],
      },
    ]);
  });

  test('preserves first-seen order of constraint names, not alphabetical', () => {
    const result = groupByConstraint([
      { constraintName: 'Zebra rule', description: 'z1' },
      { constraintName: 'Apple rule', description: 'a1' },
      { constraintName: 'Zebra rule', description: 'z2' },
    ]);
    expect(result.map((g) => g.name)).toEqual(['Zebra rule', 'Apple rule']);
  });

  test('a constraint with a single instance still becomes a one-element group', () => {
    const result = groupByConstraint([{ constraintName: 'Room capacity', description: 'over by 3' }]);
    expect(result).toEqual([{
      name: 'Room capacity',
      descriptions: ['over by 3'],
      items: [{ description: 'over by 3', assignmentIds: [] }],
    }]);
  });

  test('carries each entry\'s own assignmentIds through into items', () => {
    const result = groupByConstraint([
      { constraintName: 'No teacher double-booking', description: 'A <-> B', assignmentIds: ['a1', 'a2'] },
    ]);
    expect(result[0].items).toEqual([{ description: 'A <-> B', assignmentIds: ['a1', 'a2'] }]);
  });
});

describe('buildViolationsByAssignment', () => {
  test('empty input produces an empty map', () => {
    expect(buildViolationsByAssignment([], []).size).toBe(0);
  });

  test('indexes a hard violation under each of its assignmentIds', () => {
    const map = buildViolationsByAssignment(
      [{ constraintName: 'No teacher double-booking', description: 'A <-> B', assignmentIds: ['a1', 'a2'] }],
      [],
    );
    expect(map.get('a1')).toEqual({
      hardCount: 1, softCount: 0,
      items: [{ constraintName: 'No teacher double-booking', description: 'A <-> B', isHard: true }],
    });
    expect(map.get('a2').hardCount).toBe(1);
  });

  test('indexes a soft violation under softCount, not hardCount', () => {
    const map = buildViolationsByAssignment(
      [],
      [{ constraintName: 'Prefer block\'s specified room', description: 'A prefers R1', assignmentIds: ['a1'] }],
    );
    expect(map.get('a1')).toEqual({
      hardCount: 0, softCount: 1,
      items: [{ constraintName: 'Prefer block\'s specified room', description: 'A prefers R1', isHard: false }],
    });
  });

  test('accumulates multiple violations touching the same assignment', () => {
    const map = buildViolationsByAssignment(
      [{ constraintName: 'No teacher double-booking', description: 'A <-> B', assignmentIds: ['a1'] }],
      [{ constraintName: 'Prefer block\'s specified room', description: 'A prefers R1', assignmentIds: ['a1'] }],
    );
    expect(map.get('a1').hardCount).toBe(1);
    expect(map.get('a1').softCount).toBe(1);
    expect(map.get('a1').items).toHaveLength(2);
  });

  test('an assignment with no violations has no entry in the map', () => {
    const map = buildViolationsByAssignment(
      [{ constraintName: 'No teacher double-booking', description: 'A <-> B', assignmentIds: ['a1'] }],
      [],
    );
    expect(map.has('a3')).toBe(false);
  });
});

// Mirrors Room.satisfiesRequirement() (engine domain model / common/RoomTypeCompatibility.java).
describe('roomMatchesType', () => {
  test('no required type means every room matches', () => {
    expect(roomMatchesType({ type: 'Standard' }, null)).toBe(true);
    expect(roomMatchesType({ type: 'Standard' }, undefined)).toBe(true);
    expect(roomMatchesType({ type: 'Standard' }, '')).toBe(true);
  });

  test('no room never matches a real requirement', () => {
    expect(roomMatchesType(null, 'Standard')).toBe(false);
  });

  test('a room of the exact required type matches', () => {
    expect(roomMatchesType({ type: 'Specialized - Computer Lab' }, 'Specialized - Computer Lab')).toBe(true);
  });

  test('Mixed satisfies Standard and Specialized - Workshop', () => {
    expect(roomMatchesType({ type: 'Mixed' }, 'Standard')).toBe(true);
    expect(roomMatchesType({ type: 'Mixed' }, 'Specialized - Workshop')).toBe(true);
  });

  test('Mixed does NOT satisfy Specialized - Computer Lab', () => {
    expect(roomMatchesType({ type: 'Mixed' }, 'Specialized - Computer Lab')).toBe(false);
  });

  test('the reverse never holds - a Standard room does not satisfy a Mixed requirement', () => {
    expect(roomMatchesType({ type: 'Standard' }, 'Mixed')).toBe(false);
  });

  test('a mismatched non-Mixed type never matches', () => {
    expect(roomMatchesType({ type: 'Specialized - Workshop' }, 'Specialized - Computer Lab')).toBe(false);
  });
});

describe('teacherQualifiedFor', () => {
  const teacher = (...qualifications) => ({ qualifications: qualifications.map((q) => ({ qualification: q })) });

  test('no course name means any teacher qualifies (nothing to check yet)', () => {
    expect(teacherQualifiedFor(teacher(), null)).toBe(true);
    expect(teacherQualifiedFor(teacher(), '')).toBe(true);
  });

  test('a teacher qualified for the exact course name matches', () => {
    expect(teacherQualifiedFor(teacher('Math', 'Physics'), 'Math')).toBe(true);
  });

  test('a teacher not qualified for the course does not match', () => {
    expect(teacherQualifiedFor(teacher('Physics'), 'Math')).toBe(false);
  });

  test('a teacher with no qualifications at all does not match a real course name', () => {
    expect(teacherQualifiedFor(teacher(), 'Math')).toBe(false);
  });
});
