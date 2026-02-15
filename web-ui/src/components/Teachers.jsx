import React, { useState, useEffect } from 'react';
import { getTeachers, createTeacher, updateTeacher, deleteTeacher } from '../api';

function Teachers() {
  const [teachers, setTeachers] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [editingTeacher, setEditingTeacher] = useState(null);
  const [showForm, setShowForm] = useState(false);

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

  const handleSubmit = async (e) => {
    e.preventDefault();
    const formData = new FormData(e.target);
    const teacher = {
      id: formData.get('id'),
      name: formData.get('name'),
      lastName: formData.get('lastName'),
      maxHoursPerWeek: parseInt(formData.get('maxHoursPerWeek')),
    };

    try {
      if (editingTeacher) {
        await updateTeacher(editingTeacher.id, teacher);
      } else {
        await createTeacher(teacher);
      }
      setShowForm(false);
      setEditingTeacher(null);
      loadTeachers();
    } catch (err) {
      setError('Failed to save teacher: ' + err.message);
    }
  };

  const handleEdit = (teacher) => {
    setEditingTeacher(teacher);
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
  };

  if (loading) return <div className="loading">Loading teachers...</div>;

  return (
    <div>
      <div className="card">
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
          <h2>Teachers</h2>
          <button className="btn btn-success" onClick={() => setShowForm(true)}>
            + Add Teacher
          </button>
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
                defaultValue={editingTeacher?.id || ''} 
                required 
                disabled={!!editingTeacher}
              />
            </div>
            <div className="form-group">
              <label>Name:</label>
              <input type="text" name="name" defaultValue={editingTeacher?.name || ''} required />
            </div>
            <div className="form-group">
              <label>Last Name:</label>
              <input type="text" name="lastName" defaultValue={editingTeacher?.lastName || ''} required />
            </div>
            <div className="form-group">
              <label>Max Hours Per Week:</label>
              <input 
                type="number" 
                name="maxHoursPerWeek" 
                defaultValue={editingTeacher?.maxHoursPerWeek || 40} 
                required 
              />
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
                <td>
                  <button className="btn btn-primary" onClick={() => handleEdit(teacher)} style={{ marginRight: '5px' }}>
                    Edit
                  </button>
                  <button className="btn btn-danger" onClick={() => handleDelete(teacher.id)}>
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

export default Teachers;

