import React, { useState, useEffect, useRef } from 'react';
import { listReports, getReportStatus, generateReports, downloadReport } from '../api';
import WriteOnly from '../auth/WriteOnly';

const STATUS_POLL_MS = 3000;

const formatTimestamp = (value) => (value ? value.replace('T', ' ').split('.')[0] : '-');

const formatSize = (bytes) => {
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
};

function Reports() {
  const [reports, setReports] = useState([]);
  const [status, setStatus] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [openingFile, setOpeningFile] = useState(null);
  const [genError, setGenError] = useState(null);
  const pollRef = useRef(null);

  useEffect(() => {
    loadAll();
    return () => stopPolling();
  }, []);

  const stopPolling = () => {
    if (pollRef.current) {
      clearInterval(pollRef.current);
      pollRef.current = null;
    }
  };

  const startPolling = () => {
    stopPolling();
    pollRef.current = setInterval(loadStatus, STATUS_POLL_MS);
  };

  const loadStatus = async () => {
    try {
      const response = await getReportStatus();
      setStatus(response.data);
      if (response.data.state === 'RUNNING') {
        if (!pollRef.current) startPolling();
      } else {
        stopPolling();
        // A generation that just finished means the file list changed.
        loadReportsList();
      }
    } catch (err) {
      // Non-critical: status just won't update until the next successful poll.
    }
  };

  const loadReportsList = async () => {
    try {
      const response = await listReports();
      setReports(response.data);
    } catch (err) {
      // Leave the previous list showing; the manual Refresh button can retry.
    }
  };

  const loadAll = async () => {
    setLoading(true);
    try {
      const [reportsRes, statusRes] = await Promise.all([listReports(), getReportStatus()]);
      setReports(reportsRes.data);
      setStatus(statusRes.data);
      setError(null);
      if (statusRes.data.state === 'RUNNING') startPolling();
    } catch (err) {
      setError('Failed to load reports: ' + err.message);
    } finally {
      setLoading(false);
    }
  };

  const handleGenerate = async () => {
    setGenError(null);
    try {
      const response = await generateReports();
      setStatus(response.data);
      startPolling();
    } catch (err) {
      setGenError(err.response?.data?.message || 'Failed to start report generation: ' + err.message);
    }
  };

  const handleView = async (filename) => {
    setOpeningFile(filename);
    setError(null);
    try {
      const response = await downloadReport(filename);
      const blobUrl = URL.createObjectURL(new Blob([response.data], { type: 'application/pdf' }));
      window.open(blobUrl, '_blank');
      // Give the new tab a moment to load the blob before revoking it.
      setTimeout(() => URL.revokeObjectURL(blobUrl), 30000);
    } catch (err) {
      setError('Failed to open ' + filename + ': ' + err.message);
    } finally {
      setOpeningFile(null);
    }
  };

  if (loading) return <div className="loading">Loading reports...</div>;

  return (
    <div>
      <div className="card">
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
          <h2>Reports</h2>
          <div style={{ display: 'flex', gap: '10px' }}>
            <button className="btn btn-secondary" onClick={loadAll}>↻ Refresh</button>
            <WriteOnly>
              <button
                className="btn btn-success"
                onClick={handleGenerate}
                disabled={status?.state === 'RUNNING'}
              >
                {status?.state === 'RUNNING' ? 'Generating…' : '📄 Generate PDFs'}
              </button>
            </WriteOnly>
          </div>
        </div>
        <p style={{ marginTop: '10px', color: '#7f8c8d', fontSize: '14px' }}>
          PDF reports (violations, by-teacher, by-group) generated from the currently solved
          schedule.
        </p>
        {genError && <div className="error">{genError}</div>}
        {status && (
          <div style={{ marginTop: '10px', fontSize: '13px', color: '#7f8c8d' }}>
            Last generation: <strong>{status.state}</strong>
            {status.finishedAt && <> — finished {formatTimestamp(status.finishedAt)}</>}
            {status.state === 'RUNNING' && <> — a new set is being generated now.</>}
          </div>
        )}
      </div>

      {error && <div className="error">{error}</div>}

      <div className="card">
        <table>
          <thead>
            <tr>
              <th>Report</th>
              <th>Size</th>
              <th>Generated</th>
              <th>Actions</th>
            </tr>
          </thead>
          <tbody>
            {reports.map((r) => (
              <tr key={r.filename}>
                <td>{r.filename}</td>
                <td>{formatSize(r.sizeBytes)}</td>
                <td>{formatTimestamp(r.lastModified)}</td>
                <td>
                  <button
                    className="btn btn-primary"
                    onClick={() => handleView(r.filename)}
                    disabled={openingFile === r.filename}
                  >
                    {openingFile === r.filename ? 'Opening…' : 'View'}
                  </button>
                </td>
              </tr>
            ))}
            {reports.length === 0 && (
              <tr>
                <td colSpan={4} style={{ color: '#7f8c8d' }}>No reports generated yet</td>
              </tr>
            )}
          </tbody>
        </table>
      </div>
    </div>
  );
}

export default Reports;
