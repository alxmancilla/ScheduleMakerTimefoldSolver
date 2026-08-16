import React, { useState, useEffect } from 'react';
import { useTranslation } from 'react-i18next';
import { getMyScheduleView } from '../api';

const DAY_KEYS = ['monday', 'tuesday', 'wednesday', 'thursday', 'friday'];
const HOURS = [7, 8, 9, 10, 11, 12, 13, 14];

/**
 * A TEACHER-role account's own weekly schedule - read-only, scoped
 * server-side to GET /api/schedule/view/me (this teacher's blocks only, not
 * the full schedule other roles can see). Mirrors Schedule.jsx's grid
 * rendering but without the group/teacher filters, since there's nothing to
 * filter: the data is already scoped to one teacher.
 */
function MySchedule() {
  const { t } = useTranslation();
  const DAYS = DAY_KEYS.map((key) => t(`common.daysFull.${key}`));
  const [schedule, setSchedule] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);

  useEffect(() => {
    loadSchedule();
  }, []);

  const loadSchedule = async () => {
    try {
      setLoading(true);
      const response = await getMyScheduleView();
      setSchedule(response.data);
      setError(null);
    } catch (err) {
      setError(t('mySchedule.loadFailedPrefix') + err.message);
    } finally {
      setLoading(false);
    }
  };

  const getEntriesStartingAt = (dayOfWeek, hour) => {
    if (!schedule) return [];
    return schedule.entries.filter((entry) => entry.dayOfWeek === dayOfWeek && entry.startHour === hour);
  };

  const getEntriesForDayAndHour = (dayOfWeek, hour) => {
    if (!schedule) return [];
    return schedule.entries.filter((entry) => {
      const entryEnd = entry.startHour + entry.lengthHours;
      return entry.dayOfWeek === dayOfWeek && hour >= entry.startHour && hour < entryEnd;
    });
  };

  if (loading) return <div className="loading">{t('mySchedule.loading')}</div>;
  if (error) return <div className="error">{error}</div>;

  const hasEntries = schedule && schedule.entries.length > 0;

  return (
    <div>
      <div className="card">
        <h2>{t('mySchedule.title')}</h2>
      </div>

      {!hasEntries && (
        <div className="card">
          <p style={{ color: '#7f8c8d' }}>{t('mySchedule.none')}</p>
        </div>
      )}

      {hasEntries && (
        <div className="card" style={{ overflowX: 'auto' }}>
          <table style={{ minWidth: '900px', borderCollapse: 'collapse' }}>
            <thead>
              <tr>
                <th style={{ width: '80px', border: '1px solid #ddd', padding: '8px' }}>{t('schedule.hour')}</th>
                {DAYS.map((day, idx) => (
                  <th key={idx} style={{ border: '1px solid #ddd', padding: '8px' }}>{day}</th>
                ))}
              </tr>
            </thead>
            <tbody>
              {HOURS.map((hour) => (
                <tr key={hour}>
                  <td style={{ fontWeight: 'bold', border: '1px solid #ddd', padding: '8px' }}>{hour}:00</td>
                  {DAYS.map((day, dayIdx) => {
                    const dayOfWeek = dayIdx + 1;
                    const entriesStartingHere = getEntriesStartingAt(dayOfWeek, hour);
                    const allEntries = getEntriesForDayAndHour(dayOfWeek, hour);
                    const isCoveredByPreviousBlock = allEntries.some((entry) => entry.startHour < hour);

                    if (isCoveredByPreviousBlock && entriesStartingHere.length === 0) {
                      return null;
                    }

                    return (
                      <td
                        key={dayIdx}
                        rowSpan={entriesStartingHere.length > 0 && entriesStartingHere[0].lengthHours > 1 ? entriesStartingHere[0].lengthHours : 1}
                        style={{ verticalAlign: 'top', padding: '0', border: '1px solid #ddd', height: '60px' }}
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
                              boxSizing: 'border-box',
                            }}
                          >
                            <div style={{ fontWeight: 'bold', marginBottom: '4px' }}>{entry.courseName}</div>
                            <div style={{ fontSize: '11px', color: '#555' }}>{entry.groupName}</div>
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
      )}
    </div>
  );
}

export default MySchedule;
