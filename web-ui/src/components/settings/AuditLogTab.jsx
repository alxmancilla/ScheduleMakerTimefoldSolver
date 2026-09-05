import React, { useState, useEffect } from 'react';
import { useTranslation } from 'react-i18next';
import { getAuditLog } from '../../api';
import { usePagination, Pagination, DEFAULT_PAGE_SIZE } from '../../ui/Pagination';
import { formatTimestamp } from './constants';

function AuditLogTab({ hidden }) {
  const { t } = useTranslation();

  const [auditLog, setAuditLog] = useState([]);
  const [auditLogError, setAuditLogError] = useState(null);
  const auditLogPagination = usePagination(auditLog);

  useEffect(() => {
    loadAuditLog();
  }, []);

  const loadAuditLog = async () => {
    try {
      const response = await getAuditLog();
      setAuditLog(response.data);
      setAuditLogError(null);
    } catch (err) {
      setAuditLogError(t('settings.auditLog.loadFailedPrefix') + err.message);
    }
  };

  return (
    <div role="tabpanel" id="settings-panel-auditLog" aria-labelledby="settings-tab-auditLog" hidden={hidden}>
      <div className="card">
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
          <h3>{t('settings.auditLog.title')}</h3>
          <button className="btn btn-secondary" onClick={loadAuditLog}>↻ {t('settings.auditLog.refresh')}</button>
        </div>
        <p style={{ marginTop: '8px', color: 'var(--color-text-secondary)', fontSize: '13px' }}>
          {t('settings.auditLog.description')}
        </p>
        {auditLogError && <div className="error" role="alert">{auditLogError}</div>}
        {auditLog.length === 0 && !auditLogError && (
          <p style={{ color: 'var(--color-text-secondary)', fontSize: '13px' }}>{t('settings.auditLog.none')}</p>
        )}
        {auditLog.length > 0 && (
          <>
            <table style={{ marginTop: '8px' }}>
              <thead>
                <tr>
                  <th>{t('settings.auditLog.table.when')}</th>
                  <th>{t('settings.auditLog.table.user')}</th>
                  <th>{t('settings.auditLog.table.method')}</th>
                  <th>{t('settings.auditLog.table.path')}</th>
                  <th>{t('settings.auditLog.table.status')}</th>
                </tr>
              </thead>
              <tbody>
                {auditLogPagination.pageItems.map((entry) => (
                  <tr key={entry.id}>
                    <td>{formatTimestamp(entry.occurredAt)}</td>
                    <td>{entry.username}</td>
                    <td>{entry.httpMethod}</td>
                    <td>{entry.path}</td>
                    <td>{entry.statusCode}</td>
                  </tr>
                ))}
              </tbody>
            </table>
            <Pagination
              page={auditLogPagination.page}
              pageCount={auditLogPagination.pageCount}
              totalItems={auditLogPagination.totalItems}
              pageSize={DEFAULT_PAGE_SIZE}
              onPageChange={auditLogPagination.setPage}
            />
          </>
        )}
      </div>
    </div>
  );
}

export default AuditLogTab;
