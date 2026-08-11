import React, { createContext, useContext, useEffect, useState } from 'react';
import { login as apiLogin, getCurrentUser, getToken, setToken, clearToken } from '../api';

/**
 * Authentication state for the SPA. Holds the current user ({username, role})
 * and exposes login/logout. On mount, if a token is already stored, it hydrates
 * the user via GET /api/auth/me so a page refresh keeps the session.
 */
const AuthContext = createContext(null);

export function AuthProvider({ children }) {
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
        if (active) setUser(res.data);
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
  }, []);

  const login = async (username, password) => {
    const res = await apiLogin(username, password);
    setToken(res.data.token);
    setUser({ username: res.data.username, role: res.data.role });
    return res.data;
  };

  const logout = () => {
    clearToken();
    setUser(null);
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
