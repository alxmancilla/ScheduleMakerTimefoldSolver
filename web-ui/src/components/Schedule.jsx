import React, { useState, useEffect } from 'react';
import { useTranslation } from 'react-i18next';
import { getScheduleView, getGroups } from '../api';

const DAY_KEYS = ['monday', 'tuesday', 'wednesday', 'thursday', 'friday'];
const HOURS = [7, 8, 9, 10, 11, 12, 13, 14];

function Schedule() {
  const { t } = useTranslation();
  const DAYS = DAY_KEYS.map((key) => t(`common.daysFull.${key}`));
  const [schedule, setSchedule] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [viewMode, setViewMode] = useState('grid'); // 'grid' or 'list'
  const [groups, setGroups] = useState([]);
  const [selectedGroupId, setSelectedGroupId] = useState('');
  const [selectedTeacherId, setSelectedTeacherId] = useState('');

  useEffect(() => {
    loadGroups();
    loadSchedule();
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

  const loadSchedule = async () => {
    try {
      setLoading(true);
      const response = await getScheduleView();
      setSchedule(response.data);
      setError(null);
    } catch (err) {
      setError(t('schedule.loadFailedPrefix') + err.message);
    } finally {
      setLoading(false);
    }
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

  const getEntriesForDayAndHour = (dayOfWeek, hour) => {
    const filteredEntries = getFilteredEntries();
    return filteredEntries.filter(entry => {
      const entryDay = entry.dayOfWeek;
      const entryStart = entry.startHour;
      const entryEnd = entryStart + entry.lengthHours;
      return entryDay === dayOfWeek && hour >= entryStart && hour < entryEnd;
    });
  };

  // Check if this is the starting hour for a block (for rowspan rendering)
  const isBlockStart = (entry, hour) => {
    return entry.startHour === hour;
  };

  // Get entries that START at this specific day and hour (for merged cell rendering)
  const getEntriesStartingAt = (dayOfWeek, hour) => {
    const filteredEntries = getFilteredEntries();
    return filteredEntries.filter(entry => {
      return entry.dayOfWeek === dayOfWeek && entry.startHour === hour;
    });
  };

  const handleGroupChange = (e) => {
    setSelectedGroupId(e.target.value);
    setSelectedTeacherId(''); // Reset teacher filter when group changes
  };

  const handleTeacherChange = (e) => {
    setSelectedTeacherId(e.target.value);
  };

  if (loading) return <div className="loading">{t('schedule.loading')}</div>;
  if (error) return <div className="error">{error}</div>;
  if (!schedule) return <div className="loading">{t('schedule.noData')}</div>;

  const filteredEntries = getFilteredEntries();
  const teachersForGroup = getTeachersForGroup();

  return (
    <div>
      <div className="card">
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
          <h2>{t('schedule.title')}</h2>
          <div>
            <button
              className={`btn ${viewMode === 'grid' ? 'btn-primary' : 'btn-secondary'}`}
              onClick={() => setViewMode('grid')}
              style={{ marginRight: '10px' }}
            >
              {t('schedule.gridView')}
            </button>
            <button
              className={`btn ${viewMode === 'list' ? 'btn-primary' : 'btn-secondary'}`}
              onClick={() => setViewMode('list')}
            >
              {t('schedule.listView')}
            </button>
          </div>
        </div>

        {/* Filters */}
        <div style={{ marginTop: '20px', display: 'flex', gap: '15px', alignItems: 'center', flexWrap: 'wrap' }}>
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
        </div>

        <p style={{ marginTop: '15px', color: '#7f8c8d' }}>
          {t('schedule.showing', { filtered: filteredEntries.length, total: schedule.entries.length })}
          {selectedGroupId && t('schedule.groupSuffix', { name: groups.find(g => g.id === selectedGroupId)?.name || selectedGroupId })}
          {selectedTeacherId && t('schedule.teacherSuffix', { name: teachersForGroup.find(t2 => t2.id === selectedTeacherId)?.name || selectedTeacherId })}
        </p>
      </div>

      {viewMode === 'grid' ? (
        <div className="card" style={{ overflowX: 'auto' }}>
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
                  <td style={{ fontWeight: 'bold', border: '1px solid #ddd', padding: '8px' }}>{hour}:00</td>
                  {DAYS.map((day, dayIdx) => {
                    const dayOfWeek = dayIdx + 1;
                    const entriesStartingHere = getEntriesStartingAt(dayOfWeek, hour);

                    // Check if this cell is covered by a block starting in a previous hour
                    const allEntries = getEntriesForDayAndHour(dayOfWeek, hour);
                    const isCoveredByPreviousBlock = allEntries.some(entry => entry.startHour < hour);

                    // Skip rendering this cell if it's covered by a rowspan from above
                    if (isCoveredByPreviousBlock && entriesStartingHere.length === 0) {
                      return null;
                    }

                    return (
                      <td
                        key={dayIdx}
                        rowSpan={entriesStartingHere.length > 0 && entriesStartingHere[0].lengthHours > 1 ? entriesStartingHere[0].lengthHours : 1}
                        style={{
                          verticalAlign: 'top',
                          padding: '0',
                          border: '1px solid #ddd',
                          height: '60px'
                        }}
                      >
                        {entriesStartingHere.map((entry, idx) => (
                          <div
                            key={idx}
                            style={{
                              backgroundColor: entry.pinned ? '#ffe6e6' : '#e8f4f8',
                              border: '2px solid ' + (entry.pinned ? '#ffcccc' : '#b3d9e6'),
                              borderRadius: '4px',
                              padding: '8px',
                              margin: '4px',
                              fontSize: '12px',
                              height: 'calc(100% - 8px)',
                              display: 'flex',
                              flexDirection: 'column',
                              justifyContent: 'center',
                              boxSizing: 'border-box'
                            }}
                          >
                            <div style={{ fontWeight: 'bold', marginBottom: '4px' }}>{entry.courseName}</div>
                            <div style={{ fontSize: '11px', color: '#555' }}>{entry.groupName}</div>
                            <div style={{ fontSize: '11px', color: '#555' }}>{entry.teacherName}</div>
                            <div style={{ fontSize: '11px', color: '#555' }}>{entry.roomName}</div>
                            <div style={{ fontSize: '10px', color: '#888', marginTop: '4px' }}>
                              {entry.startHour}:00 - {entry.startHour + entry.lengthHours}:00 ({entry.lengthHours}h)
                            </div>
                            {entry.pinned && <div style={{ color: '#c00', fontSize: '10px', marginTop: '2px' }}>📌 {t('schedule.pinnedLabel')}</div>}
                          </div>
                        ))}
                      </td>
                    );
                  })}
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      ) : (
        <div className="card">
          <table>
            <thead>
              <tr>
                <th>{t('schedule.list.day')}</th>
                <th>{t('schedule.list.time')}</th>
                <th>{t('schedule.list.course')}</th>
                <th>{t('schedule.list.group')}</th>
                <th>{t('schedule.list.teacher')}</th>
                <th>{t('schedule.list.room')}</th>
                <th>{t('schedule.list.pinned')}</th>
              </tr>
            </thead>
            <tbody>
              {filteredEntries.length > 0 ? (
                filteredEntries.map((entry, idx) => (
                  <tr key={idx}>
                    <td>{DAYS[entry.dayOfWeek - 1]}</td>
                    <td>{entry.startHour}:00 - {entry.startHour + entry.lengthHours}:00 ({entry.lengthHours}h)</td>
                    <td>{entry.courseName}</td>
                    <td>{entry.groupName}</td>
                    <td>{entry.teacherName}</td>
                    <td>{entry.roomName}</td>
                    <td>{entry.pinned ? '📌' : ''}</td>
                  </tr>
                ))
              ) : (
                <tr>
                  <td colSpan="7" style={{ textAlign: 'center', padding: '20px', color: '#7f8c8d' }}>
                    {t('schedule.noAssignments')}
                  </td>
                </tr>
              )}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
}

export default Schedule;
