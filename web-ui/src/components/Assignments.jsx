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
  const [searchQuery, setSearchQuery] = useState('');
  // Controlled (unlike the rest of the form, which reads from FormData on submit) so
  // selecting a group can suggest that group's preferred room, a course can suggest its
  // own room requirement, and Room/Preferred Room can be filtered by - and cleared when
  // they no longer match - Satisfies Room Type.
  const [groupId, setGroupId] = useState('');
  const [courseId, setCourseId] = useState('');
  const [roomName, setRoomName] = useState('');
  const [satisfiesRoomType, setSatisfiesRoomType] = useState('');
  const [preferredRoomName, setPreferredRoomName] = useState('');

  // Mirrors Room.satisfiesRequirement() (engine domain model): a room satisfies a
  // requirement of its own type, and a laboratorio additionally satisfies estándar
  // (it's equipped with desks/board too), but never the reverse.
  const roomMatchesType = (room, requiredType) => {
    if (!requiredType) return true;
    if (!room) return false;
    return room.type === requiredType || (room.type === 'laboratorio' && requiredType === 'estándar');
  };
  const roomsMatchingType = (requiredType) => rooms.filter((r) => roomMatchesType(r, requiredType));

  /**
   * Sets Satisfies Room Type and clears Room/Preferred Room if they no longer match it -
   * shared by the field's own onChange and by the Course auto-suggestion below, so a
   * course-derived type change clears stale room selections exactly like a manual one does.
   */
  const applySatisfiesRoomType = (newType) => {
    setSatisfiesRoomType(newType);
    const currentRoom = rooms.find((r) => r.name === roomName);
    if (!roomMatchesType(currentRoom, newType)) setRoomName('');
    const currentPreferred = rooms.find((r) => r.name === preferredRoomName);
    if (!roomMatchesType(currentPreferred, newType)) setPreferredRoomName('');
  };

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

  /**
   * {groupId}_{courseId}_{index} - the same convention BlockGenerationService uses when it
   * generates blocks, so manually-created rows stay consistent with generated ones. Scans for
   * the first free index rather than just using the current count, in case an earlier block for
   * this (group, course) pair was deleted, leaving a gap.
   */
  const nextAssignmentId = (groupId, courseId) => {
    const existingIds = new Set(assignments.map((a) => a.id));
    let index = 0;
    let id;
    do {
      id = `${groupId}_${courseId}_${index}`;
      index++;
    } while (existingIds.has(id));
    return id;
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
        // Auto-generated, matching BlockGenerationService's own {groupId}_{courseId}_{index}
        // convention, instead of asking the user to invent a unique ID by hand.
        assignment.id = nextAssignmentId(assignment.groupId, assignment.courseId);
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
    setGroupId(assignment.groupId || '');
    setCourseId(assignment.courseId || '');
    setRoomName(assignment.roomName || '');
    setSatisfiesRoomType(assignment.satisfiesRoomType || '');
    setPreferredRoomName(assignment.preferredRoomName || '');
    setFieldErrors({});
    setError(null);
    setShowForm(true);
  };

  const handleAdd = () => {
    setEditingAssignment(null);
    setGroupId('');
    setCourseId('');
    setRoomName('');
    setSatisfiesRoomType('');
    setPreferredRoomName('');
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
    if (filter === 'assigned' && a.blockTimeslotId == null) return false;
    if (filter === 'unassigned' && a.blockTimeslotId != null) return false;
    if (filter === 'pinned' && a.pinned !== true) return false;

    const query = searchQuery.trim().toLowerCase();
    if (!query) return true;
    return (
      a.groupId?.toLowerCase().includes(query) ||
      groupDisplay(a.groupId)?.toLowerCase().includes(query) ||
      a.courseId?.toLowerCase().includes(query) ||
      courseDisplay(a.courseId)?.toLowerCase().includes(query) ||
      teacherDisplay(a.teacherId)?.toLowerCase().includes(query) ||
      a.roomName?.toLowerCase().includes(query)
    );
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
            {fieldErrors.id && <div className="error">{fieldErrors.id}</div>}
            <div className="form-group">
              <label>{t('assignments.fields.group')}</label>
              <select
                name="groupId"
                value={groupId}
                onChange={(e) => {
                  const newGroupId = e.target.value;
                  setGroupId(newGroupId);
                  // Suggest the group's own preferred room, but only when the field is still
                  // empty (never clobber a value the user or the loaded assignment already
                  // set) and only when it actually matches Satisfies Room Type if that's
                  // already set - e.g. don't suggest a group's regular classroom onto a block
                  // that's already marked as requiring a laboratorio.
                  if (!preferredRoomName) {
                    const group = groups.find((g) => g.id === newGroupId);
                    const candidateRoom = rooms.find((r) => r.name === group?.preferredRoomName);
                    if (group?.preferredRoomName && roomMatchesType(candidateRoom, satisfiesRoomType)) {
                      setPreferredRoomName(group.preferredRoomName);
                    }
                  }
                }}
                required
              >
                <option value="" disabled>{t('assignments.fields.groupPlaceholder')}</option>
                {groups.map((g) => (
                  <option key={g.id} value={g.id}>{g.id} - {g.name}</option>
                ))}
              </select>
              {fieldErrors.groupId && <div className="error">{fieldErrors.groupId}</div>}
            </div>
            <div className="form-group">
              <label>{t('assignments.fields.course')}</label>
              <select
                name="courseId"
                value={courseId}
                onChange={(e) => {
                  const newCourseId = e.target.value;
                  setCourseId(newCourseId);
                  // Suggest the course's own default room requirement, but only when the
                  // field is still empty - never clobber a value already set.
                  if (!satisfiesRoomType) {
                    const course = courses.find((c) => c.id === newCourseId);
                    if (course?.roomRequirement) applySatisfiesRoomType(course.roomRequirement);
                  }
                }}
                required
              >
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
              <label>{t('assignments.fields.preferredRoom')}</label>
              <select
                name="preferredRoomName"
                value={preferredRoomName}
                onChange={(e) => setPreferredRoomName(e.target.value)}
              >
                <option value="">{t('common.noneOption')}</option>
                {roomsMatchingType(satisfiesRoomType).map((r) => (
                  <option key={r.name} value={r.name}>{r.name} ({r.type})</option>
                ))}
              </select>
              <div style={{ color: '#7f8c8d', fontSize: '12px', marginTop: '4px' }}>
                {t('assignments.preferredRoomHint')}
              </div>
              {fieldErrors.preferredRoomName && <div className="error">{fieldErrors.preferredRoomName}</div>}
            </div>
            <div className="form-group">
              <label>{t('assignments.fields.satisfiesRoomType')}</label>
              <select
                name="satisfiesRoomType"
                value={satisfiesRoomType}
                onChange={(e) => applySatisfiesRoomType(e.target.value)}
              >
                <option value="">{t('common.noneOption')}</option>
                {ROOM_TYPES.map((type) => (
                  <option key={type} value={type}>{type}</option>
                ))}
              </select>
              <div style={{ color: '#7f8c8d', fontSize: '12px', marginTop: '4px' }}>
                {t('assignments.satisfiesRoomTypeHint')}
              </div>
              {fieldErrors.satisfiesRoomType && <div className="error">{fieldErrors.satisfiesRoomType}</div>}
            </div>
            <div className="form-group">
              <label>{t('assignments.fields.room')}</label>
              <select name="roomName" value={roomName} onChange={(e) => setRoomName(e.target.value)}>
                <option value="">{t('common.noneOption')}</option>
                {roomsMatchingType(satisfiesRoomType).map((r) => (
                  <option key={r.name} value={r.name}>{r.name} ({r.type})</option>
                ))}
              </select>
              {satisfiesRoomType && (
                <div style={{ color: '#7f8c8d', fontSize: '12px', marginTop: '4px' }}>
                  {t('assignments.roomFilteredHint', { type: satisfiesRoomType })}
                </div>
              )}
              {fieldErrors.roomName && <div className="error">{fieldErrors.roomName}</div>}
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
        <div className="search-box">
          <input
            type="text"
            value={searchQuery}
            onChange={(e) => setSearchQuery(e.target.value)}
            placeholder={t('assignments.searchPlaceholder')}
          />
        </div>
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
