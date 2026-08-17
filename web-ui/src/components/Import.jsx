import React, { useState, useRef } from 'react';
import { useTranslation } from 'react-i18next';
import { importExcel, exportExcel } from '../api';

function Import() {
  const { t } = useTranslation();
  const [importFile, setImportFile] = useState(null);
  const [importResult, setImportResult] = useState(null);
  const [importError, setImportError] = useState(null);
  const [importing, setImporting] = useState(false);
  const fileInputRef = useRef(null);

  const [exporting, setExporting] = useState(false);
  const [exportError, setExportError] = useState(null);

  const handleExportExcel = async () => {
    setExporting(true);
    setExportError(null);
    try {
      const response = await exportExcel();
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
      setExporting(false);
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
        <p style={{ marginTop: '8px', color: '#7f8c8d', fontSize: '13px' }}>
          {t('importExcel.exportDescription')}
        </p>
        <button
          className="btn btn-secondary"
          onClick={handleExportExcel}
          disabled={exporting}
          style={{ marginTop: '10px' }}
        >
          {exporting ? t('importExcel.exporting') : `⇩ ${t('importExcel.exportButton')}`}
        </button>
        {exportError && <div className="error" style={{ marginTop: '10px' }}>{exportError}</div>}
      </div>

      <div className="card">
        <h2>{t('importExcel.title')}</h2>
        <p style={{ marginTop: '8px', color: '#7f8c8d', fontSize: '13px' }}>
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
        {importError && <div className="error">{importError}</div>}
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
            <div className="error">{t('importExcel.rejected')}</div>
            <ul style={{ marginTop: '6px', fontSize: '13px', color: '#c0392b' }}>
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
