import React, { useState, useEffect } from 'react';
import { useTranslation } from 'react-i18next';
import { getCalendarExceptions, setCalendarException, deleteCalendarException } from '../../api';
import { useToast } from '../../ui/ToastContext';
import { useConfirm } from '../../ui/ConfirmContext';
import { formatHour } from '../../constants';
import { START_HOURS } from './constants';

const CALENDAR_EXCEPTION_TYPES = ['HOLIDAY', 'HALF_DAY', 'EXAM_DAY'];
const EMPTY_CALENDAR_EXCEPTION_FORM = { date: '', type: 'HOLIDAY', label: '', endHour: 12 };

function CalendarTab({ hidden }) {
  const { t } = useTranslation();
  const showToast = useToast();
  const confirmAction = useConfirm();

  const [calendarExceptions, setCalendarExceptions] = useState([]);
  const [calendarExceptionsError, setCalendarExceptionsError] = useState(null);
  const [showCalendarExceptionForm, setShowCalendarExceptionForm] = useState(false);
  const [editingCalendarExceptionDate, setEditingCalendarExceptionDate] = useState(null);
  const [calendarExceptionForm, setCalendarExceptionForm] = useState(EMPTY_CALENDAR_EXCEPTION_FORM);
  const [savingCalendarException, setSavingCalendarException] = useState(false);

  useEffect(() => {
    loadCalendarExceptions();
  }, []);

  const loadCalendarExceptions = async () => {
    try {
      const response = await getCalendarExceptions();
      setCalendarExceptions(response.data);
      setCalendarExceptionsError(null);
    } catch (err) {
      setCalendarExceptionsError(t('settings.calendar.loadFailedPrefix') + err.message);
    }
  };

  const handleAddCalendarException = () => {
    setEditingCalendarExceptionDate(null);
    setCalendarExceptionForm(EMPTY_CALENDAR_EXCEPTION_FORM);
    setCalendarExceptionsError(null);
    setShowCalendarExceptionForm(true);
  };

  const handleEditCalendarException = (exception) => {
    setEditingCalendarExceptionDate(exception.exceptionDate);
    setCalendarExceptionForm({
      date: exception.exceptionDate,
      type: exception.type,
      label: exception.label || '',
      endHour: exception.endHour || 12,
    });
    setCalendarExceptionsError(null);
    setShowCalendarExceptionForm(true);
  };

  const handleCancelCalendarException = () => {
    setShowCalendarExceptionForm(false);
    setEditingCalendarExceptionDate(null);
    setCalendarExceptionsError(null);
  };

  const handleSubmitCalendarException = async (e) => {
    e.preventDefault();
    setSavingCalendarException(true);
    setCalendarExceptionsError(null);
    try {
      const endHour = calendarExceptionForm.type === 'HALF_DAY' ? calendarExceptionForm.endHour : null;
      await setCalendarException(calendarExceptionForm.date, calendarExceptionForm.type,
          calendarExceptionForm.label || null, endHour);
      handleCancelCalendarException();
      loadCalendarExceptions();
      showToast(t('settings.calendar.savedMessage'));
    } catch (err) {
      setCalendarExceptionsError(err.response?.data?.message || t('settings.calendar.saveFailedPrefix') + err.message);
    } finally {
      setSavingCalendarException(false);
    }
  };

  const handleDeleteCalendarException = async (date) => {
    if (!(await confirmAction(t('settings.calendar.confirmDelete', { date })))) return;
    try {
      await deleteCalendarException(date);
      loadCalendarExceptions();
      showToast(t('settings.calendar.deletedMessage'));
    } catch (err) {
      setCalendarExceptionsError(err.response?.data?.message || t('settings.calendar.deleteFailedPrefix') + err.message);
    }
  };

  return (
    <div role="tabpanel" id="settings-panel-calendar" aria-labelledby="settings-tab-calendar" hidden={hidden}>
      <div className="card">
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
          <h3>{t('settings.calendar.title')}</h3>
          <button className="btn btn-success" onClick={handleAddCalendarException}>
            {t('settings.calendar.addException')}
          </button>
        </div>
        <p style={{ marginTop: '8px', color: 'var(--color-text-secondary)', fontSize: '13px' }}>
          {t('settings.calendar.description')}
        </p>
        {calendarExceptionsError && <div className="error" role="alert">{calendarExceptionsError}</div>}
      </div>

      {showCalendarExceptionForm && (
        <div className="card">
          <h3>{editingCalendarExceptionDate ? t('settings.calendar.editException') : t('settings.calendar.newException')}</h3>
          <form onSubmit={handleSubmitCalendarException}>
            <div className="form-group">
              <label htmlFor="calendar-exception-date">{t('settings.calendar.fields.date')}</label>
              {editingCalendarExceptionDate ? (
                <input id="calendar-exception-date" type="text" value={calendarExceptionForm.date} disabled />
              ) : (
                <input id="calendar-exception-date"
                  type="date"
                  value={calendarExceptionForm.date}
                  onChange={(e) => setCalendarExceptionForm({ ...calendarExceptionForm, date: e.target.value })}
                  required
                />
              )}
            </div>
            <div className="form-group">
              <label htmlFor="calendar-exception-type">{t('settings.calendar.fields.type')}</label>
              <select id="calendar-exception-type"
                value={calendarExceptionForm.type}
                onChange={(e) => setCalendarExceptionForm({ ...calendarExceptionForm, type: e.target.value })}
              >
                {CALENDAR_EXCEPTION_TYPES.map((type) => (
                  <option key={type} value={type}>{t(`settings.calendar.types.${type}`)}</option>
                ))}
              </select>
            </div>
            <div className="form-group">
              <label htmlFor="calendar-exception-label">{t('settings.calendar.fields.label')}</label>
              <input id="calendar-exception-label"
                type="text"
                value={calendarExceptionForm.label}
                onChange={(e) => setCalendarExceptionForm({ ...calendarExceptionForm, label: e.target.value })}
                maxLength={200}
              />
            </div>
            {calendarExceptionForm.type === 'HALF_DAY' && (
              <div className="form-group">
                <label htmlFor="calendar-exception-end-hour">{t('settings.calendar.fields.endHour')}</label>
                <select id="calendar-exception-end-hour"
                  value={calendarExceptionForm.endHour}
                  onChange={(e) => setCalendarExceptionForm({ ...calendarExceptionForm, endHour: parseInt(e.target.value, 10) })}
                >
                  {START_HOURS.map((h) => (
                    <option key={h} value={h}>{formatHour(h)}</option>
                  ))}
                </select>
              </div>
            )}
            <div style={{ display: 'flex', gap: '10px' }}>
              <button type="submit" className="btn btn-primary" disabled={savingCalendarException || !calendarExceptionForm.date}>
                {savingCalendarException ? t('settings.calendar.saving') : t('common.save')}
              </button>
              <button type="button" className="btn btn-secondary" onClick={handleCancelCalendarException}>{t('common.cancel')}</button>
            </div>
          </form>
        </div>
      )}

      <div className="card">
        {calendarExceptions.length === 0 ? (
          <p style={{ color: 'var(--color-text-secondary)' }}>{t('settings.calendar.none')}</p>
        ) : (
          <table>
            <thead>
              <tr>
                <th>{t('settings.calendar.table.date')}</th>
                <th>{t('settings.calendar.table.type')}</th>
                <th>{t('settings.calendar.table.label')}</th>
                <th>{t('common.actions')}</th>
              </tr>
            </thead>
            <tbody>
              {calendarExceptions.map((exception) => (
                <tr key={exception.exceptionDate}>
                  <td>{exception.exceptionDate}{exception.type === 'HALF_DAY' && exception.endHour ? ` (${formatHour(exception.endHour)})` : ''}</td>
                  <td>{t(`settings.calendar.types.${exception.type}`)}</td>
                  <td>{exception.label || '-'}</td>
                  <td>
                    <button className="btn btn-primary" onClick={() => handleEditCalendarException(exception)} style={{ marginRight: '5px' }}>
                      {t('common.edit')}
                    </button>
                    <button className="btn btn-danger" onClick={() => handleDeleteCalendarException(exception.exceptionDate)}>
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

export default CalendarTab;
