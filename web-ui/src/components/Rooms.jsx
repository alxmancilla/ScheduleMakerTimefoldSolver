import React, { useState, useEffect } from 'react';
import { getRooms, createRoom, updateRoom, deleteRoom } from '../api';

function Rooms() {
  const [rooms, setRooms] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [editingRoom, setEditingRoom] = useState(null);
  const [showForm, setShowForm] = useState(false);

  useEffect(() => {
    loadRooms();
  }, []);

  const loadRooms = async () => {
    try {
      setLoading(true);
      const response = await getRooms();
      setRooms(response.data);
      setError(null);
    } catch (err) {
      setError('Failed to load rooms: ' + err.message);
    } finally {
      setLoading(false);
    }
  };

  const handleSubmit = async (e) => {
    e.preventDefault();
    const formData = new FormData(e.target);
    const room = {
      name: formData.get('name'),
      building: formData.get('building'),
      type: formData.get('type'),
    };

    try {
      if (editingRoom) {
        await updateRoom(editingRoom.name, room);
      } else {
        await createRoom(room);
      }
      setShowForm(false);
      setEditingRoom(null);
      loadRooms();
    } catch (err) {
      setError('Failed to save room: ' + err.message);
    }
  };

  const handleEdit = (room) => {
    setEditingRoom(room);
    setShowForm(true);
  };

  const handleDelete = async (name) => {
    if (!confirm('Are you sure you want to delete this room?')) return;
    try {
      await deleteRoom(name);
      loadRooms();
    } catch (err) {
      setError('Failed to delete room: ' + err.message);
    }
  };

  const handleCancel = () => {
    setShowForm(false);
    setEditingRoom(null);
  };

  if (loading) return <div className="loading">Loading rooms...</div>;

  return (
    <div>
      <div className="card">
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
          <h2>Rooms</h2>
          <button className="btn btn-success" onClick={() => setShowForm(true)}>
            + Add Room
          </button>
        </div>
      </div>

      {error && <div className="error">{error}</div>}

      {showForm && (
        <div className="card">
          <h3>{editingRoom ? 'Edit Room' : 'New Room'}</h3>
          <form onSubmit={handleSubmit}>
            <div className="form-group">
              <label>Name:</label>
              <input type="text" name="name" defaultValue={editingRoom?.name || ''} required disabled={!!editingRoom} />
            </div>
            <div className="form-group">
              <label>Building:</label>
              <input type="text" name="building" defaultValue={editingRoom?.building || ''} required />
            </div>
            <div className="form-group">
              <label>Type:</label>
              <select name="type" defaultValue={editingRoom?.type || 'estándar'}>
                <option value="estándar">Estándar</option>
                <option value="laboratorio">Laboratorio</option>
                <option value="taller">Taller</option>
                <option value="taller electromecánica">Taller Electromecánica</option>
                <option value="taller electrónica">Taller Electrónica</option>
                <option value="centro de cómputo">Centro de Cómputo</option>
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
              <th>Name</th>
              <th>Building</th>
              <th>Type</th>
              <th>Actions</th>
            </tr>
          </thead>
          <tbody>
            {rooms.map(room => (
              <tr key={room.name}>
                <td>{room.name}</td>
                <td>{room.building}</td>
                <td>{room.type}</td>
                <td>
                  <button className="btn btn-primary" onClick={() => handleEdit(room)} style={{ marginRight: '5px' }}>
                    Edit
                  </button>
                  <button className="btn btn-danger" onClick={() => handleDelete(room.name)}>
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

export default Rooms;

