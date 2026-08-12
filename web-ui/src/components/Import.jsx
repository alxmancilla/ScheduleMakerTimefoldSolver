import React, { useState, useRef } from 'react';
import { importExcel } from '../api';

function Import() {
  const [importFile, setImportFile] = useState(null);
  const [importResult, setImportResult] = useState(null);
  const [importError, setImportError] = useState(null);
  const [importing, setImporting] = useState(false);
  const fileInputRef = useRef(null);

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
        setImportError(data?.message || 'Failed to import file: ' + err.message);
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
        <h2>Import Excel</h2>
        <p style={{ marginTop: '8px', color: '#7f8c8d', fontSize: '13px' }}>
          Upserts base problem data (Teachers, Courses, Rooms, Groups, Group_Courses sheets) by
          ID/name — existing rows are updated, new ones created. Does not touch timeslots or the
          solved schedule; run the solver afterward to assign the imported courses/groups.
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
            {importing ? 'Importing…' : '⇪ Import Excel'}
          </button>
        </div>
        {importError && <div className="error">{importError}</div>}
        {importResult && importResult.success && (
          <div style={{ marginTop: '10px', fontSize: '13px', color: '#2e7d32' }}>
            Imported: {importResult.teachersImported} teachers, {importResult.coursesImported} courses,{' '}
            {importResult.roomsImported} rooms, {importResult.groupsImported} groups,{' '}
            {importResult.groupCoursesImported} group-course links.
          </div>
        )}
        {importResult && !importResult.success && (
          <div style={{ marginTop: '10px' }}>
            <div className="error">Import rejected — nothing was written. Fix these and re-upload:</div>
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
