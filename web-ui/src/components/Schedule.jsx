import React, { useState, useEffect } from 'react';
import { getScheduleView, getGroups } from '../api';

const DAYS = ['Monday', 'Tuesday', 'Wednesday', 'Thursday', 'Friday'];
const HOURS = [7, 8, 9, 10, 11, 12, 13, 14];

function Schedule() {
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
      setError('Failed to load schedule: ' + err.message);
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

    // Debug logging
    if (selectedGroupId || selectedTeacherId) {
      console.log('Filtering with:', { selectedGroupId, selectedTeacherId });
      console.log('Sample entry:', filtered[0]);
      console.log('Total entries before filter:', filtered.length);
    }

    if (selectedGroupId) {
      filtered = filtered.filter(entry => {
        const matches = entry.groupId === selectedGroupId;
        if (!matches && filtered.indexOf(entry) < 3) {
          console.log('Entry groupId:', entry.groupId, 'vs selected:', selectedGroupId, 'matches:', matches);
        }
        return matches;
      });
      console.log('After group filter:', filtered.length);
    }

    if (selectedTeacherId) {
      filtered = filtered.filter(entry => {
        const matches = entry.teacherId === selectedTeacherId;
        return matches;
      });
      console.log('After teacher filter:', filtered.length);
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

  const handleGroupChange = (e) => {
    setSelectedGroupId(e.target.value);
    setSelectedTeacherId(''); // Reset teacher filter when group changes
  };

  const handleTeacherChange = (e) => {
    setSelectedTeacherId(e.target.value);
  };

  if (loading) return <div className="loading">Loading schedule...</div>;
  if (error) return <div className="error">{error}</div>;
  if (!schedule) return <div className="loading">No schedule data</div>;

  const filteredEntries = getFilteredEntries();
  const teachersForGroup = getTeachersForGroup();

  return (
    <div>
      <div className="card">
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
          <h2>Schedule View</h2>
          <div>
            <button
              className={`btn ${viewMode === 'grid' ? 'btn-primary' : 'btn-secondary'}`}
              onClick={() => setViewMode('grid')}
              style={{ marginRight: '10px' }}
            >
              Grid View
            </button>
            <button
              className={`btn ${viewMode === 'list' ? 'btn-primary' : 'btn-secondary'}`}
              onClick={() => setViewMode('list')}
            >
              List View
            </button>
          </div>
        </div>

        {/* Filters */}
        <div style={{ marginTop: '20px', display: 'flex', gap: '15px', alignItems: 'center', flexWrap: 'wrap' }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
            <label htmlFor="groupFilter" style={{ fontWeight: 'bold' }}>Group:</label>
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
            <label htmlFor="teacherFilter" style={{ fontWeight: 'bold' }}>Teacher:</label>
            <select
              id="teacherFilter"
              value={selectedTeacherId}
              onChange={handleTeacherChange}
              style={{ padding: '8px', minWidth: '200px' }}
              disabled={!selectedGroupId}
            >
              <option value="">All Teachers</option>
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
              Clear Teacher Filter
            </button>
          )}
        </div>

        <p style={{ marginTop: '15px', color: '#7f8c8d' }}>
          Showing {filteredEntries.length} of {schedule.entries.length} assignments
          {selectedGroupId && ` | Group: ${groups.find(g => g.id === selectedGroupId)?.name || selectedGroupId}`}
          {selectedTeacherId && ` | Teacher: ${teachersForGroup.find(t => t.id === selectedTeacherId)?.name || selectedTeacherId}`}
        </p>
      </div>

      {viewMode === 'grid' ? (
        <div className="card" style={{ overflowX: 'auto' }}>
          <table style={{ minWidth: '1000px' }}>
            <thead>
              <tr>
                <th style={{ width: '80px' }}>Hour</th>
                {DAYS.map((day, idx) => (
                  <th key={idx}>{day}</th>
                ))}
              </tr>
            </thead>
            <tbody>
              {HOURS.map(hour => (
                <tr key={hour}>
                  <td style={{ fontWeight: 'bold' }}>{hour}:00</td>
                  {DAYS.map((day, dayIdx) => {
                    const entries = getEntriesForDayAndHour(dayIdx + 1, hour);
                    return (
                      <td key={dayIdx} style={{ verticalAlign: 'top', padding: '8px' }}>
                        {entries.map((entry, idx) => (
                          <div 
                            key={idx}
                            style={{
                              backgroundColor: entry.pinned ? '#ffe6e6' : '#e8f4f8',
                              border: '1px solid ' + (entry.pinned ? '#ffcccc' : '#b3d9e6'),
                              borderRadius: '4px',
                              padding: '6px',
                              marginBottom: '4px',
                              fontSize: '12px'
                            }}
                          >
                            <div style={{ fontWeight: 'bold' }}>{entry.courseName}</div>
                            <div>{entry.groupName}</div>
                            <div>{entry.teacherName}</div>
                            <div>{entry.roomName}</div>
                            {entry.pinned && <div style={{ color: '#c00', fontSize: '10px' }}>📌 PINNED</div>}
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
                <th>Day</th>
                <th>Time</th>
                <th>Course</th>
                <th>Group</th>
                <th>Teacher</th>
                <th>Room</th>
                <th>Pinned</th>
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
                    No assignments found for the selected filters
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

