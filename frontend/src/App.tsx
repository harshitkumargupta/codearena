import { Navigate, Route, Routes } from 'react-router';
import { AuthProvider } from './auth/AuthContext';
import { RequireAuth } from './auth/RequireAuth';
import { Layout } from './components/Layout';
import { LiveProvider } from './live/LiveContext';
import { LoginPage } from './pages/LoginPage';
import { NotFoundPage } from './pages/NotFoundPage';
import { ProblemListPage } from './pages/ProblemListPage';
import { ProblemPage } from './pages/ProblemPage';
import { RegisterPage } from './pages/RegisterPage';

export function AppRoutes() {
  return (
    <Routes>
      <Route element={<Layout />}>
        <Route index element={<Navigate to="/problems" replace />} />
        <Route path="problems" element={<ProblemListPage />} />
        <Route
          path="problems/:slug"
          element={
            <RequireAuth>
              <ProblemPage />
            </RequireAuth>
          }
        />
        <Route path="login" element={<LoginPage />} />
        <Route path="register" element={<RegisterPage />} />
        <Route path="*" element={<NotFoundPage />} />
      </Route>
    </Routes>
  );
}

export function App() {
  return (
    <AuthProvider>
      <LiveProvider>
        <AppRoutes />
      </LiveProvider>
    </AuthProvider>
  );
}
