import React, { useState, useEffect } from 'react';
import { useTranslation } from 'react-i18next';
import {
  getCourses, createCourse, updateCourse, deleteCourse, getCourseComponents,
  getCourseRoomRequirements, createCourseRoomRequirement, updateCourseRoomRequirement, deleteCourseRoomRequirement,
  getCourseBlockTemplates, createCourseBlockTemplate, updateCourseBlockTemplate, deleteCourseBlockTemplate,
  getCourseIdsWithRoomRequirements,
  getRooms, getGroups, listTimeslots,
} from '../api';
import WriteOnly from '../auth/WriteOnly';
import { useToast } from '../ui/ToastContext';
import { useConfirm } from '../ui/ConfirmContext';
import { ROOM_TYPES } from '../constants';
import { usePagination, Pagination, DEFAULT_PAGE_SIZE } from '../ui/Pagination';

// The `course` table enforces `CHECK (semester BETWEEN 1 AND 12)`.
const SEMESTERS = Array.from({ length: 12 }, (_, i) => i + 1);

const EMPTY_REQUIREMENT_FORM = { roomType: ROOM_TYPES[0], hoursRequired: 1, priority: 1, defaultPreferredRoom: '' };

const EMPTY_TEMPLATE_FORM = {
  groupId: '', blockIndex: 0, blockLength: 1, roomType: '', preferredRoomName: '',
  preferredDay: '', pinAssignment: false, preferredTimeslotId: '',
};

const DAY_KEYS = { 1: 'mon', 2: 'tue', 3: 'wed', 4: 'thu', 5: 'fri' };

function Courses() {
  const { t } = useTranslation();
  const showToast = useToast();
  const confirmAction = useConfirm();
  const [courses, setCourses] = useState([]);
  const [searchQuery, setSearchQuery] = useState('');
  const [components, setComponents] = useState([]);
  const [rooms, setRooms] = useState([]);
  const [groups, setGroups] = useState([]);
  const [timeslots, setTimeslots] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [fieldErrors, setFieldErrors] = useState({});
  const [editingCourse, setEditingCourse] = useState(null);
  const [showForm, setShowForm] = useState(false);

  const [roomRequirements, setRoomRequirements] = useState([]);
  const [requirementsError, setRequirementsError] = useState(null);
  const [showRequirementForm, setShowRequirementForm] = useState(false);
  const [editingRequirement, setEditingRequirement] = useState(null);
  const [requirementForm, setRequirementForm] = useState(EMPTY_REQUIREMENT_FORM);
  const [coursesWithRequirements, setCoursesWithRequirements] = useState(new Set());

  const [blockTemplates, setBlockTemplates] = useState([]);
  const [templatesError, setTemplatesError] = useState(null);
  const [showTemplateForm, setShowTemplateForm] = useState(false);
  const [editingTemplate, setEditingTemplate] = useState(null);
  const [templateForm, setTemplateForm] = useState(EMPTY_TEMPLATE_FORM);

  const timeslotLabel = (ts) => {
    const dayLabel = t(`common.days.${DAY_KEYS[ts.dayOfWeek] || 'mon'}`);
    return `${dayLabel} ${ts.startHour}:00-${ts.startHour + ts.lengthHours}:00`;
  };

  const timeslotDisplay = (timeslotId) => {
    if (!timeslotId) return '-';
    const match = timeslots.find((ts) => ts.id === timeslotId);
    return match ? timeslotLabel(match) : timeslotId;
  };

  useEffect(() => {
    loadCourses();
    loadComponents();
    loadRooms();
    loadGroups();
    loadTimeslots();
    loadCoursesWithRequirements();
  }, []);

  const loadCoursesWithRequirements = async () => {
    try {
      const response = await getCourseIdsWithRoomRequirements();
      setCoursesWithRequirements(new Set(response.data));
    } catch (err) {
      // Non-critical: the list table just won't show the "dual" badge.
    }
  };

  const loadComponents = async () => {
    try {
      const response = await getCourseComponents();
      setComponents(response.data);
    } catch (err) {
      // Non-critical: the datalist just won't have suggestions.
    }
  };

  const loadRooms = async () => {
    try {
      const response = await getRooms();
      setRooms(response.data);
    } catch (err) {
      // Non-critical: the preferred-room dropdown just won't have options.
    }
  };

  const loadGroups = async () => {
    try {
      const response = await getGroups();
      setGroups(response.data);
    } catch (err) {
      // Non-critical: the group dropdown just won't have options.
    }
  };

  const loadTimeslots = async () => {
    try {
      const response = await listTimeslots();
      setTimeslots(response.data);
    } catch (err) {
      // Non-critical: the timeslot dropdown just won't have options.
    }
  };

  const loadRoomRequirements = async (courseId) => {
    try {
      const response = await getCourseRoomRequirements(courseId);
      setRoomRequirements(response.data);
      setRequirementsError(null);
    } catch (err) {
      setRequirementsError(t('courses.roomRequirements.loadFailedPrefix') + err.message);
    }
  };

  const loadBlockTemplates = async (courseId) => {
    try {
      const response = await getCourseBlockTemplates(courseId);
      setBlockTemplates(response.data);
      setTemplatesError(null);
    } catch (err) {
      setTemplatesError(t('courses.blockTemplates.loadFailedPrefix') + err.message);
    }
  };

  const loadCourses = async () => {
    try {
      setLoading(true);
      const response = await getCourses();
      setCourses(response.data);
      setError(null);
    } catch (err) {
      setError(t('courses.loadFailedPrefix') + err.message);
    } finally {
      setLoading(false);
    }
  };

  const handleSubmit = async (e) => {
    e.preventDefault();
    setFieldErrors({});
    setError(null);
    const formData = new FormData(e.target);
    const course = {
      id: formData.get('id'),
      name: formData.get('name'),
      abbreviation: formData.get('abbreviation'),
      semester: parseInt(formData.get('semester')),
      component: formData.get('component'),
      roomRequirement: formData.get('roomRequirement'),
      requiredHoursPerWeek: parseInt(formData.get('requiredHoursPerWeek')),
      active: formData.get('active') === 'true',
    };

    try {
      if (editingCourse) {
        await updateCourse(editingCourse.id, course);
      } else {
        await createCourse(course);
      }
      setShowForm(false);
      setEditingCourse(null);
      loadCourses();
      loadComponents();
      showToast(t('courses.savedMessage'));
    } catch (err) {
      const data = err.response?.data;
      if (data?.errors) {
        setFieldErrors(data.errors);
      }
      setError(data?.message || t('courses.saveFailedPrefix') + err.message);
    }
  };

  const handleEdit = (course) => {
    setEditingCourse(course);
    setFieldErrors({});
    setError(null);
    setShowForm(true);
    setShowRequirementForm(false);
    setEditingRequirement(null);
    setRoomRequirements([]);
    loadRoomRequirements(course.id);
    setShowTemplateForm(false);
    setEditingTemplate(null);
    loadBlockTemplates(course.id);
  };

  const handleDelete = async (id) => {
    if (!(await confirmAction(t('courses.confirmDelete')))) return;
    try {
      await deleteCourse(id);
      loadCourses();
      showToast(t('courses.deletedMessage'));
    } catch (err) {
      setError(err.response?.data?.message || t('courses.deleteFailedPrefix') + err.message);
    }
  };

  const handleCancel = () => {
    setShowForm(false);
    setEditingCourse(null);
    setFieldErrors({});
    setError(null);
    setRoomRequirements([]);
    setShowRequirementForm(false);
    setEditingRequirement(null);
    setBlockTemplates([]);
    setShowTemplateForm(false);
    setEditingTemplate(null);
  };

  const handleAddRequirement = () => {
    setEditingRequirement(null);
    setRequirementForm(EMPTY_REQUIREMENT_FORM);
    setRequirementsError(null);
    setShowRequirementForm(true);
  };

  const handleEditRequirement = (req) => {
    setEditingRequirement(req);
    setRequirementForm({
      roomType: req.roomType,
      hoursRequired: req.hoursRequired,
      priority: req.priority,
      defaultPreferredRoom: req.defaultPreferredRoom || '',
    });
    setRequirementsError(null);
    setShowRequirementForm(true);
  };

  const handleCancelRequirement = () => {
    setShowRequirementForm(false);
    setEditingRequirement(null);
    setRequirementsError(null);
  };

  const handleRequirementField = (e) => {
    const { name, value } = e.target;
    setRequirementForm({ ...requirementForm, [name]: value });
  };

  const handleSubmitRequirement = async (e) => {
    e.preventDefault();
    setRequirementsError(null);
    const payload = {
      roomType: requirementForm.roomType,
      hoursRequired: parseInt(requirementForm.hoursRequired, 10),
      priority: parseInt(requirementForm.priority, 10) || 1,
      defaultPreferredRoom: requirementForm.defaultPreferredRoom || null,
    };
    try {
      if (editingRequirement) {
        await updateCourseRoomRequirement(editingCourse.id, editingRequirement.id, payload);
      } else {
        await createCourseRoomRequirement(editingCourse.id, payload);
      }
      setShowRequirementForm(false);
      setEditingRequirement(null);
      loadRoomRequirements(editingCourse.id);
      loadCoursesWithRequirements();
      showToast(t('courses.roomRequirements.savedMessage'));
    } catch (err) {
      setRequirementsError(err.response?.data?.message || t('courses.roomRequirements.saveFailedPrefix') + err.message);
    }
  };

  const handleDeleteRequirement = async (id) => {
    if (!(await confirmAction(t('courses.roomRequirements.confirmDelete')))) return;
    try {
      await deleteCourseRoomRequirement(editingCourse.id, id);
      loadRoomRequirements(editingCourse.id);
      loadCoursesWithRequirements();
      showToast(t('courses.roomRequirements.deletedMessage'));
    } catch (err) {
      setRequirementsError(err.response?.data?.message || t('courses.roomRequirements.deleteFailedPrefix') + err.message);
    }
  };

  const handleAddTemplate = () => {
    setEditingTemplate(null);
    setTemplateForm(EMPTY_TEMPLATE_FORM);
    setTemplatesError(null);
    setShowTemplateForm(true);
  };

  const handleEditTemplate = (tpl) => {
    setEditingTemplate(tpl);
    setTemplateForm({
      groupId: tpl.groupId || '',
      blockIndex: tpl.blockIndex,
      blockLength: tpl.blockLength,
      roomType: tpl.roomType || '',
      preferredRoomName: tpl.preferredRoomName || '',
      preferredDay: tpl.preferredDay ?? '',
      pinAssignment: tpl.pinAssignment || false,
      preferredTimeslotId: tpl.preferredTimeslotId || '',
    });
    setTemplatesError(null);
    setShowTemplateForm(true);
  };

  const handleCancelTemplate = () => {
    setShowTemplateForm(false);
    setEditingTemplate(null);
    setTemplatesError(null);
  };

  const handleTemplateField = (e) => {
    const { name, type, checked, value } = e.target;
    setTemplateForm({ ...templateForm, [name]: type === 'checkbox' ? checked : value });
  };

  const handleSubmitTemplate = async (e) => {
    e.preventDefault();
    setTemplatesError(null);
    if (templateForm.pinAssignment && !templateForm.preferredTimeslotId) {
      setTemplatesError(t('courses.blockTemplates.pinnedRequiresTimeslotError'));
      return;
    }
    const payload = {
      groupId: templateForm.groupId || null,
      blockIndex: parseInt(templateForm.blockIndex, 10),
      blockLength: parseInt(templateForm.blockLength, 10),
      roomType: templateForm.roomType || null,
      preferredRoomName: templateForm.preferredRoomName || null,
      preferredDay: templateForm.preferredDay === '' ? null : parseInt(templateForm.preferredDay, 10),
      pinAssignment: templateForm.pinAssignment,
      preferredTimeslotId: templateForm.preferredTimeslotId || null,
    };
    try {
      if (editingTemplate) {
        await updateCourseBlockTemplate(editingCourse.id, editingTemplate.id, payload);
      } else {
        await createCourseBlockTemplate(editingCourse.id, payload);
      }
      setShowTemplateForm(false);
      setEditingTemplate(null);
      loadBlockTemplates(editingCourse.id);
      showToast(t('courses.blockTemplates.savedMessage'));
    } catch (err) {
      setTemplatesError(err.response?.data?.message || t('courses.blockTemplates.saveFailedPrefix') + err.message);
    }
  };

  const handleDeleteTemplate = async (id) => {
    if (!(await confirmAction(t('courses.blockTemplates.confirmDelete')))) return;
    try {
      await deleteCourseBlockTemplate(editingCourse.id, id);
      loadBlockTemplates(editingCourse.id);
      showToast(t('courses.blockTemplates.deletedMessage'));
    } catch (err) {
      setTemplatesError(err.response?.data?.message || t('courses.blockTemplates.deleteFailedPrefix') + err.message);
    }
  };

  const filteredCourses = courses.filter((course) => {
    const query = searchQuery.trim().toLowerCase();
    if (!query) return true;
    return (
      course.id?.toLowerCase().includes(query) ||
      course.name?.toLowerCase().includes(query)
    );
  });
  const { page, setPage, pageCount, pageItems, totalItems } = usePagination(filteredCourses);

  if (loading) return <div className="loading">{t('courses.loading')}</div>;

  return (
    <div>
      <div className="card">
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
          <h2>{t('courses.title')}</h2>
          <WriteOnly>
            <button
              className="btn btn-success"
              onClick={() => {
                setEditingCourse(null);
                setFieldErrors({});
                setError(null);
                setShowForm(true);
                setRoomRequirements([]);
                setShowRequirementForm(false);
                setEditingRequirement(null);
                setBlockTemplates([]);
                setShowTemplateForm(false);
                setEditingTemplate(null);
              }}
            >
              {t('courses.addCourse')}
            </button>
          </WriteOnly>
        </div>
      </div>

      {error && <div className="error">{error}</div>}

      {showForm && (
        <div className="card">
          <h3>{editingCourse ? t('courses.editCourse') : t('courses.newCourse')}</h3>
          <form onSubmit={handleSubmit}>
            <div className="form-group">
              <label>{t('courses.fields.id')}</label>
              <input
                type="text"
                name="id"
                defaultValue={editingCourse?.id || ''}
                maxLength={5}
                required
                disabled={!!editingCourse}
              />
              {fieldErrors.id && <div className="error">{fieldErrors.id}</div>}
            </div>
            <div className="form-group">
              <label>{t('courses.fields.name')}</label>
              <input type="text" name="name" defaultValue={editingCourse?.name || ''} required />
              {fieldErrors.name && <div className="error">{fieldErrors.name}</div>}
            </div>
            <div className="form-group">
              <label>{t('courses.fields.abbreviation')}</label>
              <input
                type="text"
                name="abbreviation"
                defaultValue={editingCourse?.abbreviation || ''}
                maxLength={100}
                required
              />
              {fieldErrors.abbreviation && <div className="error">{fieldErrors.abbreviation}</div>}
            </div>
            <div className="form-group">
              <label>{t('courses.fields.semester')}</label>
              <select name="semester" defaultValue={editingCourse?.semester || 1}>
                {SEMESTERS.map((s) => (
                  <option key={s} value={s}>{s}</option>
                ))}
              </select>
              {fieldErrors.semester && <div className="error">{fieldErrors.semester}</div>}
            </div>
            <div className="form-group">
              <label>{t('courses.fields.component')}</label>
              <input
                type="text"
                name="component"
                list="component-options"
                defaultValue={editingCourse?.component || ''}
                maxLength={20}
                required
                placeholder={t('courses.componentPlaceholder')}
              />
              <datalist id="component-options">
                {components.map((c) => (
                  <option key={c} value={c} />
                ))}
              </datalist>
              {fieldErrors.component && <div className="error">{fieldErrors.component}</div>}
            </div>
            <div className="form-group">
              <label>{t('courses.fields.roomRequirement')}</label>
              <select name="roomRequirement" defaultValue={editingCourse?.roomRequirement || 'estándar'}>
                {ROOM_TYPES.map((type) => (
                  <option key={type} value={type}>{type}</option>
                ))}
              </select>
              {roomRequirements.length > 0 && (
                <div style={{ color: '#c0392b', fontSize: '12px', marginTop: '4px' }}>
                  {t('courses.roomRequirements.formNote')}
                </div>
              )}
            </div>
            <div className="form-group">
              <label>{t('courses.fields.requiredHoursPerWeek')}</label>
              <input type="number" name="requiredHoursPerWeek" defaultValue={editingCourse?.requiredHoursPerWeek || 1} required />
            </div>
            <div className="form-group">
              <label>{t('courses.fields.active')}</label>
              <select name="active" defaultValue={editingCourse?.active?.toString() || 'true'}>
                <option value="true">{t('common.yes')}</option>
                <option value="false">{t('common.no')}</option>
              </select>
            </div>
            <div style={{ display: 'flex', gap: '10px' }}>
              <button type="submit" className="btn btn-primary">{t('common.save')}</button>
              <button type="button" className="btn btn-secondary" onClick={handleCancel}>{t('common.cancel')}</button>
            </div>
          </form>
        </div>
      )}

      {showForm && editingCourse && (
        <div className="card">
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
            <h3>{t('courses.roomRequirements.title')}</h3>
            <WriteOnly>
              <button className="btn btn-success" onClick={handleAddRequirement}>
                {t('courses.roomRequirements.addRequirement')}
              </button>
            </WriteOnly>
          </div>
          <p style={{ marginTop: '8px', color: '#7f8c8d', fontSize: '13px' }}>
            {t('courses.roomRequirements.description')}
          </p>
          {requirementsError && <div className="error">{requirementsError}</div>}

          {showRequirementForm && (
            <form onSubmit={handleSubmitRequirement} style={{ marginTop: '12px' }}>
              <h4>{editingRequirement ? t('courses.roomRequirements.editRequirement') : t('courses.roomRequirements.newRequirement')}</h4>
              <div className="form-group">
                <label>{t('courses.roomRequirements.fields.roomType')}</label>
                <select name="roomType" value={requirementForm.roomType} onChange={handleRequirementField}>
                  {ROOM_TYPES.map((type) => (
                    <option key={type} value={type}>{type}</option>
                  ))}
                </select>
              </div>
              <div className="form-group">
                <label>{t('courses.roomRequirements.fields.hoursRequired')}</label>
                <input
                  type="number"
                  name="hoursRequired"
                  min={1}
                  value={requirementForm.hoursRequired}
                  onChange={handleRequirementField}
                  required
                />
              </div>
              <div className="form-group">
                <label>{t('courses.roomRequirements.fields.priority')}</label>
                <input
                  type="number"
                  name="priority"
                  min={1}
                  value={requirementForm.priority}
                  onChange={handleRequirementField}
                />
              </div>
              <div className="form-group">
                <label>{t('courses.roomRequirements.fields.preferredRoom')}</label>
                <select name="defaultPreferredRoom" value={requirementForm.defaultPreferredRoom} onChange={handleRequirementField}>
                  <option value="">{t('common.noneOption')}</option>
                  {rooms.map((r) => (
                    <option key={r.name} value={r.name}>{r.name} ({r.type})</option>
                  ))}
                </select>
              </div>
              <div style={{ display: 'flex', gap: '10px' }}>
                <button type="submit" className="btn btn-primary">{t('common.save')}</button>
                <button type="button" className="btn btn-secondary" onClick={handleCancelRequirement}>{t('common.cancel')}</button>
              </div>
            </form>
          )}

          <table style={{ marginTop: '12px' }}>
            <thead>
              <tr>
                <th>{t('courses.roomRequirements.table.roomType')}</th>
                <th>{t('courses.roomRequirements.table.hoursRequired')}</th>
                <th>{t('courses.roomRequirements.table.priority')}</th>
                <th>{t('courses.roomRequirements.table.preferredRoom')}</th>
                <th>{t('common.actions')}</th>
              </tr>
            </thead>
            <tbody>
              {roomRequirements.map((req) => (
                <tr key={req.id}>
                  <td>{req.roomType}</td>
                  <td>{req.hoursRequired}</td>
                  <td>{req.priority}</td>
                  <td>{req.defaultPreferredRoom || '-'}</td>
                  <td>
                    <WriteOnly>
                      <button className="btn btn-primary" onClick={() => handleEditRequirement(req)} style={{ marginRight: '5px' }}>
                        {t('common.edit')}
                      </button>
                      <button className="btn btn-danger" onClick={() => handleDeleteRequirement(req.id)}>
                        {t('common.delete')}
                      </button>
                    </WriteOnly>
                  </td>
                </tr>
              ))}
              {roomRequirements.length === 0 && (
                <tr>
                  <td colSpan={5} style={{ color: '#7f8c8d' }}>{t('courses.roomRequirements.none')}</td>
                </tr>
              )}
            </tbody>
          </table>
        </div>
      )}

      {showForm && editingCourse && (
        <div className="card">
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
            <h3>{t('courses.blockTemplates.title')}</h3>
            <WriteOnly>
              <button className="btn btn-success" onClick={handleAddTemplate}>
                {t('courses.blockTemplates.addTemplate')}
              </button>
            </WriteOnly>
          </div>
          <p style={{ marginTop: '8px', color: '#7f8c8d', fontSize: '13px' }}>
            {t('courses.blockTemplates.description')}
          </p>
          {templatesError && <div className="error">{templatesError}</div>}

          {showTemplateForm && (
            <form onSubmit={handleSubmitTemplate} style={{ marginTop: '12px' }}>
              <h4>{editingTemplate ? t('courses.blockTemplates.editTemplate') : t('courses.blockTemplates.newTemplate')}</h4>
              <div className="form-group">
                <label>{t('courses.blockTemplates.fields.group')}</label>
                <select name="groupId" value={templateForm.groupId} onChange={handleTemplateField}>
                  <option value="">{t('courses.blockTemplates.allGroups')}</option>
                  {groups.map((g) => (
                    <option key={g.id} value={g.id}>{g.id} - {g.name}</option>
                  ))}
                </select>
              </div>
              <div className="form-group">
                <label>{t('courses.blockTemplates.fields.blockIndex')}</label>
                <input
                  type="number"
                  name="blockIndex"
                  min={0}
                  value={templateForm.blockIndex}
                  onChange={handleTemplateField}
                  required
                />
              </div>
              <div className="form-group">
                <label>{t('courses.blockTemplates.fields.blockLength')}</label>
                <input
                  type="number"
                  name="blockLength"
                  min={1}
                  max={4}
                  value={templateForm.blockLength}
                  onChange={handleTemplateField}
                  required
                />
              </div>
              <div className="form-group">
                <label>{t('courses.blockTemplates.fields.roomType')}</label>
                <select name="roomType" value={templateForm.roomType} onChange={handleTemplateField}>
                  <option value="">{t('common.noneOption')}</option>
                  {ROOM_TYPES.map((type) => (
                    <option key={type} value={type}>{type}</option>
                  ))}
                </select>
              </div>
              <div className="form-group">
                <label>{t('courses.blockTemplates.fields.preferredRoom')}</label>
                <select name="preferredRoomName" value={templateForm.preferredRoomName} onChange={handleTemplateField}>
                  <option value="">{t('common.noneOption')}</option>
                  {rooms.map((r) => (
                    <option key={r.name} value={r.name}>{r.name} ({r.type})</option>
                  ))}
                </select>
              </div>
              <div className="form-group">
                <label>{t('courses.blockTemplates.fields.preferredDay')}</label>
                <select name="preferredDay" value={templateForm.preferredDay} onChange={handleTemplateField}>
                  <option value="">{t('common.noneOption')}</option>
                  {Object.entries(DAY_KEYS).map(([value, key]) => (
                    <option key={value} value={value}>{t(`common.days.${key}`)}</option>
                  ))}
                </select>
              </div>
              <div className="form-group">
                <label>{t('courses.blockTemplates.fields.preferredTimeslot')}</label>
                <select name="preferredTimeslotId" value={templateForm.preferredTimeslotId} onChange={handleTemplateField}>
                  <option value="">{t('common.noneOption')}</option>
                  {timeslots.map((ts) => (
                    <option key={ts.id} value={ts.id}>{timeslotLabel(ts)}</option>
                  ))}
                </select>
              </div>
              <div className="form-group">
                <label>
                  <input
                    type="checkbox"
                    name="pinAssignment"
                    checked={templateForm.pinAssignment}
                    onChange={handleTemplateField}
                    style={{ marginRight: '6px' }}
                  />
                  {t('courses.blockTemplates.fields.pinAssignment')}
                </label>
                <div style={{ color: '#7f8c8d', fontSize: '12px', marginTop: '4px' }}>
                  {t('courses.blockTemplates.pinHint')}
                </div>
              </div>
              <div style={{ display: 'flex', gap: '10px' }}>
                <button type="submit" className="btn btn-primary">{t('common.save')}</button>
                <button type="button" className="btn btn-secondary" onClick={handleCancelTemplate}>{t('common.cancel')}</button>
              </div>
            </form>
          )}

          <table style={{ marginTop: '12px' }}>
            <thead>
              <tr>
                <th>{t('courses.blockTemplates.table.group')}</th>
                <th>{t('courses.blockTemplates.table.blockIndex')}</th>
                <th>{t('courses.blockTemplates.table.blockLength')}</th>
                <th>{t('courses.blockTemplates.table.roomType')}</th>
                <th>{t('courses.blockTemplates.table.preferredRoom')}</th>
                <th>{t('courses.blockTemplates.table.preferredDay')}</th>
                <th>{t('courses.blockTemplates.table.timeslot')}</th>
                <th>{t('courses.blockTemplates.table.pinned')}</th>
                <th>{t('common.actions')}</th>
              </tr>
            </thead>
            <tbody>
              {blockTemplates.map((tpl) => (
                <tr key={tpl.id}>
                  <td>{tpl.groupId || t('courses.blockTemplates.allGroups')}</td>
                  <td>{tpl.blockIndex}</td>
                  <td>{tpl.blockLength}</td>
                  <td>{tpl.roomType || '-'}</td>
                  <td>{tpl.preferredRoomName || '-'}</td>
                  <td>{tpl.preferredDay ? t(`common.days.${DAY_KEYS[tpl.preferredDay]}`) : '-'}</td>
                  <td>{timeslotDisplay(tpl.preferredTimeslotId)}</td>
                  <td>{tpl.pinAssignment ? '📌' : ''}</td>
                  <td>
                    <WriteOnly>
                      <button className="btn btn-primary" onClick={() => handleEditTemplate(tpl)} style={{ marginRight: '5px' }}>
                        {t('common.edit')}
                      </button>
                      <button className="btn btn-danger" onClick={() => handleDeleteTemplate(tpl.id)}>
                        {t('common.delete')}
                      </button>
                    </WriteOnly>
                  </td>
                </tr>
              ))}
              {blockTemplates.length === 0 && (
                <tr>
                  <td colSpan={9} style={{ color: '#7f8c8d' }}>{t('courses.blockTemplates.none')}</td>
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
            placeholder={t('courses.searchPlaceholder')}
          />
        </div>
        <table>
          <thead>
            <tr>
              <th>{t('courses.table.id')}</th>
              <th>{t('courses.table.name')}</th>
              <th>{t('courses.table.semester')}</th>
              <th>{t('courses.table.roomReq')}</th>
              <th>{t('courses.table.hoursWeek')}</th>
              <th>{t('courses.table.active')}</th>
              <th>{t('common.actions')}</th>
            </tr>
          </thead>
          <tbody>
            {pageItems.map(course => (
              <tr key={course.id}>
                <td>{course.id}</td>
                <td>{course.name}</td>
                <td>{course.semester}</td>
                <td>
                  {course.roomRequirement}
                  {coursesWithRequirements.has(course.id) && (
                    <span
                      title={t('courses.roomRequirements.formNote')}
                      style={{
                        marginLeft: '6px',
                        fontSize: '11px',
                        color: '#c0392b',
                        border: '1px solid #c0392b',
                        borderRadius: '10px',
                        padding: '1px 6px',
                      }}
                    >
                      {t('courses.roomRequirements.dualBadge')}
                    </span>
                  )}
                </td>
                <td>{course.requiredHoursPerWeek}</td>
                <td>{course.active ? '✓' : '✗'}</td>
                <td>
                  <WriteOnly>
                    <button className="btn btn-primary" onClick={() => handleEdit(course)} style={{ marginRight: '5px' }}>
                      {t('common.edit')}
                    </button>
                    <button className="btn btn-danger" onClick={() => handleDelete(course.id)}>
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

export default Courses;
