import React, { useState, useEffect } from 'react';
import { useTranslation } from 'react-i18next';
import { getRooms, createRoom, updateRoom, deleteRoom } from '../api';
import WriteOnly from '../auth/WriteOnly';
import { useToast } from '../ui/ToastContext';
import { useConfirm } from '../ui/ConfirmContext';

// The `room` table enforces a CHECK constraint restricting `type` to exactly
// these values (see database/schema_block_scheduling*.sql). Keep in sync with
// that constraint and with the same list used in Courses.jsx/Assignments.jsx.
const ROOM_TYPES = [
  'estándar',
  'laboratorio',
  'taller',
  'taller electromecánica',
  'taller electrónica',
  'centro de cómputo',
];

function Rooms() {
  const { t } = useTranslation();
  const showToast = useToast();
  const confirmAction = useConfirm();
  const [rooms, setRooms] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [editingRoom, setEditingRoom] = useState(null);
  const [showForm, setShowForm] = useState(false);
  const [searchQuery, setSearchQuery] = useState('');

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
      setError(t('rooms.loadFailedPrefix') + err.message);
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
      showToast(t('rooms.savedMessage'));
    } catch (err) {
      setError(t('rooms.saveFailedPrefix') + err.message);
    }
  };

  const handleEdit = (room) => {
    setEditingRoom(room);
    setShowForm(true);
  };

  const handleDelete = async (name) => {
    if (!(await confirmAction(t('rooms.confirmDelete')))) return;
    try {
      await deleteRoom(name);
      loadRooms();
      showToast(t('rooms.deletedMessage'));
    } catch (err) {
      setError(err.response?.data?.message || t('rooms.deleteFailedPrefix') + err.message);
    }
  };

  const handleCancel = () => {
    setShowForm(false);
    setEditingRoom(null);
  };

  if (loading) return <div className="loading">{t('rooms.loading')}</div>;

  return (
    <div>
      <div className="card">
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
          <h2>{t('rooms.title')}</h2>
          <WriteOnly>
            <button className="btn btn-success" onClick={() => setShowForm(true)}>
              {t('rooms.addRoom')}
            </button>
          </WriteOnly>
        </div>
      </div>

      {error && <div className="error">{error}</div>}

      {showForm && (
        <div className="card">
          <h3>{editingRoom ? t('rooms.editRoom') : t('rooms.newRoom')}</h3>
          <form onSubmit={handleSubmit}>
            <div className="form-group">
              <label>{t('rooms.fields.name')}</label>
              <input type="text" name="name" defaultValue={editingRoom?.name || ''} required disabled={!!editingRoom} />
            </div>
            <div className="form-group">
              <label>{t('rooms.fields.building')}</label>
              <input type="text" name="building" defaultValue={editingRoom?.building || ''} required />
            </div>
            <div className="form-group">
              <label>{t('rooms.fields.type')}</label>
              <select name="type" defaultValue={editingRoom?.type || 'estándar'}>
                {ROOM_TYPES.map((type) => (
                  <option key={type} value={type}>{type}</option>
                ))}
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
        <div className="search-box">
          <input
            type="text"
            value={searchQuery}
            onChange={(e) => setSearchQuery(e.target.value)}
            placeholder={t('rooms.searchPlaceholder')}
          />
        </div>
        <table>
          <thead>
            <tr>
              <th>{t('rooms.table.name')}</th>
              <th>{t('rooms.table.building')}</th>
              <th>{t('rooms.table.type')}</th>
              <th>{t('common.actions')}</th>
            </tr>
          </thead>
          <tbody>
            {rooms.filter((room) => {
              const query = searchQuery.trim().toLowerCase();
              if (!query) return true;
              return (
                room.name?.toLowerCase().includes(query) ||
                room.building?.toLowerCase().includes(query)
              );
            }).map(room => (
              <tr key={room.name}>
                <td>{room.name}</td>
                <td>{room.building}</td>
                <td>{room.type}</td>
                <td>
                  <WriteOnly>
                    <button className="btn btn-primary" onClick={() => handleEdit(room)} style={{ marginRight: '5px' }}>
                      {t('common.edit')}
                    </button>
                    <button className="btn btn-danger" onClick={() => handleDelete(room.name)}>
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

export default Rooms;
