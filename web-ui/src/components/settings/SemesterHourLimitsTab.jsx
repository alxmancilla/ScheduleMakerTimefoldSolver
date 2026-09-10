import React, { useState, useEffect } from 'react';
import { useTranslation } from 'react-i18next';
import { getSemesterHourLimits, setSemesterHourLimit, deleteSemesterHourLimit } from '../../api';
import { useToast } from '../../ui/ToastContext';
import { useConfirm } from '../../ui/ConfirmContext';
import { formatHour } from '../../constants';

// Guardrail #1's valid range for latestEndHour - the school's real operating
// hours, one narrower than Timeslots' START_HOURS at the bottom (mirrors
// SchoolCalendarConstants.EARLIEST_START_HOUR + 1 to LATEST_HOUR in the web
// module): a 1h block starting at the earliest possible hour (7:00) can't
// finish before 8:00, so 7 itself is never a valid "must finish by" value.
const LATEST_END_HOURS = [8, 9, 10, 11, 12, 13, 14, 15];
const SEVERITY_OPTIONS = ['HARD', 'SOFT'];
const EMPTY_SEMESTER_HOUR_LIMIT_FORM = { semester: '', latestEndHour: 14, severity: 'HARD' };

function SemesterHourLimitsTab({ hidden }) {
  const { t } = useTranslation();
  const showToast = useToast();
  const confirmAction = useConfirm();

  const [semesterHourLimits, setSemesterHourLimits] = useState([]);
  const [semesterHourLimitsError, setSemesterHourLimitsError] = useState(null);
  const [showSemesterHourLimitForm, setShowSemesterHourLimitForm] = useState(false);
  const [editingSemester, setEditingSemester] = useState(null);
  const [semesterHourLimitForm, setSemesterHourLimitForm] = useState(EMPTY_SEMESTER_HOUR_LIMIT_FORM);
  const [savingSemesterHourLimit, setSavingSemesterHourLimit] = useState(false);
  // Guardrail #3's capacity warnings from the last successful save - not
  // per-row state, since they describe the save that just happened, not
  // any one row currently on screen.
  const [semesterHourLimitWarnings, setSemesterHourLimitWarnings] = useState([]);

  useEffect(() => {
    loadSemesterHourLimits();
  }, []);

  // Per-semester finish-by-hour limits: an open-ended set of semesters (not
  // a fixed list like constraint weights), so this follows the Block Rules
  // add/edit-modal pattern instead of the constraint-weights fixed-table one.
  const loadSemesterHourLimits = async () => {
    try {
      const response = await getSemesterHourLimits();
      setSemesterHourLimits(response.data);
      setSemesterHourLimitsError(null);
    } catch (err) {
      setSemesterHourLimitsError(t('settings.semesterHourLimits.loadFailedPrefix') + err.message);
    }
  };

  const handleAddSemesterHourLimit = () => {
    setEditingSemester(null);
    setSemesterHourLimitForm(EMPTY_SEMESTER_HOUR_LIMIT_FORM);
    setSemesterHourLimitsError(null);
    setSemesterHourLimitWarnings([]);
    setShowSemesterHourLimitForm(true);
  };

  const handleEditSemesterHourLimit = (limit) => {
    setEditingSemester(limit.semester);
    setSemesterHourLimitForm({
      semester: limit.semester,
      latestEndHour: limit.latestEndHour,
      severity: limit.severity,
    });
    setSemesterHourLimitsError(null);
    setSemesterHourLimitWarnings([]);
    setShowSemesterHourLimitForm(true);
  };

  const handleCancelSemesterHourLimit = () => {
    setShowSemesterHourLimitForm(false);
    setEditingSemester(null);
    setSemesterHourLimitsError(null);
  };

  const handleSubmitSemesterHourLimit = async (e) => {
    e.preventDefault();
    setSavingSemesterHourLimit(true);
    setSemesterHourLimitsError(null);
    try {
      // Guardrail #2 (blocking, pinned-data conflict) throws and lands in
      // the catch block below; guardrail #3 (capacity warning) never blocks
      // the save - it comes back in the response and is shown separately,
      // surviving the form close so it stays visible.
      const response = await setSemesterHourLimit(
        semesterHourLimitForm.semester,
        semesterHourLimitForm.latestEndHour,
        semesterHourLimitForm.severity
      );
      handleCancelSemesterHourLimit();
      loadSemesterHourLimits();
      setSemesterHourLimitWarnings(response.data.warnings || []);
      showToast(t('settings.semesterHourLimits.savedMessage'));
    } catch (err) {
      // Guardrail #1's bounds violations land in errors.latestEndHour (a
      // MethodArgumentNotValidException field error), with only the generic
      // "Validation failed" in the top-level message - check the specific
      // field first so the actual bounds explanation is what's shown.
      setSemesterHourLimitsError(
        err.response?.data?.errors?.latestEndHour
          || err.response?.data?.errors?.severity
          || err.response?.data?.message
          || t('settings.semesterHourLimits.saveFailedPrefix') + err.message
      );
    } finally {
      setSavingSemesterHourLimit(false);
    }
  };

  const handleDeleteSemesterHourLimit = async (semester) => {
    if (!(await confirmAction(t('settings.semesterHourLimits.confirmDelete', { semester })))) return;
    try {
      await deleteSemesterHourLimit(semester);
      loadSemesterHourLimits();
      showToast(t('settings.semesterHourLimits.deletedMessage'));
    } catch (err) {
      setSemesterHourLimitsError(err.response?.data?.message || t('settings.semesterHourLimits.deleteFailedPrefix') + err.message);
    }
  };

  return (
    <div role="tabpanel" id="settings-panel-semesterHourLimits" aria-labelledby="settings-tab-semesterHourLimits" hidden={hidden}>
      <div className="card">
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
          <h3>{t('settings.semesterHourLimits.title')}</h3>
          <button className="btn btn-success" onClick={handleAddSemesterHourLimit}>
            {t('settings.semesterHourLimits.addLimit')}
          </button>
        </div>
        <p style={{ marginTop: '8px', color: 'var(--color-text-secondary)', fontSize: '13px' }}>
          {t('settings.semesterHourLimits.description')}
        </p>
        {semesterHourLimitsError && <div className="error" role="alert">{semesterHourLimitsError}</div>}
        {semesterHourLimitWarnings.length > 0 && (
          <div
            role="alert"
            style={{
              marginTop: '10px', padding: '10px 14px', borderRadius: '8px',
              background: 'color-mix(in srgb, var(--color-warning) 12%, transparent)',
              border: '1px solid var(--color-warning)', color: 'var(--color-text)', fontSize: '13px',
            }}
          >
            <strong>{t('settings.semesterHourLimits.warningsTitle')}</strong>
            <ul style={{ margin: '6px 0 0', paddingLeft: '20px' }}>
              {semesterHourLimitWarnings.map((warning, idx) => (
                <li key={idx}>{warning}</li>
              ))}
            </ul>
          </div>
        )}
      </div>

      {showSemesterHourLimitForm && (
        <div className="card">
          <h3>{editingSemester !== null ? t('settings.semesterHourLimits.editLimit') : t('settings.semesterHourLimits.newLimit')}</h3>
          <form onSubmit={handleSubmitSemesterHourLimit}>
            <div className="form-group">
              <label htmlFor="semester-limit-semester">{t('settings.semesterHourLimits.fields.semester')}</label>
              <input id="semester-limit-semester"
                type="number"
                min={1}
                max={12}
                value={semesterHourLimitForm.semester}
                onChange={(e) => setSemesterHourLimitForm({ ...semesterHourLimitForm, semester: e.target.value })}
                disabled={editingSemester !== null}
                required
              />
            </div>
            <div className="form-group">
              <label htmlFor="semester-limit-end-hour">{t('settings.semesterHourLimits.fields.latestEndHour')}</label>
              <select id="semester-limit-end-hour"
                value={semesterHourLimitForm.latestEndHour}
                onChange={(e) => setSemesterHourLimitForm({ ...semesterHourLimitForm, latestEndHour: parseInt(e.target.value, 10) })}
              >
                {LATEST_END_HOURS.map((hour) => (
                  <option key={hour} value={hour}>{formatHour(hour)}</option>
                ))}
              </select>
            </div>
            <div className="form-group">
              <label htmlFor="semester-limit-severity">{t('settings.semesterHourLimits.fields.severity')}</label>
              <select id="semester-limit-severity"
                value={semesterHourLimitForm.severity}
                onChange={(e) => setSemesterHourLimitForm({ ...semesterHourLimitForm, severity: e.target.value })}
              >
                {SEVERITY_OPTIONS.map((severity) => (
                  <option key={severity} value={severity}>{t(`settings.semesterHourLimits.severities.${severity}`)}</option>
                ))}
              </select>
              <div style={{ color: 'var(--color-text-secondary)', fontSize: '12px', marginTop: '4px' }}>
                {semesterHourLimitForm.severity === 'HARD'
                  ? t('settings.semesterHourLimits.hardHint')
                  : t('settings.semesterHourLimits.softHint')}
              </div>
            </div>
            <div style={{ display: 'flex', gap: '10px' }}>
              <button type="submit" className="btn btn-primary" disabled={savingSemesterHourLimit || !semesterHourLimitForm.semester}>
                {savingSemesterHourLimit ? t('settings.semesterHourLimits.saving') : t('common.save')}
              </button>
              <button type="button" className="btn btn-secondary" onClick={handleCancelSemesterHourLimit}>{t('common.cancel')}</button>
            </div>
          </form>
        </div>
      )}

      <div className="card">
        {semesterHourLimits.length === 0 ? (
          <p style={{ color: 'var(--color-text-secondary)' }}>{t('settings.semesterHourLimits.none')}</p>
        ) : (
          <table>
            <thead>
              <tr>
                <th>{t('settings.semesterHourLimits.table.semester')}</th>
                <th>{t('settings.semesterHourLimits.table.latestEndHour')}</th>
                <th>{t('settings.semesterHourLimits.table.severity')}</th>
                <th>{t('common.actions')}</th>
              </tr>
            </thead>
            <tbody>
              {semesterHourLimits.map((limit) => (
                <tr key={limit.semester}>
                  <td>{limit.semester}</td>
                  <td>{formatHour(limit.latestEndHour)}</td>
                  <td>{t(`settings.semesterHourLimits.severities.${limit.severity}`)}</td>
                  <td>
                    <button className="btn btn-primary" onClick={() => handleEditSemesterHourLimit(limit)} style={{ marginRight: '5px' }}>
                      {t('common.edit')}
                    </button>
                    <button className="btn btn-danger" onClick={() => handleDeleteSemesterHourLimit(limit.semester)}>
                      {t('common.delete')}
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

export default SemesterHourLimitsTab;
