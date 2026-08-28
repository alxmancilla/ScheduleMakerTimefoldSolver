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
import CourseCoverage from './components/CourseCoverage';
import Reports from './components/Reports';
import ImportExcel from './components/Import';
import Settings from './components/Settings';
import Users from './components/Users';
import Login from './components/Login';
import ProtectedRoute from './auth/ProtectedRoute';
import AdminRoute from './auth/AdminRoute';
import AdminOnly from './auth/AdminOnly';
import WriteRoute from './auth/WriteRoute';
import { useAuth } from './auth/AuthContext';
import { getTerm, TERM_UPDATED_EVENT } from './api';

const navLinkClass = ({ isActive }) => (isActive ? 'active' : '');

const SETUP_ITEMS = [
  { path: '/teachers', labelKey: 'nav.teachers' },
  { path: '/courses', labelKey: 'nav.courses' },
  { path: '/rooms', labelKey: 'nav.rooms' },
  { path: '/groups', labelKey: 'nav.groups' },
];
const ADMIN_ITEMS = [
  { path: '/settings', labelKey: 'nav.settings' },
  { path: '/users', labelKey: 'nav.users' },
];
// Reports/Course Coverage are READER+ (no gate); Import is WRITER+ (writeOnly) -
// filtered per role before rendering, unlike SETUP_ITEMS/ADMIN_ITEMS which are uniform.
const TOOLS_ITEMS = [
  { path: '/reports', labelKey: 'nav.reports' },
  { path: '/course-coverage', labelKey: 'nav.courseCoverage' },
  { path: '/import', labelKey: 'nav.import', writeOnly: true },
];

/**
 * Shared dropdown shell (toggle button + click-outside/Escape-to-close menu)
 * used for the Setup, Admin, and Profile nav dropdowns. `children` is a
 * render-prop so menu items can close the dropdown after they're clicked.
 * `activeLabel`, when set, shows which child page is current (e.g.
 * "Setup · Rooms") so the toggle stays meaningful once the menu is closed.
 */
function NavDropdown({ label, active, activeLabel, menuStyle, children }) {
  const [open, setOpen] = useState(false);
  const ref = useRef(null);

  useEffect(() => {
    const handleClickOutside = (e) => {
      if (ref.current && !ref.current.contains(e.target)) setOpen(false);
    };
    document.addEventListener('mousedown', handleClickOutside);
    return () => document.removeEventListener('mousedown', handleClickOutside);
  }, []);

  return (
    <div
      className="nav-dropdown"
      ref={ref}
      onKeyDown={(e) => { if (e.key === 'Escape') setOpen(false); }}
    >
      <button
        type="button"
        className={`nav-dropdown-toggle ${active ? 'active' : ''}`}
        onClick={() => setOpen((o) => !o)}
        aria-haspopup="true"
        aria-expanded={open}
        aria-current={active ? 'page' : undefined}
      >
        {label}
        {activeLabel && <span className="nav-dropdown-sublabel"> · {activeLabel}</span>}
        <span className="nav-dropdown-caret"> ▾</span>
      </button>
      {open && (
        <div className="nav-dropdown-menu" style={menuStyle}>
          {children(() => setOpen(false))}
        </div>
      )}
    </div>
  );
}

function SetupNavDropdown() {
  const { t } = useTranslation();
  const location = useLocation();
  const activeItem = SETUP_ITEMS.find((item) => item.path === location.pathname);

  return (
    <NavDropdown
      label={t('nav.setup')}
      active={!!activeItem}
      activeLabel={activeItem ? t(activeItem.labelKey) : null}
    >
      {(close) => (
        <>
          {SETUP_ITEMS.map((item) => (
            <NavLink key={item.path} to={item.path} className={navLinkClass} onClick={close}>
              {t(item.labelKey)}
            </NavLink>
          ))}
        </>
      )}
    </NavDropdown>
  );
}

function ToolsNavDropdown() {
  const { t } = useTranslation();
  const location = useLocation();
  const { canWrite } = useAuth();
  const visibleItems = TOOLS_ITEMS.filter((item) => !item.writeOnly || canWrite());
  const activeItem = visibleItems.find((item) => item.path === location.pathname);

  return (
    <NavDropdown
      label={t('nav.tools')}
      active={!!activeItem}
      activeLabel={activeItem ? t(activeItem.labelKey) : null}
    >
      {(close) => (
        <>
          {visibleItems.map((item) => (
            <NavLink key={item.path} to={item.path} className={navLinkClass} onClick={close}>
              {t(item.labelKey)}
            </NavLink>
          ))}
        </>
      )}
    </NavDropdown>
  );
}

function AdminNavDropdown() {
  const { t } = useTranslation();
  const location = useLocation();
  const activeItem = ADMIN_ITEMS.find((item) => item.path === location.pathname);

  return (
    <NavDropdown
      label={t('nav.admin')}
      active={!!activeItem}
      activeLabel={activeItem ? t(activeItem.labelKey) : null}
    >
      {(close) => (
        <>
          {ADMIN_ITEMS.map((item) => (
            <NavLink key={item.path} to={item.path} className={navLinkClass} onClick={close}>
              {t(item.labelKey)}
            </NavLink>
          ))}
        </>
      )}
    </NavDropdown>
  );
}

/** Consolidates username/role, language switcher, and logout into one dropdown. */
function ProfileNavDropdown({ user, language, onChangeLanguage, onLogout }) {
  const { t } = useTranslation();

  return (
    <NavDropdown label={`${user.username} (${user.role})`} active={false} menuStyle={{ right: 0, left: 'auto' }}>
      {(close) => (
        <div style={{ padding: '12px 16px', display: 'flex', flexDirection: 'column', gap: '10px', minWidth: '160px' }}>
          <div>
            <label htmlFor="language-select" style={{ display: 'block', fontSize: '12px', color: 'var(--color-text-secondary)', marginBottom: '4px' }}>
              {t('nav.language')}
            </label>
            <select
              id="language-select"
              value={language}
              onChange={(e) => onChangeLanguage(e.target.value)}
              style={{ width: '100%' }}
            >
              <option value="en">EN</option>
              <option value="es">ES</option>
            </select>
          </div>
          <button
            className="btn btn-secondary"
            onClick={() => { close(); onLogout(); }}
            style={{ width: '100%' }}
          >
            {t('nav.logout')}
          </button>
        </div>
      )}
    </NavDropdown>
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
  const location = useLocation();
  const { t, i18n } = useTranslation();
  const [termLabel, setTermLabel] = useState('');
  const [mobileNavOpen, setMobileNavOpen] = useState(false);
  const headerRef = useRef(null);

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

  // Collapse the mobile nav drawer whenever the route changes, so following a
  // link (including one inside a dropdown) closes it instead of leaving it
  // open over the new page.
  useEffect(() => {
    setMobileNavOpen(false);
  }, [location.pathname]);

  useEffect(() => {
    const handleClickOutside = (e) => {
      if (headerRef.current && !headerRef.current.contains(e.target)) setMobileNavOpen(false);
    };
    document.addEventListener('mousedown', handleClickOutside);
    return () => document.removeEventListener('mousedown', handleClickOutside);
  }, []);

  const handleLogout = () => {
    logout();
    navigate('/login', { replace: true });
  };

  return (
    <div className="app">
      <header className="header" ref={headerRef}>
        <div className="container">
          <Link to="/" className="header-home-link">
            <h1>
              Schedule Maker
              {termLabel && <span className="header-term-label"> · {termLabel}</span>}
            </h1>
          </Link>
          {user && (
            <button
              type="button"
              className="nav-toggle"
              onClick={() => setMobileNavOpen((o) => !o)}
              aria-label={t('nav.toggleMenu')}
              aria-expanded={mobileNavOpen}
            >
              ☰
            </button>
          )}
          <nav className={`nav ${mobileNavOpen ? 'mobile-open' : ''}`}>
            {user?.role === 'TEACHER' ? (
              <NavLink to="/" className={navLinkClass}>{t('nav.mySchedule')}</NavLink>
            ) : (
              <>
                <NavLink to="/" className={navLinkClass}>{t('nav.schedule')}</NavLink>
                <AdminOnly>
                  <NavLink to="/assignments" className={navLinkClass}>{t('nav.assignments')}</NavLink>
                </AdminOnly>
                <SetupNavDropdown />
                <ToolsNavDropdown />
                <AdminOnly>
                  <AdminNavDropdown />
                </AdminOnly>
              </>
            )}
          </nav>
          {user && (
            <div className={`user-box ${mobileNavOpen ? 'mobile-open' : ''}`}>
              <ProfileNavDropdown
                user={user}
                language={i18n.language}
                onChangeLanguage={changeLanguage}
                onLogout={handleLogout}
              />
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
            <Route path="/reports" element={<Reports />} />
            <Route path="/course-coverage" element={<CourseCoverage />} />
            <Route element={<WriteRoute />}>
              <Route path="/import" element={<ImportExcel />} />
            </Route>
            <Route element={<AdminRoute />}>
              <Route path="/assignments" element={<Assignments />} />
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

