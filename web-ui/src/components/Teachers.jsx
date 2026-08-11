import React, { useState, useEffect } from 'react';
import { getTeachers, createTeacher, updateTeacher, deleteTeacher } from '../api';
import WriteOnly from '../auth/WriteOnly';

const DAY_LABELS = [
  { value: 1, label: 'Mon' },
  { value: 2, label: 'Tue' },
  { value: 3, label: 'Wed' },
  { value: 4, label: 'Thu' },
  { value: 5, label: 'Fri' },
];
const AVAILABILITY_HOURS = [7, 8, 9, 10, 11, 12, 13, 14, 15];
const EMPTY_FORM = { id: '', name: '', lastName: '', maxHoursPerWeek: 40 };

function Teachers() {
  const [teachers, setTeachers] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [fieldErrors, setFieldErrors] = useState({});
  const [editingTeacher, setEditingTeacher] = useState(null);
  const [showForm, setShowForm] = useState(false);
  const [form, setForm] = useState(EMPTY_FORM);
  const [qualifications, setQualifications] = useState([]);
  const [qualInput, setQualInput] = useState('');
  const [availability, setAvailability] = useState(new Set());

  useEffect(() => {
    loadTeachers();
  }, []);

  const loadTeachers = async () => {
    try {
      setLoading(true);
      const response = await getTeachers();
      setTeachers(response.data);
      setError(null);
    } catch (err) {
      setError('Failed to load teachers: ' + err.message);
    } finally {
      setLoading(false);
    }
  };

  const handleField = (e) => {
    setForm({ ...form, [e.target.name]: e.target.value });
  };

  const availabilityKey = (day, hour) => `${day}-${hour}`;

  const addQualification = () => {
    const value = qualInput.trim();
    if (value && !qualifications.includes(value)) {
      setQualifications([...qualifications, value]);
    }
    setQualInput('');
  };

  const removeQualification = (value) => {
    setQualifications(qualifications.filter((q) => q !== value));
  };

  const toggleAvailability = (day, hour) => {
    const key = availabilityKey(day, hour);
    const next = new Set(availability);
    if (next.has(key)) {
      next.delete(key);
    } else {
      next.add(key);
    }
    setAvailability(next);
  };

  const resetForm = () => {
    setForm(EMPTY_FORM);
    setQualifications([]);
    setQualInput('');
    setAvailability(new Set());
    setFieldErrors({});
  };

  const handleSubmit = async (e) => {
    e.preventDefault();
    setFieldErrors({});
    setError(null);

    const teacher = {
      id: form.id,
      name: form.name,
      lastName: form.lastName,
      maxHoursPerWeek: parseInt(form.maxHoursPerWeek, 10),
      qualifications,
      availability: Array.from(availability).map((key) => {
        const [dayOfWeek, hour] = key.split('-').map(Number);
        return { dayOfWeek, hour };
      }),
    };

    try {
      if (editingTeacher) {
        await updateTeacher(editingTeacher.id, teacher);
      } else {
        await createTeacher(teacher);
      }
      handleCancel();
      loadTeachers();
    } catch (err) {
      const data = err.response?.data;
      if (data?.errors) {
        setFieldErrors(data.errors);
      }
      setError(data?.message || 'Failed to save teacher: ' + err.message);
    }
  };

  const handleAdd = () => {
    setEditingTeacher(null);
    resetForm();
    setError(null);
    setShowForm(true);
  };

  const handleEdit = (teacher) => {
    setEditingTeacher(teacher);
    setForm({
      id: teacher.id,
      name: teacher.name || '',
      lastName: teacher.lastName || '',
      maxHoursPerWeek: teacher.maxHoursPerWeek ?? 40,
    });
    setQualifications((teacher.qualifications || []).map((q) => q.qualification));
    setQualInput('');
    setAvailability(new Set(
      (teacher.availability || []).map((slot) => availabilityKey(slot.dayOfWeek, slot.hour))
    ));
    setFieldErrors({});
    setError(null);
    setShowForm(true);
  };

  const handleDelete = async (id) => {
    if (!confirm('Are you sure you want to delete this teacher?')) return;
    try {
      await deleteTeacher(id);
      loadTeachers();
    } catch (err) {
      setError('Failed to delete teacher: ' + err.message);
    }
  };

  const handleCancel = () => {
    setShowForm(false);
    setEditingTeacher(null);
    resetForm();
  };

  if (loading) return <div className="loading">Loading teachers...</div>;

  return (
    <div>
      <div className="card">
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
          <h2>Teachers</h2>
          <WriteOnly>
            <button className="btn btn-success" onClick={handleAdd}>
              + Add Teacher
            </button>
          </WriteOnly>
        </div>
      </div>

      {error && <div className="error">{error}</div>}

      {showForm && (
        <div className="card">
          <h3>{editingTeacher ? 'Edit Teacher' : 'New Teacher'}</h3>
          <form onSubmit={handleSubmit}>
            <div className="form-group">
              <label>ID:</label>
              <input
                type="text"
                name="id"
                value={form.id}
                onChange={handleField}
                required
                disabled={!!editingTeacher}
              />
              {fieldErrors.id && <div className="error">{fieldErrors.id}</div>}
            </div>
            <div className="form-group">
              <label>Name:</label>
              <input type="text" name="name" value={form.name} onChange={handleField} required />
              {fieldErrors.name && <div className="error">{fieldErrors.name}</div>}
            </div>
            <div className="form-group">
              <label>Last Name:</label>
              <input type="text" name="lastName" value={form.lastName} onChange={handleField} required />
              {fieldErrors.lastName && <div className="error">{fieldErrors.lastName}</div>}
            </div>
            <div className="form-group">
              <label>Max Hours Per Week:</label>
              <input
                type="number"
                name="maxHoursPerWeek"
                value={form.maxHoursPerWeek}
                onChange={handleField}
                required
              />
              {fieldErrors.maxHoursPerWeek && <div className="error">{fieldErrors.maxHoursPerWeek}</div>}
            </div>

            <div className="form-group">
              <label>Qualifications:</label>
              <div style={{ display: 'flex', gap: '10px', marginBottom: '8px' }}>
                <input
                  type="text"
                  value={qualInput}
                  onChange={(e) => setQualInput(e.target.value)}
                  onKeyDown={(e) => {
                    if (e.key === 'Enter') {
                      e.preventDefault();
                      addQualification();
                    }
                  }}
                  placeholder="Add a qualification and press Enter"
                />
                <button type="button" className="btn btn-secondary" onClick={addQualification}>
                  Add
                </button>
              </div>
              <div style={{ display: 'flex', flexWrap: 'wrap', gap: '6px' }}>
                {qualifications.map((q) => (
                  <span
                    key={q}
                    style={{
                      display: 'inline-flex',
                      alignItems: 'center',
                      gap: '6px',
                      background: '#e8f4f8',
                      border: '1px solid #b3d9e6',
                      borderRadius: '12px',
                      padding: '2px 10px',
                      fontSize: '13px',
                    }}
                  >
                    {q}
                    <button
                      type="button"
                      onClick={() => removeQualification(q)}
                      style={{
                        border: 'none',
                        background: 'transparent',
                        cursor: 'pointer',
                        fontWeight: 'bold',
                      }}
                    >
                      ×
                    </button>
                  </span>
                ))}
                {qualifications.length === 0 && (
                  <span style={{ color: '#7f8c8d', fontSize: '13px' }}>No qualifications added</span>
                )}
              </div>
            </div>

            <div className="form-group">
              <label>Availability:</label>
              <table style={{ borderCollapse: 'collapse' }}>
                <thead>
                  <tr>
                    <th style={{ padding: '4px 8px' }}></th>
                    {DAY_LABELS.map((day) => (
                      <th key={day.value} style={{ padding: '4px 8px', textAlign: 'center' }}>{day.label}</th>
                    ))}
                  </tr>
                </thead>
                <tbody>
                  {AVAILABILITY_HOURS.map((hour) => (
                    <tr key={hour}>
                      <td style={{ padding: '4px 8px', fontWeight: 'bold' }}>{hour}:00</td>
                      {DAY_LABELS.map((day) => {
                        const active = availability.has(availabilityKey(day.value, hour));
                        return (
                          <td key={day.value} style={{ padding: '2px', textAlign: 'center' }}>
                            <button
                              type="button"
                              onClick={() => toggleAvailability(day.value, hour)}
                              style={{
                                width: '32px',
                                height: '28px',
                                border: '1px solid ' + (active ? '#2e7d32' : '#ccc'),
                                background: active ? '#a5d6a7' : '#fff',
                                borderRadius: '4px',
                                cursor: 'pointer',
                              }}
                              aria-label={`${day.label} ${hour}:00 ${active ? 'available' : 'unavailable'}`}
                            >
                              {active ? '✓' : ''}
                            </button>
                          </td>
                        );
                      })}
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>

            <div style={{ display: 'flex', gap: '10px' }}>
              <button type="submit" className="btn btn-primary">Save</button>
              <button type="button" className="btn btn-secondary" onClick={handleCancel}>Cancel</button>
            </div>
          </form>
        </div>
      )}

      <div className="card">
        <table>
          <thead>
            <tr>
              <th>ID</th>
              <th>Name</th>
              <th>Last Name</th>
              <th>Max Hours/Week</th>
              <th>Qualifications</th>
              <th>Actions</th>
            </tr>
          </thead>
          <tbody>
            {teachers.map(teacher => (
              <tr key={teacher.id}>
                <td>{teacher.id}</td>
                <td>{teacher.name}</td>
                <td>{teacher.lastName}</td>
                <td>{teacher.maxHoursPerWeek}</td>
                <td>{(teacher.qualifications || []).map((q) => q.qualification).join(', ')}</td>
                <td>
                  <WriteOnly>
                    <button className="btn btn-primary" onClick={() => handleEdit(teacher)} style={{ marginRight: '5px' }}>
                      Edit
                    </button>
                    <button className="btn btn-danger" onClick={() => handleDelete(teacher.id)}>
                      Delete
                    </button>
                  </WriteOnly>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  );
}

export default Teachers;

