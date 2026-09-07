import React from 'react';
import { useTranslation } from 'react-i18next';
import { formatHour } from '../constants';

/**
 * One schedule entry's card, shared by Schedule.jsx and MySchedule.jsx's
 * grids. `hasConflict` (true when this entry shares its merged cell with
 * another overlapping entry - see buildDayWindows in constants.js) gives the
 * card a warning border instead of its usual pinned/movable one, so a real
 * double-booking is visibly flagged rather than silently mis-rendered.
 * `showTeacher` is false in MySchedule, where every entry is already the
 * same teacher. `fillHeight` (true only when this is the cell's sole entry)
 * stretches the card to fill its parent `<td>`'s full height - which, for a
 * multi-hour block, is taller than one row thanks to `rowSpan`. It must stay
 * false whenever a cell holds more than one stacked entry (a conflict): each
 * card sizing to 100% of a shared cell would make them fight over the same
 * space instead of stacking, so a conflict cell's cards size to their own
 * natural content height instead, and the cell simply grows to fit all of
 * them. `onClick` is optional - only Schedule.jsx's grid (for a writer
 * viewing the live schedule) passes one, to open the move/pin editor;
 * MySchedule.jsx never does, so a teacher's own read-only view stays inert.
 */
function ScheduleEntryCard({ entry, hasConflict = false, showTeacher = true, fillHeight = false, onClick = null }) {
  const { t } = useTranslation();
  // Derived (color-mix) from the same primary/danger tokens the rest of the
  // app uses, rather than one-off hex - see Teachers.jsx's own
  // color-mix(...) usage for the established pattern this follows.
  const borderColor = hasConflict
    ? 'var(--color-danger)'
    : entry.pinned
      ? 'color-mix(in srgb, var(--color-danger) 45%, white)'
      : 'color-mix(in srgb, var(--color-primary) 45%, white)';

  return (
    <div
      role={onClick ? 'button' : undefined}
      tabIndex={onClick ? 0 : undefined}
      onClick={onClick || undefined}
      onKeyDown={onClick ? (e) => { if (e.key === 'Enter' || e.key === ' ') { e.preventDefault(); onClick(); } } : undefined}
      style={{
        backgroundColor: entry.pinned ? 'var(--color-danger-bg)' : 'var(--color-info-bg)',
        border: `2px solid ${borderColor}`,
        borderRadius: '4px',
        padding: '8px',
        margin: '4px',
        fontSize: '12px',
        boxSizing: 'border-box',
        ...(onClick ? { cursor: 'pointer' } : {}),
        ...(fillHeight ? { height: 'calc(100% - 8px)' } : {}),
      }}
    >
      <div style={{ fontWeight: 'bold', marginBottom: '4px' }}>{entry.courseName}</div>
      <div style={{ fontSize: '11px', color: 'var(--color-text-secondary)' }}>{entry.groupName}</div>
      {showTeacher && <div style={{ fontSize: '11px', color: 'var(--color-text-secondary)' }}>{entry.teacherName}</div>}
      <div style={{ fontSize: '11px', color: 'var(--color-text-secondary)' }}>{entry.roomName}</div>
      <div style={{ fontSize: '10px', color: 'var(--color-text-secondary)', marginTop: '4px' }}>
        {formatHour(entry.startHour)} - {formatHour(entry.startHour + entry.lengthHours)} ({entry.lengthHours}h)
      </div>
      {entry.pinned && (
        <div style={{ color: 'var(--color-danger-text)', fontSize: '10px', marginTop: '2px' }}>
          <span aria-hidden="true">📌</span> {t('schedule.pinnedLabel')}
        </div>
      )}
    </div>
  );
}

export default ScheduleEntryCard;
