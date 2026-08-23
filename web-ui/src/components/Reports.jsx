import React, { useState, useEffect, useRef } from 'react';
import { useTranslation } from 'react-i18next';
import { listReports, getReportStatus, generateReports, downloadReport } from '../api';
import WriteOnly from '../auth/WriteOnly';

const STATUS_POLL_MS = 3000;

const formatTimestamp = (value) => (value ? value.replace('T', ' ').split('.')[0] : '-');

const formatSize = (bytes) => {
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
};

const openingKey = (runId, filename) => `${runId}::${filename}`;

function Reports() {
  const { t } = useTranslation();
  const [runs, setRuns] = useState([]);
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
        // A generation that just finished added a new run to the list.
        loadRunsList();
      }
    } catch (err) {
      // Non-critical: status just won't update until the next successful poll.
    }
  };

  const loadRunsList = async () => {
    try {
      const response = await listReports();
      setRuns(response.data);
    } catch (err) {
      // Leave the previous list showing; the manual Refresh button can retry.
    }
  };

  const loadAll = async () => {
    setLoading(true);
    try {
      const [runsRes, statusRes] = await Promise.all([listReports(), getReportStatus()]);
      setRuns(runsRes.data);
      setStatus(statusRes.data);
      setError(null);
      if (statusRes.data.state === 'RUNNING') startPolling();
    } catch (err) {
      setError(t('reports.loadFailedPrefix') + err.message);
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
      setGenError(err.response?.data?.message || t('reports.genFailedPrefix') + err.message);
    }
  };

  const handleView = async (runId, filename) => {
    const key = openingKey(runId, filename);
    setOpeningFile(key);
    setError(null);
    try {
      const response = await downloadReport(runId, filename);
      const blobUrl = URL.createObjectURL(new Blob([response.data], { type: 'application/pdf' }));
      window.open(blobUrl, '_blank');
      // Give the new tab a moment to load the blob before revoking it.
      setTimeout(() => URL.revokeObjectURL(blobUrl), 30000);
    } catch (err) {
      setError(t('reports.openFailedPrefix') + filename + ': ' + err.message);
    } finally {
      setOpeningFile(null);
    }
  };

  if (loading) return <div className="loading">{t('reports.loading')}</div>;

  return (
    <div>
      <div className="card">
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
          <h2>{t('reports.title')}</h2>
          <div style={{ display: 'flex', gap: '10px' }}>
            <button className="btn btn-secondary" onClick={loadAll}>↻ {t('reports.refresh')}</button>
            <WriteOnly>
              <button
                className="btn btn-success"
                onClick={handleGenerate}
                disabled={status?.state === 'RUNNING'}
              >
                {status?.state === 'RUNNING' ? t('reports.generating') : `📄 ${t('reports.generatePdfs')}`}
              </button>
            </WriteOnly>
          </div>
        </div>
        <p style={{ marginTop: '10px', color: 'var(--color-text-secondary)', fontSize: '14px' }}>
          {t('reports.description')}
        </p>
        {genError && <div className="error" role="alert">{genError}</div>}
        {status && (
          <div style={{ marginTop: '10px', fontSize: '13px', color: 'var(--color-text-secondary)' }}>
            {t('reports.lastGeneration')}<strong>{status.state}</strong>
            {status.finishedAt && t('reports.finishedSuffix', { timestamp: formatTimestamp(status.finishedAt) })}
            {status.state === 'RUNNING' && t('reports.runningSuffix')}
          </div>
        )}
      </div>

      {error && <div className="error" role="alert">{error}</div>}

      {runs.map((run) => (
        <div className="card" key={run.runId}>
          <h3>{formatTimestamp(run.generatedAt)}</h3>
          <table>
            <thead>
              <tr>
                <th>{t('reports.table.report')}</th>
                <th>{t('reports.table.size')}</th>
                <th>{t('common.actions')}</th>
              </tr>
            </thead>
            <tbody>
              {run.files.map((f) => {
                const key = openingKey(run.runId, f.filename);
                return (
                  <tr key={f.filename}>
                    <td>{f.filename}</td>
                    <td>{formatSize(f.sizeBytes)}</td>
                    <td>
                      <button
                        className="btn btn-primary"
                        onClick={() => handleView(run.runId, f.filename)}
                        disabled={openingFile === key}
                      >
                        {openingFile === key ? t('reports.opening') : t('reports.view')}
                      </button>
                    </td>
                  </tr>
                );
              })}
              {run.files.length === 0 && (
                <tr>
                  <td colSpan={3} style={{ color: 'var(--color-text-secondary)' }}>{t('reports.noFilesInRun')}</td>
                </tr>
              )}
            </tbody>
          </table>
        </div>
      ))}

      {runs.length === 0 && (
        <div className="card">
          <p style={{ color: 'var(--color-text-secondary)' }}>{t('reports.noReportsYet')}</p>
        </div>
      )}
    </div>
  );
}

export default Reports;
