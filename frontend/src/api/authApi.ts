// api/authApi.ts
import { apiClient } from '../client';

export const authApi = {
  verifyToken: async (token: string): Promise<boolean> => {
    try {
      const response = await apiClient.get('/v1/auth/user', {
        headers: { 
          'Authorization': `Bearer ${token}`,
          'Cache-Control': 'no-cache',
          'Pragma': 'no-cache'
        }
      });
      return true;
    } catch (error) {
      console.error('Token verification failed:', error);
      return false;
    }
  },

  getCurrentUser: async (token: string) => {
    const response = await apiClient.get('/v1/auth/user', {
      headers: { 
        'Authorization': `Bearer ${token}`,
        'Cache-Control': 'no-cache',
        'Pragma': 'no-cache'
      }
    });
    return response.data;
  },

  getToken: (): string | null => {
    return localStorage.getItem('token');
  },

  setToken: (token: string): void => {
    localStorage.setItem('token', token);
  },

  removeToken: (): void => {
    localStorage.removeItem('token');
    localStorage.removeItem('username');
    localStorage.removeItem('role');
  },

  checkAuth: async (): Promise<boolean> => {
    const token = authApi.getToken();
    if (!token) return false;
    return await authApi.verifyToken(token);
  },

  login: async (credentials: { 
    username: string; 
    password: string;
    authType?: string;
  }) => {
    const response = await apiClient.post('/v1/auth/login', credentials, {
      headers: {
        'X-Auth-Type': credentials.authType || 'DB'
      }
    });
    
    const { token, username, role } = response.data;
    
    // Сохраняем все полученные данные
    if (token) authApi.setToken(token);
    if (username) localStorage.setItem('username', username);
    if (role) localStorage.setItem('role', role);
    
    return response.data;
  },

  logout: (): void => {
    authApi.removeToken();
  }
};