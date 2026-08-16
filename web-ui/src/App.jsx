import React, { useEffect, useRef, useState } from 'react';
import { BrowserRouter as Router, Routes, Route, Link, NavLink, Outlet, useLocation, useNavigate } from 'react-router-dom';
import { useTranslation } from 'react-i18next';
import Schedule from './components/Schedule';
import MySchedule from './components/MySchedule';
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
import { getTerm, TERM_UPDATED_EVENT } from './api';

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

/** TEACHER accounts land on their own schedule; every other role sees the full Schedule view. */
function Home() {
  const { user } = useAuth();
  return user?.role === 'TEACHER' ? <MySchedule /> : <Schedule />;
}

function Layout() {
  const { user, logout, changeLanguage } = useAuth();
  const navigate = useNavigate();
  const { t, i18n } = useTranslation();
  const [termLabel, setTermLabel] = useState('');

  useEffect(() => {
    const loadTerm = () => {
      getTerm()
        .then((res) => setTermLabel(res.data.label || ''))
        .catch(() => {
          // Non-critical: the header just won't show a term label.
        });
    };
    loadTerm();
    // Layout doesn't remount on client-side navigation, so without this the
    // header would keep showing the term label from whenever it first
    // mounted even after Settings saves a new one; TERM_UPDATED_EVENT lets
    // that save push a refresh here immediately instead of waiting for a
    // full page reload.
    window.addEventListener(TERM_UPDATED_EVENT, loadTerm);
    return () => window.removeEventListener(TERM_UPDATED_EVENT, loadTerm);
  }, []);

  const handleLogout = () => {
    logout();
    navigate('/login', { replace: true });
  };

  return (
    <div className="app">
      <header className="header">
        <div className="container">
          <Link to="/" className="header-home-link">
            <h1>
              Schedule Maker
              {termLabel && <span className="header-term-label"> · {termLabel}</span>}
            </h1>
          </Link>
          <nav className="nav">
            {user?.role === 'TEACHER' ? (
              <NavLink to="/" className={navLinkClass}>{t('nav.mySchedule')}</NavLink>
            ) : (
              <>
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
              </>
            )}
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
            <Route path="/" element={<Home />} />
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

