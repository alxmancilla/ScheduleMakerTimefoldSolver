import React from 'react';
import { Navigate, Outlet } from 'react-router-dom';
import { useAuth } from './AuthContext';

/**
 * Guards nested routes to ADMIN users only. Assumes it is nested inside
 * ProtectedRoute, so authentication has already been checked. Non-admins are
 * redirected to the schedule home page.
 */
function AdminRoute() {
  const { isAdmin } = useAuth();

  if (!isAdmin()) {
    return <Navigate to="/" replace />;
  }

  return <Outlet />;
}

export default AdminRoute;
