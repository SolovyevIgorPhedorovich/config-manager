// components/ProtectedRoute.tsx
import { useEffect, useState } from 'react';
import { Navigate, useLocation } from 'react-router-dom';
import { Spin } from 'antd';
import { authApi } from '../api/authApi';

interface ProtectedRouteProps {
  children: React.ReactNode;
}

const ProtectedRoute = ({ children }: ProtectedRouteProps) => {
  const [isAuthenticated, setIsAuthenticated] = useState<boolean | null>(null);
  const location = useLocation();

  useEffect(() => {
    const checkAuth = async () => {
      const token = localStorage.getItem('token');

      if (!token) {
        setIsAuthenticated(false);
        return;
      }

      try {
        const isValid = await authApi.checkAuth();
        
        if (!isValid) {
          // Если токен невалиден, очищаем все данные
          localStorage.removeItem('token');
          localStorage.removeItem('username');
          localStorage.removeItem('role');
        }
        
        setIsAuthenticated(isValid);
      } catch (error) {
        console.error('Auth check failed:', error);
        localStorage.removeItem('token');
        localStorage.removeItem('username');
        localStorage.removeItem('role');
        setIsAuthenticated(false);
      }
    };

    checkAuth();
  }, [location.pathname]);

  // Показываем загрузку только при первой проверке
  if (isAuthenticated === null) {
    return (
      <div style={{ 
        display: 'flex', 
        justifyContent: 'center', 
        alignItems: 'center', 
        height: '100vh',
        flexDirection: 'column',
        gap: '16px'
      }}>
        <Spin size="large" />
        <div style={{ color: '#666' }}>Проверка авторизации...</div>
      </div>
    );
  }

  // Если не авторизован - редирект на логин
  if (isAuthenticated === false) {
    return <Navigate to="/login" replace state={{ from: location }} />;
  }

  // Авторизован - показываем содержимое
  return <>{children}</>;
};

export default ProtectedRoute;