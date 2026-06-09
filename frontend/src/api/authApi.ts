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
    localStorage.removeItem('refreshToken');
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

    const { token, refreshToken, username, role } = response.data;

    // Сохраняем все полученные данные
    if (token) authApi.setToken(token);
    if (refreshToken) localStorage.setItem('refreshToken', refreshToken);
    if (username) localStorage.setItem('username', username);
    if (role) localStorage.setItem('role', role);

    return response.data;
  },

  // Обновление access-токена по refresh-токену (вызывается перехватчиком при 401)
  refresh: async (): Promise<string | null> => {
    const refreshToken = localStorage.getItem('refreshToken');
    if (!refreshToken) return null;
    const response = await apiClient.post('/v1/auth/refresh', { refreshToken });
    const { token, refreshToken: newRefresh } = response.data;
    if (token) authApi.setToken(token);
    if (newRefresh) localStorage.setItem('refreshToken', newRefresh);
    return token ?? null;
  },

  logout: async (): Promise<void> => {
    const refreshToken = localStorage.getItem('refreshToken');
    try {
      // Отзываем токены на сервере (чёрный список)
      await apiClient.post('/v1/auth/logout', { refreshToken });
    } catch {
      // даже при ошибке очищаем локальные данные
    }
    authApi.removeToken();
  }
};