import React, { createContext, useContext, useState, useEffect } from 'react';
import type { User } from '../types/auth';
import { authApi } from '../services/api';

interface AuthContextType {
  user: User | null;
  loading: boolean;
  login: () => void;
  logout: () => Promise<void>;
  checkAuth: () => Promise<void>;
  updateEmail: (email: string) => Promise<void>;
  deleteEmail: () => Promise<void>;
}

const AuthContext = createContext<AuthContextType | undefined>(undefined);

export const AuthProvider: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  const [user, setUser] = useState<User | null>(null);
  const [loading, setLoading] = useState(true);

  const checkAuth = async () => {
    try {
      const response = await authApi.checkAuth();
      if (response) {
        // Also fetch user profile to get email
        try {
          const profile = await authApi.getUserProfile();
          setUser({
            steamId: response.steamId,
            username: response.username,
            profilePictureUrl: response.profilePictureUrl,
            email: profile.email,
            authenticated: response.authenticated,
          });
        } catch {
          // If profile fetch fails, still set user without email
          setUser({
            steamId: response.steamId,
            username: response.username,
            profilePictureUrl: response.profilePictureUrl,
            authenticated: response.authenticated,
          });
        }
      } else {
        setUser(null);
      }
    } catch (error) {
      console.error('Auth check failed:', error);
      setUser(null);
    } finally {
      setLoading(false);
    }
  };

  const updateEmail = async (email: string) => {
    await authApi.saveEmail(email);
    if (user) {
      setUser({ ...user, email });
    }
  };

  const deleteEmail = async () => {
    await authApi.deleteEmail();
    if (user) {
      setUser({ ...user, email: undefined });
    }
  };

  useEffect(() => {
    checkAuth();
  }, []);

  const login = () => {
    authApi.login();
  };

  const logout = async () => {
    try {
      await authApi.logout();
    } catch (error) {
      console.error('Logout failed:', error);
    } finally {
      setUser(null);
    }
  };

  return (
    <AuthContext.Provider value={{ user, loading, login, logout, checkAuth, updateEmail, deleteEmail }}>
      {children}
    </AuthContext.Provider>
  );
};

// eslint-disable-next-line react-refresh/only-export-components
export const useAuth = () => {
  const context = useContext(AuthContext);
  if (context === undefined) {
    throw new Error('useAuth must be used within an AuthProvider');
  }
  return context;
};
