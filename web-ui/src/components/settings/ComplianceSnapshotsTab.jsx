import React, { useState, useEffect } from 'react';
import { useTranslation } from 'react-i18next';
import { listAdminReports, downloadAdminReport } from '../../api';
import { ENGINE_RUN_FINISHED_EVENT, formatTimestamp } from './constants';

function ComplianceSnapshotsTab({ hidden }) {
  const { t } = useTranslation();

  const [adminReports, setAdminReports] = useState([]);
  const [adminReportsError, setAdminReportsError] = useState(null);
  const [openingSnapshot, setOpeningSnapshot] = useState(null);

  useEffect(() => {
    loadAdminReports();
    // See ENGINE_RUN_FINISHED_EVENT's doc comment: a finished solver run
    // (SolverTab, a sibling with no shared state) may have written a new
    // snapshot.
    window.addEventListener(ENGINE_RUN_FINISHED_EVENT, loadAdminReports);
    return () => window.removeEventListener(ENGINE_RUN_FINISHED_EVENT, loadAdminReports);
  }, []);

  const loadAdminReports = async () => {
    try {
      const response = await listAdminReports();
      setAdminReports(response.data);
      setAdminReportsError(null);
    } catch (err) {
      setAdminReportsError(t('settings.complianceSnapshots.loadFailedPrefix') + err.message);
    }
  };

  const handleViewSnapshot = async (runId, filename) => {
    const key = `${runId}::${filename}`;
    setOpeningSnapshot(key);
    try {
      const response = await downloadAdminReport(runId, filename);
      const blobUrl = URL.createObjectURL(new Blob([response.data], { type: 'application/pdf' }));
      window.open(blobUrl, '_blank');
      setTimeout(() => URL.revokeObjectURL(blobUrl), 30000);
    } catch (err) {
      setAdminReportsError(t('settings.complianceSnapshots.openFailedPrefix') + filename + ': ' + err.message);
    } finally {
      setOpeningSnapshot(null);
    }
  };

  return (
    <div role="tabpanel" id="settings-panel-complianceSnapshots" aria-labelledby="settings-tab-complianceSnapshots" hidden={hidden}>
      <div className="card">
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
          <h3>{t('settings.complianceSnapshots.title')}</h3>
          <button className="btn btn-secondary" onClick={loadAdminReports}>↻ {t('settings.complianceSnapshots.refresh')}</button>
        </div>
        <p style={{ marginTop: '8px', color: 'var(--color-text-secondary)', fontSize: '13px' }}>
          {t('settings.complianceSnapshots.description')}
        </p>
        {adminReportsError && <div className="error" role="alert">{adminReportsError}</div>}
        {adminReports.length === 0 && !adminReportsError && (
          <p style={{ color: 'var(--color-text-secondary)', fontSize: '13px' }}>{t('settings.complianceSnapshots.none')}</p>
        )}
        {adminReports.length > 0 && (
          <table style={{ marginTop: '8px' }}>
            <thead>
              <tr>
                <th>{t('settings.complianceSnapshots.table.run')}</th>
                <th>{t('settings.complianceSnapshots.table.file')}</th>
                <th>{t('common.actions')}</th>
              </tr>
            </thead>
            <tbody>
              {adminReports.map((run) =>
                run.files.map((f) => {
                  const key = `${run.runId}::${f.filename}`;
                  return (
                    <tr key={key}>
                      <td>{formatTimestamp(run.generatedAt)}</td>
                      <td>{f.filename}</td>
                      <td>
                        <button
                          className="btn btn-primary"
                          onClick={() => handleViewSnapshot(run.runId, f.filename)}
                          disabled={openingSnapshot === key}
                        >
                          {openingSnapshot === key ? t('settings.complianceSnapshots.opening') : t('settings.complianceSnapshots.view')}
                        </button>
                      </td>
                    </tr>
                  );
                })
              )}
            </tbody>
          </table>
        )}
      </div>
    </div>
  );
}

export default ComplianceSnapshotsTab;
