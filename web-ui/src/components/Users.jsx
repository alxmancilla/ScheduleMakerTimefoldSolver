import React, { useState, useEffect } from 'react';
import { useTranslation } from 'react-i18next';
import { getUsers, createUser, updateUser, resetUserPassword, deleteUser, getTeachers } from '../api';
import { useAuth } from '../auth/AuthContext';
import { useToast } from '../ui/ToastContext';
import { useConfirm } from '../ui/ConfirmContext';

const ROLES = ['ADMIN', 'WRITER', 'READER', 'TEACHER'];
// Each language's own native name, shown regardless of the current UI language
// (the conventional pattern for language pickers).
const LANGUAGES = [
  { value: 'en', label: 'English' },
  { value: 'es', label: 'Español' },
];
const EMPTY_FORM = { username: '', password: '', role: 'READER', enabled: true, preferredLanguage: 'en', teacherId: '' };
const formatTimestamp = (value) => (value ? value.replace('T', ' ').split('.')[0] : '-');

function Users() {
  const { t } = useTranslation();
  const { user: currentUser } = useAuth();
  const showToast = useToast();
  const confirmAction = useConfirm();
  const [users, setUsers] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [fieldErrors, setFieldErrors] = useState({});
  const [editingUser, setEditingUser] = useState(null);
  const [showForm, setShowForm] = useState(false);
  const [form, setForm] = useState(EMPTY_FORM);

  const [resettingUsername, setResettingUsername] = useState(null);
  const [newPassword, setNewPassword] = useState('');
  const [resetError, setResetError] = useState(null);

  const [teachers, setTeachers] = useState([]);

  useEffect(() => {
    loadUsers();
    loadTeachers();
  }, []);

  const loadTeachers = async () => {
    try {
      const response = await getTeachers();
      setTeachers(response.data);
    } catch (err) {
      // Non-critical: the teacher-link dropdown just won't have options.
    }
  };

  const loadUsers = async () => {
    try {
      setLoading(true);
      const response = await getUsers();
      setUsers(response.data);
      setError(null);
    } catch (err) {
      setError(t('users.loadFailedPrefix') + err.message);
    } finally {
      setLoading(false);
    }
  };

  const handleField = (e) => {
    const { name, type, checked, value } = e.target;
    setForm({ ...form, [name]: type === 'checkbox' ? checked : value });
  };

  const handleAdd = () => {
    setEditingUser(null);
    setForm(EMPTY_FORM);
    setFieldErrors({});
    setError(null);
    setShowForm(true);
  };

  const handleEdit = (u) => {
    setEditingUser(u);
    setForm({
      username: u.username,
      password: '',
      role: u.role,
      enabled: u.enabled,
      preferredLanguage: u.preferredLanguage,
      teacherId: u.teacherId || '',
    });
    setFieldErrors({});
    setError(null);
    setShowForm(true);
  };

  const handleCancel = () => {
    setShowForm(false);
    setEditingUser(null);
    setForm(EMPTY_FORM);
    setFieldErrors({});
    setError(null);
  };

  const handleSubmit = async (e) => {
    e.preventDefault();
    setFieldErrors({});
    setError(null);
    try {
      const teacherId = form.role === 'TEACHER' ? (form.teacherId || null) : null;
      if (editingUser) {
        await updateUser(editingUser.username, {
          role: form.role,
          enabled: form.enabled,
          preferredLanguage: form.preferredLanguage,
          teacherId,
        });
      } else {
        await createUser({ username: form.username, password: form.password, role: form.role, teacherId });
      }
      handleCancel();
      loadUsers();
      showToast(t('users.savedMessage'));
    } catch (err) {
      const data = err.response?.data;
      if (data?.errors) {
        setFieldErrors(data.errors);
      }
      setError(data?.message || t('users.saveFailedPrefix') + err.message);
    }
  };

  const handleDelete = async (u) => {
    if (!(await confirmAction(t('users.confirmDelete', { username: u.username })))) return;
    try {
      await deleteUser(u.username);
      loadUsers();
      showToast(t('users.deletedMessage'));
    } catch (err) {
      setError(err.response?.data?.message || t('users.deleteFailedPrefix') + err.message);
    }
  };

  const handleStartReset = (username) => {
    setResettingUsername(username);
    setNewPassword('');
    setResetError(null);
  };

  const handleCancelReset = () => {
    setResettingUsername(null);
    setNewPassword('');
    setResetError(null);
  };

  const handleSubmitReset = async (e) => {
    e.preventDefault();
    setResetError(null);
    try {
      await resetUserPassword(resettingUsername, newPassword);
      handleCancelReset();
      showToast(t('users.passwordResetMessage'));
    } catch (err) {
      setResetError(err.response?.data?.errors?.newPassword || err.response?.data?.message || t('users.resetFailedPrefix') + err.message);
    }
  };

  if (loading) return <div className="loading">{t('users.loading')}</div>;

  return (
    <div>
      <div className="card">
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
          <h2>{t('users.title')}</h2>
          <button className="btn btn-success" onClick={handleAdd}>
            {t('users.addUser')}
          </button>
        </div>
        <p style={{ marginTop: '8px', color: 'var(--color-text-secondary)', fontSize: '13px' }}>
          {t('users.description')}
        </p>
      </div>

      {error && <div className="error" role="alert">{error}</div>}

      {showForm && (
        <div className="card">
          <h3>{editingUser ? `${t('users.editUserPrefix')}${editingUser.username}` : t('users.newUser')}</h3>
          <form onSubmit={handleSubmit}>
            <div className="form-group">
              <label htmlFor="user-username">{t('users.fields.username')}</label>
              <input
                type="text"
                id="user-username" name="username"
                value={form.username}
                onChange={handleField}
                required
                disabled={!!editingUser}
              />
              {fieldErrors.username && <div className="error" role="alert">{fieldErrors.username}</div>}
            </div>
            {!editingUser && (
              <div className="form-group">
                <label htmlFor="user-password">{t('users.fields.password')}</label>
                <input
                  type="password"
                  id="user-password" name="password"
                  value={form.password}
                  onChange={handleField}
                  required
                  minLength={8}
                />
                {fieldErrors.password && <div className="error" role="alert">{fieldErrors.password}</div>}
              </div>
            )}
            <div className="form-group">
              <label htmlFor="user-role">{t('users.fields.role')}</label>
              <select id="user-role" name="role" value={form.role} onChange={handleField}>
                {ROLES.map((r) => (
                  <option key={r} value={r}>{r}</option>
                ))}
              </select>
              {fieldErrors.role && <div className="error" role="alert">{fieldErrors.role}</div>}
            </div>
            {form.role === 'TEACHER' && (
              <div className="form-group">
                <label htmlFor="user-teacher">{t('users.fields.linkedTeacher')}</label>
                <select id="user-teacher" name="teacherId" value={form.teacherId} onChange={handleField}>
                  <option value="">{t('common.noneOption')}</option>
                  {teachers.map((tch) => (
                    <option key={tch.id} value={tch.id}>{tch.id} - {tch.name} {tch.lastName}</option>
                  ))}
                </select>
                {fieldErrors.teacherId && <div className="error" role="alert">{fieldErrors.teacherId}</div>}
              </div>
            )}
            {editingUser && (
              <>
                <div className="form-group">
                  <label>
                    <input
                      type="checkbox"
                      name="enabled"
                      checked={form.enabled}
                      onChange={handleField}
                      style={{ marginRight: '6px' }}
                    />
                    {t('users.fields.enabled')}
                  </label>
                  {fieldErrors.enabled && <div className="error" role="alert">{fieldErrors.enabled}</div>}
                </div>
                <div className="form-group">
                  <label htmlFor="user-language">{t('users.fields.preferredLanguage')}</label>
                  <select id="user-language" name="preferredLanguage" value={form.preferredLanguage} onChange={handleField}>
                    {LANGUAGES.map((l) => (
                      <option key={l.value} value={l.value}>{l.label}</option>
                    ))}
                  </select>
                  {fieldErrors.preferredLanguage && <div className="error" role="alert">{fieldErrors.preferredLanguage}</div>}
                </div>
              </>
            )}
            <div style={{ display: 'flex', gap: '10px' }}>
              <button type="submit" className="btn btn-primary">{t('common.save')}</button>
              <button type="button" className="btn btn-secondary" onClick={handleCancel}>{t('common.cancel')}</button>
            </div>
          </form>
        </div>
      )}

      <div className="card table-wrap">
        <table>
          <thead>
            <tr>
              <th>{t('users.table.username')}</th>
              <th>{t('users.table.role')}</th>
              <th>{t('users.table.linkedTeacher')}</th>
              <th>{t('users.table.enabled')}</th>
              <th>{t('users.table.language')}</th>
              <th>{t('users.table.created')}</th>
              <th>{t('common.actions')}</th>
            </tr>
          </thead>
          <tbody>
            {users.map((u) => (
              <React.Fragment key={u.username}>
                <tr>
                  <td>{u.username}{currentUser?.username === u.username && t('users.you')}</td>
                  <td>{u.role}</td>
                  <td>{u.teacherId || '-'}</td>
                  <td>{u.enabled ? t('common.yes') : t('common.no')}</td>
                  <td>{u.preferredLanguage}</td>
                  <td>{formatTimestamp(u.createdAt)}</td>
                  <td>
                    <button className="btn btn-primary" onClick={() => handleEdit(u)} style={{ marginRight: '5px' }}>
                      {t('common.edit')}
                    </button>
                    <button className="btn btn-secondary" onClick={() => handleStartReset(u.username)} style={{ marginRight: '5px' }}>
                      {t('users.resetPassword')}
                    </button>
                    <button
                      className="btn btn-danger"
                      onClick={() => handleDelete(u)}
                      disabled={currentUser?.username === u.username}
                      title={currentUser?.username === u.username ? t('users.selfDeleteTooltip') : undefined}
                    >
                      {t('common.delete')}
                    </button>
                  </td>
                </tr>
                {resettingUsername === u.username && (
                  <tr>
                    <td colSpan={7}>
                      <form onSubmit={handleSubmitReset} style={{ display: 'flex', gap: '10px', alignItems: 'center' }}>
                        <label style={{ margin: 0 }} htmlFor={`reset-password-${u.username}`}>{t('users.resetPasswordFor', { username: u.username })}</label>
                        <input
                          id={`reset-password-${u.username}`}
                          type="password"
                          value={newPassword}
                          onChange={(e) => setNewPassword(e.target.value)}
                          minLength={8}
                          required
                          autoFocus
                        />
                        <button type="submit" className="btn btn-primary">{t('common.save')}</button>
                        <button type="button" className="btn btn-secondary" onClick={handleCancelReset}>{t('common.cancel')}</button>
                      </form>
                      {resetError && <div className="error" role="alert">{resetError}</div>}
                    </td>
                  </tr>
                )}
              </React.Fragment>
            ))}
            {users.length === 0 && (
              <tr>
                <td colSpan={7} style={{ color: 'var(--color-text-secondary)' }}>{t('users.noUsers')}</td>
              </tr>
            )}
          </tbody>
        </table>
      </div>
    </div>
  );
}

export default Users;
