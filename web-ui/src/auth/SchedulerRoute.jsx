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
 * Used for /scheduler (SchedulerSettings.jsx - Solver + Constraint Weights,
 * SCHEDULER's own dedicated page, split out of Settings.jsx on 2026-09-07 so
 * SCHEDULER doesn't have to browse a menu labeled "Admin"). /settings and
 * /users stay under the stricter AdminRoute - everything else in Settings,
 * and user management, are not scheduling concerns.
 */
function SchedulerRoute() {
  const { canEditSchedule } = useAuth();

  if (!canEditSchedule()) {
    return <Navigate to="/" replace />;
  }

  return <Outlet />;
}

export default SchedulerRoute;
