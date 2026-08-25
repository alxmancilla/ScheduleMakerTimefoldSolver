import axios from 'axios';

// Base URL for API calls. In development this defaults to '/api', which Vite
// proxies to the backend (see vite.config.js). In production, set
// VITE_API_BASE_URL at build time to the backend's public URL (e.g.
// https://api.example.com/api) when the SPA is deployed on a different origin.
const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || '/api';

const api = axios.create({
  baseURL: API_BASE_URL,
  headers: {
    'Content-Type': 'application/json',
  },
});

// localStorage key holding the signed JWT.
export const TOKEN_KEY = 'auth_token';

export const getToken = () => localStorage.getItem(TOKEN_KEY);
export const setToken = (token) => localStorage.setItem(TOKEN_KEY, token);
export const clearToken = () => localStorage.removeItem(TOKEN_KEY);

// Attach the Bearer token (when present) to every request.
api.interceptors.request.use((config) => {
  const token = getToken();
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

// On 401 (expired/invalid token), drop the token and bounce to the login page.
// The login request itself is exempt so a bad-credentials error surfaces inline.
api.interceptors.response.use(
  (response) => response,
  (error) => {
    const status = error.response && error.response.status;
    const url = error.config && error.config.url;
    const isLogin = url && url.includes('/auth/login');
    if (status === 401 && !isLogin) {
      clearToken();
      if (window.location.pathname !== '/login') {
        window.location.assign('/login');
      }
    }
    return Promise.reject(error);
  }
);

// Auth
export const login = (username, password) => api.post('/auth/login', { username, password });
export const getCurrentUser = () => api.get('/auth/me');
export const updatePreferredLanguage = (language) => api.put('/auth/preferred-language', { language });

// Teachers
export const getTeachers = () => api.get('/teachers');
export const getTeacher = (id) => api.get(`/teachers/${id}`);
export const createTeacher = (teacher) => api.post('/teachers', teacher);
export const updateTeacher = (id, teacher) => api.put(`/teachers/${id}`, teacher);
export const deleteTeacher = (id) => api.delete(`/teachers/${id}`);
export const searchTeachers = (query) => api.get(`/teachers/search?query=${query}`);

// Courses
export const getCourses = () => api.get('/courses');
export const getCourse = (id) => api.get(`/courses/${id}`);
export const createCourse = (course) => api.post('/courses', course);
export const updateCourse = (id, course) => api.put(`/courses/${id}`, course);
export const deleteCourse = (id) => api.delete(`/courses/${id}`);
export const searchCourses = (query) => api.get(`/courses/search?query=${query}`);
export const getActiveCourses = () => api.get('/courses/active');
export const getCourseDesignations = () => api.get('/courses/designations');
export const getCourseIdsWithRoomRequirements = () => api.get('/courses/with-room-requirements');

// Course room requirements (dual room requirements: a course's hours split
// across multiple room types, e.g. 4h in a computer center + 1h standard)
export const getCourseRoomRequirements = (courseId) => api.get(`/courses/${courseId}/room-requirements`);
export const createCourseRoomRequirement = (courseId, requirement) =>
  api.post(`/courses/${courseId}/room-requirements`, requirement);
export const updateCourseRoomRequirement = (courseId, id, requirement) =>
  api.put(`/courses/${courseId}/room-requirements/${id}`, requirement);
export const deleteCourseRoomRequirement = (courseId, id) =>
  api.delete(`/courses/${courseId}/room-requirements/${id}`);

// Course block templates (explicit, hand-authored block decomposition for a
// course - optionally scoped to one group, or every group when groupId is null)
export const getCourseBlockTemplates = (courseId) => api.get(`/courses/${courseId}/block-templates`);
export const createCourseBlockTemplate = (courseId, template) =>
  api.post(`/courses/${courseId}/block-templates`, template);
export const updateCourseBlockTemplate = (courseId, id, template) =>
  api.put(`/courses/${courseId}/block-templates/${id}`, template);
export const deleteCourseBlockTemplate = (courseId, id) =>
  api.delete(`/courses/${courseId}/block-templates/${id}`);

// Group courses (which courses a group takes - group_course, keyed by course name)
export const getGroupCourses = (groupId) => api.get(`/groups/${groupId}/courses`);
export const addGroupCourse = (groupId, courseName) =>
  api.post(`/groups/${groupId}/courses`, { courseName });
export const removeGroupCourse = (groupId, courseName) =>
  api.delete(`/groups/${groupId}/courses/${encodeURIComponent(courseName)}`);
// Pre-assigns (or clears, with teacherId null) a teacher for a group's course before
// blocks exist. Applied automatically by "Generate Blocks" to every block it creates
// for that pairing; has no effect once blocks already exist for it.
export const setGroupCourseDefaultTeacher = (groupId, courseName, teacherId) =>
  api.put(`/groups/${groupId}/courses/${encodeURIComponent(courseName)}/default-teacher`, { teacherId });

// Rooms
export const getRooms = () => api.get('/rooms');
export const getRoom = (name) => api.get(`/rooms/${name}`);
export const createRoom = (room) => api.post('/rooms', room);
export const updateRoom = (name, room) => api.put(`/rooms/${name}`, room);
export const deleteRoom = (name) => api.delete(`/rooms/${name}`);
export const getRoomsByType = (type) => api.get(`/rooms/type/${type}`);
export const getRoomsByBuilding = (building) => api.get(`/rooms/building/${building}`);

// Student Groups
export const getGroups = () => api.get('/groups');
export const getGroup = (id) => api.get(`/groups/${id}`);
export const createGroup = (group) => api.post('/groups', group);
export const updateGroup = (id, group) => api.put(`/groups/${id}`, group);
export const deleteGroup = (id) => api.delete(`/groups/${id}`);
export const searchGroups = (query) => api.get(`/groups/search?query=${query}`);

// Group room ranges (a group's curated acceptable rooms per room type,
// replacing the old single preferredRoomName)
export const getGroupRoomRanges = (groupId) => api.get(`/groups/${groupId}/room-ranges`);
export const createGroupRoomRange = (groupId, range) =>
  api.post(`/groups/${groupId}/room-ranges`, range);
export const updateGroupRoomRange = (groupId, id, range) =>
  api.put(`/groups/${groupId}/room-ranges/${id}`, range);
export const deleteGroupRoomRange = (groupId, id) =>
  api.delete(`/groups/${groupId}/room-ranges/${id}`);

// Assignments
export const getAssignments = () => api.get('/assignments');
export const getAssignment = (id) => api.get(`/assignments/${id}`);
export const createAssignment = (assignment) => api.post('/assignments', assignment);
export const updateAssignment = (id, assignment) => api.put(`/assignments/${id}`, assignment);
export const deleteAssignment = (id) => api.delete(`/assignments/${id}`);
export const getAssignmentsByGroup = (groupId) => api.get(`/assignments/group/${groupId}`);
export const getAssignmentsByTeacher = (teacherId) => api.get(`/assignments/teacher/${teacherId}`);
export const getAssignmentsByRoom = (roomName) => api.get(`/assignments/room/${roomName}`);
export const getAssignedBlocks = () => api.get('/assignments/assigned');
export const getUnassignedBlocks = () => api.get('/assignments/unassigned');
export const getPinnedAssignments = () => api.get('/assignments/pinned');

// Timeslots (read-only, any authenticated role - e.g. for the Assignments form)
export const listTimeslots = () => api.get('/timeslots');

// Admin: Engine (solver)
export const runEngine = (overrides) => api.post('/admin/engine/run', overrides || {});
export const getEngineStatus = () => api.get('/admin/engine/status');

// Admin: Generate Blocks
export const generateBlocks = () => api.post('/admin/blocks/generate');
export const clearUnpinnedTimeslots = () => api.post('/admin/blocks/clear-timeslots');

// Admin: compliance-snapshot PDFs (calendario-incumplimientos.pdf),
// generated automatically after each engine run - ADMIN only, distinct from
// the WRITER-accessible /reports runs above.
export const listAdminReports = () => api.get('/admin/reports');
export const downloadAdminReport = (runId, filename) =>
  api.get(`/admin/reports/${encodeURIComponent(runId)}/${encodeURIComponent(filename)}`, { responseType: 'blob' });

// Admin: application users (who can sign in and with which role)
export const getUsers = () => api.get('/admin/users');
export const createUser = (user) => api.post('/admin/users', user);
export const updateUser = (username, user) => api.put(`/admin/users/${encodeURIComponent(username)}`, user);
export const resetUserPassword = (username, newPassword) =>
  api.put(`/admin/users/${encodeURIComponent(username)}/password`, { newPassword });
export const deleteUser = (username) => api.delete(`/admin/users/${encodeURIComponent(username)}`);

// Reports (read-only for any authenticated role; generate requires WRITER/ADMIN)
// listReports() returns past "Generate PDFs" runs, newest first, each with its own
// files - past runs aren't overwritten, so they stay around for comparison.
export const listReports = () => api.get('/reports');
export const getReportStatus = () => api.get('/reports/status');
export const generateReports = (runId) => api.post('/reports/generate', null, { params: runId ? { runId } : {} });
export const downloadReport = (runId, filename) =>
  api.get(`/reports/${encodeURIComponent(runId)}/${encodeURIComponent(filename)}`, { responseType: 'blob' });

// Excel import (WRITER or ADMIN)
export const importExcel = (file) => {
  const formData = new FormData();
  formData.append('file', file);
  return api.post('/import/excel', formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
  });
};

// Excel export (READER/WRITER/ADMIN - not TEACHER) - the same sheet/column layout importExcel
// expects. entities: optional array of 'teachers' | 'courses' | 'rooms' | 'groups' to export
// just those sheets ('groups' includes Group_Courses); omit for everything.
export const exportExcel = (entities) =>
  api.get('/import/excel', {
    responseType: 'blob',
    params: entities && entities.length ? { entities: entities.join(',') } : undefined,
  });

// Admin: Timeslots
export const getTimeslots = () => api.get('/admin/timeslots');
export const getTimeslot = (id) => api.get(`/admin/timeslots/${id}`);
export const createTimeslot = (timeslot) => api.post('/admin/timeslots', timeslot);
export const updateTimeslot = (id, timeslot) => api.put(`/admin/timeslots/${id}`, timeslot);
export const deleteTimeslot = (id) => api.delete(`/admin/timeslots/${id}`);

// School term (current term/period label, e.g. "Fall 2026") - read is open to
// any authenticated role (shown in the header), write is ADMIN-only.
// TERM_UPDATED_EVENT: the header (App.jsx's Layout) mounts once and doesn't
// re-fetch on client-side navigation, so updateTerm() dispatches this on the
// window after a successful save - the header listens for it to refresh
// immediately instead of waiting for a full page reload.
export const TERM_UPDATED_EVENT = 'term-updated';
export const getTerm = () => api.get('/term');
export const updateTerm = (label) =>
  api.put('/admin/term', { label }).then((res) => {
    window.dispatchEvent(new Event(TERM_UPDATED_EVENT));
    return res;
  });

// Admin: audit log (most recent 200 write requests, logged automatically)
export const getAuditLog = () => api.get('/admin/audit-log');

// Admin: per-component preferred block size and max blocks per day. Block
// size is read by BlockGenerationService ("Generate Blocks") when
// decomposing a course's weekly hours into blocks; max blocks per day is
// enforced by the solver's HARD constraint limiting same-day concentration.
// A component with no rule here falls back to size 2 / max 2 per day
// server-side.
export const getComponentBlockRules = () => api.get('/admin/component-block-rules');
export const setComponentBlockRule = (component, preferredBlockSize, maxBlocksPerDay) =>
  api.put(`/admin/component-block-rules/${encodeURIComponent(component)}`, {
    preferredBlockSize,
    maxBlocksPerDay,
  });
export const deleteComponentBlockRule = (component) =>
  api.delete(`/admin/component-block-rules/${encodeURIComponent(component)}`);

// Admin: calendar exceptions (holidays, half-days, exam days). Record-keeping
// only for now - not yet read by block generation or the solver, since
// block_timeslot has no calendar-date concept at all (pure recurring weekly
// template). See database/migrations/add_calendar_exception.sql.
export const getCalendarExceptions = () => api.get('/admin/calendar-exceptions');
export const setCalendarException = (date, type, label, endHour) =>
  api.put(`/admin/calendar-exceptions/${encodeURIComponent(date)}`, { type, label, endHour });
export const deleteCalendarException = (date) =>
  api.delete(`/admin/calendar-exceptions/${encodeURIComponent(date)}`);

// Schedule
export const getScheduleRuns = () => api.get('/schedule/runs');
export const getScheduleView = (runId) => api.get('/schedule/view', { params: runId ? { runId } : {} });
export const getScheduleViewByGroup = (groupId, runId) =>
  api.get(`/schedule/view/group/${groupId}`, { params: runId ? { runId } : {} });
export const getScheduleViewByTeacher = (teacherId, runId) =>
  api.get(`/schedule/view/teacher/${teacherId}`, { params: runId ? { runId } : {} });
export const getScheduleViewByRoom = (roomName, runId) =>
  api.get(`/schedule/view/room/${roomName}`, { params: runId ? { runId } : {} });
export const getMyScheduleView = () => api.get('/schedule/view/me');

export default api;

