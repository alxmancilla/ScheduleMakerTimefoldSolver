import React, { useState, useEffect } from 'react';
import { useTranslation } from 'react-i18next';
import { getMyScheduleView } from '../api';
import { formatHour, buildDayWindows } from '../constants';
import ScheduleEntryCard from './ScheduleEntryCard';

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

  if (loading) return <div className="loading">{t('mySchedule.loading')}</div>;
  if (error) return <div className="error" role="alert">{error}</div>;

  const hasEntries = schedule && schedule.entries.length > 0;
  // One merged-window list per day - see buildDayWindows in constants.js for
  // why overlapping entries (a real double-booking) must be merged into one
  // shared window instead of each claiming their own table cell.
  const dayWindows = hasEntries
    ? DAY_KEYS.map((_, idx) => buildDayWindows(schedule.entries.filter((entry) => entry.dayOfWeek === idx + 1)))
    : [];

  return (
    <div>
      <div className="card">
        <h2>{t('mySchedule.title')}</h2>
      </div>

      {!hasEntries && (
        <div className="card">
          <p style={{ color: 'var(--color-text-secondary)' }}>{t('mySchedule.none')}</p>
        </div>
      )}

      {hasEntries && (
        <div className="card table-wrap">
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
                  <td style={{ fontWeight: 'bold', border: '1px solid #ddd', padding: '8px' }}>{formatHour(hour)}-{formatHour(hour + 1)}</td>
                  {DAYS.map((day, dayIdx) => {
                    const windows = dayWindows[dayIdx];
                    const cellWindow = windows.find((w) => w.startHour === hour);

                    if (!cellWindow) {
                      const isCoveredByEarlierWindow = windows.some((w) => w.startHour < hour && hour < w.endHour);
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
                          <ScheduleEntryCard key={idx} entry={entry} hasConflict={hasConflict} showTeacher={false} fillHeight={!hasConflict} />
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
