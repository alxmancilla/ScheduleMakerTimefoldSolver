import { describe, expect, test } from 'vitest';
import { formatHour, buildDayWindows, groupByConstraint } from './constants';

describe('formatHour', () => {
  test('zero-pads a single-digit hour', () => {
    expect(formatHour(7)).toBe('07:00');
  });

  test('leaves a two-digit hour as-is', () => {
    expect(formatHour(14)).toBe('14:00');
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
      { name: 'No teacher double-booking', descriptions: ['A <-> B', 'C <-> D'] },
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
    expect(result).toEqual([{ name: 'Room capacity', descriptions: ['over by 3'] }]);
  });
});
