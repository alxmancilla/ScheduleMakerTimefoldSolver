import React, { useEffect, useMemo, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { getAssignment, updateAssignment, validateAssignmentMove } from '../api';
import { useToast } from '../ui/ToastContext';
import { formatHour } from '../constants';

const DAY_KEY_BY_NUMBER = { 1: 'mon', 2: 'tue', 3: 'wed', 4: 'thu', 5: 'fri' };
const VALIDATE_DEBOUNCE_MS = 300;

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
 * `PUT` itself performs no hard-constraint validation (double-booking etc.
 * are solver constraints, checked at solve time or by PreSolveValidator, not
 * by plain CRUD), so every day/hour/pinned change is re-checked here against
 * `POST /api/assignments/{id}/validate-move` (debounced), which re-derives
 * the same facts server-side against the full, current database state -
 * see AssignmentMoveValidationService. Its `violations` are constraints
 * currently configured HARD - Save is disabled while any are present, the
 * same posture as PreSolveValidator's blocking problems. Its `warnings` are
 * the same checks' SOFT-configured counterpart (an admin has switched that
 * constraint to SOFT via Settings > Constraint Weights, or the target
 * semester's semester_hour_limit is SOFT-severity) - shown, but never
 * blocking, since the solver itself wouldn't reject them either.
 */
function AssignmentMoveEditor({ entry, timeslots, onClose, onSaved }) {
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
  const [validating, setValidating] = useState(false);
  const [violations, setViolations] = useState([]);
  const [warnings, setWarnings] = useState([]);

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
  const pinBlocked = !entry.roomName;

  // Re-validate (debounced) whenever the candidate move/pin changes. Skipped
  // entirely while no target timeslot resolves (shouldn't happen - the
  // selects only offer matching-length timeslots - but guards against a
  // stale/empty list).
  useEffect(() => {
    if (!targetTimeslot) {
      setViolations([]);
      setWarnings([]);
      return undefined;
    }
    setValidating(true);
    const timer = setTimeout(async () => {
      try {
        const response = await validateAssignmentMove(entry.id, {
          blockTimeslotId: targetTimeslot.id,
          pinned,
        });
        setViolations(response.data.violations || []);
        setWarnings(response.data.warnings || []);
      } catch (err) {
        // Fail closed: an unreachable check shouldn't silently let Save through.
        setViolations([t('schedule.moveEditor.validationFailedPrefix') + err.message]);
        setWarnings([]);
      } finally {
        setValidating(false);
      }
    }, VALIDATE_DEBOUNCE_MS);
    return () => clearTimeout(timer);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [entry.id, targetTimeslot?.id, pinned]);

  const handleKeyDown = (e) => {
    if (e.key === 'Escape') onClose();
  };

  const handleSave = async () => {
    if (!targetTimeslot || violations.length > 0) return;
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

        {validating && (
          <p style={{ color: 'var(--color-text-secondary)', fontSize: '12px', marginBottom: '8px' }}>
            {t('schedule.moveEditor.validating')}
          </p>
        )}

        {violations.length > 0 && (
          <div className="error" role="alert" style={{ marginBottom: '12px' }}>
            {t('schedule.moveEditor.violationsHeading')}
            <ul style={{ margin: '6px 0 0 18px' }}>
              {violations.map((v, i) => <li key={i}>{v}</li>)}
            </ul>
          </div>
        )}

        {warnings.length > 0 && (
          <div
            role="status"
            style={{
              marginBottom: '12px', padding: '8px 12px', borderRadius: '4px',
              background: 'color-mix(in srgb, var(--color-warning) 12%, transparent)',
              border: '1px solid var(--color-warning)', color: 'var(--color-ink)',
              fontSize: '13px',
            }}
          >
            {t('schedule.moveEditor.warningsHeading')}
            <ul style={{ margin: '6px 0 0 18px' }}>
              {warnings.map((w, i) => <li key={i}>{w}</li>)}
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
          <button
            type="button"
            className="btn btn-primary"
            onClick={handleSave}
            disabled={saving || validating || !targetTimeslot || violations.length > 0}
          >
            {saving ? t('schedule.moveEditor.saving') : t('common.save')}
          </button>
        </div>
      </div>
    </div>
  );
}

export default AssignmentMoveEditor;
