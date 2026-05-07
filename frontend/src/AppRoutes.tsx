import React from 'react';
import { Routes, Route, Navigate } from 'react-router-dom';
import DashboardPage from './pages/DashboardPage';
import AppWithNavigation from './components/AppWithNavigation';
import LoginPage from './pages/LoginPage';
import ProtectedRoute from './components/ProtectedRoute';

const isAuthenticated = () => {
  return document.cookie.includes('JSESSIONID'); // заменить на куки/токены
};

export const AppRoutes = () => {
  return (
    <Routes>

      <Route path="/login" element={<LoginPage />} />

      
      <Route path="/" element={
      <ProtectedRoute>
        <DashboardPage />
      </ProtectedRoute>
    } />

    <Route path="/devices/:type" element={
      <ProtectedRoute>
        <AppWithNavigation />
      </ProtectedRoute>
    } />

      <Route path="/devices/:type" element={
        <AppWithNavigation/>
      } />

      <Route path="*" element={<Navigate to="/login" replace />} />
    </Routes>
  );
};
