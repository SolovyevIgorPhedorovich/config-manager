import axios from 'axios';

export const apiClient = axios.create({
  baseURL: '/api',
  timeout: 10000,
  // withCredentials: true,  // <-- временно отключено для диагностики
});

apiClient.interceptors.request.use(
  (config) => {
    // Не добавляем токен для запроса логина
    if (!config.url?.includes('/auth/login')) {
      const token = localStorage.getItem('token');
      if (token) {
        config.headers.Authorization = `Bearer ${token}`;
      }
    }
    
    // Не кэшируем GET запросы
    if (config.method === 'get') {
      config.headers['Cache-Control'] = 'no-cache';
      config.headers['Pragma'] = 'no-cache';
    }
    
    return config;
  },
  (error) => {
    return Promise.reject(error);
  }
);

// Чистим сессию и уводим на страницу входа
function forceLogout() {
  localStorage.removeItem('token');
  localStorage.removeItem('refreshToken');
  localStorage.removeItem('username');
  localStorage.removeItem('role');
  if (window.location.pathname !== '/login') {
    window.location.href = '/login';
  }
}

// Single-flight: пока идёт обновление, остальные 401 ждут один и тот же запрос
let refreshing: Promise<string | null> | null = null;

async function refreshAccessToken(): Promise<string | null> {
  const refreshToken = localStorage.getItem('refreshToken');
  if (!refreshToken) return null;
  // Отдельный экземпляр axios — чтобы не зациклить перехватчик на 401
  const resp = await axios.post('/api/v1/auth/refresh', { refreshToken });
  const newToken = resp.data?.token ?? null;
  if (newToken) localStorage.setItem('token', newToken);
  if (resp.data?.refreshToken) localStorage.setItem('refreshToken', resp.data.refreshToken);
  return newToken;
}

apiClient.interceptors.response.use(
  (response) => response,
  async (error) => {
    const original = error.config;
    const status = error.response?.status;
    const url: string = original?.url || '';

    // 401 на access-токене → пробуем обновить токен и повторить запрос один раз
    const isAuthCall = url.includes('/auth/login') || url.includes('/auth/refresh');
    if (status === 401 && !isAuthCall && original && !original._retry) {
      original._retry = true;
      try {
        if (!refreshing) {
          refreshing = refreshAccessToken().finally(() => { refreshing = null; });
        }
        const newToken = await refreshing;
        if (newToken) {
          original.headers = original.headers || {};
          original.headers.Authorization = `Bearer ${newToken}`;
          return apiClient(original); // повтор исходного запроса
        }
      } catch {
        // обновление не удалось — ниже выполнится forceLogout
      }
      forceLogout();
    }

    console.error('API Error:', error.response?.data || error.message);
    return Promise.reject(error);
  }
);