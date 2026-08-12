import React, { useState, useEffect } from 'react';
import { getTimeslots, createTimeslot, updateTimeslot, deleteTimeslot } from '../api';

const DAY_LABELS = [
  { value: 1, label: 'Mon' },
  { value: 2, label: 'Tue' },
  { value: 3, label: 'Wed' },
  { value: 4, label: 'Thu' },
  { value: 5, label: 'Fri' },
];
const dayLabel = (value) => DAY_LABELS.find((d) => d.value === value)?.label || value;
const START_HOURS = [7, 8, 9, 10, 11, 12, 13, 14, 15];
const LENGTHS = [1, 2, 3, 4];
const EMPTY_FORM = { dayOfWeek: 1, startHour: 7, lengthHours: 1 };

function Settings() {
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
      setError('Failed to load timeslots: ' + err.message);
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
    } catch (err) {
      const data = err.response?.data;
      if (data?.errors) {
        setFieldErrors(data.errors);
      }
      setError(data?.message || 'Failed to save timeslot: ' + err.message);
    }
  };

  const handleDelete = async (timeslot) => {
    if (!confirm(`Delete ${dayLabel(timeslot.dayOfWeek)} ${timeslot.startHour}:00-${timeslot.startHour + timeslot.lengthHours}:00?`)) return;
    try {
      await deleteTimeslot(timeslot.id);
      loadTimeslots();
    } catch (err) {
      setError(err.response?.data?.message || 'Failed to delete timeslot: ' + err.message);
    }
  };

  const endHour = form.startHour + form.lengthHours;
  const exceedsDayBounds = endHour > 15;

  if (loading) return <div className="loading">Loading settings...</div>;

  return (
    <div>
      <div className="card">
        <h2>Settings</h2>
        <p style={{ color: '#7f8c8d', fontSize: '14px' }}>Admin-only configuration for values used across the scheduler.</p>
      </div>

      <div className="card">
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
          <h3>Timeslots</h3>
          <button className="btn btn-success" onClick={handleAdd}>
            + Add Timeslot
          </button>
        </div>
      </div>

      {error && <div className="error">{error}</div>}

      {showForm && (
        <div className="card">
          <h3>{editingTimeslot ? 'Edit Timeslot' : 'New Timeslot'}</h3>
          <form onSubmit={handleSubmit}>
            <div className="form-group">
              <label>Day:</label>
              <select name="dayOfWeek" value={form.dayOfWeek} onChange={handleField}>
                {DAY_LABELS.map((day) => (
                  <option key={day.value} value={day.value}>{day.label}</option>
                ))}
              </select>
              {fieldErrors.dayOfWeek && <div className="error">{fieldErrors.dayOfWeek}</div>}
            </div>
            <div className="form-group">
              <label>Start Hour:</label>
              <select name="startHour" value={form.startHour} onChange={handleField}>
                {START_HOURS.map((h) => (
                  <option key={h} value={h}>{h}:00</option>
                ))}
              </select>
              {fieldErrors.startHour && <div className="error">{fieldErrors.startHour}</div>}
            </div>
            <div className="form-group">
              <label>Length (hours):</label>
              <select name="lengthHours" value={form.lengthHours} onChange={handleField}>
                {LENGTHS.map((l) => (
                  <option key={l} value={l}>{l}</option>
                ))}
              </select>
              {fieldErrors.lengthHours && <div className="error">{fieldErrors.lengthHours}</div>}
            </div>
            <div className="form-group">
              <label>Ends at:</label>
              <span style={{ color: exceedsDayBounds ? '#c0392b' : undefined }}>
                {endHour}:00{exceedsDayBounds ? ' — exceeds the school day (max 15:00)' : ''}
              </span>
              {fieldErrors.withinDayBounds && <div className="error">{fieldErrors.withinDayBounds}</div>}
            </div>
            <div style={{ display: 'flex', gap: '10px' }}>
              <button type="submit" className="btn btn-primary" disabled={exceedsDayBounds}>Save</button>
              <button type="button" className="btn btn-secondary" onClick={handleCancel}>Cancel</button>
            </div>
          </form>
        </div>
      )}

      <div className="card">
        <table>
          <thead>
            <tr>
              <th>Day</th>
              <th>Start</th>
              <th>End</th>
              <th>Length (h)</th>
              <th>Actions</th>
            </tr>
          </thead>
          <tbody>
            {timeslots.map((timeslot) => (
              <tr key={timeslot.id}>
                <td>{dayLabel(timeslot.dayOfWeek)}</td>
                <td>{timeslot.startHour}:00</td>
                <td>{timeslot.startHour + timeslot.lengthHours}:00</td>
                <td>{timeslot.lengthHours}</td>
                <td>
                  <button className="btn btn-primary" onClick={() => handleEdit(timeslot)} style={{ marginRight: '5px' }}>
                    Edit
                  </button>
                  <button className="btn btn-danger" onClick={() => handleDelete(timeslot)}>
                    Delete
                  </button>
                </td>
              </tr>
            ))}
            {timeslots.length === 0 && (
              <tr>
                <td colSpan={5} style={{ color: '#7f8c8d' }}>No timeslots defined</td>
              </tr>
            )}
          </tbody>
        </table>
      </div>
    </div>
  );
}

export default Settings;
