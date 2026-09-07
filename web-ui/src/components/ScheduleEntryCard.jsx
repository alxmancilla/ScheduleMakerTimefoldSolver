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
 * `violationInfo` (optional, only Schedule.jsx passes one - see
 * buildViolationsByAssignment in constants.js) is this entry's own persisted
 * hard/soft violations for the currently-viewed run, so the card that's
 * actually involved gets flagged directly instead of only appearing in the
 * separate violations list above the grid. `highlighted` (also only
 * Schedule.jsx) is true right after that same list was clicked to point at
 * this specific card. `idSuffix` disambiguates the DOM id when the same
 * entry renders twice (Schedule.jsx's desktop table and its CSS-only-hidden
 * mobile list both render unconditionally) - without it, both copies would
 * share one id and getElementById would only ever find the desktop one.
 */
function ScheduleEntryCard({
  entry, hasConflict = false, showTeacher = true, fillHeight = false, onClick = null,
  violationInfo = null, highlighted = false, idSuffix = '',
}) {
  const { t } = useTranslation();
  const hasHardViolation = (violationInfo?.hardCount ?? 0) > 0;
  const hasSoftViolation = (violationInfo?.softCount ?? 0) > 0;
  // Derived (color-mix) from the same primary/danger tokens the rest of the
  // app uses, rather than one-off hex - see Teachers.jsx's own
  // color-mix(...) usage for the established pattern this follows. A real
  // conflict (double-booking, computed live from the grid itself) and a
  // persisted hard violation both read as "hard" severity, so they share the
  // same solid danger border; a persisted soft-only violation gets the
  // warning token instead, distinct from the pinned/movable outline below.
  const borderColor = (hasConflict || hasHardViolation)
    ? 'var(--color-danger)'
    : hasSoftViolation
      ? 'var(--color-warning)'
      : entry.pinned
        ? 'color-mix(in srgb, var(--color-danger) 45%, white)'
        : 'color-mix(in srgb, var(--color-primary) 45%, white)';
  const violationTooltip = violationInfo
    ? violationInfo.items.map((v) => `${v.isHard ? '⛔' : '⚠'} ${v.description}`).join('\n')
    : undefined;

  return (
    <div
      id={`schedule-entry-${entry.id}${idSuffix}`}
      role={onClick ? 'button' : undefined}
      tabIndex={onClick ? 0 : undefined}
      onClick={onClick || undefined}
      onKeyDown={onClick ? (e) => { if (e.key === 'Enter' || e.key === ' ') { e.preventDefault(); onClick(); } } : undefined}
      style={{
        position: 'relative',
        backgroundColor: entry.pinned ? 'var(--color-danger-bg)' : 'var(--color-info-bg)',
        border: `2px solid ${borderColor}`,
        borderRadius: '4px',
        padding: '8px',
        margin: '4px',
        fontSize: '12px',
        boxSizing: 'border-box',
        ...(onClick ? { cursor: 'pointer' } : {}),
        ...(fillHeight ? { height: 'calc(100% - 8px)' } : {}),
        ...(highlighted ? { boxShadow: '0 0 0 3px var(--color-primary)' } : {}),
      }}
    >
      {(hasHardViolation || hasSoftViolation) && (
        <span
          aria-label={t('schedule.violations.cardBadgeLabel', {
            hard: violationInfo.hardCount, soft: violationInfo.softCount,
          })}
          title={violationTooltip}
          style={{
            position: 'absolute', top: '-8px', right: '-8px',
            background: hasHardViolation ? 'var(--color-danger)' : 'var(--color-warning)',
            color: 'white', borderRadius: '10px', padding: '1px 6px',
            fontSize: '10px', fontWeight: 'bold', lineHeight: '14px',
          }}
        >
          {hasHardViolation ? '⛔' : '⚠'} {violationInfo.hardCount + violationInfo.softCount}
        </span>
      )}
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
