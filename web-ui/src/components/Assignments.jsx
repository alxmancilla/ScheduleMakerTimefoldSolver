import React, { useState, useEffect } from 'react';
import { getAssignments, createAssignment, updateAssignment, deleteAssignment } from '../api';

function Assignments() {
  const [assignments, setAssignments] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [editingAssignment, setEditingAssignment] = useState(null);
  const [showForm, setShowForm] = useState(false);
  const [filter, setFilter] = useState('all'); // 'all', 'assigned', 'unassigned', 'pinned'

  useEffect(() => {
    loadAssignments();
  }, []);

  const loadAssignments = async () => {
    try {
      setLoading(true);
      const response = await getAssignments();
      setAssignments(response.data);
      setError(null);
    } catch (err) {
      setError('Failed to load assignments: ' + err.message);
    } finally {
      setLoading(false);
    }
  };

  const handleSubmit = async (e) => {
    e.preventDefault();
    const formData = new FormData(e.target);
    const assignment = {
      groupId: formData.get('groupId'),
      courseId: formData.get('courseId'),
      blockLength: parseInt(formData.get('blockLength')),
      pinAssignment: formData.get('pinAssignment') === 'true',
      teacherId: formData.get('teacherId') || null,
      timeslotId: formData.get('timeslotId') ? parseInt(formData.get('timeslotId')) : null,
      roomName: formData.get('roomName') || null,
      satisfiesRoomType: formData.get('satisfiesRoomType') || null,
      preferredRoomName: formData.get('preferredRoomName') || null,
    };

    try {
      if (editingAssignment) {
        await updateAssignment(editingAssignment.id, assignment);
      } else {
        await createAssignment(assignment);
      }
      setShowForm(false);
      setEditingAssignment(null);
      loadAssignments();
    } catch (err) {
      setError('Failed to save assignment: ' + err.message);
    }
  };

  const handleEdit = (assignment) => {
    setEditingAssignment(assignment);
    setShowForm(true);
  };

  const handleDelete = async (id) => {
    if (!confirm('Are you sure you want to delete this assignment?')) return;
    try {
      await deleteAssignment(id);
      loadAssignments();
    } catch (err) {
      setError('Failed to delete assignment: ' + err.message);
    }
  };

  const handleCancel = () => {
    setShowForm(false);
    setEditingAssignment(null);
  };

  const filteredAssignments = assignments.filter(a => {
    if (filter === 'assigned') return a.timeslotId !== null;
    if (filter === 'unassigned') return a.timeslotId === null;
    if (filter === 'pinned') return a.pinAssignment === true;
    return true;
  });

  if (loading) return <div className="loading">Loading assignments...</div>;

  return (
    <div>
      <div className="card">
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
          <h2>Course Block Assignments</h2>
          <div>
            <select 
              value={filter} 
              onChange={(e) => setFilter(e.target.value)}
              style={{ marginRight: '10px', padding: '8px' }}
            >
              <option value="all">All</option>
              <option value="assigned">Assigned</option>
              <option value="unassigned">Unassigned</option>
              <option value="pinned">Pinned</option>
            </select>
            <button className="btn btn-success" onClick={() => setShowForm(true)}>
              + Add Assignment
            </button>
          </div>
        </div>
        <p style={{ marginTop: '10px', color: '#7f8c8d' }}>
          Showing {filteredAssignments.length} of {assignments.length} assignments
        </p>
      </div>

      {error && <div className="error">{error}</div>}

      {showForm && (
        <div className="card">
          <h3>{editingAssignment ? 'Edit Assignment' : 'New Assignment'}</h3>
          <form onSubmit={handleSubmit}>
            <div className="form-group">
              <label>Group ID:</label>
              <input type="text" name="groupId" defaultValue={editingAssignment?.groupId || ''} required />
            </div>
            <div className="form-group">
              <label>Course ID:</label>
              <input type="text" name="courseId" defaultValue={editingAssignment?.courseId || ''} required />
            </div>
            <div className="form-group">
              <label>Block Length (hours):</label>
              <input type="number" name="blockLength" defaultValue={editingAssignment?.blockLength || 1} required min="1" max="4" />
            </div>
            <div className="form-group">
              <label>Teacher ID (optional):</label>
              <input type="text" name="teacherId" defaultValue={editingAssignment?.teacherId || ''} />
            </div>
            <div className="form-group">
              <label>Timeslot ID (optional):</label>
              <input type="number" name="timeslotId" defaultValue={editingAssignment?.timeslotId || ''} />
            </div>
            <div className="form-group">
              <label>Room Name (optional):</label>
              <input type="text" name="roomName" defaultValue={editingAssignment?.roomName || ''} />
            </div>
            <div className="form-group">
              <label>Satisfies Room Type:</label>
              <input type="text" name="satisfiesRoomType" defaultValue={editingAssignment?.satisfiesRoomType || 'estándar'} />
            </div>
            <div className="form-group">
              <label>Preferred Room Name (optional):</label>
              <input type="text" name="preferredRoomName" defaultValue={editingAssignment?.preferredRoomName || ''} />
            </div>
            <div className="form-group">
              <label>Pin Assignment:</label>
              <select name="pinAssignment" defaultValue={editingAssignment?.pinAssignment?.toString() || 'false'}>
                <option value="false">No</option>
                <option value="true">Yes</option>
              </select>
            </div>
            <div style={{ display: 'flex', gap: '10px' }}>
              <button type="submit" className="btn btn-primary">Save</button>
              <button type="button" className="btn btn-secondary" onClick={handleCancel}>Cancel</button>
            </div>
          </form>
        </div>
      )}

      <div className="card" style={{ overflowX: 'auto' }}>
        <table>
          <thead>
            <tr>
              <th>ID</th>
              <th>Group</th>
              <th>Course</th>
              <th>Block Length</th>
              <th>Teacher</th>
              <th>Block Timeslot</th>
              <th>Room</th>
              <th>Pinned</th>
              <th>Actions</th>
            </tr>
          </thead>
          <tbody>
            {filteredAssignments.map(assignment => (
              <tr key={assignment.id}>
                <td>{assignment.id}</td>
                <td>{assignment.groupId}</td>
                <td>{assignment.courseId}</td>
                <td>{assignment.blockLength}h</td>
                <td>{assignment.teacherId || '-'}</td>
                <td>{assignment.blockTimeslotId || '-'}</td>
                <td>{assignment.roomName || '-'}</td>
                <td>{assignment.pinned ? '📌' : ''}</td>
                <td>
                  <button className="btn btn-primary" onClick={() => handleEdit(assignment)} style={{ marginRight: '5px' }}>
                    Edit
                  </button>
                  <button className="btn btn-danger" onClick={() => handleDelete(assignment.id)}>
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

export default Assignments;

