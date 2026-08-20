import React, { useState, useEffect, useRef } from 'react';
import { useTranslation } from 'react-i18next';
import {
  getTimeslots, createTimeslot, updateTimeslot, deleteTimeslot,
  runEngine, getEngineStatus, generateBlocks,
  listAdminReports, downloadAdminReport,
  getTerm, updateTerm, getAuditLog,
} from '../api';
import { useToast } from '../ui/ToastContext';
import { useConfirm } from '../ui/ConfirmContext';
import { usePagination, Pagination, DEFAULT_PAGE_SIZE } from '../ui/Pagination';

const ENGINE_POLL_MS = 3000;

const DAY_LABELS = [
  { value: 1, key: 'mon' },
  { value: 2, key: 'tue' },
  { value: 3, key: 'wed' },
  { value: 4, key: 'thu' },
  { value: 5, key: 'fri' },
];
const formatTimestamp = (value) => (value ? value.replace('T', ' ').split('.')[0] : '-');
const START_HOURS = [7, 8, 9, 10, 11, 12, 13, 14, 15];
const LENGTHS = [1, 2, 3, 4];
const EMPTY_FORM = { dayOfWeek: 1, startHour: 7, lengthHours: 1 };

const SETTINGS_TABS = [
  { key: 'term', labelKey: 'settings.term.title' },
  { key: 'solver', labelKey: 'settings.solver.title' },
  { key: 'complianceSnapshots', labelKey: 'settings.complianceSnapshots.title' },
  { key: 'generateBlocks', labelKey: 'settings.generateBlocks.title' },
  { key: 'timeslots', labelKey: 'settings.timeslots.title' },
  { key: 'auditLog', labelKey: 'settings.auditLog.title' },
];

function Settings() {
  const { t } = useTranslation();
  const showToast = useToast();
  const confirmAction = useConfirm();
  const dayLabel = (value) => t(`common.days.${DAY_LABELS.find((d) => d.value === value)?.key || 'mon'}`);

  const [timeslots, setTimeslots] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [fieldErrors, setFieldErrors] = useState({});
  const [editingTimeslot, setEditingTimeslot] = useState(null);
  const [showForm, setShowForm] = useState(false);
  const [form, setForm] = useState(EMPTY_FORM);

  const [engineStatus, setEngineStatus] = useState(null);
  const [engineError, setEngineError] = useState(null);
  const pollRef = useRef(null);

  const [adminReports, setAdminReports] = useState([]);
  const [adminReportsError, setAdminReportsError] = useState(null);
  const [openingSnapshot, setOpeningSnapshot] = useState(null);

  const [termInput, setTermInput] = useState('');
  const [termError, setTermError] = useState(null);
  const [savingTerm, setSavingTerm] = useState(false);

  const [auditLog, setAuditLog] = useState([]);
  const [auditLogError, setAuditLogError] = useState(null);

  const [activeTab, setActiveTab] = useState('term');

  useEffect(() => {
    loadTimeslots();
    loadEngineStatus();
    loadAdminReports();
    loadTerm();
    loadAuditLog();
    return () => stopPolling();
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

  const loadTerm = async () => {
    try {
      const response = await getTerm();
      setTermInput(response.data.label || '');
      setTermError(null);
    } catch (err) {
      setTermError(t('settings.term.loadFailedPrefix') + err.message);
    }
  };

  const handleSaveTerm = async (e) => {
    e.preventDefault();
    setSavingTerm(true);
    setTermError(null);
    try {
      const response = await updateTerm(termInput.trim());
      setTermInput(response.data.label || '');
      showToast(t('settings.term.savedMessage'));
    } catch (err) {
      setTermError(err.response?.data?.message || t('settings.term.saveFailedPrefix') + err.message);
    } finally {
      setSavingTerm(false);
    }
  };

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

  const stopPolling = () => {
    if (pollRef.current) {
      clearInterval(pollRef.current);
      pollRef.current = null;
    }
  };

  const startPolling = () => {
    stopPolling();
    pollRef.current = setInterval(loadEngineStatus, ENGINE_POLL_MS);
  };

  const loadEngineStatus = async () => {
    try {
      const response = await getEngineStatus();
      setEngineStatus(response.data);
      if (response.data.state === 'RUNNING') {
        if (!pollRef.current) startPolling();
      } else {
        stopPolling();
        // A run that just finished may have added a new compliance snapshot.
        loadAdminReports();
      }
    } catch (err) {
      // Non-critical: status just won't update until the next successful poll.
    }
  };

  const handleRunEngine = async () => {
    setEngineError(null);
    try {
      const response = await runEngine();
      setEngineStatus(response.data);
      startPolling();
    } catch (err) {
      setEngineError(err.response?.data?.message || t('settings.solver.startFailedPrefix') + err.message);
    }
  };

  const [blockGenResult, setBlockGenResult] = useState(null);
  const [blockGenError, setBlockGenError] = useState(null);
  const [generatingBlocks, setGeneratingBlocks] = useState(false);

  const handleGenerateBlocks = async () => {
    setGeneratingBlocks(true);
    setBlockGenError(null);
    setBlockGenResult(null);
    try {
      const response = await generateBlocks();
      setBlockGenResult(response.data);
    } catch (err) {
      setBlockGenError(err.response?.data?.message || t('settings.generateBlocks.failedPrefix') + err.message);
    } finally {
      setGeneratingBlocks(false);
    }
  };

  const loadTimeslots = async () => {
    try {
      setLoading(true);
      const response = await getTimeslots();
      setTimeslots(response.data);
      setError(null);
    } catch (err) {
      setError(t('settings.timeslots.loadFailedPrefix') + err.message);
    } finally {
      setLoading(false);
    }
  };

  const handleField = (e) => {
    setForm({ ...form, [e.target.name]: parseInt(e.target.value, 10) });
  };

  const handleAdd = () => {
    setEditingTimeslot(null);
    setForm(EMPTY_FORM);
    setFieldErrors({});
    setError(null);
    setShowForm(true);
  };

  const handleEdit = (timeslot) => {
    setEditingTimeslot(timeslot);
    setForm({
      dayOfWeek: timeslot.dayOfWeek,
      startHour: timeslot.startHour,
      lengthHours: timeslot.lengthHours,
    });
    setFieldErrors({});
    setError(null);
    setShowForm(true);
  };

  const handleCancel = () => {
    setShowForm(false);
    setEditingTimeslot(null);
    setFieldErrors({});
    setError(null);
  };

  const handleSubmit = async (e) => {
    e.preventDefault();
    setFieldErrors({});
    setError(null);

    const payload = {
      dayOfWeek: form.dayOfWeek,
      startHour: form.startHour,
      lengthHours: form.lengthHours,
    };

    try {
      if (editingTimeslot) {
        await updateTimeslot(editingTimeslot.id, payload);
      } else {
        await createTimeslot(payload);
      }
      handleCancel();
      loadTimeslots();
      showToast(t('settings.timeslots.savedMessage'));
    } catch (err) {
      const data = err.response?.data;
      if (data?.errors) {
        setFieldErrors(data.errors);
      }
      setError(data?.message || t('settings.timeslots.saveFailedPrefix') + err.message);
    }
  };

  const handleDelete = async (timeslot) => {
    if (!(await confirmAction(t('settings.timeslots.confirmDelete', {
      day: dayLabel(timeslot.dayOfWeek),
      start: timeslot.startHour,
      end: timeslot.startHour + timeslot.lengthHours,
    })))) return;
    try {
      await deleteTimeslot(timeslot.id);
      loadTimeslots();
      showToast(t('settings.timeslots.deletedMessage'));
    } catch (err) {
      setError(err.response?.data?.message || t('settings.timeslots.deleteFailedPrefix') + err.message);
    }
  };

  const endHour = form.startHour + form.lengthHours;
  const exceedsDayBounds = endHour > 15;

  const auditLogPagination = usePagination(auditLog);

  if (loading) return <div className="loading">{t('settings.loading')}</div>;

  return (
    <div>
      <div className="card">
        <h2>{t('settings.title')}</h2>
        <p style={{ color: '#7f8c8d', fontSize: '14px' }}>{t('settings.description')}</p>
      </div>

      <div className="tabs" role="tablist">
        {SETTINGS_TABS.map((tab) => (
          <button
            key={tab.key}
            type="button"
            id={`settings-tab-${tab.key}`}
            role="tab"
            aria-selected={activeTab === tab.key}
            aria-controls={`settings-panel-${tab.key}`}
            className={`tab ${activeTab === tab.key ? 'active' : ''}`}
            onClick={() => setActiveTab(tab.key)}
          >
            {t(tab.labelKey)}
          </button>
        ))}
      </div>

      {activeTab === 'term' && (
      <div role="tabpanel" id="settings-panel-term" aria-labelledby="settings-tab-term">
      <div className="card">
        <h3>{t('settings.term.title')}</h3>
        <p style={{ marginTop: '8px', color: '#7f8c8d', fontSize: '13px' }}>
          {t('settings.term.description')}
        </p>
        {termError && <div className="error">{termError}</div>}
        <form onSubmit={handleSaveTerm} style={{ display: 'flex', gap: '10px', marginTop: '10px', alignItems: 'flex-end' }}>
          <div className="form-group" style={{ marginBottom: 0, flex: 1, maxWidth: '320px' }}>
            <label>{t('settings.term.fields.label')}</label>
            <input
              type="text"
              value={termInput}
              onChange={(e) => setTermInput(e.target.value)}
              maxLength={100}
              placeholder={t('settings.term.placeholder')}
            />
          </div>
          <button type="submit" className="btn btn-primary" disabled={savingTerm}>
            {savingTerm ? t('settings.term.saving') : t('common.save')}
          </button>
        </form>
      </div>
      </div>
      )}

      {activeTab === 'solver' && (
      <div role="tabpanel" id="settings-panel-solver" aria-labelledby="settings-tab-solver">
      <div className="card">
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
          <h3>{t('settings.solver.title')}</h3>
          <button
            className="btn btn-success"
            onClick={handleRunEngine}
            disabled={engineStatus?.state === 'RUNNING'}
          >
            {engineStatus?.state === 'RUNNING' ? t('settings.solver.running') : `▶ ${t('settings.solver.startEngine')}`}
          </button>
        </div>
        <p style={{ marginTop: '8px', color: '#7f8c8d', fontSize: '13px' }}>
          {t('settings.solver.description')}
        </p>
        {engineError && <div className="error">{engineError}</div>}
        {engineStatus && (
          <div style={{ marginTop: '10px' }}>
            <div style={{ display: 'flex', gap: '20px', fontSize: '13px', flexWrap: 'wrap' }}>
              <span><strong>{t('settings.solver.state')}</strong> {engineStatus.state}</span>
              <span><strong>{t('settings.solver.started')}</strong> {formatTimestamp(engineStatus.startedAt)}</span>
              <span><strong>{t('settings.solver.finished')}</strong> {formatTimestamp(engineStatus.finishedAt)}</span>
              <span><strong>{t('settings.solver.exitCode')}</strong> {engineStatus.exitCode ?? '-'}</span>
            </div>
            {engineStatus.log && engineStatus.log.length > 0 && (
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
                {engineStatus.log.join('\n')}
              </pre>
            )}
          </div>
        )}
      </div>
      </div>
      )}

      {activeTab === 'complianceSnapshots' && (
      <div role="tabpanel" id="settings-panel-complianceSnapshots" aria-labelledby="settings-tab-complianceSnapshots">
      <div className="card">
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
          <h3>{t('settings.complianceSnapshots.title')}</h3>
          <button className="btn btn-secondary" onClick={loadAdminReports}>↻ {t('settings.complianceSnapshots.refresh')}</button>
        </div>
        <p style={{ marginTop: '8px', color: '#7f8c8d', fontSize: '13px' }}>
          {t('settings.complianceSnapshots.description')}
        </p>
        {adminReportsError && <div className="error">{adminReportsError}</div>}
        {adminReports.length === 0 && !adminReportsError && (
          <p style={{ color: '#7f8c8d', fontSize: '13px' }}>{t('settings.complianceSnapshots.none')}</p>
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
      )}

      {activeTab === 'generateBlocks' && (
      <div role="tabpanel" id="settings-panel-generateBlocks" aria-labelledby="settings-tab-generateBlocks">
      <div className="card">
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
          <h3>{t('settings.generateBlocks.title')}</h3>
          <button
            className="btn btn-success"
            onClick={handleGenerateBlocks}
            disabled={generatingBlocks}
          >
            {generatingBlocks ? t('settings.generateBlocks.generating') : `⚙ ${t('settings.generateBlocks.button')}`}
          </button>
        </div>
        <p style={{ marginTop: '8px', color: '#7f8c8d', fontSize: '13px' }}>
          {t('settings.generateBlocks.description')}
        </p>
        {blockGenError && <div className="error">{blockGenError}</div>}
        {blockGenResult && (
          <div style={{ marginTop: '10px', fontSize: '13px' }}>
            <div style={{ color: '#2e7d32' }}>
              {t('settings.generateBlocks.resultSummary', {
                created: blockGenResult.blocksCreated,
                skipped: blockGenResult.groupCoursesSkippedExisting,
              })}
            </div>
            {blockGenResult.warnings.length > 0 && (
              <ul style={{ marginTop: '6px', color: '#c0392b' }}>
                {blockGenResult.warnings.map((w, i) => (
                  <li key={i}>{w}</li>
                ))}
              </ul>
            )}
          </div>
        )}
      </div>
      </div>
      )}

      {activeTab === 'timeslots' && (
      <div role="tabpanel" id="settings-panel-timeslots" aria-labelledby="settings-tab-timeslots">
      <div className="card">
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
          <h3>{t('settings.timeslots.title')}</h3>
          <button className="btn btn-success" onClick={handleAdd}>
            {t('settings.timeslots.addTimeslot')}
          </button>
        </div>
      </div>

      {error && <div className="error">{error}</div>}

      {showForm && (
        <div className="card">
          <h3>{editingTimeslot ? t('settings.timeslots.editTimeslot') : t('settings.timeslots.newTimeslot')}</h3>
          <form onSubmit={handleSubmit}>
            <div className="form-group">
              <label>{t('settings.timeslots.fields.day')}</label>
              <select name="dayOfWeek" value={form.dayOfWeek} onChange={handleField}>
                {DAY_LABELS.map((day) => (
                  <option key={day.value} value={day.value}>{t(`common.days.${day.key}`)}</option>
                ))}
              </select>
              {fieldErrors.dayOfWeek && <div className="error">{fieldErrors.dayOfWeek}</div>}
            </div>
            <div className="form-group">
              <label>{t('settings.timeslots.fields.startHour')}</label>
              <select name="startHour" value={form.startHour} onChange={handleField}>
                {START_HOURS.map((h) => (
                  <option key={h} value={h}>{h}:00</option>
                ))}
              </select>
              {fieldErrors.startHour && <div className="error">{fieldErrors.startHour}</div>}
            </div>
            <div className="form-group">
              <label>{t('settings.timeslots.fields.length')}</label>
              <select name="lengthHours" value={form.lengthHours} onChange={handleField}>
                {LENGTHS.map((l) => (
                  <option key={l} value={l}>{l}</option>
                ))}
              </select>
              {fieldErrors.lengthHours && <div className="error">{fieldErrors.lengthHours}</div>}
            </div>
            <div className="form-group">
              <label>{t('settings.timeslots.fields.endsAt')}</label>
              <span style={{ color: exceedsDayBounds ? '#c0392b' : undefined }}>
                {endHour}:00{exceedsDayBounds ? t('settings.timeslots.exceedsDayBounds') : ''}
              </span>
              {fieldErrors.withinDayBounds && <div className="error">{fieldErrors.withinDayBounds}</div>}
            </div>
            <div style={{ display: 'flex', gap: '10px' }}>
              <button type="submit" className="btn btn-primary" disabled={exceedsDayBounds}>{t('common.save')}</button>
              <button type="button" className="btn btn-secondary" onClick={handleCancel}>{t('common.cancel')}</button>
            </div>
          </form>
        </div>
      )}

      {timeslots.length === 0 ? (
        <div className="card">
          <p style={{ color: '#7f8c8d' }}>{t('settings.timeslots.none')}</p>
        </div>
      ) : (
        DAY_LABELS.map((day) => {
          const dayTimeslots = timeslots
            .filter((ts) => ts.dayOfWeek === day.value)
            .sort((a, b) => a.startHour - b.startHour);
          if (dayTimeslots.length === 0) return null;
          return (
            <div className="card" key={day.value}>
              <h4 style={{ marginBottom: '10px', color: '#2c3e50' }}>{t(`common.days.${day.key}`)}</h4>
              <table>
                <thead>
                  <tr>
                    <th>{t('settings.timeslots.table.start')}</th>
                    <th>{t('settings.timeslots.table.end')}</th>
                    <th>{t('settings.timeslots.table.length')}</th>
                    <th>{t('common.actions')}</th>
                  </tr>
                </thead>
                <tbody>
                  {dayTimeslots.map((timeslot) => (
                    <tr key={timeslot.id}>
                      <td>{timeslot.startHour}:00</td>
                      <td>{timeslot.startHour + timeslot.lengthHours}:00</td>
                      <td>{timeslot.lengthHours}</td>
                      <td>
                        <button className="btn btn-primary" onClick={() => handleEdit(timeslot)} style={{ marginRight: '5px' }}>
                          {t('common.edit')}
                        </button>
                        <button className="btn btn-danger" onClick={() => handleDelete(timeslot)}>
                          {t('common.delete')}
                        </button>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          );
        })
      )}
      </div>
      )}

      {activeTab === 'auditLog' && (
      <div role="tabpanel" id="settings-panel-auditLog" aria-labelledby="settings-tab-auditLog">
      <div className="card">
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
          <h3>{t('settings.auditLog.title')}</h3>
          <button className="btn btn-secondary" onClick={loadAuditLog}>↻ {t('settings.auditLog.refresh')}</button>
        </div>
        <p style={{ marginTop: '8px', color: '#7f8c8d', fontSize: '13px' }}>
          {t('settings.auditLog.description')}
        </p>
        {auditLogError && <div className="error">{auditLogError}</div>}
        {auditLog.length === 0 && !auditLogError && (
          <p style={{ color: '#7f8c8d', fontSize: '13px' }}>{t('settings.auditLog.none')}</p>
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
      )}
    </div>
  );
}

export default Settings;
