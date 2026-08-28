import React, { useState, useEffect, useMemo } from 'react';
import { useTranslation } from 'react-i18next';
import { getCourseCoverage } from '../api';
import { usePagination, Pagination, DEFAULT_PAGE_SIZE } from '../ui/Pagination';

const STATUS_COLORS = {
  Complete: 'var(--color-success)',
  Partial: 'var(--color-warning)',
  'Not Scheduled': 'var(--color-danger)',
};

function StatusBadge({ status }) {
  const color = STATUS_COLORS[status] || 'var(--color-secondary)';
  return (
    <span
      style={{
        display: 'inline-block',
        padding: '2px 10px',
        borderRadius: '12px',
        fontSize: '12px',
        fontWeight: 600,
        color: '#fff',
        background: color,
      }}
    >
      {status}
    </span>
  );
}

function CourseCoverage() {
  const { t } = useTranslation();
  const [rows, setRows] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [statusFilter, setStatusFilter] = useState('all');
  const [searchQuery, setSearchQuery] = useState('');

  useEffect(() => {
    loadCoverage();
  }, []);

  const loadCoverage = async () => {
    try {
      setLoading(true);
      const response = await getCourseCoverage();
      setRows(response.data);
      setError(null);
    } catch (err) {
      setError(t('courseCoverage.loadFailedPrefix') + err.message);
    } finally {
      setLoading(false);
    }
  };

  const counts = useMemo(() => {
    const result = { Complete: 0, Partial: 0, 'Not Scheduled': 0 };
    rows.forEach((r) => {
      result[r.schedulingStatus] = (result[r.schedulingStatus] || 0) + 1;
    });
    return result;
  }, [rows]);

  const filteredRows = rows.filter((r) => {
    if (statusFilter !== 'all' && r.schedulingStatus !== statusFilter) return false;
    const query = searchQuery.trim().toLowerCase();
    if (!query) return true;
    return (
      r.groupName?.toLowerCase().includes(query) ||
      r.groupId?.toLowerCase().includes(query) ||
      r.courseName?.toLowerCase().includes(query) ||
      r.teacherName?.toLowerCase().includes(query)
    );
  });

  const { page, setPage, pageCount, pageItems, totalItems } = usePagination(filteredRows);

  if (loading) return <div className="loading">{t('courseCoverage.loading')}</div>;

  return (
    <div>
      <div className="card">
        <h2>{t('courseCoverage.title')}</h2>
        <p style={{ marginTop: '8px', color: 'var(--color-text-secondary)', fontSize: '13px' }}>
          {t('courseCoverage.description')}
        </p>
        <div style={{ display: 'flex', gap: '10px', flexWrap: 'wrap', marginTop: '12px' }}>
          {['Complete', 'Partial', 'Not Scheduled'].map((status) => (
            <button
              key={status}
              onClick={() => setStatusFilter(statusFilter === status ? 'all' : status)}
              style={{
                border: statusFilter === status ? `2px solid ${STATUS_COLORS[status]}` : '1px solid var(--color-border)',
                borderRadius: '8px',
                padding: '8px 14px',
                background: 'var(--color-surface)',
                cursor: 'pointer',
                textAlign: 'left',
              }}
            >
              <div style={{ fontSize: '20px', fontWeight: 700, color: STATUS_COLORS[status] }}>
                {counts[status] || 0}
              </div>
              <div style={{ fontSize: '12px', color: 'var(--color-text-secondary)' }}>{status}</div>
            </button>
          ))}
        </div>
        <div style={{ display: 'flex', gap: '10px', flexWrap: 'wrap', marginTop: '14px', alignItems: 'center' }}>
          <input
            type="text"
            placeholder={t('courseCoverage.searchPlaceholder')}
            value={searchQuery}
            onChange={(e) => setSearchQuery(e.target.value)}
            style={{ padding: '8px', flex: '1', minWidth: '200px' }}
          />
          <select value={statusFilter} onChange={(e) => setStatusFilter(e.target.value)} style={{ padding: '8px' }}>
            <option value="all">{t('courseCoverage.filters.all')}</option>
            <option value="Complete">{t('courseCoverage.filters.complete')}</option>
            <option value="Partial">{t('courseCoverage.filters.partial')}</option>
            <option value="Not Scheduled">{t('courseCoverage.filters.notScheduled')}</option>
          </select>
          <button className="btn btn-secondary" onClick={loadCoverage}>↻ {t('common.actions')}</button>
        </div>
        <p style={{ marginTop: '10px', color: 'var(--color-text-secondary)' }}>
          {t('courseCoverage.showing', { filtered: filteredRows.length, total: rows.length })}
        </p>
      </div>

      {error && <div className="error" role="alert">{error}</div>}

      <div className="card">
        <table>
          <thead>
            <tr>
              <th>{t('courseCoverage.table.group')}</th>
              <th>{t('courseCoverage.table.course')}</th>
              <th>{t('courseCoverage.table.requiredHours')}</th>
              <th>{t('courseCoverage.table.scheduledHours')}</th>
              <th>{t('courseCoverage.table.status')}</th>
              <th>{t('courseCoverage.table.teacher')}</th>
              <th>{t('courseCoverage.table.rooms')}</th>
            </tr>
          </thead>
          <tbody>
            {pageItems.map((r) => (
              <tr key={`${r.groupId}::${r.courseId}::${r.teacherId ?? ''}`}>
                <td>{r.groupName || r.groupId}</td>
                <td>{r.courseName}{r.semester ? ` (S${r.semester})` : ''}</td>
                <td>{r.requiredHoursPerWeek}</td>
                <td>{r.scheduledHours}</td>
                <td><StatusBadge status={r.schedulingStatus} /></td>
                <td>{r.teacherName || <span style={{ color: 'var(--color-text-secondary)' }}>{t('courseCoverage.noTeacher')}</span>}</td>
                <td>{r.assignedRooms || '-'}</td>
              </tr>
            ))}
            {pageItems.length === 0 && (
              <tr>
                <td colSpan={7} style={{ textAlign: 'center', color: 'var(--color-text-secondary)' }}>
                  {t('courseCoverage.none')}
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

export default CourseCoverage;
