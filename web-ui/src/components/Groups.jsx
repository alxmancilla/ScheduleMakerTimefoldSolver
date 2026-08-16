import React, { useState, useEffect } from 'react';
import { useTranslation } from 'react-i18next';
import {
  getGroups, createGroup, updateGroup, deleteGroup, getRooms, getCourses,
  getGroupCourses, addGroupCourse, removeGroupCourse,
} from '../api';
import WriteOnly from '../auth/WriteOnly';
import { useToast } from '../ui/ToastContext';
import { useConfirm } from '../ui/ConfirmContext';
import { usePagination, Pagination, DEFAULT_PAGE_SIZE } from '../ui/Pagination';

function Groups() {
  const { t } = useTranslation();
  const showToast = useToast();
  const confirmAction = useConfirm();
  const [groups, setGroups] = useState([]);
  const [rooms, setRooms] = useState([]);
  const [allCourses, setAllCourses] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [editingGroup, setEditingGroup] = useState(null);
  const [showForm, setShowForm] = useState(false);
  const [searchQuery, setSearchQuery] = useState('');

  const [groupCourses, setGroupCourses] = useState([]);
  const [coursesError, setCoursesError] = useState(null);
  const [courseToAdd, setCourseToAdd] = useState('');

  useEffect(() => {
    loadGroups();
    loadRooms();
    loadAllCourses();
  }, []);

  const loadRooms = async () => {
    try {
      const response = await getRooms();
      setRooms(response.data);
    } catch (err) {
      // Non-critical: the dropdown just won't have options.
    }
  };

  const loadAllCourses = async () => {
    try {
      const response = await getCourses();
      setAllCourses(response.data);
    } catch (err) {
      // Non-critical: the "add course" dropdown just won't have options.
    }
  };

  const loadGroupCourses = async (groupId) => {
    try {
      const response = await getGroupCourses(groupId);
      setGroupCourses(response.data);
      setCoursesError(null);
    } catch (err) {
      setCoursesError(t('groups.courses.loadFailedPrefix') + err.message);
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
      showToast(t('groups.savedMessage'));
    } catch (err) {
      setError(t('groups.saveFailedPrefix') + err.message);
    }
  };

  const handleEdit = (group) => {
    setEditingGroup(group);
    setShowForm(true);
    setCoursesError(null);
    setCourseToAdd('');
    loadGroupCourses(group.id);
  };

  const handleDelete = async (id) => {
    if (!(await confirmAction(t('groups.confirmDelete')))) return;
    try {
      await deleteGroup(id);
      loadGroups();
      showToast(t('groups.deletedMessage'));
    } catch (err) {
      setError(err.response?.data?.message || t('groups.deleteFailedPrefix') + err.message);
    }
  };

  const handleCancel = () => {
    setShowForm(false);
    setEditingGroup(null);
    setGroupCourses([]);
    setCoursesError(null);
    setCourseToAdd('');
  };

  const handleAddGroupCourse = async () => {
    if (!courseToAdd) return;
    setCoursesError(null);
    try {
      await addGroupCourse(editingGroup.id, courseToAdd);
      setCourseToAdd('');
      loadGroupCourses(editingGroup.id);
      showToast(t('groups.courses.addedMessage'));
    } catch (err) {
      setCoursesError(err.response?.data?.message || t('groups.courses.addFailedPrefix') + err.message);
    }
  };

  const handleRemoveGroupCourse = async (courseName) => {
    if (!(await confirmAction(t('groups.courses.confirmRemove')))) return;
    try {
      await removeGroupCourse(editingGroup.id, courseName);
      loadGroupCourses(editingGroup.id);
      showToast(t('groups.courses.removedMessage'));
    } catch (err) {
      setCoursesError(err.response?.data?.message || t('groups.courses.removeFailedPrefix') + err.message);
    }
  };

  const filteredGroups = groups.filter((group) => {
    const query = searchQuery.trim().toLowerCase();
    if (!query) return true;
    return (
      group.id?.toLowerCase().includes(query) ||
      group.name?.toLowerCase().includes(query)
    );
  });
  const { page, setPage, pageCount, pageItems, totalItems } = usePagination(filteredGroups);

  if (loading) return <div className="loading">{t('groups.loading')}</div>;

  return (
    <div>
      <div className="card">
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
          <h2>{t('groups.title')}</h2>
          <WriteOnly>
            <button
              className="btn btn-success"
              onClick={() => {
                setEditingGroup(null);
                setShowForm(true);
                setGroupCourses([]);
                setCoursesError(null);
                setCourseToAdd('');
              }}
            >
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

      {showForm && editingGroup && (
        <div className="card">
          <h3>{t('groups.courses.title')}</h3>
          <p style={{ marginTop: '8px', color: '#7f8c8d', fontSize: '13px' }}>
            {t('groups.courses.description')}
          </p>
          {coursesError && <div className="error">{coursesError}</div>}

          <WriteOnly>
            <div style={{ display: 'flex', gap: '10px', marginTop: '12px', marginBottom: '12px' }}>
              <select value={courseToAdd} onChange={(e) => setCourseToAdd(e.target.value)} style={{ flex: 1 }}>
                <option value="">{t('groups.courses.selectPlaceholder')}</option>
                {allCourses
                  .filter((c) => !groupCourses.some((gc) => gc.courseName === c.name))
                  .map((c) => (
                    <option key={c.id} value={c.name}>{c.id} - {c.name}</option>
                  ))}
              </select>
              <button type="button" className="btn btn-success" onClick={handleAddGroupCourse} disabled={!courseToAdd}>
                {t('groups.courses.addCourse')}
              </button>
            </div>
          </WriteOnly>

          <table>
            <thead>
              <tr>
                <th>{t('groups.courses.table.course')}</th>
                <th>{t('common.actions')}</th>
              </tr>
            </thead>
            <tbody>
              {groupCourses.map((gc) => (
                <tr key={gc.courseName}>
                  <td>{gc.courseName}</td>
                  <td>
                    <WriteOnly>
                      <button className="btn btn-danger" onClick={() => handleRemoveGroupCourse(gc.courseName)}>
                        {t('common.delete')}
                      </button>
                    </WriteOnly>
                  </td>
                </tr>
              ))}
              {groupCourses.length === 0 && (
                <tr>
                  <td colSpan={2} style={{ color: '#7f8c8d' }}>{t('groups.courses.none')}</td>
                </tr>
              )}
            </tbody>
          </table>
        </div>
      )}

      <div className="card">
        <div className="search-box">
          <input
            type="text"
            value={searchQuery}
            onChange={(e) => setSearchQuery(e.target.value)}
            placeholder={t('groups.searchPlaceholder')}
          />
        </div>
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
            {pageItems.map(group => (
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
        <Pagination page={page} pageCount={pageCount} totalItems={totalItems} pageSize={DEFAULT_PAGE_SIZE} onPageChange={setPage} />
      </div>
    </div>
  );
}

export default Groups;
