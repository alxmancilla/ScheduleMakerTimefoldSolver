import React, { useEffect, useRef, useState } from 'react';
import { BrowserRouter as Router, Routes, Route, NavLink, Outlet, useLocation, useNavigate } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import Schedule from './components/Schedule';
import Teachers from './components/Teachers';
import Courses from './components/Courses';
import Rooms from './components/Rooms';
import Groups from './components/Groups';
import Assignments from './components/Assignments';
import Reports from './components/Reports';
import ImportExcel from './components/Import';
import Settings from './components/Settings';
import Users from './components/Users';
import Login from './components/Login';
import ProtectedRoute from './auth/ProtectedRoute';
import AdminRoute from './auth/AdminRoute';
import AdminOnly from './auth/AdminOnly';
import WriteRoute from './auth/WriteRoute';
import WriteOnly from './auth/WriteOnly';
import { useAuth } from './auth/AuthContext';

const navLinkClass = ({ isActive }) => (isActive ? 'active' : '');

const ADMIN_ROUTES = ['/settings', '/users'];

function AdminNavDropdown() {
  const { t } = useTranslation();
  const location = useLocation();
  const [open, setOpen] = useState(false);
  const ref = useRef(null);

  useEffect(() => {
    const handleClickOutside = (e) => {
      if (ref.current && !ref.current.contains(e.target)) setOpen(false);
    };
    document.addEventListener('mousedown', handleClickOutside);
    return () => document.removeEventListener('mousedown', handleClickOutside);
  }, []);

  const isActive = ADMIN_ROUTES.includes(location.pathname);

  return (
    <div
      className="nav-dropdown"
      ref={ref}
      onKeyDown={(e) => { if (e.key === 'Escape') setOpen(false); }}
    >
      <button
        type="button"
        className={`nav-dropdown-toggle ${isActive ? 'active' : ''}`}
        onClick={() => setOpen((o) => !o)}
        aria-haspopup="true"
        aria-expanded={open}
      >
        {t('nav.admin')} ▾
      </button>
      {open && (
        <div className="nav-dropdown-menu">
          <NavLink to="/settings" className={navLinkClass} onClick={() => setOpen(false)}>
            {t('nav.settings')}
          </NavLink>
          <NavLink to="/users" className={navLinkClass} onClick={() => setOpen(false)}>
            {t('nav.users')}
          </NavLink>
        </div>
      )}
    </div>
  );
}

function Layout() {
  const { user, logout, changeLanguage } = useAuth();
  const navigate = useNavigate();
  const { t, i18n } = useTranslation();

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
            <NavLink to="/" className={navLinkClass}>{t('nav.schedule')}</NavLink>
            <NavLink to="/teachers" className={navLinkClass}>{t('nav.teachers')}</NavLink>
            <NavLink to="/courses" className={navLinkClass}>{t('nav.courses')}</NavLink>
            <NavLink to="/rooms" className={navLinkClass}>{t('nav.rooms')}</NavLink>
            <NavLink to="/groups" className={navLinkClass}>{t('nav.groups')}</NavLink>
            <NavLink to="/assignments" className={navLinkClass}>{t('nav.assignments')}</NavLink>
            <NavLink to="/reports" className={navLinkClass}>{t('nav.reports')}</NavLink>
            <WriteOnly>
              <NavLink to="/import" className={navLinkClass}>{t('nav.import')}</NavLink>
            </WriteOnly>
            <AdminOnly>
              <AdminNavDropdown />
            </AdminOnly>
          </nav>
          {user && (
            <div className="user-box" style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
              <label htmlFor="language-select" style={{ display: 'none' }}>{t('nav.language')}</label>
              <select
                id="language-select"
                value={i18n.language}
                onChange={(e) => changeLanguage(e.target.value)}
              >
                <option value="en">EN</option>
                <option value="es">ES</option>
              </select>
              <span>{user.username} ({user.role})</span>
              <button className="btn btn-secondary" onClick={handleLogout}>{t('nav.logout')}</button>
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
            <Route path="/reports" element={<Reports />} />
            <Route element={<WriteRoute />}>
              <Route path="/import" element={<ImportExcel />} />
            </Route>
            <Route element={<AdminRoute />}>
              <Route path="/settings" element={<Settings />} />
              <Route path="/users" element={<Users />} />
            </Route>
          </Route>
        </Route>
      </Routes>
    </Router>
  );
}

export default App;

