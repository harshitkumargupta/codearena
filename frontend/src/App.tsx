import { Navigate, Route, Routes } from 'react-router';
import { AuthProvider } from './auth/AuthContext';
import { RequireAdmin } from './auth/RequireAdmin';
import { RequireAuth } from './auth/RequireAuth';
import { Layout } from './components/Layout';
import { LiveProvider } from './live/LiveContext';
import { AdminHomePage } from './pages/admin/AdminHomePage';
import { ContestFormPage } from './pages/admin/ContestFormPage';
import { ProblemFormPage } from './pages/admin/ProblemFormPage';
import { TestCasesPage } from './pages/admin/TestCasesPage';
import { ContestListPage } from './pages/ContestListPage';
import { ContestPage } from './pages/ContestPage';
import { LoginPage } from './pages/LoginPage';
import { NotFoundPage } from './pages/NotFoundPage';
import { ProblemListPage } from './pages/ProblemListPage';
import { ProblemPage } from './pages/ProblemPage';
import { ProfilePage } from './pages/ProfilePage';
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
        <Route path="contests" element={<ContestListPage />} />
        <Route path="contests/:id" element={<ContestPage />} />
        <Route
          path="profile"
          element={
            <RequireAuth>
              <ProfilePage />
            </RequireAuth>
          }
        />
        <Route path="admin" element={<RequireAdmin><AdminHomePage /></RequireAdmin>} />
        <Route path="admin/problems/new" element={<RequireAdmin><ProblemFormPage /></RequireAdmin>} />
        <Route path="admin/problems/:slug/edit" element={<RequireAdmin><ProblemFormPage /></RequireAdmin>} />
        <Route path="admin/problems/:slug/testcases" element={<RequireAdmin><TestCasesPage /></RequireAdmin>} />
        <Route path="admin/contests/new" element={<RequireAdmin><ContestFormPage /></RequireAdmin>} />
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
