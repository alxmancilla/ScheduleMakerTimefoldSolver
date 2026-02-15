import React, { useState, useEffect } from 'react';
import { getCourses, createCourse, updateCourse, deleteCourse } from '../api';

function Courses() {
  const [courses, setCourses] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
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
      setError('Failed to save course: ' + err.message);
    }
  };

  const handleEdit = (course) => {
    setEditingCourse(course);
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
  };

  if (loading) return <div className="loading">Loading courses...</div>;

  return (
    <div>
      <div className="card">
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
          <h2>Courses</h2>
          <button className="btn btn-success" onClick={() => setShowForm(true)}>
            + Add Course
          </button>
        </div>
      </div>

      {error && <div className="error">{error}</div>}

      {showForm && (
        <div className="card">
          <h3>{editingCourse ? 'Edit Course' : 'New Course'}</h3>
          <form onSubmit={handleSubmit}>
            <div className="form-group">
              <label>ID:</label>
              <input type="text" name="id" defaultValue={editingCourse?.id || ''} required disabled={!!editingCourse} />
            </div>
            <div className="form-group">
              <label>Name:</label>
              <input type="text" name="name" defaultValue={editingCourse?.name || ''} required />
            </div>
            <div className="form-group">
              <label>Abbreviation:</label>
              <input type="text" name="abbreviation" defaultValue={editingCourse?.abbreviation || ''} />
            </div>
            <div className="form-group">
              <label>Semester:</label>
              <input type="number" name="semester" defaultValue={editingCourse?.semester || 1} required />
            </div>
            <div className="form-group">
              <label>Component:</label>
              <input type="text" name="component" defaultValue={editingCourse?.component || ''} />
            </div>
            <div className="form-group">
              <label>Room Requirement:</label>
              <input type="text" name="roomRequirement" defaultValue={editingCourse?.roomRequirement || 'estándar'} />
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
                  <button className="btn btn-primary" onClick={() => handleEdit(course)} style={{ marginRight: '5px' }}>
                    Edit
                  </button>
                  <button className="btn btn-danger" onClick={() => handleDelete(course.id)}>
                    Delete
                  </button>
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

