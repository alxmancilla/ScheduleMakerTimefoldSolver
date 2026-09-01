import React, { useState, useEffect, useRef } from 'react';
import { useTranslation } from 'react-i18next';
import {
  getAssignments, createAssignment, updateAssignment, deleteAssignment,
  getGroups, getCourses, getTeachers, getRooms, listTimeslots, getGroupRoomRanges,
  exportAssignments, importAssignments,
} from '../api';
import AdminOnly from '../auth/AdminOnly';
import { useToast } from '../ui/ToastContext';
import { useConfirm } from '../ui/ConfirmContext';
import { ROOM_TYPES, formatHour } from '../constants';
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
  // selecting a group can suggest that group's preferred room and filter Course to only
  // that group's courses, a course can suggest its own room requirement and filter
  // Teacher to only qualified teachers, and Room/Preferred Room can be filtered by - and
  // cleared when they no longer match - Satisfies Room Type.
  const [groupId, setGroupId] = useState('');
  const [courseId, setCourseId] = useState('');
  const [teacherId, setTeacherId] = useState('');
  const [roomName, setRoomName] = useState('');
  const [satisfiesRoomType, setSatisfiesRoomType] = useState('');
  const [preferredRoomHint, setPreferredRoomHint] = useState('');

  const [exporting, setExporting] = useState(false);
  const [exportError, setExportError] = useState(null);
  const [importFile, setImportFile] = useState(null);
  const [importing, setImporting] = useState(false);
  const [importResult, setImportResult] = useState(null);
  const [importError, setImportError] = useState(null);
  const importFileInputRef = useRef(null);

  // Mirrors Room.satisfiesRequirement() (engine domain model): a room satisfies a
  // requirement of its own type, and a Mixed room additionally satisfies Standard
  // and Specialized - Workshop (it's equipped for both), but never the reverse.
  // Specialized - Computer Lab stays strictly separate - not satisfied by Mixed.
  const roomMatchesType = (room, requiredType) => {
    if (!requiredType) return true;
    if (!room) return false;
    return room.type === requiredType
      || (room.type === 'Mixed' && (requiredType === 'Standard' || requiredType === 'Specialized - Workshop'));
  };
  const roomsMatchingType = (requiredType) => rooms.filter((r) => roomMatchesType(r, requiredType));

  // Group.courses / Teacher.qualifications come embedded from getGroups()/getTeachers(),
  // so filtering Course by group and Teacher by course needs no extra requests.
  const coursesForGroup = (gId) => {
    const group = groups.find((g) => g.id === gId);
    if (!group) return courses;
    const names = new Set(group.courses.map((gc) => gc.courseName));
    return courses.filter((c) => names.has(c.name));
  };
  const teacherQualifiedFor = (teacher, courseName) =>
    !courseName || teacher.qualifications.some((q) => q.qualification === courseName);
  const teachersForCourse = (cId) => {
    const course = courses.find((c) => c.id === cId);
    if (!course) return teachers;
    return teachers.filter((t) => teacherQualifiedFor(t, course.name));
  };

  /**
   * Sets Satisfies Room Type and clears Room/Preferred Room if they no longer match it -
   * shared by the field's own onChange and by the Course auto-suggestion below, so a
   * course-derived type change clears stale room selections exactly like a manual one does.
   */
  const applySatisfiesRoomType = (newType) => {
    setSatisfiesRoomType(newType);
    const currentRoom = rooms.find((r) => r.name === roomName);
    if (!roomMatchesType(currentRoom, newType)) setRoomName('');
    const currentPreferred = rooms.find((r) => r.name === preferredRoomHint);
    if (!roomMatchesType(currentPreferred, newType)) setPreferredRoomHint('');
  };

  const timeslotLabel = (ts) => {
    const dayLabel = t(`common.days.${DAY_KEYS[ts.dayOfWeek] || 'mon'}`);
    return `${dayLabel} ${formatHour(ts.startHour)}-${formatHour(ts.startHour + ts.lengthHours)} (${ts.lengthHours}h)`;
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

  const handleExportAssignments = async () => {
    setExporting(true);
    setExportError(null);
    try {
      const response = await exportAssignments();
      const blobUrl = URL.createObjectURL(new Blob([response.data], {
        type: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
      }));
      const disposition = response.headers['content-disposition'];
      const match = disposition && disposition.match(/filename="(.+)"/);
      const link = document.createElement('a');
      link.href = blobUrl;
      link.download = match ? match[1] : 'assignments-export.xlsx';
      document.body.appendChild(link);
      link.click();
      document.body.removeChild(link);
      setTimeout(() => URL.revokeObjectURL(blobUrl), 30000);
    } catch (err) {
      setExportError(err.response?.data?.message || t('assignments.exportFailedPrefix') + err.message);
    } finally {
      setExporting(false);
    }
  };

  const handleImportAssignments = async () => {
    if (!importFile) return;
    setImporting(true);
    setImportError(null);
    setImportResult(null);
    try {
      const response = await importAssignments(importFile);
      setImportResult(response.data);
      if (response.data.success) {
        loadAssignments();
      }
    } catch (err) {
      const data = err.response?.data;
      if (data && typeof data.success === 'boolean') {
        setImportResult(data);
      } else {
        setImportError(data?.message || t('assignments.importFailedPrefix') + err.message);
      }
    } finally {
      setImporting(false);
      setImportFile(null);
      if (importFileInputRef.current) importFileInputRef.current.value = '';
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
      preferredRoomHint: formData.get('preferredRoomHint') || null,
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
    setTeacherId(assignment.teacherId || '');
    setRoomName(assignment.roomName || '');
    setSatisfiesRoomType(assignment.satisfiesRoomType || '');
    setPreferredRoomHint(assignment.preferredRoomHint || '');
    setFieldErrors({});
    setError(null);
    setShowForm(true);
  };

  const handleAdd = () => {
    setEditingAssignment(null);
    setGroupId('');
    setCourseId('');
    setTeacherId('');
    setRoomName('');
    setSatisfiesRoomType('');
    setPreferredRoomHint('');
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
            <AdminOnly>
              <button className="btn btn-success" onClick={handleAdd}>
                {t('assignments.addAssignment')}
              </button>
            </AdminOnly>
          </div>
        </div>
        <p style={{ marginTop: '10px', color: 'var(--color-text-secondary)' }}>
          {t('assignments.showing', { filtered: filteredAssignments.length, total: assignments.length })}
        </p>
      </div>

      <div className="card">
        <h3>{t('assignments.backup.title')}</h3>
        <p style={{ marginTop: '8px', color: 'var(--color-text-secondary)', fontSize: '13px' }}>
          {t('assignments.backup.description')}
        </p>
        <div style={{ display: 'flex', gap: '16px', flexWrap: 'wrap', marginTop: '10px', alignItems: 'flex-start' }}>
          <div>
            <button className="btn btn-secondary" onClick={handleExportAssignments} disabled={exporting}>
              {exporting ? t('assignments.backup.exporting') : `⇩ ${t('assignments.backup.exportButton')}`}
            </button>
            {exportError && <div className="error" role="alert" style={{ marginTop: '8px' }}>{exportError}</div>}
          </div>
          <AdminOnly>
            <div>
              <div style={{ display: 'flex', gap: '10px', alignItems: 'center' }}>
                <input
                  ref={importFileInputRef}
                  type="file"
                  accept=".xlsx"
                  onChange={(e) => setImportFile(e.target.files[0] || null)}
                />
                <button
                  className="btn btn-success"
                  onClick={handleImportAssignments}
                  disabled={!importFile || importing}
                >
                  {importing ? t('assignments.backup.importing') : `⇪ ${t('assignments.backup.importButton')}`}
                </button>
              </div>
              {importError && <div className="error" role="alert" style={{ marginTop: '8px' }}>{importError}</div>}
              {importResult && importResult.success && (
                <div style={{ marginTop: '8px', fontSize: '13px', color: '#2e7d32' }}>
                  {t('assignments.backup.importedSummary', { count: importResult.assignmentsImported })}
                </div>
              )}
              {importResult && !importResult.success && (
                <div style={{ marginTop: '8px' }}>
                  <div className="error" role="alert">{t('assignments.backup.rejected')}</div>
                  <ul style={{ marginTop: '6px', fontSize: '13px', color: 'var(--color-danger-dark)' }}>
                    {importResult.errors.map((e, i) => (
                      <li key={i}>{e}</li>
                    ))}
                  </ul>
                </div>
              )}
            </div>
          </AdminOnly>
        </div>
      </div>

      {error && <div className="error" role="alert">{error}</div>}

      {showForm && (
        <div className="card">
          <h3>{editingAssignment ? t('assignments.editAssignment') : t('assignments.newAssignment')}</h3>
          <form onSubmit={handleSubmit}>
            {fieldErrors.id && <div className="error" role="alert">{fieldErrors.id}</div>}
            <div className="form-group">
              <label>{t('assignments.fields.group')}</label>
              <select
                name="groupId"
                value={groupId}
                onChange={(e) => {
                  const newGroupId = e.target.value;
                  setGroupId(newGroupId);
                  // Suggest a room from the group's own curated range for the
                  // current Satisfies Room Type, but only when the field is
                  // still empty (never clobber a value the user or the loaded
                  // assignment already set), only when Satisfies Room Type is
                  // already chosen (ranges are keyed by room type, so there's
                  // no type-agnostic range to fall back to), and only when
                  // that range resolves to exactly one room - the same "only
                  // auto-pick when unambiguous" rule the backend applies to
                  // this same range.
                  if (!preferredRoomHint && newGroupId && satisfiesRoomType) {
                    getGroupRoomRanges(newGroupId)
                      .then((response) => {
                        const matching = response.data.filter((r) => r.roomType === satisfiesRoomType);
                        if (matching.length === 1) {
                          setPreferredRoomHint(matching[0].roomName);
                        }
                      })
                      .catch(() => {
                        // Non-critical: the preferred-room suggestion just won't populate.
                      });
                  }
                  // Course is scoped to this group's own courses below - clear it (and the
                  // Teacher that depends on it) instead of silently keeping a course the new
                  // group doesn't even take.
                  if (courseId && !coursesForGroup(newGroupId).some((c) => c.id === courseId)) {
                    setCourseId('');
                    setTeacherId('');
                  }
                }}
                required
              >
                <option value="" disabled>{t('assignments.fields.groupPlaceholder')}</option>
                {groups.map((g) => (
                  <option key={g.id} value={g.id}>{g.id} - {g.name}</option>
                ))}
              </select>
              {fieldErrors.groupId && <div className="error" role="alert">{fieldErrors.groupId}</div>}
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
                  // Teacher is scoped to this course's qualified teachers below - clear it
                  // instead of silently keeping a teacher who isn't qualified for it.
                  if (teacherId && !teachersForCourse(newCourseId).some((teacher) => teacher.id === teacherId)) {
                    setTeacherId('');
                  }
                }}
                required
              >
                <option value="" disabled>{t('assignments.fields.coursePlaceholder')}</option>
                {coursesForGroup(groupId).map((c) => (
                  <option key={c.id} value={c.id}>{c.id} - {c.name}</option>
                ))}
              </select>
              {groupId && (
                <div style={{ color: 'var(--color-text-secondary)', fontSize: '12px', marginTop: '4px' }}>
                  {t('assignments.courseFilteredHint')}
                </div>
              )}
              {fieldErrors.courseId && <div className="error" role="alert">{fieldErrors.courseId}</div>}
            </div>
            <div className="form-group">
              <label>{t('assignments.fields.blockLength')}</label>
              <select name="blockLength" defaultValue={editingAssignment?.blockLength || 1}>
                {BLOCK_LENGTHS.map((l) => (
                  <option key={l} value={l}>{l}</option>
                ))}
              </select>
              {fieldErrors.blockLength && <div className="error" role="alert">{fieldErrors.blockLength}</div>}
            </div>
            <div className="form-group">
              <label>{t('assignments.fields.teacher')}</label>
              <select name="teacherId" value={teacherId} onChange={(e) => setTeacherId(e.target.value)}>
                <option value="">{t('common.noneOption')}</option>
                {teachersForCourse(courseId).map((tc) => (
                  <option key={tc.id} value={tc.id}>{tc.id} - {tc.name} {tc.lastName}</option>
                ))}
              </select>
              {courseId && (
                <div style={{ color: 'var(--color-text-secondary)', fontSize: '12px', marginTop: '4px' }}>
                  {t('assignments.teacherFilteredHint')}
                </div>
              )}
              {fieldErrors.teacherId && <div className="error" role="alert">{fieldErrors.teacherId}</div>}
            </div>
            <div className="form-group">
              <label>{t('assignments.fields.blockTimeslot')}</label>
              <select name="blockTimeslotId" defaultValue={editingAssignment?.blockTimeslotId || ''}>
                <option value="">{t('common.noneOption')}</option>
                {timeslots.map((ts) => (
                  <option key={ts.id} value={ts.id}>{timeslotLabel(ts)}</option>
                ))}
              </select>
              {fieldErrors.blockTimeslotId && <div className="error" role="alert">{fieldErrors.blockTimeslotId}</div>}
            </div>
            <div className="form-group">
              <label>{t('assignments.fields.preferredRoom')}</label>
              <select
                name="preferredRoomHint"
                value={preferredRoomHint}
                onChange={(e) => setPreferredRoomHint(e.target.value)}
              >
                <option value="">{t('common.noneOption')}</option>
                {roomsMatchingType(satisfiesRoomType).map((r) => (
                  <option key={r.name} value={r.name}>{r.name} ({r.type})</option>
                ))}
              </select>
              <div style={{ color: 'var(--color-text-secondary)', fontSize: '12px', marginTop: '4px' }}>
                {t('assignments.preferredRoomHint')}
              </div>
              {fieldErrors.preferredRoomHint && <div className="error" role="alert">{fieldErrors.preferredRoomHint}</div>}
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
              <div style={{ color: 'var(--color-text-secondary)', fontSize: '12px', marginTop: '4px' }}>
                {t('assignments.satisfiesRoomTypeHint')}
              </div>
              {fieldErrors.satisfiesRoomType && <div className="error" role="alert">{fieldErrors.satisfiesRoomType}</div>}
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
                <div style={{ color: 'var(--color-text-secondary)', fontSize: '12px', marginTop: '4px' }}>
                  {t('assignments.roomFilteredHint', { type: satisfiesRoomType })}
                </div>
              )}
              {fieldErrors.roomName && <div className="error" role="alert">{fieldErrors.roomName}</div>}
            </div>
            <div className="form-group">
              <label>{t('assignments.fields.pin')}</label>
              <select name="pinned" defaultValue={editingAssignment?.pinned?.toString() || 'false'}>
                <option value="false">{t('common.no')}</option>
                <option value="true">{t('common.yes')}</option>
              </select>
              <div style={{ color: 'var(--color-text-secondary)', fontSize: '12px', marginTop: '4px' }}>
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

      <div className="card table-wrap">
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
                  <AdminOnly>
                    <button className="btn btn-primary" onClick={() => handleEdit(assignment)} style={{ marginRight: '5px' }}>
                      {t('common.edit')}
                    </button>
                    <button className="btn btn-danger" onClick={() => handleDelete(assignment.id)}>
                      {t('common.delete')}
                    </button>
                  </AdminOnly>
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
