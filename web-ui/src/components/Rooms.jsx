import React, { useState, useEffect } from 'react';
import { useTranslation } from 'react-i18next';
import { getRooms, createRoom, updateRoom, deleteRoom, getRoomUtilization } from '../api';
import WriteOnly from '../auth/WriteOnly';
import { useToast } from '../ui/ToastContext';
import { useConfirm } from '../ui/ConfirmContext';
import { ROOM_TYPES } from '../constants';
import { usePagination, Pagination, DEFAULT_PAGE_SIZE } from '../ui/Pagination';

// The school week's total available hours (5 days x 8 slots, 7:00-15:00) -
// the denominator for the utilization bar below. A room can exceed this
// (bar clamped visually, but the raw number still shown) when the data has
// unresolved room double-booking violations - see RoomUtilizationEntity.
const WEEKLY_AVAILABLE_HOURS = 40;

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
  const [utilizationByRoom, setUtilizationByRoom] = useState({});

  useEffect(() => {
    loadRooms();
    loadUtilization();
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

  // Current room utilization: assignment count and total hours booked per
  // week, computed server-side by v_room_utilization via GET
  // /api/rooms/utilization.
  const loadUtilization = async () => {
    try {
      const response = await getRoomUtilization();
      const byRoom = {};
      response.data.forEach((u) => {
        byRoom[u.name] = u;
      });
      setUtilizationByRoom(byRoom);
    } catch (err) {
      // Non-critical: the utilization column just won't have data.
    }
  };

  const handleSubmit = async (e) => {
    e.preventDefault();
    const formData = new FormData(e.target);
    const capacity = formData.get('capacity');
    const room = {
      name: formData.get('name'),
      building: formData.get('building'),
      type: formData.get('type'),
      capacity: capacity ? parseInt(capacity, 10) : null,
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

  const filteredRooms = rooms.filter((room) => {
    const query = searchQuery.trim().toLowerCase();
    if (!query) return true;
    return (
      room.name?.toLowerCase().includes(query) ||
      room.building?.toLowerCase().includes(query)
    );
  });
  const { page, setPage, pageCount, pageItems, totalItems } = usePagination(filteredRooms);

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

      {error && <div className="error" role="alert">{error}</div>}

      {showForm && (
        <div className="card">
          <h3>{editingRoom ? t('rooms.editRoom') : t('rooms.newRoom')}</h3>
          <form onSubmit={handleSubmit}>
            <div className="form-group">
              <label htmlFor="room-name">{t('rooms.fields.name')}</label>
              <input type="text" id="room-name" name="name" defaultValue={editingRoom?.name || ''} required disabled={!!editingRoom} />
            </div>
            <div className="form-group">
              <label htmlFor="room-building">{t('rooms.fields.building')}</label>
              <input type="text" id="room-building" name="building" defaultValue={editingRoom?.building || ''} required />
            </div>
            <div className="form-group">
              <label htmlFor="room-type">{t('rooms.fields.type')}</label>
              <select id="room-type" name="type" defaultValue={editingRoom?.type || 'Standard'}>
                {ROOM_TYPES.map((type) => (
                  <option key={type} value={type}>{type}</option>
                ))}
              </select>
            </div>
            <div className="form-group">
              <label htmlFor="room-capacity">{t('rooms.fields.capacity')}</label>
              <input type="number" id="room-capacity" name="capacity" min={1} defaultValue={editingRoom?.capacity ?? ''} placeholder={t('rooms.capacityPlaceholder')} />
            </div>
            <div style={{ display: 'flex', gap: '10px' }}>
              <button type="submit" className="btn btn-primary">{t('common.save')}</button>
              <button type="button" className="btn btn-secondary" onClick={handleCancel}>{t('common.cancel')}</button>
            </div>
          </form>
        </div>
      )}

      <div className="card table-wrap">
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
              <th>{t('rooms.table.capacity')}</th>
              <th>{t('rooms.table.utilization')}</th>
              <th>{t('common.actions')}</th>
            </tr>
          </thead>
          <tbody>
            {pageItems.map(room => (
              <tr key={room.name}>
                <td>{room.name}</td>
                <td>{room.building}</td>
                <td>{room.type}</td>
                <td>{room.capacity ?? '-'}</td>
                <td>
                  {(() => {
                    const utilization = utilizationByRoom[room.name];
                    if (!utilization) return '-';
                    const hoursUsed = utilization.totalHoursUsed || 0;
                    const pct = Math.round((hoursUsed / WEEKLY_AVAILABLE_HOURS) * 100);
                    const barColor = pct > 100 ? 'var(--color-danger)' : pct >= 80 ? 'var(--color-warning)' : 'var(--color-success)';
                    return (
                      <div style={{ display: 'flex', alignItems: 'center', gap: '8px', minWidth: '140px' }}>
                        <div style={{ flex: 1, background: 'var(--color-border-soft)', borderRadius: '4px', height: '8px', overflow: 'hidden' }}>
                          <div style={{ width: `${Math.min(pct, 100)}%`, background: barColor, height: '100%' }} />
                        </div>
                        <span style={{ fontSize: '12px', color: 'var(--color-text-secondary)', whiteSpace: 'nowrap' }}>
                          {hoursUsed}/{WEEKLY_AVAILABLE_HOURS}h ({utilization.assignmentsCount || 0})
                        </span>
                      </div>
                    );
                  })()}
                </td>
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
        <Pagination page={page} pageCount={pageCount} totalItems={totalItems} pageSize={DEFAULT_PAGE_SIZE} onPageChange={setPage} />
      </div>
    </div>
  );
}

export default Rooms;
