import React from 'react';
import { Routes, Route } from 'react-router-dom';
import DashboardPage from './pages/DashboardPage';
import AppWithNavigation from './components/AppWithNavigation';

export const AppRoutes = () => {
  return (
    <Routes>
      <Route path="/" element={<DashboardPage />} />

      <Route path="/devices/:type" element={
        <AppWithNavigation/>
      } />
    </Routes>
  );
};
