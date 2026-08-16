import React, { useState, useEffect } from 'react';
import { useTranslation } from 'react-i18next';
import {
  getAssignments, createAssignment, updateAssignment, deleteAssignment,
  getGroups, getCourses, getTeachers, getRooms, listTimeslots,
} from '../api';
import WriteOnly from '../auth/WriteOnly';
import { useToast } from '../ui/ToastContext';
import { useConfirm } from '../ui/ConfirmContext';
import { ROOM_TYPES } from '../constants';
import { usePagination, Pagination, DEFAULT_PAGE_SIZE } from '../ui/Pagination';

const BLOCK_LENGTHS = [1, 2, 3, 4];

const DAY_KEYS = { 1: 'mon', 2: 'tue', 3: 'wed', 4: 'thu', 5: 'fri', 6: 'sat', 7: 'sun' };

function Assignments() {
  const { t } = useTranslation();
  const showToast = useToast();
  const confirmAction = useConfirm();
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

  const timeslotLabel = (ts) => {
    const dayLabel = t(`common.days.${DAY_KEYS[ts.dayOfWeek] || 'mon'}`);
    return `${dayLabel} ${ts.startHour}:00-${ts.startHour + ts.lengthHours}:00 (${ts.lengthHours}h)`;
  };

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
      setError(t('assignments.loadFailedPrefix') + err.message);
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
      setFieldErrors({ blockTimeslotId: t('assignments.pinnedRequiresTimeslotError') });
      setError(t('assignments.cannotSavePinned'));
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
      showToast(t('assignments.savedMessage'));
    } catch (err) {
      const data = err.response?.data;
      if (data?.errors) {
        setFieldErrors(data.errors);
      }
      setError(data?.message || t('assignments.saveFailedPrefix') + err.message);
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
    if (!(await confirmAction(t('assignments.confirmDelete')))) return;
    try {
      await deleteAssignment(id);
      loadAssignments();
      showToast(t('assignments.deletedMessage'));
    } catch (err) {
      setError(err.response?.data?.message || t('assignments.deleteFailedPrefix') + err.message);
    }
  };

  const handleCancel = () => {
    setShowForm(false);
    setEditingAssignment(null);
    setFieldErrors({});
    setError(null);
  };

  const timeslotDisplay = (blockTimeslotId) => {
    if (!blockTimeslotId) return '-';
    const match = timeslots.find((ts) => ts.id === blockTimeslotId);
    return match ? timeslotLabel(match) : blockTimeslotId;
  };

  const groupDisplay = (groupId) => groups.find((g) => g.id === groupId)?.name || groupId;
  const courseDisplay = (courseId) => courses.find((c) => c.id === courseId)?.name || courseId;
  const teacherDisplay = (teacherId) => {
    if (!teacherId) return '-';
    const tch = teachers.find((tc) => tc.id === teacherId);
    return tch ? `${tch.name} ${tch.lastName}` : teacherId;
  };

  const filteredAssignments = assignments.filter(a => {
    if (filter === 'assigned') return a.blockTimeslotId != null;
    if (filter === 'unassigned') return a.blockTimeslotId == null;
    if (filter === 'pinned') return a.pinned === true;
    return true;
  });
  const { page, setPage, pageCount, pageItems, totalItems } = usePagination(filteredAssignments);

  if (loading) return <div className="loading">{t('assignments.loading')}</div>;

  return (
    <div>
      <div className="card">
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
          <h2>{t('assignments.title')}</h2>
          <div>
            <select
              value={filter}
              onChange={(e) => setFilter(e.target.value)}
              style={{ marginRight: '10px', padding: '8px' }}
            >
              <option value="all">{t('assignments.filters.all')}</option>
              <option value="assigned">{t('assignments.filters.assigned')}</option>
              <option value="unassigned">{t('assignments.filters.unassigned')}</option>
              <option value="pinned">{t('assignments.filters.pinned')}</option>
            </select>
            <WriteOnly>
              <button className="btn btn-success" onClick={handleAdd}>
                {t('assignments.addAssignment')}
              </button>
            </WriteOnly>
          </div>
        </div>
        <p style={{ marginTop: '10px', color: '#7f8c8d' }}>
          {t('assignments.showing', { filtered: filteredAssignments.length, total: assignments.length })}
        </p>
      </div>

      {error && <div className="error">{error}</div>}

      {showForm && (
        <div className="card">
          <h3>{editingAssignment ? t('assignments.editAssignment') : t('assignments.newAssignment')}</h3>
          <form onSubmit={handleSubmit}>
            {!editingAssignment && (
              <div className="form-group">
                <label>{t('assignments.fields.id')}</label>
                <input type="text" name="id" pattern="[A-Za-z0-9_-]+"
                  title={t('assignments.fields.idPatternTitle')} required />
                {fieldErrors.id && <div className="error">{fieldErrors.id}</div>}
              </div>
            )}
            <div className="form-group">
              <label>{t('assignments.fields.group')}</label>
              <select name="groupId" defaultValue={editingAssignment?.groupId || ''} required>
                <option value="" disabled>{t('assignments.fields.groupPlaceholder')}</option>
                {groups.map((g) => (
                  <option key={g.id} value={g.id}>{g.id} - {g.name}</option>
                ))}
              </select>
              {fieldErrors.groupId && <div className="error">{fieldErrors.groupId}</div>}
            </div>
            <div className="form-group">
              <label>{t('assignments.fields.course')}</label>
              <select name="courseId" defaultValue={editingAssignment?.courseId || ''} required>
                <option value="" disabled>{t('assignments.fields.coursePlaceholder')}</option>
                {courses.map((c) => (
                  <option key={c.id} value={c.id}>{c.id} - {c.name}</option>
                ))}
              </select>
              {fieldErrors.courseId && <div className="error">{fieldErrors.courseId}</div>}
            </div>
            <div className="form-group">
              <label>{t('assignments.fields.blockLength')}</label>
              <select name="blockLength" defaultValue={editingAssignment?.blockLength || 1}>
                {BLOCK_LENGTHS.map((l) => (
                  <option key={l} value={l}>{l}</option>
                ))}
              </select>
              {fieldErrors.blockLength && <div className="error">{fieldErrors.blockLength}</div>}
            </div>
            <div className="form-group">
              <label>{t('assignments.fields.teacher')}</label>
              <select name="teacherId" defaultValue={editingAssignment?.teacherId || ''}>
                <option value="">{t('common.noneOption')}</option>
                {teachers.map((tc) => (
                  <option key={tc.id} value={tc.id}>{tc.id} - {tc.name} {tc.lastName}</option>
                ))}
              </select>
              {fieldErrors.teacherId && <div className="error">{fieldErrors.teacherId}</div>}
            </div>
            <div className="form-group">
              <label>{t('assignments.fields.blockTimeslot')}</label>
              <select name="blockTimeslotId" defaultValue={editingAssignment?.blockTimeslotId || ''}>
                <option value="">{t('common.noneOption')}</option>
                {timeslots.map((ts) => (
                  <option key={ts.id} value={ts.id}>{timeslotLabel(ts)}</option>
                ))}
              </select>
              {fieldErrors.blockTimeslotId && <div className="error">{fieldErrors.blockTimeslotId}</div>}
            </div>
            <div className="form-group">
              <label>{t('assignments.fields.room')}</label>
              <select name="roomName" defaultValue={editingAssignment?.roomName || ''}>
                <option value="">{t('common.noneOption')}</option>
                {rooms.map((r) => (
                  <option key={r.name} value={r.name}>{r.name} ({r.type})</option>
                ))}
              </select>
              {fieldErrors.roomName && <div className="error">{fieldErrors.roomName}</div>}
            </div>
            <div className="form-group">
              <label>{t('assignments.fields.satisfiesRoomType')}</label>
              <select name="satisfiesRoomType" defaultValue={editingAssignment?.satisfiesRoomType || ''}>
                <option value="">{t('common.noneOption')}</option>
                {ROOM_TYPES.map((type) => (
                  <option key={type} value={type}>{type}</option>
                ))}
              </select>
              {fieldErrors.satisfiesRoomType && <div className="error">{fieldErrors.satisfiesRoomType}</div>}
            </div>
            <div className="form-group">
              <label>{t('assignments.fields.preferredRoom')}</label>
              <select name="preferredRoomName" defaultValue={editingAssignment?.preferredRoomName || ''}>
                <option value="">{t('common.noneOption')}</option>
                {rooms.map((r) => (
                  <option key={r.name} value={r.name}>{r.name} ({r.type})</option>
                ))}
              </select>
              {fieldErrors.preferredRoomName && <div className="error">{fieldErrors.preferredRoomName}</div>}
            </div>
            <div className="form-group">
              <label>{t('assignments.fields.pin')}</label>
              <select name="pinned" defaultValue={editingAssignment?.pinned?.toString() || 'false'}>
                <option value="false">{t('common.no')}</option>
                <option value="true">{t('common.yes')}</option>
              </select>
              <div style={{ color: '#7f8c8d', fontSize: '12px', marginTop: '4px' }}>
                {t('assignments.pinHint')}
              </div>
            </div>
            <div style={{ display: 'flex', gap: '10px' }}>
              <button type="submit" className="btn btn-primary">{t('common.save')}</button>
              <button type="button" className="btn btn-secondary" onClick={handleCancel}>{t('common.cancel')}</button>
            </div>
          </form>
        </div>
      )}

      <div className="card" style={{ overflowX: 'auto' }}>
        <table>
          <thead>
            <tr>
              <th>{t('assignments.table.id')}</th>
              <th>{t('assignments.table.group')}</th>
              <th>{t('assignments.table.course')}</th>
              <th>{t('assignments.table.blockLength')}</th>
              <th>{t('assignments.table.teacher')}</th>
              <th>{t('assignments.table.schedule')}</th>
              <th>{t('assignments.table.room')}</th>
              <th>{t('assignments.table.pinned')}</th>
              <th>{t('common.actions')}</th>
            </tr>
          </thead>
          <tbody>
            {pageItems.map(assignment => (
              <tr key={assignment.id}>
                <td>{assignment.id}</td>
                <td>{groupDisplay(assignment.groupId)}</td>
                <td>{courseDisplay(assignment.courseId)}</td>
                <td>{assignment.blockLength}h</td>
                <td>{teacherDisplay(assignment.teacherId)}</td>
                <td>{timeslotDisplay(assignment.blockTimeslotId)}</td>
                <td>{assignment.roomName || '-'}</td>
                <td>{assignment.pinned ? '📌' : ''}</td>
                <td>
                  <WriteOnly>
                    <button className="btn btn-primary" onClick={() => handleEdit(assignment)} style={{ marginRight: '5px' }}>
                      {t('common.edit')}
                    </button>
                    <button className="btn btn-danger" onClick={() => handleDelete(assignment.id)}>
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

export default Assignments;
