import React from 'react';
import { Routes, Route, Navigate } from 'react-router-dom';
import DashboardPage from './pages/DashboardPage';
import AppWithNavigation from './components/AppWithNavigation';
import LoginPage from './pages/LoginPage';
import ProtectedRoute from './components/ProtectedRoute';

const isAuthenticated = () => {
  return !!localStorage.getItem('token');
};

export const AppRoutes = () => {
  return (
    <Routes>

      <Route path="/login" element={<LoginPage />} />

      
      <Route path="/" element={
      <ProtectedRoute>
        <AppWithNavigation />
      </ProtectedRoute>
    } />

      <Route path="/devices/:type" element={
        <ProtectedRoute>
          <AppWithNavigation />
        </ProtectedRoute>
      } />

      <Route path="/templates" element={
        <ProtectedRoute>
          <AppWithNavigation />
        </ProtectedRoute>
      } />

      <Route path="/admin/*" element={
        <ProtectedRoute>
          <AppWithNavigation />
        </ProtectedRoute>
      } />

      <Route path="*" element={<Navigate to="/login" replace />} />
    </Routes>
  );
};
