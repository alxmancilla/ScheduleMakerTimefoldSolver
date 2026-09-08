import React, { useEffect, useMemo, useState } from 'react';
import { useTranslation } from 'react-i18next';
import { moveAssignment, validateAssignmentMove, getAssignment, updateAssignment, getRooms, getTeachers } from '../api';
import { useAuth } from '../auth/AuthContext';
import { useToast } from '../ui/ToastContext';
import { formatHour, formatPinProvenance, roomMatchesType, teacherQualifiedFor } from '../constants';

const DAY_KEY_BY_NUMBER = { 1: 'mon', 2: 'tue', 3: 'wed', 4: 'thu', 5: 'fri' };
const VALIDATE_DEBOUNCE_MS = 300;

/**
 * Move/pin editor opened by clicking a card in Schedule.jsx's grid
 * (SCHEDULER/ADMIN only, live schedule only - see Schedule.jsx's
 * canEditSchedule()): day/hour, pinned, and - also SCHEDULER/ADMIN, gated
 * separately below since it goes through a different, broader-access
 * endpoint - room/teacher reassignment. A full edit (course, block length,
 * etc.) still belongs to Assignments.jsx; this stays scoped to what a
 * quick in-grid fix realistically needs.
 *
 * Two different save paths, chosen by what actually changed:
 *  - day/hour/pinned only: `PUT /api/assignments/{id}/move`
 *    (`moveAssignment` in api.js) - a narrow endpoint that touches only
 *    blockTimeslotId/pinned, SCHEDULER/ADMIN-accessible (matching this
 *    popover's own gate), and re-validates server-side before saving
 *    instead of trusting the client's last debounced check.
 *  - room and/or teacher also changed (the fields below only render for
 *    canEditRoomTeacher, i.e. isAdmin() || isScheduler()): the general
 *    `PUT /api/assignments/{id}` (`updateAssignment`), which needs the full
 *    CourseBlockAssignmentDTO - fetched fresh via getAssignment() right
 *    before saving, same explicit-field-list approach Assignments.jsx's own
 *    submit handler uses. Deliberately NOT covered by the live validate-move
 *    check below (see the room/teacher section's own note) - same lack of
 *    live pre-validation Assignments.jsx's full edit form already has today
 *    for these two fields, not a new gap.
 *
 * The live check itself: every day/hour/pinned change is re-checked,
 * debounced, against `POST /api/assignments/{id}/validate-move`, which
 * re-derives the same facts server-side against the full, current database
 * state - see AssignmentMoveValidationService. Its `violations` are
 * constraints currently configured HARD - Save is disabled while any are
 * present, the same posture as PreSolveValidator's blocking problems. Its
 * `warnings` are the same checks' SOFT-configured counterpart (an admin has
 * switched that constraint to SOFT via Settings > Constraint Weights, or
 * the target semester's semester_hour_limit is SOFT-severity) - shown, but
 * never blocking, since the solver itself wouldn't reject them either.
 */
function AssignmentMoveEditor({ entry, timeslots, onClose, onSaved }) {
  const { t } = useTranslation();
  const { isAdmin, isScheduler } = useAuth();
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
  const [roomName, setRoomName] = useState(entry.roomName || '');
  const [teacherId, setTeacherId] = useState(entry.teacherId || '');
  const [rooms, setRooms] = useState([]);
  const [teachers, setTeachers] = useState([]);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState(null);
  const [validating, setValidating] = useState(false);
  const [violations, setViolations] = useState([]);
  const [warnings, setWarnings] = useState([]);

  // Room/teacher reassignment goes through the general PUT (SCHEDULER or
  // ADMIN - see SecurityConfig), so both roles get these fields, not just
  // ADMIN.
  const canEditRoomTeacher = isAdmin() || isScheduler();

  // Rooms/teachers are READER-accessible endpoints, so loading them
  // unconditionally (not gated on the role check) keeps this simple - the
  // fields themselves still only render for canEditRoomTeacher.
  useEffect(() => {
    if (!canEditRoomTeacher) return;
    getRooms().then((res) => setRooms(res.data)).catch(() => setRooms([]));
    getTeachers().then((res) => setTeachers(res.data)).catch(() => setTeachers([]));
  }, [canEditRoomTeacher]);

  const roomsForType = useMemo(
    () => rooms.filter((r) => roomMatchesType(r, entry.satisfiesRoomType)),
    [rooms, entry.satisfiesRoomType],
  );
  const teachersForCourse = useMemo(
    () => teachers.filter((tc) => teacherQualifiedFor(tc, entry.courseName)),
    [teachers, entry.courseName],
  );
  const roomOrTeacherChanged = roomName !== (entry.roomName || '') || teacherId !== (entry.teacherId || '');

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
  const pinBlocked = !roomName;

  // Re-validate (debounced) whenever the candidate day/hour/pinned changes.
  // Deliberately does NOT re-run when room/teacher change - the endpoint
  // validates against the assignment's CURRENT room/teacher, so a preview
  // that mixed in a hypothetical new room/teacher would be misleading
  // rather than merely incomplete. Skipped entirely while no target
  // timeslot resolves (shouldn't happen - the selects only offer
  // matching-length timeslots - but guards against a stale/empty list).
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
      if (roomOrTeacherChanged) {
        const full = await getAssignment(entry.id);
        const a = full.data;
        await updateAssignment(entry.id, {
          groupId: a.groupId,
          courseId: a.courseId,
          blockLength: a.blockLength,
          pinned,
          teacherId: teacherId || null,
          blockTimeslotId: targetTimeslot.id,
          roomName: roomName || null,
          satisfiesRoomType: a.satisfiesRoomType,
          preferredRoomHint: a.preferredRoomHint,
        });
      } else {
        await moveAssignment(entry.id, { blockTimeslotId: targetTimeslot.id, pinned });
      }
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
          {entry.pinned && (
            <p style={{ color: 'var(--color-text-secondary)', fontSize: '12px', marginTop: '4px' }}>
              {formatPinProvenance(entry, t)}
            </p>
          )}
          {pinBlocked && !pinned && (
            <p style={{ color: 'var(--color-text-secondary)', fontSize: '12px', marginTop: '4px' }}>
              {t('schedule.moveEditor.pinBlockedNoRoom')}
            </p>
          )}
        </div>

        {canEditRoomTeacher && (
          <>
            <div className="form-group">
              <label htmlFor="move-editor-room">{t('schedule.moveEditor.room')}</label>
              <select id="move-editor-room" value={roomName} onChange={(e) => setRoomName(e.target.value)}>
                <option value="">{t('common.noneOption')}</option>
                {roomsForType.map((r) => (
                  <option key={r.name} value={r.name}>{r.name} ({r.type})</option>
                ))}
              </select>
            </div>

            <div className="form-group">
              <label htmlFor="move-editor-teacher">{t('schedule.moveEditor.teacher')}</label>
              <select id="move-editor-teacher" value={teacherId} onChange={(e) => setTeacherId(e.target.value)}>
                <option value="">{t('common.noneOption')}</option>
                {teachersForCourse.map((tc) => (
                  <option key={tc.id} value={tc.id}>{tc.id} - {tc.name} {tc.lastName}</option>
                ))}
              </select>
            </div>

            {roomOrTeacherChanged && (
              <p style={{ color: 'var(--color-text-secondary)', fontSize: '12px', marginBottom: '12px' }}>
                {t('schedule.moveEditor.roomTeacherNotLiveValidated')}
              </p>
            )}
          </>
        )}

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
