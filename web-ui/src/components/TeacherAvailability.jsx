import React, { useState, useEffect } from 'react';
import { useTranslation } from 'react-i18next';
import { getTeacherAvailabilityGrid } from '../api';
import { usePagination, Pagination, DEFAULT_PAGE_SIZE } from '../ui/Pagination';

const DAY_COLUMNS = [
  { key: 'mondayHours', labelKey: 'teacherAvailability.days.mon' },
  { key: 'tuesdayHours', labelKey: 'teacherAvailability.days.tue' },
  { key: 'wednesdayHours', labelKey: 'teacherAvailability.days.wed' },
  { key: 'thursdayHours', labelKey: 'teacherAvailability.days.thu' },
  { key: 'fridayHours', labelKey: 'teacherAvailability.days.fri' },
  { key: 'saturdayHours', labelKey: 'teacherAvailability.days.sat' },
  { key: 'sundayHours', labelKey: 'teacherAvailability.days.sun' },
];

/**
 * Collapses a comma-separated hour list ("7, 8, 9, 11, 12") into compact
 * ranges ("7-9, 11-12") - the view already pre-aggregates per teacher/day,
 * this just makes a full week readable at a glance instead of a long list
 * of individual hours.
 */
function compressHours(hoursCsv) {
  if (!hoursCsv || !hoursCsv.trim()) return null;
  const hours = hoursCsv.split(',').map((h) => parseInt(h.trim(), 10)).filter((h) => !Number.isNaN(h));
  if (hours.length === 0) return null;
  const ranges = [];
  let start = hours[0];
  let prev = hours[0];
  for (let i = 1; i < hours.length; i++) {
    const h = hours[i];
    if (h === prev + 1) {
      prev = h;
      continue;
    }
    ranges.push(start === prev ? `${start}` : `${start}-${prev + 1}`);
    start = h;
    prev = h;
  }
  ranges.push(start === prev ? `${start}` : `${start}-${prev + 1}`);
  return ranges.join(', ');
}

function TeacherAvailability() {
  const { t } = useTranslation();
  const [rows, setRows] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [searchQuery, setSearchQuery] = useState('');

  useEffect(() => {
    loadGrid();
  }, []);

  const loadGrid = async () => {
    try {
      setLoading(true);
      const response = await getTeacherAvailabilityGrid();
      setRows(response.data);
      setError(null);
    } catch (err) {
      setError(t('teacherAvailability.loadFailedPrefix') + err.message);
    } finally {
      setLoading(false);
    }
  };

  const filteredRows = rows.filter((r) => {
    const query = searchQuery.trim().toLowerCase();
    if (!query) return true;
    return r.teacherFullName?.toLowerCase().includes(query);
  });

  const { page, setPage, pageCount, pageItems, totalItems } = usePagination(filteredRows);

  if (loading) return <div className="loading">{t('teacherAvailability.loading')}</div>;

  return (
    <div>
      <div className="card">
        <h2>{t('teacherAvailability.title')}</h2>
        <p style={{ marginTop: '8px', color: 'var(--color-text-secondary)', fontSize: '13px' }}>
          {t('teacherAvailability.description')}
        </p>
        <div style={{ display: 'flex', gap: '10px', flexWrap: 'wrap', marginTop: '14px', alignItems: 'center' }}>
          <input
            type="text"
            placeholder={t('teacherAvailability.searchPlaceholder')}
            value={searchQuery}
            onChange={(e) => setSearchQuery(e.target.value)}
            style={{ padding: '8px', flex: '1', minWidth: '200px' }}
          />
          <button className="btn btn-secondary" onClick={loadGrid}>↻ {t('common.actions')}</button>
        </div>
      </div>

      {error && <div className="error" role="alert">{error}</div>}

      <div className="card table-wrap">
        <table>
          <thead>
            <tr>
              <th>{t('teacherAvailability.table.teacher')}</th>
              {DAY_COLUMNS.map((day) => (
                <th key={day.key}>{t(day.labelKey)}</th>
              ))}
            </tr>
          </thead>
          <tbody>
            {pageItems.map((r) => (
              <tr key={r.teacherId}>
                <td>{r.teacherFullName}</td>
                {DAY_COLUMNS.map((day) => {
                  const compressed = compressHours(r[day.key]);
                  return (
                    <td key={day.key} style={{ whiteSpace: 'nowrap', fontVariantNumeric: 'tabular-nums' }}>
                      {compressed || <span style={{ color: 'var(--color-text-secondary)' }}>—</span>}
                    </td>
                  );
                })}
              </tr>
            ))}
            {pageItems.length === 0 && (
              <tr>
                <td colSpan={DAY_COLUMNS.length + 1} style={{ textAlign: 'center', color: 'var(--color-text-secondary)' }}>
                  {t('teacherAvailability.none')}
                </td>
              </tr>
            )}
          </tbody>
        </table>
        <Pagination page={page} pageCount={pageCount} onPageChange={setPage} totalItems={totalItems} pageSize={DEFAULT_PAGE_SIZE} />
      </div>
    </div>
  );
}

export default TeacherAvailability;
