import React, { useState, useEffect, useRef } from 'react';
import { useTranslation } from 'react-i18next';
import { runPreSolveValidation, getPreSolveValidationStatus } from '../api';

const POLL_MS = 3000;
const formatTimestamp = (value) => (value ? value.replace('T', ' ').split('.')[0] : '-');

/**
 * Runs PreSolveValidator by itself (WRITER or ADMIN - the route itself is
 * gated via WriteRoute in App.jsx, so anyone who reaches this page can
 * already run it), independent of actually solving. A Tools page rather
 * than a Settings tab: Settings is entirely ADMIN-gated (see App.jsx's
 * ADMIN_ITEMS/AdminRoute), which would have hidden this from WRITER users
 * even though the feature is explicitly for both roles.
 */
function PreSolveValidation() {
  const { t } = useTranslation();
  const [status, setStatus] = useState(null);
  const [error, setError] = useState(null);
  const pollRef = useRef(null);

  useEffect(() => {
    loadStatus();
    return () => stopPolling();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const stopPolling = () => {
    if (pollRef.current) {
      clearInterval(pollRef.current);
      pollRef.current = null;
    }
  };

  const startPolling = () => {
    stopPolling();
    pollRef.current = setInterval(loadStatus, POLL_MS);
  };

  const loadStatus = async () => {
    try {
      const response = await getPreSolveValidationStatus();
      setStatus(response.data);
      if (response.data.state === 'RUNNING') {
        if (!pollRef.current) startPolling();
      } else {
        stopPolling();
      }
    } catch (err) {
      // Non-critical: status just won't update until the next successful poll.
    }
  };

  const handleRun = async () => {
    setError(null);
    try {
      const response = await runPreSolveValidation();
      setStatus(response.data);
      startPolling();
    } catch (err) {
      setError(err.response?.data?.message || t('preSolveValidation.startFailedPrefix') + err.message);
    }
  };

  return (
    <div>
      <div className="card">
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', flexWrap: 'wrap', gap: '12px' }}>
          <h2>{t('preSolveValidation.title')}</h2>
          <button
            className="btn btn-success"
            onClick={handleRun}
            disabled={status?.state === 'RUNNING'}
          >
            {status?.state === 'RUNNING' ? t('preSolveValidation.running') : `▶ ${t('preSolveValidation.runValidation')}`}
          </button>
        </div>
        <p style={{ marginTop: '8px', color: 'var(--color-text-secondary)', fontSize: '13px' }}>
          {t('preSolveValidation.description')}
        </p>
        {error && <div className="error" role="alert">{error}</div>}
        {status && (
          <div style={{ marginTop: '10px' }}>
            <div style={{ display: 'flex', gap: '20px', fontSize: '13px', flexWrap: 'wrap' }}>
              <span><strong>{t('preSolveValidation.state')}</strong> {status.state}</span>
              <span><strong>{t('preSolveValidation.started')}</strong> {formatTimestamp(status.startedAt)}</span>
              <span><strong>{t('preSolveValidation.finished')}</strong> {formatTimestamp(status.finishedAt)}</span>
            </div>
            {status.state === 'COMPLETED' && (
              <p style={{
                marginTop: '8px', fontWeight: 600, fontSize: '13px',
                color: status.exitCode === 0 ? 'var(--color-success)' : 'var(--color-warning)',
              }}>
                {status.exitCode === 0 ? t('preSolveValidation.resultPassed') : t('preSolveValidation.resultProblems')}
              </p>
            )}
            {status.state === 'FAILED' && (
              <p style={{ marginTop: '8px', fontWeight: 600, fontSize: '13px', color: 'var(--color-danger)' }}>
                {t('preSolveValidation.resultFailed')}
              </p>
            )}
            {status.log && status.log.length > 0 && (
              <pre
                style={{
                  marginTop: '10px',
                  maxHeight: '420px',
                  overflowY: 'auto',
                  background: '#1e1e1e',
                  color: '#d4d4d4',
                  padding: '10px',
                  borderRadius: '4px',
                  fontSize: '12px',
                  whiteSpace: 'pre-wrap',
                }}
              >
                {status.log.join('\n')}
              </pre>
            )}
          </div>
        )}
      </div>
    </div>
  );
}

export default PreSolveValidation;
