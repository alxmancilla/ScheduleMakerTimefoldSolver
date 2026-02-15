import React, { useState, useEffect } from 'react';
import { getScheduleView } from '../api';

const DAYS = ['Monday', 'Tuesday', 'Wednesday', 'Thursday', 'Friday'];
const HOURS = [7, 8, 9, 10, 11, 12, 13, 14];

function Schedule() {
  const [schedule, setSchedule] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [viewMode, setViewMode] = useState('grid'); // 'grid' or 'list'

  useEffect(() => {
    loadSchedule();
  }, []);

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

  const getEntriesForDayAndHour = (dayOfWeek, hour) => {
    if (!schedule) return [];
    return schedule.entries.filter(entry => {
      const entryDay = entry.dayOfWeek;
      const entryStart = entry.startHour;
      const entryEnd = entryStart + entry.lengthHours;
      return entryDay === dayOfWeek && hour >= entryStart && hour < entryEnd;
    });
  };

  if (loading) return <div className="loading">Loading schedule...</div>;
  if (error) return <div className="error">{error}</div>;
  if (!schedule) return <div className="loading">No schedule data</div>;

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
        <p style={{ marginTop: '10px', color: '#7f8c8d' }}>
          Total: {schedule.totalAssignments} | Assigned: {schedule.assignedCount} | Unassigned: {schedule.unassignedCount}
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
              {schedule.entries.map((entry, idx) => (
                <tr key={idx}>
                  <td>{DAYS[entry.dayOfWeek - 1]}</td>
                  <td>{entry.startHour}:00 - {entry.startHour + entry.lengthHours}:00 ({entry.lengthHours}h)</td>
                  <td>{entry.courseName}</td>
                  <td>{entry.groupName}</td>
                  <td>{entry.teacherName}</td>
                  <td>{entry.roomName}</td>
                  <td>{entry.pinned ? '📌' : ''}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
}

export default Schedule;

