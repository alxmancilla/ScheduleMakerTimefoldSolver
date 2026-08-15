import React, { useState, useEffect, useRef } from 'react';
import { useTranslation } from 'react-i18next';
import { getTeachers, createTeacher, updateTeacher, deleteTeacher, getCourses } from '../api';
import WriteOnly from '../auth/WriteOnly';

const MIN_CHARS_FOR_SUGGESTIONS = 2;
const escapeRegExp = (value) => value.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');

const DAY_LABELS = [
  { value: 1, dayKey: 'mon' },
  { value: 2, dayKey: 'tue' },
  { value: 3, dayKey: 'wed' },
  { value: 4, dayKey: 'thu' },
  { value: 5, dayKey: 'fri' },
];
const AVAILABILITY_HOURS = [7, 8, 9, 10, 11, 12, 13, 14, 15];
const EMPTY_FORM = { id: '', name: '', lastName: '', maxHoursPerWeek: 40 };

function Teachers() {
  const { t } = useTranslation();
  const [teachers, setTeachers] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [fieldErrors, setFieldErrors] = useState({});
  const [editingTeacher, setEditingTeacher] = useState(null);
  const [showForm, setShowForm] = useState(false);
  const [form, setForm] = useState(EMPTY_FORM);
  const [qualifications, setQualifications] = useState([]);
  const [qualInput, setQualInput] = useState('');
  const [courseNames, setCourseNames] = useState([]);
  const [showSuggestions, setShowSuggestions] = useState(false);
  const qualBoxRef = useRef(null);
  const [availability, setAvailability] = useState(new Set());

  useEffect(() => {
    loadTeachers();
    loadCourseNames();
  }, []);

  useEffect(() => {
    const handleClickOutside = (e) => {
      if (qualBoxRef.current && !qualBoxRef.current.contains(e.target)) {
        setShowSuggestions(false);
      }
    };
    document.addEventListener('mousedown', handleClickOutside);
    return () => document.removeEventListener('mousedown', handleClickOutside);
  }, []);

  const loadCourseNames = async () => {
    try {
      const response = await getCourses();
      setCourseNames(response.data.map((c) => c.name).filter(Boolean));
    } catch (err) {
      // Non-critical: autocomplete just won't have suggestions.
    }
  };

  const loadTeachers = async () => {
    try {
      setLoading(true);
      const response = await getTeachers();
      setTeachers(response.data);
      setError(null);
    } catch (err) {
      setError(t('teachers.loadFailedPrefix') + err.message);
    } finally {
      setLoading(false);
    }
  };

  const handleField = (e) => {
    setForm({ ...form, [e.target.name]: e.target.value });
  };

  const availabilityKey = (day, hour) => `${day}-${hour}`;

  const addQualification = (rawValue) => {
    const value = (rawValue ?? qualInput).trim();
    if (value && !qualifications.includes(value)) {
      setQualifications([...qualifications, value]);
    }
    setQualInput('');
    setShowSuggestions(false);
  };

  const qualSuggestions = (() => {
    const query = qualInput.trim();
    if (query.length < MIN_CHARS_FOR_SUGGESTIONS) return [];
    const pattern = new RegExp(escapeRegExp(query), 'i');
    return courseNames.filter((name) => pattern.test(name) && !qualifications.includes(name)).slice(0, 8);
  })();

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

  const checkAllAvailability = () => {
    const all = new Set();
    DAY_LABELS.forEach((day) => {
      AVAILABILITY_HOURS.forEach((hour) => {
        all.add(availabilityKey(day.value, hour));
      });
    });
    setAvailability(all);
  };

  const clearAllAvailability = () => {
    setAvailability(new Set());
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
      setError(data?.message || t('teachers.saveFailedPrefix') + err.message);
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
    if (!confirm(t('teachers.confirmDelete'))) return;
    try {
      await deleteTeacher(id);
      loadTeachers();
    } catch (err) {
      setError(t('teachers.deleteFailedPrefix') + err.message);
    }
  };

  const handleCancel = () => {
    setShowForm(false);
    setEditingTeacher(null);
    resetForm();
  };

  if (loading) return <div className="loading">{t('teachers.loading')}</div>;

  return (
    <div>
      <div className="card">
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
          <h2>{t('teachers.title')}</h2>
          <WriteOnly>
            <button className="btn btn-success" onClick={handleAdd}>
              {t('teachers.addTeacher')}
            </button>
          </WriteOnly>
        </div>
      </div>

      {error && <div className="error">{error}</div>}

      {showForm && (
        <div className="card">
          <h3>{editingTeacher ? t('teachers.editTeacher') : t('teachers.newTeacher')}</h3>
          <form onSubmit={handleSubmit}>
            <div className="form-group">
              <label>{t('teachers.fields.id')}</label>
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
              <label>{t('teachers.fields.name')}</label>
              <input type="text" name="name" value={form.name} onChange={handleField} required />
              {fieldErrors.name && <div className="error">{fieldErrors.name}</div>}
            </div>
            <div className="form-group">
              <label>{t('teachers.fields.lastName')}</label>
              <input type="text" name="lastName" value={form.lastName} onChange={handleField} required />
              {fieldErrors.lastName && <div className="error">{fieldErrors.lastName}</div>}
            </div>
            <div className="form-group">
              <label>{t('teachers.fields.maxHoursPerWeek')}</label>
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
              <label>{t('teachers.fields.qualifications')}</label>
              <div ref={qualBoxRef} style={{ position: 'relative', marginBottom: '8px' }}>
                <div style={{ display: 'flex', gap: '10px' }}>
                  <input
                    type="text"
                    value={qualInput}
                    onChange={(e) => {
                      setQualInput(e.target.value);
                      setShowSuggestions(true);
                    }}
                    onFocus={() => setShowSuggestions(true)}
                    onKeyDown={(e) => {
                      if (e.key === 'Enter') {
                        e.preventDefault();
                        addQualification();
                      } else if (e.key === 'Escape') {
                        setShowSuggestions(false);
                      }
                    }}
                    placeholder={t('teachers.qualificationsPlaceholder')}
                    autoComplete="off"
                  />
                  <button type="button" className="btn btn-secondary" onClick={() => addQualification()}>
                    {t('teachers.add')}
                  </button>
                </div>
                {showSuggestions && qualSuggestions.length > 0 && (
                  <ul
                    style={{
                      position: 'absolute',
                      top: '100%',
                      left: 0,
                      right: '90px',
                      marginTop: '2px',
                      background: '#fff',
                      border: '1px solid #ccc',
                      borderRadius: '4px',
                      maxHeight: '200px',
                      overflowY: 'auto',
                      listStyle: 'none',
                      padding: '4px 0',
                      zIndex: 10,
                      boxShadow: '0 2px 6px rgba(0,0,0,0.15)',
                    }}
                  >
                    {qualSuggestions.map((name) => (
                      <li
                        key={name}
                        onMouseDown={(e) => {
                          e.preventDefault();
                          addQualification(name);
                        }}
                        style={{ padding: '6px 10px', cursor: 'pointer' }}
                        onMouseEnter={(e) => (e.currentTarget.style.background = '#e8f4f8')}
                        onMouseLeave={(e) => (e.currentTarget.style.background = 'transparent')}
                      >
                        {name}
                      </li>
                    ))}
                  </ul>
                )}
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
                  <span style={{ color: '#7f8c8d', fontSize: '13px' }}>{t('teachers.noQualifications')}</span>
                )}
              </div>
            </div>

            <div className="form-group">
              <label>{t('teachers.fields.availability')}</label>
              <div style={{ display: 'flex', gap: '10px', marginBottom: '8px' }}>
                <button type="button" className="btn btn-secondary" onClick={checkAllAvailability}>
                  {t('teachers.checkAll')}
                </button>
                <button type="button" className="btn btn-secondary" onClick={clearAllAvailability}>
                  {t('teachers.uncheckAll')}
                </button>
              </div>
              <table style={{ borderCollapse: 'collapse' }}>
                <thead>
                  <tr>
                    <th style={{ padding: '4px 8px' }}></th>
                    {DAY_LABELS.map((day) => (
                      <th key={day.value} style={{ padding: '4px 8px', textAlign: 'center' }}>{t(`teachers.days.${day.dayKey}`)}</th>
                    ))}
                  </tr>
                </thead>
                <tbody>
                  {AVAILABILITY_HOURS.map((hour) => (
                    <tr key={hour}>
                      <td style={{ padding: '4px 8px', fontWeight: 'bold' }}>{hour}:00</td>
                      {DAY_LABELS.map((day) => {
                        const active = availability.has(availabilityKey(day.value, hour));
                        const dayLabel = t(`teachers.days.${day.dayKey}`);
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
                              aria-label={`${dayLabel} ${hour}:00 ${active ? t('teachers.available') : t('teachers.unavailable')}`}
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
              <button type="submit" className="btn btn-primary">{t('common.save')}</button>
              <button type="button" className="btn btn-secondary" onClick={handleCancel}>{t('common.cancel')}</button>
            </div>
          </form>
        </div>
      )}

      <div className="card">
        <table>
          <thead>
            <tr>
              <th>{t('teachers.table.id')}</th>
              <th>{t('teachers.table.name')}</th>
              <th>{t('teachers.table.lastName')}</th>
              <th>{t('teachers.table.maxHoursWeek')}</th>
              <th>{t('teachers.table.qualifications')}</th>
              <th>{t('teachers.table.actions')}</th>
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
                      {t('common.edit')}
                    </button>
                    <button className="btn btn-danger" onClick={() => handleDelete(teacher.id)}>
                      {t('common.delete')}
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

