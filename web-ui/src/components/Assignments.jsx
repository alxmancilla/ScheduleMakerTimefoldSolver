import React, { useState, useEffect } from 'react';
import {
  getAssignments, createAssignment, updateAssignment, deleteAssignment,
  getGroups, getCourses, getTeachers, getRooms, listTimeslots,
} from '../api';
import WriteOnly from '../auth/WriteOnly';

// The `room` table enforces a CHECK constraint restricting `type` (and, by
// convention, course_block_assignment.satisfies_room_type) to exactly these
// values. Keep in sync with database/schema_block_scheduling*.sql.
const ROOM_TYPES = [
  'estándar',
  'laboratorio',
  'taller',
  'taller electromecánica',
  'taller electrónica',
  'centro de cómputo',
];

const BLOCK_LENGTHS = [1, 2, 3, 4];

const DAY_LABELS = { 1: 'Mon', 2: 'Tue', 3: 'Wed', 4: 'Thu', 5: 'Fri', 6: 'Sat', 7: 'Sun' };

const timeslotLabel = (t) =>
  `${DAY_LABELS[t.dayOfWeek] || t.dayOfWeek} ${t.startHour}:00-${t.startHour + t.lengthHours}:00 (${t.lengthHours}h)`;

function Assignments() {
  const [assignments, setAssignments] = useState([]);
  const [groups, setGroups] = useState([]);
  const [courses, setCourses] = useState([]);
  const [teachers, setTeachers] = useState([]);
  const [rooms, setRooms] = useState([]);
  const [timeslots, setTimeslots] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [fieldErrors, setFieldErrors] = useState({});
  const [editingAssignment, setEditingAssignment] = useState(null);
  const [showForm, setShowForm] = useState(false);
  const [filter, setFilter] = useState('all'); // 'all', 'assigned', 'unassigned', 'pinned'

  useEffect(() => {
    loadAssignments();
    loadOptions();
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

  const loadOptions = async () => {
    try {
      const [groupsRes, coursesRes, teachersRes, roomsRes, timeslotsRes] = await Promise.all([
        getGroups(), getCourses(), getTeachers(), getRooms(), listTimeslots(),
      ]);
      setGroups(groupsRes.data);
      setCourses(coursesRes.data);
      setTeachers(teachersRes.data);
      setRooms(roomsRes.data);
      setTimeslots(timeslotsRes.data);
    } catch (err) {
      // Non-critical: the dropdowns just won't have options.
    }
  };

  const handleSubmit = async (e) => {
    e.preventDefault();
    setFieldErrors({});
    setError(null);
    const formData = new FormData(e.target);
    const pinned = formData.get('pinned') === 'true';
    const blockTimeslotId = formData.get('blockTimeslotId') || null;

    if (pinned && !blockTimeslotId) {
      setFieldErrors({ blockTimeslotId: 'A pinned assignment must have a block timeslot' });
      setError('Cannot save: pinned assignments require a block timeslot.');
      return;
    }

    const assignment = {
      groupId: formData.get('groupId'),
      courseId: formData.get('courseId'),
      blockLength: parseInt(formData.get('blockLength'), 10),
      pinned,
      teacherId: formData.get('teacherId') || null,
      blockTimeslotId,
      roomName: formData.get('roomName') || null,
      satisfiesRoomType: formData.get('satisfiesRoomType') || null,
      preferredRoomName: formData.get('preferredRoomName') || null,
    };

    try {
      if (editingAssignment) {
        // On update the ID comes from the path; the backend ignores it in the body.
        await updateAssignment(editingAssignment.id, assignment);
      } else {
        // On create the client supplies the ID (validated against ^[A-Za-z0-9_-]+$).
        assignment.id = formData.get('id');
        await createAssignment(assignment);
      }
      handleCancel();
      loadAssignments();
    } catch (err) {
      const data = err.response?.data;
      if (data?.errors) {
        setFieldErrors(data.errors);
      }
      setError(data?.message || 'Failed to save assignment: ' + err.message);
    }
  };

  const handleEdit = (assignment) => {
    setEditingAssignment(assignment);
    setFieldErrors({});
    setError(null);
    setShowForm(true);
  };

  const handleAdd = () => {
    setEditingAssignment(null);
    setFieldErrors({});
    setError(null);
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
    setFieldErrors({});
    setError(null);
  };

  const filteredAssignments = assignments.filter(a => {
    if (filter === 'assigned') return a.blockTimeslotId != null;
    if (filter === 'unassigned') return a.blockTimeslotId == null;
    if (filter === 'pinned') return a.pinned === true;
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
            <WriteOnly>
              <button className="btn btn-success" onClick={handleAdd}>
                + Add Assignment
              </button>
            </WriteOnly>
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
            {!editingAssignment && (
              <div className="form-group">
                <label>Assignment ID:</label>
                <input type="text" name="id" pattern="[A-Za-z0-9_-]+"
                  title="Only letters, numbers, hyphens, and underscores" required />
                {fieldErrors.id && <div className="error">{fieldErrors.id}</div>}
              </div>
            )}
            <div className="form-group">
              <label>Group:</label>
              <select name="groupId" defaultValue={editingAssignment?.groupId || ''} required>
                <option value="" disabled>-- Select a group --</option>
                {groups.map((g) => (
                  <option key={g.id} value={g.id}>{g.id} - {g.name}</option>
                ))}
              </select>
              {fieldErrors.groupId && <div className="error">{fieldErrors.groupId}</div>}
            </div>
            <div className="form-group">
              <label>Course:</label>
              <select name="courseId" defaultValue={editingAssignment?.courseId || ''} required>
                <option value="" disabled>-- Select a course --</option>
                {courses.map((c) => (
                  <option key={c.id} value={c.id}>{c.id} - {c.name}</option>
                ))}
              </select>
              {fieldErrors.courseId && <div className="error">{fieldErrors.courseId}</div>}
            </div>
            <div className="form-group">
              <label>Block Length (hours):</label>
              <select name="blockLength" defaultValue={editingAssignment?.blockLength || 1}>
                {BLOCK_LENGTHS.map((l) => (
                  <option key={l} value={l}>{l}</option>
                ))}
              </select>
              {fieldErrors.blockLength && <div className="error">{fieldErrors.blockLength}</div>}
            </div>
            <div className="form-group">
              <label>Teacher (optional):</label>
              <select name="teacherId" defaultValue={editingAssignment?.teacherId || ''}>
                <option value="">-- None --</option>
                {teachers.map((t) => (
                  <option key={t.id} value={t.id}>{t.id} - {t.name} {t.lastName}</option>
                ))}
              </select>
              {fieldErrors.teacherId && <div className="error">{fieldErrors.teacherId}</div>}
            </div>
            <div className="form-group">
              <label>Block Timeslot (optional):</label>
              <select name="blockTimeslotId" defaultValue={editingAssignment?.blockTimeslotId || ''}>
                <option value="">-- None --</option>
                {timeslots.map((t) => (
                  <option key={t.id} value={t.id}>{timeslotLabel(t)}</option>
                ))}
              </select>
              {fieldErrors.blockTimeslotId && <div className="error">{fieldErrors.blockTimeslotId}</div>}
            </div>
            <div className="form-group">
              <label>Room (optional):</label>
              <select name="roomName" defaultValue={editingAssignment?.roomName || ''}>
                <option value="">-- None --</option>
                {rooms.map((r) => (
                  <option key={r.name} value={r.name}>{r.name} ({r.type})</option>
                ))}
              </select>
              {fieldErrors.roomName && <div className="error">{fieldErrors.roomName}</div>}
            </div>
            <div className="form-group">
              <label>Satisfies Room Type (optional):</label>
              <select name="satisfiesRoomType" defaultValue={editingAssignment?.satisfiesRoomType || ''}>
                <option value="">-- None --</option>
                {ROOM_TYPES.map((type) => (
                  <option key={type} value={type}>{type}</option>
                ))}
              </select>
              {fieldErrors.satisfiesRoomType && <div className="error">{fieldErrors.satisfiesRoomType}</div>}
            </div>
            <div className="form-group">
              <label>Preferred Room (optional):</label>
              <select name="preferredRoomName" defaultValue={editingAssignment?.preferredRoomName || ''}>
                <option value="">-- None --</option>
                {rooms.map((r) => (
                  <option key={r.name} value={r.name}>{r.name} ({r.type})</option>
                ))}
              </select>
              {fieldErrors.preferredRoomName && <div className="error">{fieldErrors.preferredRoomName}</div>}
            </div>
            <div className="form-group">
              <label>Pin Assignment:</label>
              <select name="pinned" defaultValue={editingAssignment?.pinned?.toString() || 'false'}>
                <option value="false">No</option>
                <option value="true">Yes</option>
              </select>
              <div style={{ color: '#7f8c8d', fontSize: '12px', marginTop: '4px' }}>
                Pinned assignments require a block timeslot.
              </div>
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
                  <WriteOnly>
                    <button className="btn btn-primary" onClick={() => handleEdit(assignment)} style={{ marginRight: '5px' }}>
                      Edit
                    </button>
                    <button className="btn btn-danger" onClick={() => handleDelete(assignment.id)}>
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

export default Assignments;
