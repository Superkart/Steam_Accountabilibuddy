import axios from 'axios';
import type { AuthResponse, Game, WishlistEntry } from '../types/auth';

const api = axios.create({
  baseURL: '', // Use Vite proxy
  withCredentials: true, // Important for session cookies
  headers: {
    'Content-Type': 'application/json',
  },
});

export const authApi = {
  // Redirect to Steam login
  login: () => {
    window.location.href = '/auth/steam/login';
  },

  // Check if user is authenticated (can be called after Steam redirect)
  checkAuth: async (): Promise<AuthResponse | null> => {
    try {
      // Get current user info to verify session
      const response = await api.get<AuthResponse>('/auth/steam/me');
      return response.data;
    } catch {
      return null; // Not authenticated
    }
  },

  // Get user's Steam library
  getLibrary: async (): Promise<Game[]> => {
    const response = await api.get<Game[]>('/auth/steam/library');
    return response.data;
  },

  // Get user's Steam wishlist
  getWishlist: async (): Promise<WishlistEntry[]> => {
    const response = await api.get<WishlistEntry[]>('/auth/steam/wishlist');
    return response.data;
  },

  // Logout user
  logout: async (): Promise<void> => {
    await api.post('/auth/steam/logout');
  },
};

export default api;
