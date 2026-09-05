import React, { useState, useEffect } from 'react';
import { useTranslation } from 'react-i18next';
import { getTimeslots, createTimeslot, updateTimeslot, deleteTimeslot } from '../../api';
import { useToast } from '../../ui/ToastContext';
import { useConfirm } from '../../ui/ConfirmContext';
import { formatHour } from '../../constants';
import { START_HOURS } from './constants';

const DAY_LABELS = [
  { value: 1, key: 'mon' },
  { value: 2, key: 'tue' },
  { value: 3, key: 'wed' },
  { value: 4, key: 'thu' },
  { value: 5, key: 'fri' },
];
const LENGTHS = [1, 2, 3, 4];
const EMPTY_FORM = { dayOfWeek: 1, startHour: 7, lengthHours: 1 };

function TimeslotsTab({ hidden }) {
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

  useEffect(() => {
    loadTimeslots();
  }, []);

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

  if (loading) {
    return (
      <div role="tabpanel" id="settings-panel-timeslots" aria-labelledby="settings-tab-timeslots" hidden={hidden}>
        <div className="loading">{t('settings.loading')}</div>
      </div>
    );
  }

  return (
    <div role="tabpanel" id="settings-panel-timeslots" aria-labelledby="settings-tab-timeslots" hidden={hidden}>
      <div className="card">
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
          <h3>{t('settings.timeslots.title')}</h3>
          <button className="btn btn-success" onClick={handleAdd}>
            {t('settings.timeslots.addTimeslot')}
          </button>
        </div>
      </div>

      {error && <div className="error" role="alert">{error}</div>}

      {showForm && (
        <div className="card">
          <h3>{editingTimeslot ? t('settings.timeslots.editTimeslot') : t('settings.timeslots.newTimeslot')}</h3>
          <form onSubmit={handleSubmit}>
            <div className="form-group">
              <label htmlFor="timeslot-day">{t('settings.timeslots.fields.day')}</label>
              <select id="timeslot-day" name="dayOfWeek" value={form.dayOfWeek} onChange={handleField}>
                {DAY_LABELS.map((day) => (
                  <option key={day.value} value={day.value}>{t(`common.days.${day.key}`)}</option>
                ))}
              </select>
              {fieldErrors.dayOfWeek && <div className="error" role="alert">{fieldErrors.dayOfWeek}</div>}
            </div>
            <div className="form-group">
              <label htmlFor="timeslot-start-hour">{t('settings.timeslots.fields.startHour')}</label>
              <select id="timeslot-start-hour" name="startHour" value={form.startHour} onChange={handleField}>
                {START_HOURS.map((h) => (
                  <option key={h} value={h}>{formatHour(h)}</option>
                ))}
              </select>
              {fieldErrors.startHour && <div className="error" role="alert">{fieldErrors.startHour}</div>}
            </div>
            <div className="form-group">
              <label htmlFor="timeslot-length">{t('settings.timeslots.fields.length')}</label>
              <select id="timeslot-length" name="lengthHours" value={form.lengthHours} onChange={handleField}>
                {LENGTHS.map((l) => (
                  <option key={l} value={l}>{l}</option>
                ))}
              </select>
              {fieldErrors.lengthHours && <div className="error" role="alert">{fieldErrors.lengthHours}</div>}
            </div>
            <div className="form-group">
              {/*
                * "Ends at" captions a computed read-only value, not a form
                * control, so a <label> would have nothing to point at -
                * aria-labelledby on the value gives it the same accessible
                * name without claiming to be a field label.
                */}
              <span className="form-group-label" id="timeslot-ends-at-label">{t('settings.timeslots.fields.endsAt')}</span>
              <span
                aria-labelledby="timeslot-ends-at-label"
                style={{ color: exceedsDayBounds ? 'var(--color-danger-dark)' : undefined }}
              >
                {formatHour(endHour)}{exceedsDayBounds ? t('settings.timeslots.exceedsDayBounds') : ''}
              </span>
              {fieldErrors.withinDayBounds && <div className="error" role="alert">{fieldErrors.withinDayBounds}</div>}
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
          <p style={{ color: 'var(--color-text-secondary)' }}>{t('settings.timeslots.none')}</p>
        </div>
      ) : (
        DAY_LABELS.map((day) => {
          const dayTimeslots = timeslots
            .filter((ts) => ts.dayOfWeek === day.value)
            .sort((a, b) => a.startHour - b.startHour);
          if (dayTimeslots.length === 0) return null;
          return (
            <div className="card" key={day.value}>
              <h4 style={{ marginBottom: '10px', color: 'var(--color-ink)' }}>{t(`common.days.${day.key}`)}</h4>
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
                      <td>{formatHour(timeslot.startHour)}</td>
                      <td>{formatHour(timeslot.startHour + timeslot.lengthHours)}</td>
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
  );
}

export default TimeslotsTab;
