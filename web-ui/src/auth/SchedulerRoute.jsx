import React from 'react';
import { Navigate, Outlet } from 'react-router-dom';
import { useAuth } from './AuthContext';

/**
 * Guards nested routes to SCHEDULER/ADMIN users (same role set as
 * canEditSchedule() - reused directly rather than a third definition of the
 * same two roles). Assumes it is nested inside ProtectedRoute, so
 * authentication has already been checked. Non-scheduler/admins are
 * redirected to the schedule home page, same as AdminRoute's own behavior.
 *
 * Used for /settings, which SCHEDULER now needs (Solver + Constraint
 * Weights tabs - see Settings.jsx's own per-tab filtering for the rest,
 * which stay ADMIN-only within the page). /users stays under the stricter
 * AdminRoute - user management is not a scheduling concern.
 */
function SchedulerRoute() {
  const { canEditSchedule } = useAuth();

  if (!canEditSchedule()) {
    return <Navigate to="/" replace />;
  }

  return <Outlet />;
}

export default SchedulerRoute;
