import { render, screen, within } from '@testing-library/react';
import { MemoryRouter } from 'react-router';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { AuthProvider } from '../auth/AuthContext';
import { tokenStore } from '../auth/tokenStore';
import { authResponse, fetchRouter, jsonResponse } from '../test/fakes';
import { ProfilePage } from './ProfilePage';

afterEach(() => vi.unstubAllGlobals());

function renderProfile(routes: Parameters<typeof fetchRouter>[0]) {
  tokenStore.save(authResponse());
  vi.stubGlobal('fetch', fetchRouter(routes).fn);
  render(
    <MemoryRouter>
      <AuthProvider>
        <ProfilePage />
      </AuthProvider>
    </MemoryRouter>,
  );
}

describe('ProfilePage', () => {
  it('shows the user, total submissions and recent submissions, and says the solved count is unavailable', async () => {
    renderProfile({
      'GET /api/auth/me': () => jsonResponse(200, { id: 1, username: 'alice', email: 'alice@example.com', role: 'USER', createdAt: '2026-10-01T10:00:00Z' }),
      'GET /api/submissions/me': () =>
        jsonResponse(200, {
          content: [
            { id: 12, problemId: 1, language: 'PYTHON', status: 'ACCEPTED', timeMs: 40, memoryKb: 4800, createdAt: '2026-10-13T10:00:00Z' },
            { id: 11, problemId: 99, language: 'JAVA', status: 'WRONG_ANSWER', timeMs: 90, memoryKb: 42000, createdAt: '2026-10-13T09:00:00Z' },
          ],
          page: 0, size: 20, totalElements: 57, totalPages: 3,
        }),
      'GET /api/problems': () => jsonResponse(200, { content: [{ id: 1, slug: 'two-sum', title: 'Two Sum', difficulty: 'EASY' }], page: 0, size: 50, totalElements: 1, totalPages: 1 }),
    });

    expect(await screen.findByRole('heading', { name: 'alice' })).toBeInTheDocument();
    expect(screen.getByText('User')).toBeInTheDocument();
    expect(screen.getByText('57')).toBeInTheDocument();
    expect(screen.getByText(/Not available yet: the api does not report a solved count/)).toBeInTheDocument();
    const rows = within(screen.getByRole('table')).getAllByRole('row');
    expect(rows[1]).toHaveTextContent('Two Sum');
    expect(rows[1]).toHaveTextContent('Accepted');
    expect(rows[2]).toHaveTextContent('Problem #99');
    expect(rows[2]).toHaveTextContent('Wrong answer');
  });

  it('shows an error state when the profile cannot load', async () => {
    renderProfile({
      'GET /api/auth/me': () => jsonResponse(503, { status: 503, message: 'The service is temporarily unavailable.' }),
      'GET /api/submissions/me': () => jsonResponse(200, { content: [], page: 0, size: 20, totalElements: 0, totalPages: 0 }),
    });

    expect(await screen.findByText('The service is temporarily unavailable.')).toBeInTheDocument();
  });
});
