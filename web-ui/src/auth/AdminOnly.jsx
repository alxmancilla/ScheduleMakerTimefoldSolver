import { useAuth } from './AuthContext';

/**
 * Renders its children only for ADMIN users. This is a UX convenience; the
 * backend enforces authorization regardless (/api/admin/** requires ADMIN).
 */
function AdminOnly({ children }) {
  const { isAdmin } = useAuth();
  return isAdmin() ? children : null;
}

export default AdminOnly;
