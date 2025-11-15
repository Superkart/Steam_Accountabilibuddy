import axios from 'axios';
import type { AuthResponse, Game, WishlistEntry, PriceAlert } from '../types/auth';

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
    try {
      const response = await api.get<Game[]>('/auth/steam/library');
      return response.data;
    } catch (error) {
      if (axios.isAxiosError(error) && error.response?.data?.error) {
        throw new Error(error.response.data.error);
      }
      throw new Error('Failed to load library. Please try again.');
    }
  },

  // Get user's Steam wishlist
  getWishlist: async (): Promise<WishlistEntry[]> => {
    try {
      const response = await api.get<WishlistEntry[]>('/auth/steam/wishlist');
      return response.data;
    } catch (error) {
      if (axios.isAxiosError(error) && error.response?.data?.error) {
        throw new Error(error.response.data.error);
      }
      throw new Error('Failed to load wishlist. Please try again.');
    }
  },

  // Logout user
  logout: async (): Promise<void> => {
    await api.post('/auth/steam/logout');
  },

  // Get user profile (including email)
  getUserProfile: async (): Promise<{ steamId: string; email: string }> => {
    const response = await api.get('/user/profile');
    return response.data;
  },

  // Save user email
  saveEmail: async (email: string): Promise<void> => {
    await api.post('/user/email', { email });
  },

  // Get all price alerts for the user
  getPriceAlerts: async (): Promise<PriceAlert[]> => {
    try {
      const response = await api.get<{ alerts: PriceAlert[] }>('/price-alerts');
      return response.data.alerts;
    } catch (error) {
      if (axios.isAxiosError(error) && error.response?.data?.error) {
        throw new Error(error.response.data.error);
      }
      throw new Error('Failed to load price alerts');
    }
  },

  // Create or update a price alert
  createPriceAlert: async (appId: number, gameName: string, targetPrice: number): Promise<void> => {
    try {
      await api.post('/price-alerts', { appId, gameName, targetPrice });
    } catch (error) {
      if (axios.isAxiosError(error) && error.response?.data?.error) {
        throw new Error(error.response.data.error);
      }
      throw new Error('Failed to create price alert');
    }
  },

  // Delete a price alert
  deletePriceAlert: async (appId: number): Promise<void> => {
    try {
      await api.delete(`/price-alerts/${appId}`);
    } catch (error) {
      if (axios.isAxiosError(error) && error.response?.data?.error) {
        throw new Error(error.response.data.error);
      }
      throw new Error('Failed to delete price alert');
    }
  },
};

export default api;
