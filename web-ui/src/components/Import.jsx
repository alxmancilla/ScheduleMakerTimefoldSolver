import React, { useState, useRef } from 'react';
import { useTranslation } from 'react-i18next';
import { importExcel, exportExcel } from '../api';

// entities: null exports every sheet; otherwise the single-entity filter passed to
// GET /api/import/excel?entities=... ('groups' bundles Group_Courses server-side).
const EXPORT_OPTIONS = [
  { key: 'all', entities: null, labelKey: 'importExcel.exportAll' },
  { key: 'teachers', entities: ['teachers'], labelKey: 'importExcel.exportTeachers' },
  { key: 'courses', entities: ['courses'], labelKey: 'importExcel.exportCourses' },
  { key: 'rooms', entities: ['rooms'], labelKey: 'importExcel.exportRooms' },
  { key: 'groups', entities: ['groups'], labelKey: 'importExcel.exportGroups' },
];

function Import() {
  const { t } = useTranslation();
  const [importFile, setImportFile] = useState(null);
  const [importResult, setImportResult] = useState(null);
  const [importError, setImportError] = useState(null);
  const [importing, setImporting] = useState(false);
  const fileInputRef = useRef(null);

  const [exportingKey, setExportingKey] = useState(null);
  const [exportError, setExportError] = useState(null);

  const handleExportExcel = async (option) => {
    setExportingKey(option.key);
    setExportError(null);
    try {
      const response = await exportExcel(option.entities);
      const blobUrl = URL.createObjectURL(new Blob([response.data], {
        type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
      }));
      const disposition = response.headers['content-disposition'];
      const match = disposition && disposition.match(/filename="(.+)"/);
      const link = document.createElement('a');
      link.href = blobUrl;
      link.download = match ? match[1] : 'schedule-export.xlsx';
      document.body.appendChild(link);
      link.click();
      document.body.removeChild(link);
      setTimeout(() => URL.revokeObjectURL(blobUrl), 30000);
    } catch (err) {
      setExportError(err.response?.data?.message || t('importExcel.exportFailedPrefix') + err.message);
    } finally {
      setExportingKey(null);
    }
  };

  const handleImportExcel = async () => {
    if (!importFile) return;
    setImporting(true);
    setImportError(null);
    setImportResult(null);
    try {
      const response = await importExcel(importFile);
      setImportResult(response.data);
    } catch (err) {
      const data = err.response?.data;
      if (data && typeof data.success === 'boolean') {
        setImportResult(data);
      } else {
        setImportError(data?.message || t('importExcel.failedPrefix') + err.message);
      }
    } finally {
      setImporting(false);
      setImportFile(null);
      if (fileInputRef.current) fileInputRef.current.value = '';
    }
  };

  return (
    <div>
      <div className="card">
        <h2>{t('importExcel.exportTitle')}</h2>
        <p style={{ marginTop: '8px', color: 'var(--color-text-secondary)', fontSize: '13px' }}>
          {t('importExcel.exportDescription')}
        </p>
        <div style={{ display: 'flex', gap: '8px', flexWrap: 'wrap', marginTop: '10px' }}>
          {EXPORT_OPTIONS.map((option) => (
            <button
              key={option.key}
              className="btn btn-secondary"
              onClick={() => handleExportExcel(option)}
              disabled={exportingKey !== null}
            >
              {exportingKey === option.key ? t('importExcel.exporting') : `⇩ ${t(option.labelKey)}`}
            </button>
          ))}
        </div>
        {exportError && <div className="error" role="alert" style={{ marginTop: '10px' }}>{exportError}</div>}
      </div>

      <div className="card">
        <h2>{t('importExcel.title')}</h2>
        <p style={{ marginTop: '8px', color: 'var(--color-text-secondary)', fontSize: '13px' }}>
          {t('importExcel.description')}
        </p>
        <div style={{ display: 'flex', gap: '10px', alignItems: 'center', marginTop: '10px' }}>
          <input
            ref={fileInputRef}
            type="file"
            accept=".xlsx"
            onChange={(e) => setImportFile(e.target.files[0] || null)}
          />
          <button
            className="btn btn-success"
            onClick={handleImportExcel}
            disabled={!importFile || importing}
          >
            {importing ? t('importExcel.importing') : `⇪ ${t('importExcel.importButton')}`}
          </button>
        </div>
        {importError && <div className="error" role="alert">{importError}</div>}
        {importResult && importResult.success && (
          <div style={{ marginTop: '10px', fontSize: '13px', color: '#2e7d32' }}>
            {t('importExcel.importedSummary', {
              teachers: importResult.teachersImported,
              courses: importResult.coursesImported,
              rooms: importResult.roomsImported,
              groups: importResult.groupsImported,
              groupCourses: importResult.groupCoursesImported,
            })}
          </div>
        )}
        {importResult && !importResult.success && (
          <div style={{ marginTop: '10px' }}>
            <div className="error" role="alert">{t('importExcel.rejected')}</div>
            <ul style={{ marginTop: '6px', fontSize: '13px', color: 'var(--color-danger-dark)' }}>
              {importResult.errors.map((e, i) => (
                <li key={i}>{e}</li>
              ))}
            </ul>
          </div>
        )}
      </div>
    </div>
  );
}

export default Import;
