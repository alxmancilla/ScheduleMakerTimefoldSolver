import React from 'react';
import { BrowserRouter as Router, Routes, Route, NavLink, Outlet, useNavigate } from 'react-router-dom';
import Schedule from './components/Schedule';
import Teachers from './components/Teachers';
import Courses from './components/Courses';
import Rooms from './components/Rooms';
import Groups from './components/Groups';
import Assignments from './components/Assignments';
import Settings from './components/Settings';
import Login from './components/Login';
import ProtectedRoute from './auth/ProtectedRoute';
import AdminRoute from './auth/AdminRoute';
import AdminOnly from './auth/AdminOnly';
import { useAuth } from './auth/AuthContext';

const navLinkClass = ({ isActive }) => (isActive ? 'active' : '');

function Layout() {
  const { user, logout } = useAuth();
  const navigate = useNavigate();

  const handleLogout = () => {
    logout();
    navigate('/login', { replace: true });
  };

  return (
    <div className="app">
      <header className="header">
        <div className="container">
          <h1>Schedule Maker</h1>
          <nav className="nav">
            <NavLink to="/" className={navLinkClass}>Schedule</NavLink>
            <NavLink to="/teachers" className={navLinkClass}>Teachers</NavLink>
            <NavLink to="/courses" className={navLinkClass}>Courses</NavLink>
            <NavLink to="/rooms" className={navLinkClass}>Rooms</NavLink>
            <NavLink to="/groups" className={navLinkClass}>Groups</NavLink>
            <NavLink to="/assignments" className={navLinkClass}>Assignments</NavLink>
            <AdminOnly>
              <NavLink to="/settings" className={navLinkClass}>Settings</NavLink>
            </AdminOnly>
          </nav>
          {user && (
            <div className="user-box" style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
              <span>{user.username} ({user.role})</span>
              <button className="btn btn-secondary" onClick={handleLogout}>Logout</button>
            </div>
          )}
        </div>
      </header>

      <div className="container">
        <Outlet />
      </div>
    </div>
  );
}

function App() {
  return (
    <Router>
      <Routes>
        <Route path="/login" element={<Login />} />
        <Route element={<ProtectedRoute />}>
          <Route element={<Layout />}>
            <Route path="/" element={<Schedule />} />
            <Route path="/teachers" element={<Teachers />} />
            <Route path="/courses" element={<Courses />} />
            <Route path="/rooms" element={<Rooms />} />
            <Route path="/groups" element={<Groups />} />
            <Route path="/assignments" element={<Assignments />} />
            <Route element={<AdminRoute />}>
              <Route path="/settings" element={<Settings />} />
            </Route>
          </Route>
        </Route>
      </Routes>
    </Router>
  );
}

export default App;

