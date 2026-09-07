import { useAuth } from './AuthContext';

/**
 * Renders its children only for SCHEDULER/ADMIN users (same role set as
 * AuthContext's canEditSchedule()). This is a UX convenience; the backend
 * enforces authorization regardless - see SecurityConfig's own doc comment.
 * Used wherever a control is scoped to that pair of roles: Assignments.jsx's
 * add/edit/delete/import (/api/assignments/**'s writes require SCHEDULER or
 * ADMIN), the "Admin" nav dropdown (App.jsx - it now also gates Settings,
 * which SCHEDULER needs), and Schedule.jsx's edit-mode toggle (its grid
 * editing itself goes through canEditSchedule() directly instead, since that
 * gate also folds in the run-selection/edit-mode-toggle state this simple
 * wrapper doesn't know about).
 */
function ScheduleEditOnly({ children }) {
  const { canEditSchedule } = useAuth();
  return canEditSchedule() ? children : null;
}

export default ScheduleEditOnly;
