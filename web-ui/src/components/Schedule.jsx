import React, { useState, useEffect } from 'react';
import { useTranslation } from 'react-i18next';
import { getScheduleView, getScheduleRuns, getGroups, listTimeslots, getScheduleViolations } from '../api';
import { formatHour, buildDayWindows, groupByConstraint, buildViolationsByAssignment } from '../constants';
import { useAuth } from '../auth/AuthContext';
import { useConfirm } from '../ui/ConfirmContext';
import ScheduleEntryCard from './ScheduleEntryCard';
import AssignmentMoveEditor from './AssignmentMoveEditor';

const DAY_KEYS = ['monday', 'tuesday', 'wednesday', 'thursday', 'friday'];
const HOURS = [7, 8, 9, 10, 11, 12, 13, 14];
const formatRunTimestamp = (value) => (value ? value.replace('T', ' ').split('.')[0] : '-');

function Schedule() {
  const { t } = useTranslation();
  const { canEditSchedule } = useAuth();
  const confirmAction = useConfirm();
  const DAYS = DAY_KEYS.map((key) => t(`common.daysFull.${key}`));
  const [schedule, setSchedule] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [groups, setGroups] = useState([]);
  const [selectedGroupId, setSelectedGroupId] = useState('');
  const [selectedTeacherId, setSelectedTeacherId] = useState('');
  const [runs, setRuns] = useState([]);
  // '' means "latest" (no runId sent - the current schedule).
  const [selectedRunId, setSelectedRunId] = useState('');
  const [timeslots, setTimeslots] = useState([]);
  const [editingEntry, setEditingEntry] = useState(null);
  // Grid editing is opt-in and resets to OFF on every visit to this page
  // (plain component state, nothing persisted) - a scheduler/admin must
  // deliberately turn it on, and confirm doing so, before any card becomes
  // clickable. This is on top of, not instead of, the existing role/
  // run-selection gate below: a reader or writer (who can no longer edit the
  // schedule - see canEditSchedule()) never even sees an editable grid.
  const [editModeEnabled, setEditModeEnabled] = useState(false);
  // Moving/pinning a block edits the live course_block_assignment row, which
  // has no notion of "which run you were viewing" - so it's only offered to
  // SCHEDULER/ADMIN (canEditSchedule(), matching SecurityConfig's own
  // /api/assignments/** write carve-out - WRITER no longer qualifies) looking
  // at the live schedule (selectedRunId === ''), never a past run's
  // read-only snapshot.
  const canEditGrid = canEditSchedule() && !selectedRunId && editModeEnabled;

  const [violations, setViolations] = useState({ hard: [], soft: [] });
  const [violationsError, setViolationsError] = useState(null);
  const [violationsExpanded, setViolationsExpanded] = useState(false);
  // Set while a violations-panel description is clicked, so its own card(s)
  // get an extra highlight ring on top of their usual violation badge - see
  // highlightAssignments below. Cleared on the next click / run change.
  const [highlightedAssignmentIds, setHighlightedAssignmentIds] = useState(new Set());

  // Persisted at solve time from BlockScheduleAnalyzer's own detailed
  // analysis (see ScheduleRunViolationEntity) - re-fetched whenever the
  // selected run changes, including on mount. These reflect the last SOLVE
  // for this run, not any manual move/pin made since - a grid edit doesn't
  // create a new schedule_run, so it can't retroactively update what a past
  // solve's own violations were.
  useEffect(() => {
    loadViolations(selectedRunId);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [selectedRunId]);

  const loadViolations = async (runId) => {
    try {
      const response = await getScheduleViolations(runId || undefined);
      setViolations({ hard: response.data.hard || [], soft: response.data.soft || [] });
      setViolationsError(null);
    } catch (err) {
      setViolationsError(t('schedule.violations.loadFailedPrefix') + err.message);
    }
  };

  // Clicking a violation description in the panel jumps to and rings the
  // grid card(s) it's about (assignmentIds - see ScheduleViolationsDTO.Entry
  // / buildViolationsByAssignment). A card outside the current group/teacher
  // filter simply won't be found by getElementById - the highlight set still
  // updates so it applies as soon as the filters bring the card into view.
  // Every entry actually renders twice - once in the desktop table, once in
  // the CSS-only-hidden mobile list (see the two ScheduleEntryCard call
  // sites' idSuffix) - so this picks whichever copy the current breakpoint
  // is actually showing rather than always scrolling to the (possibly
  // invisible) desktop one.
  const highlightAssignments = (assignmentIds) => {
    if (!assignmentIds || assignmentIds.length === 0) return;
    setHighlightedAssignmentIds(new Set(assignmentIds));
    const candidates = [
      document.getElementById(`schedule-entry-${assignmentIds[0]}`),
      document.getElementById(`schedule-entry-${assignmentIds[0]}-mobile`),
    ].filter(Boolean);
    const target = candidates.find((el) => el.getClientRects().length > 0) || candidates[0];
    if (target) target.scrollIntoView({ behavior: 'smooth', block: 'center' });
  };

  const handleToggleEditMode = async (e) => {
    const wantsEnabled = e.target.checked;
    if (wantsEnabled) {
      if (!(await confirmAction(t('schedule.editMode.confirmEnable')))) return;
    } else {
      setEditingEntry(null);
    }
    setEditModeEnabled(wantsEnabled);
  };

  useEffect(() => {
    loadGroups();
    loadRuns();
    loadSchedule('');
    loadTimeslots();
  }, []);

  useEffect(() => {
    // Auto-select first group when groups are loaded
    if (groups.length > 0 && !selectedGroupId) {
      setSelectedGroupId(groups[0].id);
    }
  }, [groups]);

  const loadGroups = async () => {
    try {
      const response = await getGroups();
      setGroups(response.data);
    } catch (err) {
      console.error('Failed to load groups:', err);
    }
  };

  const loadRuns = async () => {
    try {
      const response = await getScheduleRuns();
      setRuns(response.data);
    } catch (err) {
      console.error('Failed to load run history:', err);
    }
  };

  const loadTimeslots = async () => {
    try {
      const response = await listTimeslots();
      setTimeslots(response.data);
    } catch (err) {
      console.error('Failed to load timeslots:', err);
    }
  };

  const loadSchedule = async (runId) => {
    try {
      setLoading(true);
      const response = await getScheduleView(runId || undefined);
      setSchedule(response.data);
      setError(null);
    } catch (err) {
      setError(t('schedule.loadFailedPrefix') + err.message);
    } finally {
      setLoading(false);
    }
  };

  const handleRunChange = (e) => {
    const runId = e.target.value;
    setSelectedRunId(runId);
    loadSchedule(runId);
  };

  // Get unique teachers for the selected group
  const getTeachersForGroup = () => {
    if (!schedule || !selectedGroupId) return [];
    const teacherMap = new Map();
    schedule.entries
      .filter(entry => entry.groupId === selectedGroupId && entry.teacherId)
      .forEach(entry => {
        if (!teacherMap.has(entry.teacherId)) {
          teacherMap.set(entry.teacherId, entry.teacherName);
        }
      });
    return Array.from(teacherMap.entries()).map(([id, name]) => ({ id, name }));
  };

  // Filter entries based on selected group and teacher
  const getFilteredEntries = () => {
    if (!schedule) return [];
    let filtered = schedule.entries;

    if (selectedGroupId) {
      filtered = filtered.filter(entry => entry.groupId === selectedGroupId);
    }

    if (selectedTeacherId) {
      filtered = filtered.filter(entry => entry.teacherId === selectedTeacherId);
    }

    return filtered;
  };

  const handleGroupChange = (e) => {
    setSelectedGroupId(e.target.value);
    setSelectedTeacherId(''); // Reset teacher filter when group changes
  };

  const handleTeacherChange = (e) => {
    setSelectedTeacherId(e.target.value);
  };

  if (loading) return <div className="loading">{t('schedule.loading')}</div>;
  if (error) return <div className="error" role="alert">{error}</div>;
  if (!schedule) return <div className="loading">{t('schedule.noData')}</div>;

  const filteredEntries = getFilteredEntries();
  const teachersForGroup = getTeachersForGroup();
  // One merged-window list per day, computed once for the whole grid rather
  // than per cell - see buildDayWindows in constants.js for why overlapping
  // entries (a real double-booking) must be merged into one shared window
  // instead of each claiming their own table cell.
  const dayWindows = DAY_KEYS.map((_, idx) =>
    buildDayWindows(filteredEntries.filter((entry) => entry.dayOfWeek === idx + 1)));
  // Re-indexed by assignment id so each rendered card can look up its own
  // violations in O(1) - see buildViolationsByAssignment in constants.js.
  const violationsByAssignment = buildViolationsByAssignment(violations.hard, violations.soft);

  return (
    <div>
      <div className="card">
        <h2>{t('schedule.title')}</h2>

        {/* Filters */}
        <div style={{ marginTop: '20px', display: 'flex', gap: '15px', alignItems: 'center', flexWrap: 'wrap' }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
            <label htmlFor="runFilter" style={{ fontWeight: 'bold' }}>{t('schedule.run')}</label>
            <select
              id="runFilter"
              value={selectedRunId}
              onChange={handleRunChange}
              style={{ padding: '8px', minWidth: '260px' }}
            >
              <option value="">{t('schedule.latestRun')}</option>
              {runs.map((run) => (
                <option key={run.id} value={run.id}>
                  {t('schedule.runOption', {
                    id: run.id,
                    timestamp: formatRunTimestamp(run.createdAt),
                    hard: run.hardScore,
                    soft: run.softScore,
                  })}
                </option>
              ))}
            </select>
          </div>

          <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
            <label htmlFor="groupFilter" style={{ fontWeight: 'bold' }}>{t('schedule.group')}</label>
            <select
              id="groupFilter"
              value={selectedGroupId}
              onChange={handleGroupChange}
              style={{ padding: '8px', minWidth: '150px' }}
            >
              {groups.map(group => (
                <option key={group.id} value={group.id}>{group.name}</option>
              ))}
            </select>
          </div>

          <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
            <label htmlFor="teacherFilter" style={{ fontWeight: 'bold' }}>{t('schedule.teacher')}</label>
            <select
              id="teacherFilter"
              value={selectedTeacherId}
              onChange={handleTeacherChange}
              style={{ padding: '8px', minWidth: '200px' }}
              disabled={!selectedGroupId}
            >
              <option value="">{t('schedule.allTeachers')}</option>
              {teachersForGroup.map(teacher => (
                <option key={teacher.id} value={teacher.id}>{teacher.name}</option>
              ))}
            </select>
          </div>

          {selectedTeacherId && (
            <button
              className="btn btn-secondary"
              onClick={() => setSelectedTeacherId('')}
              style={{ padding: '8px 16px' }}
            >
              {t('schedule.clearTeacherFilter')}
            </button>
          )}

          {canEditSchedule() && (
            <label
              htmlFor="editModeToggle"
              style={{ display: 'flex', alignItems: 'center', gap: '8px', marginLeft: 'auto', cursor: 'pointer' }}
              title={selectedRunId ? t('schedule.editMode.unavailableForPastRun') : undefined}
            >
              <input
                id="editModeToggle"
                type="checkbox"
                checked={editModeEnabled}
                disabled={!!selectedRunId}
                onChange={handleToggleEditMode}
              />
              {t('schedule.editMode.toggleLabel')}
            </label>
          )}
        </div>

        <p style={{ marginTop: '15px', color: 'var(--color-text-secondary)' }}>
          {t('schedule.showing', { filtered: filteredEntries.length, total: schedule.entries.length })}
          {selectedGroupId && t('schedule.groupSuffix', { name: groups.find(g => g.id === selectedGroupId)?.name || selectedGroupId })}
          {selectedTeacherId && t('schedule.teacherSuffix', { name: teachersForGroup.find(t2 => t2.id === selectedTeacherId)?.name || selectedTeacherId })}
        </p>
        {selectedRunId && (
          <p style={{ marginTop: '6px', color: 'var(--color-danger-dark)', fontSize: '13px' }}>
            {t('schedule.pastRunNotice')}
          </p>
        )}
        {canEditGrid && (
          <p style={{ marginTop: '6px', color: 'var(--color-danger-dark)', fontSize: '13px', fontWeight: 'bold' }}>
            ✎ {t('schedule.editMode.activeNotice')}
          </p>
        )}
      </div>

      <div className="card">
        <button
          type="button"
          className="btn btn-secondary"
          onClick={() => setViolationsExpanded((v) => !v)}
        >
          {violationsExpanded ? '▾' : '▸'} {t('schedule.violations.summary', {
            hard: violations.hard.length, soft: violations.soft.length,
          })}
        </button>

        {violationsError && <div className="error" role="alert" style={{ marginTop: '10px' }}>{violationsError}</div>}

        {violationsExpanded && (
          <div style={{ marginTop: '12px' }}>
            {violations.hard.length === 0 && violations.soft.length === 0 && !violationsError && (
              <p style={{ color: 'var(--color-text-secondary)' }}>{t('schedule.violations.none')}</p>
            )}
            {violations.hard.length > 0 && (
              <div className="error" role="alert" style={{ marginBottom: '12px' }}>
                <strong>{t('schedule.violations.hardHeading')}</strong>
                {groupByConstraint(violations.hard).map((group) => (
                  <div key={group.name} style={{ marginTop: '8px' }}>
                    <div style={{ fontWeight: 'bold' }}>{group.name} ({group.descriptions.length})</div>
                    <ul style={{ margin: '4px 0 0 18px' }}>
                      {group.items.map((item, i) => (
                        <li
                          key={i}
                          onClick={item.assignmentIds.length > 0 ? () => highlightAssignments(item.assignmentIds) : undefined}
                          style={item.assignmentIds.length > 0
                            ? { cursor: 'pointer', textDecoration: 'underline dotted' } : undefined}
                          title={item.assignmentIds.length > 0 ? t('schedule.violations.clickToHighlight') : undefined}
                        >
                          {item.description}
                        </li>
                      ))}
                    </ul>
                  </div>
                ))}
              </div>
            )}
            {violations.soft.length > 0 && (
              <div
                role="status"
                style={{
                  padding: '8px 12px', borderRadius: '4px',
                  background: 'color-mix(in srgb, var(--color-warning) 12%, transparent)',
                  border: '1px solid var(--color-warning)', color: 'var(--color-ink)',
                }}
              >
                <strong>{t('schedule.violations.softHeading')}</strong>
                {groupByConstraint(violations.soft).map((group) => (
                  <div key={group.name} style={{ marginTop: '8px' }}>
                    <div style={{ fontWeight: 'bold' }}>{group.name} ({group.descriptions.length})</div>
                    <ul style={{ margin: '4px 0 0 18px' }}>
                      {group.items.map((item, i) => (
                        <li
                          key={i}
                          onClick={item.assignmentIds.length > 0 ? () => highlightAssignments(item.assignmentIds) : undefined}
                          style={item.assignmentIds.length > 0
                            ? { cursor: 'pointer', textDecoration: 'underline dotted' } : undefined}
                          title={item.assignmentIds.length > 0 ? t('schedule.violations.clickToHighlight') : undefined}
                        >
                          {item.description}
                        </li>
                      ))}
                    </ul>
                  </div>
                ))}
              </div>
            )}
          </div>
        )}
      </div>

      <div className="card table-wrap desktop-schedule-table">
        <table style={{ minWidth: '1000px', borderCollapse: 'collapse' }}>
          <thead>
            <tr>
              <th style={{ width: '80px', border: '1px solid var(--color-border)', padding: '8px' }}>{t('schedule.hour')}</th>
              {DAYS.map((day, idx) => (
                <th key={idx} style={{ border: '1px solid var(--color-border)', padding: '8px' }}>{day}</th>
              ))}
            </tr>
          </thead>
          <tbody>
            {HOURS.map(hour => (
              <tr key={hour}>
                <td style={{ fontWeight: 'bold', border: '1px solid var(--color-border)', padding: '8px' }}>{formatHour(hour)}-{formatHour(hour + 1)}</td>
                {DAYS.map((day, dayIdx) => {
                  const windows = dayWindows[dayIdx];
                  const cellWindow = windows.find(w => w.startHour === hour);

                  // Skip rendering this cell if an earlier row's window (merged
                  // block, or several overlapping ones) already spans into it.
                  if (!cellWindow) {
                    const isCoveredByEarlierWindow = windows.some(w => w.startHour < hour && hour < w.endHour);
                    return isCoveredByEarlierWindow ? null : (
                      <td key={dayIdx} style={{ border: '1px solid var(--color-border)', height: '60px' }} />
                    );
                  }

                  const hasConflict = cellWindow.entries.length > 1;

                  return (
                    <td
                      key={dayIdx}
                      rowSpan={cellWindow.endHour - cellWindow.startHour}
                      style={{
                        verticalAlign: 'top',
                        padding: '0',
                        border: '1px solid var(--color-border)',
                        height: '60px',
                        ...(hasConflict ? { backgroundColor: 'color-mix(in srgb, var(--color-danger) 8%, transparent)' } : {}),
                      }}
                    >
                      {hasConflict && (
                        <div className="schedule-conflict-banner">⚠ {t('schedule.conflictLabel')}</div>
                      )}
                      {cellWindow.entries.map((entry, idx) => (
                        <ScheduleEntryCard
                          key={idx}
                          entry={entry}
                          hasConflict={hasConflict}
                          fillHeight={!hasConflict}
                          onClick={canEditGrid ? () => setEditingEntry(entry) : null}
                          violationInfo={violationsByAssignment.get(entry.id)}
                          highlighted={highlightedAssignmentIds.has(entry.id)}
                        />
                      ))}
                    </td>
                  );
                })}
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      {/*
        * Same dayWindows the table above uses, stacked as day-by-day cards
        * for narrow viewports - see index.css's phone breakpoint. Filters
        * (group/teacher/run) already apply upstream via filteredEntries, so
        * this reflects the exact same scoped view as the table.
        */}
      <div className="mobile-schedule-list">
        {DAY_KEYS.map((_, dayIdx) => {
          const windows = dayWindows[dayIdx];
          if (windows.length === 0) return null;
          return (
            <div className="card mobile-schedule-day" key={dayIdx}>
              <h3>{DAYS[dayIdx]}</h3>
              {windows.map((window, wIdx) => {
                const hasConflict = window.entries.length > 1;
                return (
                  <div className="mobile-schedule-window" key={wIdx}>
                    {hasConflict && (
                      <div className="schedule-conflict-banner">⚠ {t('schedule.conflictLabel')}</div>
                    )}
                    {window.entries.map((entry, eIdx) => (
                      <ScheduleEntryCard
                        key={eIdx}
                        entry={entry}
                        hasConflict={hasConflict}
                        onClick={canEditGrid ? () => setEditingEntry(entry) : null}
                        violationInfo={violationsByAssignment.get(entry.id)}
                        highlighted={highlightedAssignmentIds.has(entry.id)}
                        idSuffix="-mobile"
                      />
                    ))}
                  </div>
                );
              })}
            </div>
          );
        })}
      </div>

      {editingEntry && (
        <AssignmentMoveEditor
          entry={editingEntry}
          timeslots={timeslots}
          onClose={() => setEditingEntry(null)}
          onSaved={() => {
            setEditingEntry(null);
            loadSchedule(selectedRunId);
          }}
        />
      )}
    </div>
  );
}

export default Schedule;
