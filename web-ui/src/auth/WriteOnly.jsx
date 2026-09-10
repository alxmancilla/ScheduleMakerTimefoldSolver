import { useAuth } from './AuthContext';

/**
 * Renders its children only for users who can write (WRITER or ADMIN). Readers
 * see nothing. This is a UX convenience; the backend enforces authorization
 * regardless, returning 403 to readers attempting writes.
 */
function WriteOnly({ children }) {
  const { canWrite } = useAuth();
  return canWrite() ? children : null;
}

export default WriteOnly;
