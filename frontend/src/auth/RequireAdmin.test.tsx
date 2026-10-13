import { render, screen } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router';
import { describe, expect, it } from 'vitest';
import { Layout } from '../components/Layout';
import { authResponse } from '../test/fakes';
import { AuthProvider } from './AuthContext';
import { RequireAdmin } from './RequireAdmin';
import { tokenStore } from './tokenStore';

function renderAdmin() {
  render(
    <MemoryRouter initialEntries={['/admin']}>
      <AuthProvider>
        <Routes>
          <Route element={<Layout />}>
            <Route path="/login" element={<p>login page</p>} />
            <Route
              path="/admin"
              element={
                <RequireAdmin>
                  <p>admin tools</p>
                </RequireAdmin>
              }
            />
          </Route>
        </Routes>
      </AuthProvider>
    </MemoryRouter>,
  );
}

describe('admin visibility', () => {
  it('shows the Admin link and page to an ADMIN', () => {
    tokenStore.save(authResponse({ user: { id: 1, username: 'root', email: 'r@example.com', role: 'ADMIN', createdAt: '2026-10-13T10:00:00Z' } }));

    renderAdmin();

    expect(screen.getByRole('link', { name: 'Admin' })).toBeInTheDocument();
    expect(screen.getByText('admin tools')).toBeInTheDocument();
  });

  it('hides the Admin link from a USER and blocks the page', () => {
    tokenStore.save(authResponse());

    renderAdmin();

    expect(screen.queryByRole('link', { name: 'Admin' })).not.toBeInTheDocument();
    expect(screen.getByRole('alert')).toHaveTextContent('Administrators only');
    expect(screen.queryByText('admin tools')).not.toBeInTheDocument();
  });

  it('sends a logged-out visitor to login', () => {
    renderAdmin();

    expect(screen.getByText('login page')).toBeInTheDocument();
  });
});
