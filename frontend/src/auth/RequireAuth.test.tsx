import { render, screen } from '@testing-library/react';
import { MemoryRouter, Route, Routes, useLocation } from 'react-router';
import { describe, expect, it } from 'vitest';
import { authResponse } from '../test/fakes';
import { AuthProvider } from './AuthContext';
import { RequireAuth } from './RequireAuth';
import { tokenStore } from './tokenStore';

function LoginProbe() {
  const state = useLocation().state as { from?: string } | null;
  return <p>login page (from {state?.from ?? 'nowhere'})</p>;
}

function renderAt(path: string) {
  render(
    <MemoryRouter initialEntries={[path]}>
      <AuthProvider>
        <Routes>
          <Route path="/login" element={<LoginProbe />} />
          <Route
            path="/problems/:slug"
            element={
              <RequireAuth>
                <p>secret editor</p>
              </RequireAuth>
            }
          />
        </Routes>
      </AuthProvider>
    </MemoryRouter>,
  );
}

describe('RequireAuth', () => {
  it('redirects to login and remembers where the user was going', () => {
    renderAt('/problems/two-sum?tab=1');

    expect(screen.getByText('login page (from /problems/two-sum?tab=1)')).toBeInTheDocument();
    expect(screen.queryByText('secret editor')).not.toBeInTheDocument();
  });

  it('renders the protected page with a valid session', () => {
    tokenStore.save(authResponse());

    renderAt('/problems/two-sum');

    expect(screen.getByText('secret editor')).toBeInTheDocument();
  });

  it('treats an expired session as logged out', () => {
    tokenStore.save(authResponse({ expiresInSeconds: 10 }), Date.now() - 60_000);

    renderAt('/problems/two-sum');

    expect(screen.getByText(/login page/)).toBeInTheDocument();
  });
});
