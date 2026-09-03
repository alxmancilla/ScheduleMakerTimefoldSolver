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
 * them.
 */
function ScheduleEntryCard({ entry, hasConflict = false, showTeacher = true, fillHeight = false }) {
  const { t } = useTranslation();
  const borderColor = hasConflict ? '#e74c3c' : entry.pinned ? '#ffcccc' : '#b3d9e6';

  return (
    <div
      style={{
        backgroundColor: entry.pinned ? '#ffe6e6' : '#e8f4f8',
        border: `2px solid ${borderColor}`,
        borderRadius: '4px',
        padding: '8px',
        margin: '4px',
        fontSize: '12px',
        boxSizing: 'border-box',
        ...(fillHeight ? { height: 'calc(100% - 8px)' } : {}),
      }}
    >
      <div style={{ fontWeight: 'bold', marginBottom: '4px' }}>{entry.courseName}</div>
      <div style={{ fontSize: '11px', color: '#555' }}>{entry.groupName}</div>
      {showTeacher && <div style={{ fontSize: '11px', color: '#555' }}>{entry.teacherName}</div>}
      <div style={{ fontSize: '11px', color: '#555' }}>{entry.roomName}</div>
      <div style={{ fontSize: '10px', color: '#888', marginTop: '4px' }}>
        {formatHour(entry.startHour)} - {formatHour(entry.startHour + entry.lengthHours)} ({entry.lengthHours}h)
      </div>
      {entry.pinned && <div style={{ color: '#c00', fontSize: '10px', marginTop: '2px' }}>📌 {t('schedule.pinnedLabel')}</div>}
    </div>
  );
}

export default ScheduleEntryCard;
