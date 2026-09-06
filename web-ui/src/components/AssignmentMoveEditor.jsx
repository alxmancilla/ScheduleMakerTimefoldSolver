import React, { useMemo, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { getAssignment, updateAssignment } from '../api';
import { useToast } from '../ui/ToastContext';
import { formatHour } from '../constants';

const DAY_KEY_BY_NUMBER = { 1: 'mon', 2: 'tue', 3: 'wed', 4: 'thu', 5: 'fri' };

/**
 * Move/pin editor opened by clicking a card in Schedule.jsx's grid (writers
 * only, live schedule only - see Schedule.jsx). Deliberately scoped to just
 * two actions rather than a full edit form (Assignments.jsx already owns
 * that): changing the block's timeslot, and toggling pinned. Room, teacher
 * and course stay untouched here.
 *
 * `PUT /api/assignments/{id}` is a full-replace endpoint (CourseBlockAssignmentDTO
 * has no partial-update support), so saving re-fetches the current assignment
 * via getAssignment() and sends every field back unchanged except the two
 * this editor actually offers - the same explicit-field-list approach
 * Assignments.jsx's own submit handler uses, rather than blindly spreading
 * the fetched entity (which also carries nested group/course/teacher/room
 * objects the DTO doesn't want).
 *
 * The conflict check below is advisory, not authoritative: `PUT` performs no
 * hard-constraint validation of its own (double-booking etc. are solver
 * constraints, checked at solve time / by PreSolveValidator, not by plain
 * CRUD), so this cross-references the schedule already loaded in memory for
 * the same three double-booking rules (teacher/group/room) and warns - it
 * never blocks the save - exactly like validateSharedTeacherLoad's own
 * warn-don't-block posture for a heuristic that can have false positives
 * (here, entries outside the currently loaded/filtered list aren't checked).
 */
function AssignmentMoveEditor({ entry, timeslots, allEntries, onClose, onSaved }) {
  const { t } = useTranslation();
  const showToast = useToast();

  const matchingTimeslots = useMemo(
    () => timeslots.filter((ts) => ts.lengthHours === entry.lengthHours),
    [timeslots, entry.lengthHours],
  );
  const availableDays = useMemo(
    () => [...new Set(matchingTimeslots.map((ts) => ts.dayOfWeek))].sort((a, b) => a - b),
    [matchingTimeslots],
  );

  const [dayOfWeek, setDayOfWeek] = useState(entry.dayOfWeek);
  const [startHour, setStartHour] = useState(entry.startHour);
  const [pinned, setPinned] = useState(entry.pinned);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState(null);

  const hoursForDay = useMemo(
    () => matchingTimeslots
      .filter((ts) => ts.dayOfWeek === dayOfWeek)
      .map((ts) => ts.startHour)
      .sort((a, b) => a - b),
    [matchingTimeslots, dayOfWeek],
  );

  const handleDayChange = (e) => {
    const nextDay = parseInt(e.target.value, 10);
    setDayOfWeek(nextDay);
    const hours = matchingTimeslots
      .filter((ts) => ts.dayOfWeek === nextDay)
      .map((ts) => ts.startHour)
      .sort((a, b) => a - b);
    if (!hours.includes(startHour)) setStartHour(hours[0]);
  };

  const targetTimeslot = matchingTimeslots.find(
    (ts) => ts.dayOfWeek === dayOfWeek && ts.startHour === startHour,
  );
  const hasMoved = dayOfWeek !== entry.dayOfWeek || startHour !== entry.startHour;
  const pinBlocked = !entry.roomName;

  const conflicts = useMemo(() => {
    if (!hasMoved) return [];
    const newStart = startHour;
    const newEnd = startHour + entry.lengthHours;
    return allEntries.filter((other) => {
      if (other.id === entry.id) return false;
      if (other.dayOfWeek !== dayOfWeek) return false;
      const otherStart = other.startHour;
      const otherEnd = other.startHour + other.lengthHours;
      const overlaps = newStart < otherEnd && otherStart < newEnd;
      if (!overlaps) return false;
      return (
        (entry.teacherId && other.teacherId === entry.teacherId)
        || other.groupId === entry.groupId
        || (entry.roomName && other.roomName === entry.roomName)
      );
    });
  }, [allEntries, hasMoved, dayOfWeek, startHour, entry]);

  const handleKeyDown = (e) => {
    if (e.key === 'Escape') onClose();
  };

  const handleSave = async () => {
    if (!targetTimeslot) return;
    setSaving(true);
    setError(null);
    try {
      const full = await getAssignment(entry.id);
      const a = full.data;
      const payload = {
        groupId: a.groupId,
        courseId: a.courseId,
        blockLength: a.blockLength,
        pinned,
        teacherId: a.teacherId,
        blockTimeslotId: targetTimeslot.id,
        roomName: a.roomName,
        satisfiesRoomType: a.satisfiesRoomType,
        preferredRoomHint: a.preferredRoomHint,
      };
      await updateAssignment(entry.id, payload);
      showToast(t('schedule.moveEditor.savedMessage'));
      onSaved();
    } catch (err) {
      setError(err.response?.data?.message || t('schedule.moveEditor.saveFailedPrefix') + err.message);
    } finally {
      setSaving(false);
    }
  };

  return (
    <div className="confirm-overlay" onKeyDown={handleKeyDown}>
      <div
        className="confirm-dialog"
        role="dialog"
        aria-modal="true"
        aria-labelledby="move-editor-title"
        style={{ maxWidth: '440px' }}
      >
        <h3 id="move-editor-title" style={{ marginBottom: '4px' }}>{entry.courseName}</h3>
        <p style={{ color: 'var(--color-text-secondary)', fontSize: '13px', marginBottom: '16px' }}>
          {entry.groupName}{entry.teacherName ? ` · ${entry.teacherName}` : ''}{entry.roomName ? ` · ${entry.roomName}` : ''}
        </p>

        <div className="form-group">
          <label htmlFor="move-editor-day">{t('schedule.moveEditor.day')}</label>
          <select id="move-editor-day" value={dayOfWeek} onChange={handleDayChange} autoFocus>
            {availableDays.map((day) => (
              <option key={day} value={day}>{t(`common.days.${DAY_KEY_BY_NUMBER[day]}`)}</option>
            ))}
          </select>
        </div>

        <div className="form-group">
          <label htmlFor="move-editor-hour">{t('schedule.moveEditor.startHour')}</label>
          <select id="move-editor-hour" value={startHour} onChange={(e) => setStartHour(parseInt(e.target.value, 10))}>
            {hoursForDay.map((h) => (
              <option key={h} value={h}>{formatHour(h)}</option>
            ))}
          </select>
        </div>

        {conflicts.length > 0 && (
          <div className="error" role="alert" style={{ marginBottom: '12px' }}>
            {t('schedule.moveEditor.conflictWarning', { count: conflicts.length })}
            <ul style={{ margin: '6px 0 0 18px' }}>
              {conflicts.map((c) => (
                <li key={c.id}>{c.courseName} · {c.groupName}{c.teacherName ? ` · ${c.teacherName}` : ''}</li>
              ))}
            </ul>
          </div>
        )}

        <div className="form-group">
          <label htmlFor="move-editor-pinned" style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
            <input
              id="move-editor-pinned"
              type="checkbox"
              checked={pinned}
              disabled={pinBlocked && !pinned}
              onChange={(e) => setPinned(e.target.checked)}
            />
            {t('schedule.moveEditor.pinned')}
          </label>
          {pinBlocked && !pinned && (
            <p style={{ color: 'var(--color-text-secondary)', fontSize: '12px', marginTop: '4px' }}>
              {t('schedule.moveEditor.pinBlockedNoRoom')}
            </p>
          )}
        </div>

        {error && <div className="error" role="alert">{error}</div>}

        <div className="confirm-actions">
          <button type="button" className="btn btn-secondary" onClick={onClose} disabled={saving}>
            {t('common.cancel')}
          </button>
          <button type="button" className="btn btn-primary" onClick={handleSave} disabled={saving || !targetTimeslot}>
            {saving ? t('schedule.moveEditor.saving') : t('common.save')}
          </button>
        </div>
      </div>
    </div>
  );
}

export default AssignmentMoveEditor;
