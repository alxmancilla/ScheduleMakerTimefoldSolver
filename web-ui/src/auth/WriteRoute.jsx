import React from 'react';
import { Navigate, Outlet } from 'react-router-dom';
import { useAuth } from './AuthContext';

/**
 * Guards nested routes to WRITER/ADMIN users (mirrors AdminRoute, but for
 * canWrite() instead of isAdmin()). Assumes it is nested inside
 * ProtectedRoute, so authentication has already been checked. READERs are
 * redirected to the schedule home page.
 */
function WriteRoute() {
  const { canWrite } = useAuth();

  if (!canWrite()) {
    return <Navigate to="/" replace />;
  }

  return <Outlet />;
}

export default WriteRoute;
