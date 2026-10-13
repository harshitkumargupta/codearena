import type { ReactNode } from 'react';
import { Navigate, useLocation } from 'react-router';
import ui from '../components/ui.module.css';
import { useAuth } from './useAuth';

export function RequireAdmin({ children }: { children: ReactNode }) {
  const { session } = useAuth();
  const location = useLocation();
  if (!session) return <Navigate to="/login" replace state={{ from: `${location.pathname}${location.search}` }} />;
  if (session.user.role !== 'ADMIN') {
    return (
      <div className={ui.card} role="alert">
        <h1>Administrators only</h1>
        <p>You need an administrator account to open this page.</p>
      </div>
    );
  }
  return <>{children}</>;
}
