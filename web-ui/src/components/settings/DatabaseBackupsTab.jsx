import React, { useState, useEffect, useRef } from 'react';
import { useTranslation } from 'react-i18next';
import {
  exportDatabase, importDatabase, getDatabaseBackupStatus, listDatabaseBackups, downloadDatabaseBackup,
} from '../../api';
import { useConfirm } from '../../ui/ConfirmContext';
import { ENGINE_POLL_MS, formatTimestamp } from './constants';

const formatBytes = (bytes) => {
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
};

function DatabaseBackupsTab({ hidden }) {
  const { t } = useTranslation();
  const confirmAction = useConfirm();

  const [dbBackups, setDbBackups] = useState([]);
  const [dbBackupsError, setDbBackupsError] = useState(null);
  const [dbStatus, setDbStatus] = useState(null);
  const [dbError, setDbError] = useState(null);
  const [downloadingBackup, setDownloadingBackup] = useState(null);
  const dbPollRef = useRef(null);

  useEffect(() => {
    loadDatabaseBackups();
    loadDatabaseStatus();
    return () => stopDbPolling();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const loadDatabaseBackups = async () => {
    try {
      const response = await listDatabaseBackups();
      setDbBackups(response.data);
      setDbBackupsError(null);
    } catch (err) {
      setDbBackupsError(t('settings.databaseBackups.loadFailedPrefix') + err.message);
    }
  };

  const stopDbPolling = () => {
    if (dbPollRef.current) {
      clearInterval(dbPollRef.current);
      dbPollRef.current = null;
    }
  };

  const startDbPolling = () => {
    stopDbPolling();
    dbPollRef.current = setInterval(loadDatabaseStatus, ENGINE_POLL_MS);
  };

  const loadDatabaseStatus = async () => {
    try {
      const response = await getDatabaseBackupStatus();
      setDbStatus(response.data);
      if (response.data.state === 'RUNNING') {
        if (!dbPollRef.current) startDbPolling();
      } else {
        stopDbPolling();
        // A run that just finished may have added/replaced a backup file.
        loadDatabaseBackups();
      }
    } catch (err) {
      // Non-critical: status just won't update until the next successful poll.
    }
  };

  const handleExportDatabase = async () => {
    setDbError(null);
    try {
      const response = await exportDatabase();
      setDbStatus(response.data);
      startDbPolling();
    } catch (err) {
      setDbError(err.response?.data?.message || t('settings.databaseBackups.exportFailedPrefix') + err.message);
    }
  };

  const handleImportDatabase = async (filename) => {
    if (!(await confirmAction(t('settings.databaseBackups.importConfirm', { filename })))) return;
    setDbError(null);
    try {
      const response = await importDatabase(filename);
      setDbStatus(response.data);
      startDbPolling();
    } catch (err) {
      setDbError(err.response?.data?.message || t('settings.databaseBackups.importFailedPrefix') + err.message);
    }
  };

  const handleDownloadBackup = async (filename) => {
    setDownloadingBackup(filename);
    try {
      const response = await downloadDatabaseBackup(filename);
      const blobUrl = URL.createObjectURL(new Blob([response.data]));
      const link = document.createElement('a');
      link.href = blobUrl;
      link.download = filename;
      document.body.appendChild(link);
      link.click();
      document.body.removeChild(link);
      setTimeout(() => URL.revokeObjectURL(blobUrl), 30000);
    } catch (err) {
      setDbBackupsError(t('settings.databaseBackups.downloadFailedPrefix') + filename + ': ' + err.message);
    } finally {
      setDownloadingBackup(null);
    }
  };

  return (
    <div role="tabpanel" id="settings-panel-databaseBackups" aria-labelledby="settings-tab-databaseBackups" hidden={hidden}>
      <div className="card">
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', flexWrap: 'wrap', gap: '12px' }}>
          <h3>{t('settings.databaseBackups.title')}</h3>
          <button
            className="btn btn-success"
            onClick={handleExportDatabase}
            disabled={dbStatus?.state === 'RUNNING'}
          >
            {dbStatus?.state === 'RUNNING' ? t('settings.databaseBackups.running') : `⇩ ${t('settings.databaseBackups.exportButton')}`}
          </button>
        </div>
        <p style={{ marginTop: '8px', color: 'var(--color-text-secondary)', fontSize: '13px' }}>
          {t('settings.databaseBackups.description')}
        </p>
        {dbError && <div className="error" role="alert">{dbError}</div>}
        {dbStatus && (
          <div style={{ marginTop: '10px' }}>
            <div style={{ display: 'flex', gap: '20px', fontSize: '13px', flexWrap: 'wrap' }}>
              <span><strong>{t('settings.databaseBackups.lastOperation')}</strong> {dbStatus.lastOperation ?? '-'}</span>
              <span><strong>{t('settings.solver.state')}</strong> {dbStatus.state}</span>
              <span><strong>{t('settings.solver.started')}</strong> {formatTimestamp(dbStatus.startedAt)}</span>
              <span><strong>{t('settings.solver.finished')}</strong> {formatTimestamp(dbStatus.finishedAt)}</span>
              <span><strong>{t('settings.solver.exitCode')}</strong> {dbStatus.exitCode ?? '-'}</span>
            </div>
            {dbStatus.log && dbStatus.log.length > 0 && (
              <pre
                style={{
                  marginTop: '10px',
                  maxHeight: '260px',
                  overflowY: 'auto',
                  background: '#1e1e1e',
                  color: '#d4d4d4',
                  padding: '10px',
                  borderRadius: '4px',
                  fontSize: '12px',
                  whiteSpace: 'pre-wrap',
                }}
              >
                {dbStatus.log.join('\n')}
              </pre>
            )}
          </div>
        )}
      </div>

      <div className="card">
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
          <h3>{t('settings.databaseBackups.filesTitle')}</h3>
          <button className="btn btn-secondary" onClick={loadDatabaseBackups}>↻ {t('settings.complianceSnapshots.refresh')}</button>
        </div>
        <p style={{ marginTop: '8px', color: 'var(--color-text-secondary)', fontSize: '13px' }}>
          {t('settings.databaseBackups.filesDescription')}
        </p>
        {dbBackupsError && <div className="error" role="alert">{dbBackupsError}</div>}
        {dbBackups.length === 0 && !dbBackupsError && (
          <p style={{ color: 'var(--color-text-secondary)', fontSize: '13px' }}>{t('settings.databaseBackups.none')}</p>
        )}
        {dbBackups.length > 0 && (
          <table style={{ marginTop: '8px' }}>
            <thead>
              <tr>
                <th>{t('settings.databaseBackups.table.filename')}</th>
                <th>{t('settings.databaseBackups.table.size')}</th>
                <th>{t('settings.databaseBackups.table.modified')}</th>
                <th>{t('common.actions')}</th>
              </tr>
            </thead>
            <tbody>
              {dbBackups.map((f) => (
                <tr key={f.filename}>
                  <td>{f.filename}</td>
                  <td>{formatBytes(f.sizeBytes)}</td>
                  <td>{formatTimestamp(f.modifiedAt)}</td>
                  <td style={{ display: 'flex', gap: '8px' }}>
                    <button
                      className="btn btn-primary"
                      onClick={() => handleDownloadBackup(f.filename)}
                      disabled={downloadingBackup === f.filename}
                    >
                      {downloadingBackup === f.filename ? t('settings.complianceSnapshots.opening') : t('settings.databaseBackups.download')}
                    </button>
                    <button
                      className="btn btn-danger"
                      onClick={() => handleImportDatabase(f.filename)}
                      disabled={dbStatus?.state === 'RUNNING'}
                    >
                      {t('settings.databaseBackups.restore')}
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </div>
    </div>
  );
}

export default DatabaseBackupsTab;
