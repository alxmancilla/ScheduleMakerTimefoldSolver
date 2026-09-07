import { useAuth } from './AuthContext';

/**
 * Renders its children only for ADMIN users. This is a UX convenience; the
 * backend enforces authorization regardless (/api/admin/** requires ADMIN,
 * except the /api/admin/engine/**+/api/admin/constraint-config/** carve-out
 * that admits SCHEDULER too - see ScheduleEditOnly for the wrapper matching
 * that pair of roles instead).
 */
function AdminOnly({ children }) {
  const { isAdmin } = useAuth();
  return isAdmin() ? children : null;
}

export default AdminOnly;
