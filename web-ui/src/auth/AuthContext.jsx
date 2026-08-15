import React, { createContext, useContext, useEffect, useState } from 'react';
import { useTranslation } from 'react-i18next';
import {
  login as apiLogin,
  getCurrentUser,
  updatePreferredLanguage as apiUpdatePreferredLanguage,
  getToken,
  setToken,
  clearToken,
} from '../api';

/**
 * Authentication state for the SPA. Holds the current user ({username, role,
 * preferredLanguage}) and exposes login/logout. On mount, if a token is
 * already stored, it hydrates the user via GET /api/auth/me so a page
 * refresh keeps the session. The user's preferredLanguage drives the i18next
 * active language on both hydration paths.
 */
const AuthContext = createContext(null);

export function AuthProvider({ children }) {
  const { i18n } = useTranslation();
  const [user, setUser] = useState(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    let active = true;
    if (!getToken()) {
      setLoading(false);
      return;
    }
    getCurrentUser()
      .then((res) => {
        if (active) {
          setUser(res.data);
          if (res.data.preferredLanguage) i18n.changeLanguage(res.data.preferredLanguage);
        }
      })
      .catch(() => {
        clearToken();
        if (active) setUser(null);
      })
      .finally(() => {
        if (active) setLoading(false);
      });
    return () => {
      active = false;
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const login = async (username, password) => {
    const res = await apiLogin(username, password);
    setToken(res.data.token);
    setUser({ username: res.data.username, role: res.data.role, preferredLanguage: res.data.preferredLanguage });
    if (res.data.preferredLanguage) i18n.changeLanguage(res.data.preferredLanguage);
    return res.data;
  };

  const logout = () => {
    clearToken();
    setUser(null);
  };

  const changeLanguage = async (language) => {
    i18n.changeLanguage(language);
    setUser((prev) => (prev ? { ...prev, preferredLanguage: language } : prev));
    await apiUpdatePreferredLanguage(language);
  };

  const hasRole = (...roles) => user != null && roles.includes(user.role);
  const canWrite = () => hasRole('WRITER', 'ADMIN');
  const isAdmin = () => hasRole('ADMIN');

  const value = {
    user,
    loading,
    isAuthenticated: user != null,
    login,
    logout,
    changeLanguage,
    hasRole,
    canWrite,
    isAdmin,
  };

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth() {
  const ctx = useContext(AuthContext);
  if (ctx == null) {
    throw new Error('useAuth must be used within an AuthProvider');
  }
  return ctx;
}
