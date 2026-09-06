import React, { useState, useEffect } from 'react';
import { useTranslation } from 'react-i18next';
import { getScheduleView, getScheduleRuns, getGroups, listTimeslots } from '../api';
import { formatHour, buildDayWindows } from '../constants';
import { useAuth } from '../auth/AuthContext';
import { useConfirm } from '../ui/ConfirmContext';
import ScheduleEntryCard from './ScheduleEntryCard';
import AssignmentMoveEditor from './AssignmentMoveEditor';

const DAY_KEYS = ['monday', 'tuesday', 'wednesday', 'thursday', 'friday'];
const HOURS = [7, 8, 9, 10, 11, 12, 13, 14];
const formatRunTimestamp = (value) => (value ? value.replace('T', ' ').split('.')[0] : '-');

function Schedule() {
  const { t } = useTranslation();
  const { canWrite } = useAuth();
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
  // (plain component state, nothing persisted) - a writer must deliberately
  // turn it on, and confirm doing so, before any card becomes clickable.
  // This is on top of, not instead of, the existing role/run-selection gate
  // below: a reader (or a writer just browsing) never even sees an editable
  // grid by accident.
  const [editModeEnabled, setEditModeEnabled] = useState(false);
  // Moving/pinning a block edits the live course_block_assignment row, which
  // has no notion of "which run you were viewing" - so it's only offered
  // for writers looking at the live schedule (selectedRunId === ''), never a
  // past run's read-only snapshot.
  const canEditGrid = canWrite() && !selectedRunId && editModeEnabled;

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

          {canWrite() && (
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

      <div className="card table-wrap">
        <table style={{ minWidth: '1000px', borderCollapse: 'collapse' }}>
          <thead>
            <tr>
              <th style={{ width: '80px', border: '1px solid #ddd', padding: '8px' }}>{t('schedule.hour')}</th>
              {DAYS.map((day, idx) => (
                <th key={idx} style={{ border: '1px solid #ddd', padding: '8px' }}>{day}</th>
              ))}
            </tr>
          </thead>
          <tbody>
            {HOURS.map(hour => (
              <tr key={hour}>
                <td style={{ fontWeight: 'bold', border: '1px solid #ddd', padding: '8px' }}>{formatHour(hour)}-{formatHour(hour + 1)}</td>
                {DAYS.map((day, dayIdx) => {
                  const windows = dayWindows[dayIdx];
                  const cellWindow = windows.find(w => w.startHour === hour);

                  // Skip rendering this cell if an earlier row's window (merged
                  // block, or several overlapping ones) already spans into it.
                  if (!cellWindow) {
                    const isCoveredByEarlierWindow = windows.some(w => w.startHour < hour && hour < w.endHour);
                    return isCoveredByEarlierWindow ? null : (
                      <td key={dayIdx} style={{ border: '1px solid #ddd', height: '60px' }} />
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
                        border: '1px solid #ddd',
                        height: '60px',
                        ...(hasConflict ? { backgroundColor: '#fdecea' } : {}),
                      }}
                    >
                      {hasConflict && (
                        <div style={{
                          background: '#e74c3c', color: 'white', fontSize: '10px', fontWeight: 'bold',
                          padding: '3px 6px', textAlign: 'center',
                        }}>
                          ⚠ {t('schedule.conflictLabel')}
                        </div>
                      )}
                      {cellWindow.entries.map((entry, idx) => (
                        <ScheduleEntryCard
                          key={idx}
                          entry={entry}
                          hasConflict={hasConflict}
                          fillHeight={!hasConflict}
                          onClick={canEditGrid ? () => setEditingEntry(entry) : null}
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
