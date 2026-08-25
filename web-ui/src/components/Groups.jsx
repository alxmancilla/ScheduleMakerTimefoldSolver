import React, { useState, useEffect } from 'react';
import { useTranslation } from 'react-i18next';
import {
  getGroups, createGroup, updateGroup, deleteGroup, getRooms, getCourses, getTeachers,
  getGroupCourses, addGroupCourse, removeGroupCourse, setGroupCourseDefaultTeacher,
  getAssignmentsByGroup, updateAssignment,
  getGroupRoomRanges, createGroupRoomRange, updateGroupRoomRange, deleteGroupRoomRange,
} from '../api';
import WriteOnly from '../auth/WriteOnly';
import { useAuth } from '../auth/AuthContext';
import { useToast } from '../ui/ToastContext';
import { useConfirm } from '../ui/ConfirmContext';
import { usePagination, Pagination, DEFAULT_PAGE_SIZE } from '../ui/Pagination';
import { ROOM_TYPES } from '../constants';

const EMPTY_RANGE_FORM = { roomType: ROOM_TYPES[0], roomName: '' };

function Groups() {
  const { t } = useTranslation();
  const showToast = useToast();
  const confirmAction = useConfirm();
  const { canWrite } = useAuth();
  const [groups, setGroups] = useState([]);
  const [rooms, setRooms] = useState([]);
  const [allCourses, setAllCourses] = useState([]);
  const [teachers, setTeachers] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [editingGroup, setEditingGroup] = useState(null);
  const [showForm, setShowForm] = useState(false);
  const [searchQuery, setSearchQuery] = useState('');

  const [groupCourses, setGroupCourses] = useState([]);
  const [coursesError, setCoursesError] = useState(null);
  const [courseToAdd, setCourseToAdd] = useState('');
  const [groupAssignments, setGroupAssignments] = useState([]);
  const [savingTeacherFor, setSavingTeacherFor] = useState(null);

  const [roomRanges, setRoomRanges] = useState([]);
  const [rangesError, setRangesError] = useState(null);
  const [showRangeForm, setShowRangeForm] = useState(false);
  const [editingRange, setEditingRange] = useState(null);
  const [rangeForm, setRangeForm] = useState(EMPTY_RANGE_FORM);

  useEffect(() => {
    loadGroups();
    loadRooms();
    loadAllCourses();
    loadTeachers();
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

  const loadTeachers = async () => {
    try {
      const response = await getTeachers();
      setTeachers(response.data);
    } catch (err) {
      // Non-critical: the "no qualified teacher" warning just won't show.
    }
  };

  // Course names with at least one teacher qualification matching them exactly
  // (the same convention the solver and Assignments.jsx use for "qualified").
  const qualifiedCourseNames = new Set(
    teachers.flatMap((t) => (t.qualifications || []).map((q) => q.qualification))
  );

  const loadGroupCourses = async (groupId) => {
    try {
      const response = await getGroupCourses(groupId);
      setGroupCourses(response.data);
      setCoursesError(null);
    } catch (err) {
      setCoursesError(t('groups.courses.loadFailedPrefix') + err.message);
    }
  };

  const loadGroupAssignments = async (groupId) => {
    try {
      const response = await getAssignmentsByGroup(groupId);
      setGroupAssignments(response.data);
    } catch (err) {
      // Non-critical: the inline teacher picker just won't have data to work with.
    }
  };

  const loadRoomRanges = async (groupId) => {
    try {
      const response = await getGroupRoomRanges(groupId);
      setRoomRanges(response.data);
      setRangesError(null);
    } catch (err) {
      setRangesError(t('groups.roomRanges.loadFailedPrefix') + err.message);
    }
  };

  // Teachers qualified for a course, same "qualification string === course name"
  // convention the solver and Assignments.jsx use.
  const teachersForCourseName = (courseName) =>
    teachers.filter((teacher) => (teacher.qualifications || []).some((q) => q.qualification === courseName));

  const blocksForCourse = (courseName) => {
    const course = allCourses.find((c) => c.name === courseName);
    if (!course) return [];
    return groupAssignments.filter((a) => a.courseId === course.id);
  };

  // The teacher shown for a course row: the value shared by every block of that
  // course, or '' if there are no blocks yet or their teachers disagree.
  const teacherForCourse = (courseName) => {
    const blocks = blocksForCourse(courseName);
    if (blocks.length === 0) return '';
    const teacherIds = new Set(blocks.map((b) => b.teacherId || ''));
    return teacherIds.size === 1 ? [...teacherIds][0] : '';
  };

  const handleSetCourseTeacher = async (courseName, newTeacherId) => {
    const blocks = blocksForCourse(courseName);
    if (blocks.length === 0) return;
    setSavingTeacherFor(courseName);
    setCoursesError(null);
    try {
      await Promise.all(
        blocks.map((block) =>
          updateAssignment(block.id, {
            groupId: block.groupId,
            courseId: block.courseId,
            blockLength: block.blockLength,
            pinned: block.pinned,
            teacherId: newTeacherId || null,
            blockTimeslotId: block.blockTimeslotId,
            roomName: block.roomName,
            satisfiesRoomType: block.satisfiesRoomType,
            preferredRoomHint: block.preferredRoomHint,
          })
        )
      );
      await loadGroupAssignments(editingGroup.id);
      showToast(t('groups.courses.teacherUpdatedMessage'));
    } catch (err) {
      setCoursesError(err.response?.data?.message || t('groups.courses.teacherUpdateFailedPrefix') + err.message);
    } finally {
      setSavingTeacherFor(null);
    }
  };

  // Pre-assigns a teacher for a (group, course) pair before blocks exist - stored on
  // group_course.default_teacher_id and applied by "Generate Blocks" to every block it
  // creates for this pairing. Only relevant while blocksForCourse(courseName) is empty.
  const handleSetDefaultTeacher = async (courseName, newTeacherId) => {
    setSavingTeacherFor(courseName);
    setCoursesError(null);
    try {
      await setGroupCourseDefaultTeacher(editingGroup.id, courseName, newTeacherId || null);
      await loadGroupCourses(editingGroup.id);
      showToast(t('groups.courses.defaultTeacherSavedMessage'));
    } catch (err) {
      setCoursesError(err.response?.data?.message || t('groups.courses.teacherUpdateFailedPrefix') + err.message);
    } finally {
      setSavingTeacherFor(null);
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
    const studentCount = formData.get('studentCount');
    const group = {
      id: formData.get('id'),
      name: formData.get('name'),
      studentCount: studentCount ? parseInt(studentCount, 10) : null,
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
    loadGroupAssignments(group.id);
    setRangesError(null);
    setShowRangeForm(false);
    setEditingRange(null);
    loadRoomRanges(group.id);
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
    setGroupAssignments([]);
    setCoursesError(null);
    setCourseToAdd('');
    setRoomRanges([]);
    setRangesError(null);
    setShowRangeForm(false);
    setEditingRange(null);
  };

  const handleAddRange = () => {
    setEditingRange(null);
    setRangeForm(EMPTY_RANGE_FORM);
    setRangesError(null);
    setShowRangeForm(true);
  };

  const handleEditRange = (range) => {
    setEditingRange(range);
    setRangeForm({ roomType: range.roomType, roomName: range.roomName });
    setRangesError(null);
    setShowRangeForm(true);
  };

  const handleCancelRange = () => {
    setShowRangeForm(false);
    setEditingRange(null);
    setRangesError(null);
  };

  const handleRangeField = (e) => {
    const { name, value } = e.target;
    setRangeForm({ ...rangeForm, [name]: value });
  };

  const handleSubmitRange = async (e) => {
    e.preventDefault();
    setRangesError(null);
    const payload = { roomType: rangeForm.roomType, roomName: rangeForm.roomName };
    try {
      if (editingRange) {
        await updateGroupRoomRange(editingGroup.id, editingRange.id, payload);
      } else {
        await createGroupRoomRange(editingGroup.id, payload);
      }
      setShowRangeForm(false);
      setEditingRange(null);
      loadRoomRanges(editingGroup.id);
      showToast(t('groups.roomRanges.savedMessage'));
    } catch (err) {
      setRangesError(err.response?.data?.message || t('groups.roomRanges.saveFailedPrefix') + err.message);
    }
  };

  const handleDeleteRange = async (id) => {
    if (!(await confirmAction(t('groups.roomRanges.confirmDelete')))) return;
    try {
      await deleteGroupRoomRange(editingGroup.id, id);
      loadRoomRanges(editingGroup.id);
      showToast(t('groups.roomRanges.deletedMessage'));
    } catch (err) {
      setRangesError(err.response?.data?.message || t('groups.roomRanges.deleteFailedPrefix') + err.message);
    }
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
                setGroupAssignments([]);
                setCoursesError(null);
                setCourseToAdd('');
                setRoomRanges([]);
                setRangesError(null);
                setShowRangeForm(false);
                setEditingRange(null);
              }}
            >
              {t('groups.addGroup')}
            </button>
          </WriteOnly>
        </div>
      </div>

      {error && <div className="error" role="alert">{error}</div>}

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
              <label>{t('groups.fields.studentCount')}</label>
              <input type="number" name="studentCount" min={1} defaultValue={editingGroup?.studentCount ?? ''} placeholder={t('groups.studentCountPlaceholder')} />
            </div>
            <div style={{ display: 'flex', gap: '10px' }}>
              <button type="submit" className="btn btn-primary">{t('common.save')}</button>
              <button type="button" className="btn btn-secondary" onClick={handleCancel}>{t('common.cancel')}</button>
            </div>
          </form>
        </div>
      )}

      {showForm && editingGroup && (
        <div className="card table-wrap">
          <h3>{t('groups.courses.title')}</h3>
          <p style={{ marginTop: '8px', color: 'var(--color-text-secondary)', fontSize: '13px' }}>
            {t('groups.courses.description')}
          </p>
          {coursesError && <div className="error" role="alert">{coursesError}</div>}

          <WriteOnly>
            <div style={{ display: 'flex', gap: '10px', marginTop: '12px', marginBottom: '12px' }}>
              <select value={courseToAdd} onChange={(e) => setCourseToAdd(e.target.value)} style={{ flex: 1 }}>
                <option value="">{t('groups.courses.selectPlaceholder')}</option>
                {allCourses
                  .filter((c) => c.active !== false && !groupCourses.some((gc) => gc.courseName === c.name))
                  .map((c) => (
                    <option key={c.id} value={c.name}>
                      {c.id} - {c.name}{!qualifiedCourseNames.has(c.name) ? ` (${t('groups.courses.noQualifiedTeacherShort')})` : ''}
                    </option>
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
                <th>{t('groups.courses.table.teacher')}</th>
                <th>{t('common.actions')}</th>
              </tr>
            </thead>
            <tbody>
              {groupCourses.map((gc) => {
                const blocks = blocksForCourse(gc.courseName);
                const qualifiedTeachers = teachersForCourseName(gc.courseName);
                return (
                  <tr key={gc.courseName}>
                    <td>
                      {gc.courseName}
                      {!qualifiedCourseNames.has(gc.courseName) && (
                        <span
                          title={t('groups.courses.noQualifiedTeacherHint')}
                          style={{ color: '#e67e22', marginLeft: '8px', fontSize: '12px', whiteSpace: 'nowrap' }}
                        >
                          ⚠ {t('groups.courses.noQualifiedTeacherShort')}
                        </span>
                      )}
                    </td>
                    <td>
                      {blocks.length === 0 ? (
                        canWrite() ? (
                          <>
                            <select
                              value={gc.defaultTeacherId || ''}
                              disabled={savingTeacherFor === gc.courseName}
                              onChange={(e) => handleSetDefaultTeacher(gc.courseName, e.target.value)}
                            >
                              <option value="">{t('common.noneOption')}</option>
                              {qualifiedTeachers.map((qt) => (
                                <option key={qt.id} value={qt.id}>{qt.id} - {qt.name} {qt.lastName}</option>
                              ))}
                            </select>
                            <div style={{ color: 'var(--color-text-secondary)', fontSize: '12px', marginTop: '2px' }}>
                              {t('groups.courses.noBlocksYetHint')}
                            </div>
                          </>
                        ) : (
                          (() => {
                            const currentTeacher = teachers.find((tc) => tc.id === gc.defaultTeacherId);
                            return currentTeacher
                              ? `${currentTeacher.name} ${currentTeacher.lastName} (${t('groups.courses.noBlocksYet')})`
                              : t('common.noneOption');
                          })()
                        )
                      ) : canWrite() ? (
                        <select
                          value={teacherForCourse(gc.courseName)}
                          disabled={savingTeacherFor === gc.courseName}
                          onChange={(e) => handleSetCourseTeacher(gc.courseName, e.target.value)}
                        >
                          <option value="">{t('common.noneOption')}</option>
                          {qualifiedTeachers.map((qt) => (
                            <option key={qt.id} value={qt.id}>{qt.id} - {qt.name} {qt.lastName}</option>
                          ))}
                        </select>
                      ) : (
                        (() => {
                          const currentId = teacherForCourse(gc.courseName);
                          const currentTeacher = teachers.find((tc) => tc.id === currentId);
                          return currentTeacher
                            ? `${currentTeacher.name} ${currentTeacher.lastName}`
                            : t('common.noneOption');
                        })()
                      )}
                    </td>
                    <td>
                      <WriteOnly>
                        <button className="btn btn-danger" onClick={() => handleRemoveGroupCourse(gc.courseName)}>
                          {t('common.delete')}
                        </button>
                      </WriteOnly>
                    </td>
                  </tr>
                );
              })}
              {groupCourses.length === 0 && (
                <tr>
                  <td colSpan={3} style={{ color: 'var(--color-text-secondary)' }}>{t('groups.courses.none')}</td>
                </tr>
              )}
            </tbody>
          </table>
        </div>
      )}

      {showForm && editingGroup && (
        <div className="card table-wrap">
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
            <h3>{t('groups.roomRanges.title')}</h3>
            <WriteOnly>
              <button className="btn btn-success" onClick={handleAddRange}>
                {t('groups.roomRanges.addRange')}
              </button>
            </WriteOnly>
          </div>
          <p style={{ marginTop: '8px', color: 'var(--color-text-secondary)', fontSize: '13px' }}>
            {t('groups.roomRanges.description')}
          </p>
          {rangesError && <div className="error" role="alert">{rangesError}</div>}

          {showRangeForm && (
            <form onSubmit={handleSubmitRange} style={{ marginTop: '12px' }}>
              <h4>{editingRange ? t('groups.roomRanges.editRange') : t('groups.roomRanges.newRange')}</h4>
              <div className="form-group">
                <label>{t('groups.roomRanges.fields.roomType')}</label>
                <select name="roomType" value={rangeForm.roomType} onChange={handleRangeField}>
                  {ROOM_TYPES.map((type) => (
                    <option key={type} value={type}>{type}</option>
                  ))}
                </select>
              </div>
              <div className="form-group">
                <label>{t('groups.roomRanges.fields.roomName')}</label>
                <select name="roomName" value={rangeForm.roomName} onChange={handleRangeField} required>
                  <option value="">{t('common.noneOption')}</option>
                  {rooms.map((r) => (
                    <option key={r.name} value={r.name}>{r.name} ({r.type})</option>
                  ))}
                </select>
              </div>
              <div style={{ display: 'flex', gap: '10px' }}>
                <button type="submit" className="btn btn-primary">{t('common.save')}</button>
                <button type="button" className="btn btn-secondary" onClick={handleCancelRange}>{t('common.cancel')}</button>
              </div>
            </form>
          )}

          <table style={{ marginTop: '12px' }}>
            <thead>
              <tr>
                <th>{t('groups.roomRanges.table.roomType')}</th>
                <th>{t('groups.roomRanges.table.roomName')}</th>
                <th>{t('common.actions')}</th>
              </tr>
            </thead>
            <tbody>
              {roomRanges.map((range) => (
                <tr key={range.id}>
                  <td>{range.roomType}</td>
                  <td>{range.roomName}</td>
                  <td>
                    <WriteOnly>
                      <button className="btn btn-primary" onClick={() => handleEditRange(range)} style={{ marginRight: '5px' }}>
                        {t('common.edit')}
                      </button>
                      <button className="btn btn-danger" onClick={() => handleDeleteRange(range.id)}>
                        {t('common.delete')}
                      </button>
                    </WriteOnly>
                  </td>
                </tr>
              ))}
              {roomRanges.length === 0 && (
                <tr>
                  <td colSpan={3} style={{ color: 'var(--color-text-secondary)' }}>{t('groups.roomRanges.none')}</td>
                </tr>
              )}
            </tbody>
          </table>
        </div>
      )}

      <div className="card table-wrap">
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
              <th>{t('groups.table.studentCount')}</th>
              <th>{t('common.actions')}</th>
            </tr>
          </thead>
          <tbody>
            {pageItems.map(group => (
              <tr key={group.id}>
                <td>{group.id}</td>
                <td>{group.name}</td>
                <td>{group.studentCount ?? '-'}</td>
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
