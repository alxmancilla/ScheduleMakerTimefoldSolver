import React, { useState, useEffect } from 'react';
import { useTranslation } from 'react-i18next';
import { getGroups, createGroup, updateGroup, deleteGroup, getRooms } from '../api';
import WriteOnly from '../auth/WriteOnly';

function Groups() {
  const { t } = useTranslation();
  const [groups, setGroups] = useState([]);
  const [rooms, setRooms] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [editingGroup, setEditingGroup] = useState(null);
  const [showForm, setShowForm] = useState(false);

  useEffect(() => {
    loadGroups();
    loadRooms();
  }, []);

  const loadRooms = async () => {
    try {
      const response = await getRooms();
      setRooms(response.data);
    } catch (err) {
      // Non-critical: the dropdown just won't have options.
    }
  };

  const loadGroups = async () => {
    try {
      setLoading(true);
      const response = await getGroups();
      setGroups(response.data);
      setError(null);
    } catch (err) {
      setError(t('groups.loadFailedPrefix') + err.message);
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
      setError(t('groups.saveFailedPrefix') + err.message);
    }
  };

  const handleEdit = (group) => {
    setEditingGroup(group);
    setShowForm(true);
  };

  const handleDelete = async (id) => {
    if (!confirm(t('groups.confirmDelete'))) return;
    try {
      await deleteGroup(id);
      loadGroups();
    } catch (err) {
      setError(t('groups.deleteFailedPrefix') + err.message);
    }
  };

  const handleCancel = () => {
    setShowForm(false);
    setEditingGroup(null);
  };

  if (loading) return <div className="loading">{t('groups.loading')}</div>;

  return (
    <div>
      <div className="card">
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
          <h2>{t('groups.title')}</h2>
          <WriteOnly>
            <button className="btn btn-success" onClick={() => setShowForm(true)}>
              {t('groups.addGroup')}
            </button>
          </WriteOnly>
        </div>
      </div>

      {error && <div className="error">{error}</div>}

      {showForm && (
        <div className="card">
          <h3>{editingGroup ? t('groups.editGroup') : t('groups.newGroup')}</h3>
          <form onSubmit={handleSubmit}>
            <div className="form-group">
              <label>{t('groups.fields.id')}</label>
              <input type="text" name="id" defaultValue={editingGroup?.id || ''} required disabled={!!editingGroup} />
            </div>
            <div className="form-group">
              <label>{t('groups.fields.name')}</label>
              <input type="text" name="name" defaultValue={editingGroup?.name || ''} required />
            </div>
            <div className="form-group">
              <label>{t('groups.fields.preferredRoom')}</label>
              <select name="preferredRoomName" defaultValue={editingGroup?.preferredRoomName || ''}>
                <option value="">{t('common.noneOption')}</option>
                {rooms.map((room) => (
                  <option key={room.name} value={room.name}>{room.name}</option>
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
        <table>
          <thead>
            <tr>
              <th>{t('groups.table.id')}</th>
              <th>{t('groups.table.name')}</th>
              <th>{t('groups.table.preferredRoom')}</th>
              <th>{t('common.actions')}</th>
            </tr>
          </thead>
          <tbody>
            {groups.map(group => (
              <tr key={group.id}>
                <td>{group.id}</td>
                <td>{group.name}</td>
                <td>{group.preferredRoomName || '-'}</td>
                <td>
                  <WriteOnly>
                    <button className="btn btn-primary" onClick={() => handleEdit(group)} style={{ marginRight: '5px' }}>
                      {t('common.edit')}
                    </button>
                    <button className="btn btn-danger" onClick={() => handleDelete(group.id)}>
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

export default Groups;
