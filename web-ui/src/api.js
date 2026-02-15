import axios from 'axios';

const API_BASE_URL = '/api';

const api = axios.create({
  baseURL: API_BASE_URL,
  headers: {
    'Content-Type': 'application/json',
  },
});

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

// Schedule
export const getScheduleView = () => api.get('/schedule/view');
export const getScheduleViewByGroup = (groupId) => api.get(`/schedule/view/group/${groupId}`);
export const getScheduleViewByTeacher = (teacherId) => api.get(`/schedule/view/teacher/${teacherId}`);
export const getScheduleViewByRoom = (roomName) => api.get(`/schedule/view/room/${roomName}`);

export default api;

