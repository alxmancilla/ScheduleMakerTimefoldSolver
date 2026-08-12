import React, { useState, useEffect } from 'react';
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
      setError('Failed to load courses: ' + err.message);
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
      setError(data?.message || 'Failed to save course: ' + err.message);
    }
  };

  const handleEdit = (course) => {
    setEditingCourse(course);
    setFieldErrors({});
    setError(null);
    setShowForm(true);
  };

  const handleDelete = async (id) => {
    if (!confirm('Are you sure you want to delete this course?')) return;
    try {
      await deleteCourse(id);
      loadCourses();
    } catch (err) {
      setError('Failed to delete course: ' + err.message);
    }
  };

  const handleCancel = () => {
    setShowForm(false);
    setEditingCourse(null);
    setFieldErrors({});
    setError(null);
  };

  if (loading) return <div className="loading">Loading courses...</div>;

  return (
    <div>
      <div className="card">
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
          <h2>Courses</h2>
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
              + Add Course
            </button>
          </WriteOnly>
        </div>
      </div>

      {error && <div className="error">{error}</div>}

      {showForm && (
        <div className="card">
          <h3>{editingCourse ? 'Edit Course' : 'New Course'}</h3>
          <form onSubmit={handleSubmit}>
            <div className="form-group">
              <label>ID:</label>
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
              <label>Name:</label>
              <input type="text" name="name" defaultValue={editingCourse?.name || ''} required />
              {fieldErrors.name && <div className="error">{fieldErrors.name}</div>}
            </div>
            <div className="form-group">
              <label>Abbreviation:</label>
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
              <label>Semester:</label>
              <select name="semester" defaultValue={editingCourse?.semester || 1}>
                {SEMESTERS.map((s) => (
                  <option key={s} value={s}>{s}</option>
                ))}
              </select>
              {fieldErrors.semester && <div className="error">{fieldErrors.semester}</div>}
            </div>
            <div className="form-group">
              <label>Component:</label>
              <input
                type="text"
                name="component"
                defaultValue={editingCourse?.component || ''}
                maxLength={20}
                required
                placeholder="e.g. BASICAS, TPROG, TEM"
              />
              {fieldErrors.component && <div className="error">{fieldErrors.component}</div>}
            </div>
            <div className="form-group">
              <label>Room Requirement:</label>
              <select name="roomRequirement" defaultValue={editingCourse?.roomRequirement || 'estándar'}>
                {ROOM_TYPES.map((type) => (
                  <option key={type} value={type}>{type}</option>
                ))}
              </select>
            </div>
            <div className="form-group">
              <label>Required Hours Per Week:</label>
              <input type="number" name="requiredHoursPerWeek" defaultValue={editingCourse?.requiredHoursPerWeek || 1} required />
            </div>
            <div className="form-group">
              <label>Active:</label>
              <select name="active" defaultValue={editingCourse?.active?.toString() || 'true'}>
                <option value="true">Yes</option>
                <option value="false">No</option>
              </select>
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
              <th>Semester</th>
              <th>Room Req.</th>
              <th>Hours/Week</th>
              <th>Active</th>
              <th>Actions</th>
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
                      Edit
                    </button>
                    <button className="btn btn-danger" onClick={() => handleDelete(course.id)}>
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

export default Courses;

