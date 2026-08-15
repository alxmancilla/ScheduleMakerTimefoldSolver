import React, { useState, useEffect } from 'react';
import { useTranslation } from 'react-i18next';
import { getCourses, createCourse, updateCourse, deleteCourse } from '../api';
import WriteOnly from '../auth/WriteOnly';

// The `room` table enforces a CHECK constraint restricting `type` to exactly
// these values (see database/schema_block_scheduling*.sql). Keep in sync with
// that constraint and with CLAUDE.md's room type list.
const ROOM_TYPES = [
  'estándar',
  'laboratorio',
  'taller',
  'taller electromecánica',
  'taller electrónica',
  'centro de cómputo',
];

// The `course` table enforces `CHECK (semester BETWEEN 1 AND 12)`.
const SEMESTERS = Array.from({ length: 12 }, (_, i) => i + 1);

function Courses() {
  const { t } = useTranslation();
  const [courses, setCourses] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [fieldErrors, setFieldErrors] = useState({});
  const [editingCourse, setEditingCourse] = useState(null);
  const [showForm, setShowForm] = useState(false);

  useEffect(() => {
    loadCourses();
  }, []);

  const loadCourses = async () => {
    try {
      setLoading(true);
      const response = await getCourses();
      setCourses(response.data);
      setError(null);
    } catch (err) {
      setError(t('courses.loadFailedPrefix') + err.message);
    } finally {
      setLoading(false);
    }
  };

  const handleSubmit = async (e) => {
    e.preventDefault();
    setFieldErrors({});
    setError(null);
    const formData = new FormData(e.target);
    const course = {
      id: formData.get('id'),
      name: formData.get('name'),
      abbreviation: formData.get('abbreviation'),
      semester: parseInt(formData.get('semester')),
      component: formData.get('component'),
      roomRequirement: formData.get('roomRequirement'),
      requiredHoursPerWeek: parseInt(formData.get('requiredHoursPerWeek')),
      active: formData.get('active') === 'true',
    };

    try {
      if (editingCourse) {
        await updateCourse(editingCourse.id, course);
      } else {
        await createCourse(course);
      }
      setShowForm(false);
      setEditingCourse(null);
      loadCourses();
    } catch (err) {
      const data = err.response?.data;
      if (data?.errors) {
        setFieldErrors(data.errors);
      }
      setError(data?.message || t('courses.saveFailedPrefix') + err.message);
    }
  };

  const handleEdit = (course) => {
    setEditingCourse(course);
    setFieldErrors({});
    setError(null);
    setShowForm(true);
  };

  const handleDelete = async (id) => {
    if (!confirm(t('courses.confirmDelete'))) return;
    try {
      await deleteCourse(id);
      loadCourses();
    } catch (err) {
      setError(t('courses.deleteFailedPrefix') + err.message);
    }
  };

  const handleCancel = () => {
    setShowForm(false);
    setEditingCourse(null);
    setFieldErrors({});
    setError(null);
  };

  if (loading) return <div className="loading">{t('courses.loading')}</div>;

  return (
    <div>
      <div className="card">
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
          <h2>{t('courses.title')}</h2>
          <WriteOnly>
            <button
              className="btn btn-success"
              onClick={() => {
                setEditingCourse(null);
                setFieldErrors({});
                setError(null);
                setShowForm(true);
              }}
            >
              {t('courses.addCourse')}
            </button>
          </WriteOnly>
        </div>
      </div>

      {error && <div className="error">{error}</div>}

      {showForm && (
        <div className="card">
          <h3>{editingCourse ? t('courses.editCourse') : t('courses.newCourse')}</h3>
          <form onSubmit={handleSubmit}>
            <div className="form-group">
              <label>{t('courses.fields.id')}</label>
              <input
                type="text"
                name="id"
                defaultValue={editingCourse?.id || ''}
                maxLength={5}
                required
                disabled={!!editingCourse}
              />
              {fieldErrors.id && <div className="error">{fieldErrors.id}</div>}
            </div>
            <div className="form-group">
              <label>{t('courses.fields.name')}</label>
              <input type="text" name="name" defaultValue={editingCourse?.name || ''} required />
              {fieldErrors.name && <div className="error">{fieldErrors.name}</div>}
            </div>
            <div className="form-group">
              <label>{t('courses.fields.abbreviation')}</label>
              <input
                type="text"
                name="abbreviation"
                defaultValue={editingCourse?.abbreviation || ''}
                maxLength={100}
                required
              />
              {fieldErrors.abbreviation && <div className="error">{fieldErrors.abbreviation}</div>}
            </div>
            <div className="form-group">
              <label>{t('courses.fields.semester')}</label>
              <select name="semester" defaultValue={editingCourse?.semester || 1}>
                {SEMESTERS.map((s) => (
                  <option key={s} value={s}>{s}</option>
                ))}
              </select>
              {fieldErrors.semester && <div className="error">{fieldErrors.semester}</div>}
            </div>
            <div className="form-group">
              <label>{t('courses.fields.component')}</label>
              <input
                type="text"
                name="component"
                defaultValue={editingCourse?.component || ''}
                maxLength={20}
                required
                placeholder={t('courses.componentPlaceholder')}
              />
              {fieldErrors.component && <div className="error">{fieldErrors.component}</div>}
            </div>
            <div className="form-group">
              <label>{t('courses.fields.roomRequirement')}</label>
              <select name="roomRequirement" defaultValue={editingCourse?.roomRequirement || 'estándar'}>
                {ROOM_TYPES.map((type) => (
                  <option key={type} value={type}>{type}</option>
                ))}
              </select>
            </div>
            <div className="form-group">
              <label>{t('courses.fields.requiredHoursPerWeek')}</label>
              <input type="number" name="requiredHoursPerWeek" defaultValue={editingCourse?.requiredHoursPerWeek || 1} required />
            </div>
            <div className="form-group">
              <label>{t('courses.fields.active')}</label>
              <select name="active" defaultValue={editingCourse?.active?.toString() || 'true'}>
                <option value="true">{t('common.yes')}</option>
                <option value="false">{t('common.no')}</option>
              </select>
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
              <th>{t('courses.table.id')}</th>
              <th>{t('courses.table.name')}</th>
              <th>{t('courses.table.semester')}</th>
              <th>{t('courses.table.roomReq')}</th>
              <th>{t('courses.table.hoursWeek')}</th>
              <th>{t('courses.table.active')}</th>
              <th>{t('common.actions')}</th>
            </tr>
          </thead>
          <tbody>
            {courses.map(course => (
              <tr key={course.id}>
                <td>{course.id}</td>
                <td>{course.name}</td>
                <td>{course.semester}</td>
                <td>{course.roomRequirement}</td>
                <td>{course.requiredHoursPerWeek}</td>
                <td>{course.active ? '✓' : '✗'}</td>
                <td>
                  <WriteOnly>
                    <button className="btn btn-primary" onClick={() => handleEdit(course)} style={{ marginRight: '5px' }}>
                      {t('common.edit')}
                    </button>
                    <button className="btn btn-danger" onClick={() => handleDelete(course.id)}>
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

export default Courses;
