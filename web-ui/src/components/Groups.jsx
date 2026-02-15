import React, { useState, useEffect } from 'react';
import { getGroups, createGroup, updateGroup, deleteGroup } from '../api';

function Groups() {
  const [groups, setGroups] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [editingGroup, setEditingGroup] = useState(null);
  const [showForm, setShowForm] = useState(false);

  useEffect(() => {
    loadGroups();
  }, []);

  const loadGroups = async () => {
    try {
      setLoading(true);
      const response = await getGroups();
      setGroups(response.data);
      setError(null);
    } catch (err) {
      setError('Failed to load groups: ' + err.message);
    } finally {
      setLoading(false);
    }
  };

  const handleSubmit = async (e) => {
    e.preventDefault();
    const formData = new FormData(e.target);
    const group = {
      id: formData.get('id'),
      name: formData.get('name'),
      preferredRoomName: formData.get('preferredRoomName') || null,
    };

    try {
      if (editingGroup) {
        await updateGroup(editingGroup.id, group);
      } else {
        await createGroup(group);
      }
      setShowForm(false);
      setEditingGroup(null);
      loadGroups();
    } catch (err) {
      setError('Failed to save group: ' + err.message);
    }
  };

  const handleEdit = (group) => {
    setEditingGroup(group);
    setShowForm(true);
  };

  const handleDelete = async (id) => {
    if (!confirm('Are you sure you want to delete this group?')) return;
    try {
      await deleteGroup(id);
      loadGroups();
    } catch (err) {
      setError('Failed to delete group: ' + err.message);
    }
  };

  const handleCancel = () => {
    setShowForm(false);
    setEditingGroup(null);
  };

  if (loading) return <div className="loading">Loading groups...</div>;

  return (
    <div>
      <div className="card">
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
          <h2>Student Groups</h2>
          <button className="btn btn-success" onClick={() => setShowForm(true)}>
            + Add Group
          </button>
        </div>
      </div>

      {error && <div className="error">{error}</div>}

      {showForm && (
        <div className="card">
          <h3>{editingGroup ? 'Edit Group' : 'New Group'}</h3>
          <form onSubmit={handleSubmit}>
            <div className="form-group">
              <label>ID:</label>
              <input type="text" name="id" defaultValue={editingGroup?.id || ''} required disabled={!!editingGroup} />
            </div>
            <div className="form-group">
              <label>Name:</label>
              <input type="text" name="name" defaultValue={editingGroup?.name || ''} required />
            </div>
            <div className="form-group">
              <label>Preferred Room (optional):</label>
              <input type="text" name="preferredRoomName" defaultValue={editingGroup?.preferredRoomName || ''} />
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
              <th>Preferred Room</th>
              <th>Actions</th>
            </tr>
          </thead>
          <tbody>
            {groups.map(group => (
              <tr key={group.id}>
                <td>{group.id}</td>
                <td>{group.name}</td>
                <td>{group.preferredRoomName || '-'}</td>
                <td>
                  <button className="btn btn-primary" onClick={() => handleEdit(group)} style={{ marginRight: '5px' }}>
                    Edit
                  </button>
                  <button className="btn btn-danger" onClick={() => handleDelete(group.id)}>
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

export default Groups;

