import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { AuthProvider } from '../../auth/AuthContext';
import { tokenStore } from '../../auth/tokenStore';
import { authResponse, fetchRouter, jsonBody, jsonResponse } from '../../test/fakes';
import { ContestFormPage } from './ContestFormPage';
import { ProblemFormPage } from './ProblemFormPage';
import { TestCasesPage } from './TestCasesPage';

const ADMIN = { id: 1, username: 'root', email: 'r@example.com', role: 'ADMIN' as const, createdAt: '2026-10-13T10:00:00Z' };

function renderAt(path: string, routes: Record<string, () => Response> = {}) {
  const router = fetchRouter({
    'GET /api/problems': () => jsonResponse(200, { content: [{ id: 1, slug: 'two-sum', title: 'Two Sum', difficulty: 'EASY' }, { id: 2, slug: 'fizzbuzz', title: 'FizzBuzz', difficulty: 'EASY' }], page: 0, size: 50, totalElements: 2, totalPages: 1 }),
    ...routes,
  });
  vi.stubGlobal('fetch', router.fn);
  render(
    <MemoryRouter initialEntries={[path]}>
      <AuthProvider>
        <Routes>
          <Route path="/admin/problems/new" element={<ProblemFormPage />} />
          <Route path="/admin/problems/:slug/edit" element={<ProblemFormPage />} />
          <Route path="/admin/problems/:slug/testcases" element={<TestCasesPage />} />
          <Route path="/admin/contests/new" element={<ContestFormPage />} />
          <Route path="/contests/:id" element={<p>contest page</p>} />
        </Routes>
      </AuthProvider>
    </MemoryRouter>,
  );
  return router;
}

beforeEach(() => tokenStore.save(authResponse({ user: ADMIN })));
afterEach(() => vi.unstubAllGlobals());

describe('problem form', () => {
  it('shows inline errors and sends nothing when invalid', async () => {
    const router = renderAt('/admin/problems/new');

    await userEvent.type(screen.getByLabelText('Slug'), 'Bad Slug');
    await userEvent.clear(screen.getByLabelText('Time limit (ms)'));
    await userEvent.type(screen.getByLabelText('Time limit (ms)'), '50');
    await userEvent.click(screen.getByRole('button', { name: 'Create problem' }));

    expect(screen.getByText('Title is required')).toBeInTheDocument();
    expect(screen.getByText('Use lowercase letters and digits separated by single hyphens')).toBeInTheDocument();
    expect(screen.getByText('Time limit must be between 100 and 10000')).toBeInTheDocument();
    expect(screen.getByText('Statement is required')).toBeInTheDocument();
    expect(screen.getByLabelText('Slug')).toHaveAttribute('aria-invalid', 'true');
    expect(router.calls.filter((c) => c.key.startsWith('POST'))).toHaveLength(0);
  });

  it('suggests the slug from the title, previews markdown and maps server field errors', async () => {
    const router = renderAt('/admin/problems/new', {
      'POST /api/admin/problems': () => jsonResponse(409, { status: 409, message: 'Slug is already in use' }),
    });

    await userEvent.type(screen.getByLabelText('Title'), 'Echo Lines');
    expect(screen.getByLabelText('Slug')).toHaveValue('echo-lines');
    await userEvent.type(screen.getByLabelText('Statement (Markdown)'), 'Print **each** line.');
    expect(screen.getByTestId('statement-preview').querySelector('strong')).toHaveTextContent('each');
    await userEvent.click(screen.getByRole('button', { name: 'Create problem' }));

    expect(await screen.findByRole('alert')).toHaveTextContent('Slug is already in use');
    const body = jsonBody<Record<string, unknown>>(router.calls.find((c) => c.key === 'POST /api/admin/problems')?.init);
    expect(body).toMatchObject({ title: 'Echo Lines', slug: 'echo-lines', timeLimitMs: 2000, memoryLimitMb: 256, published: false });
  });
});

describe('contest form', () => {
  it('rejects an end before the start and duplicate problems without calling the api', async () => {
    const router = renderAt('/admin/contests/new');
    await screen.findByLabelText('Problem A');

    await userEvent.type(screen.getByLabelText('Title'), 'Weekly');
    const start = screen.getByLabelText(/^Start/);
    const end = screen.getByLabelText(/^End/);
    await userEvent.clear(start);
    await userEvent.type(start, '2026-10-20T18:00');
    await userEvent.clear(end);
    await userEvent.type(end, '2026-10-20T17:00');
    await userEvent.selectOptions(screen.getByLabelText('Problem A'), 'Two Sum');
    await userEvent.click(screen.getByRole('button', { name: 'Add problem' }));
    await userEvent.selectOptions(screen.getByLabelText('Problem B'), 'Two Sum');
    await userEvent.click(screen.getByRole('button', { name: 'Create contest' }));

    expect(screen.getByText('End must be after the start')).toBeInTheDocument();
    expect(screen.getByText('A problem can appear only once')).toBeInTheDocument();
    expect(router.calls.filter((c) => c.key === 'POST /api/admin/contests')).toHaveLength(0);
  });

  it('creates a valid contest and opens it', async () => {
    const router = renderAt('/admin/contests/new', {
      'POST /api/admin/contests': () => jsonResponse(201, { id: 5 }),
    });
    await screen.findByLabelText('Problem A');

    await userEvent.type(screen.getByLabelText('Title'), 'Weekly');
    await userEvent.selectOptions(screen.getByLabelText('Problem A'), 'FizzBuzz');
    await userEvent.click(screen.getByRole('button', { name: 'Create contest' }));

    expect(await screen.findByText('contest page')).toBeInTheDocument();
    const body = jsonBody<Record<string, unknown>>(router.calls.find((c) => c.key === 'POST /api/admin/contests')?.init);
    expect(body).toMatchObject({ title: 'Weekly', penaltyMinutes: 20, problems: [{ label: 'A', problemId: 2 }] });
  });
});

describe('test cases form', () => {
  it('fills a text area from a file and lists only ordinal, id and sample flag afterwards', async () => {
    const router = renderAt('/admin/problems/two-sum/testcases', {
      'GET /api/problems/two-sum': () => jsonResponse(200, { id: 1, slug: 'two-sum', title: 'Two Sum', statementMd: 's', difficulty: 'EASY', timeLimitMs: 2000, memoryLimitMb: 256, samples: [] }),
      'POST /api/admin/problems/1/testcases': () => jsonResponse(201, [{ id: 40, ordinal: 7, sample: false }]),
    });
    await screen.findByText('Test cases: Two Sum');

    await userEvent.upload(screen.getByLabelText('Input file for test case 1'), new File(['3 6\n3 2 4\n'], 'in.txt', { type: 'text/plain' }));
    await waitFor(() => expect(screen.getByLabelText('Input')).toHaveValue('3 6\n3 2 4\n'));
    await userEvent.type(screen.getByLabelText('Expected output'), '1 2');
    await userEvent.click(screen.getByRole('button', { name: 'Upload 1 test case' }));

    const table = await screen.findByTestId('added-test-cases');
    expect(table).toHaveTextContent('7');
    expect(table).toHaveTextContent('40');
    expect(table).toHaveTextContent('No (hidden)');
    expect(table).not.toHaveTextContent('3 6');
    const body = jsonBody<{ testCases: unknown[] }>(router.calls.find((c) => c.key.startsWith('POST'))?.init);
    expect(body.testCases).toEqual([{ input: '3 6\n3 2 4\n', expectedOutput: '1 2', sample: false }]);
  });

  it('refuses to upload an empty test case', async () => {
    const router = renderAt('/admin/problems/two-sum/testcases', {
      'GET /api/problems/two-sum': () => jsonResponse(200, { id: 1, slug: 'two-sum', title: 'Two Sum', statementMd: 's', difficulty: 'EASY', timeLimitMs: 2000, memoryLimitMb: 256, samples: [] }),
    });
    await userEvent.click(await screen.findByRole('button', { name: 'Upload 1 test case' }));

    expect(screen.getByRole('alert')).toHaveTextContent('Test case 1 has no input and no expected output');
    expect(router.calls.filter((c) => c.key.startsWith('POST'))).toHaveLength(0);
  });
});

describe('problem form after create', () => {
  it('switches to edit mode with a fresh form, a confirmation and the new id for test cases', async () => {
    renderAt('/admin/problems/new', {
      'POST /api/admin/problems': () =>
        jsonResponse(201, {
          id: 31, slug: 'echo-lines', title: 'Echo Lines', statementMd: 'Echo.', difficulty: 'EASY', timeLimitMs: 2000,
          memoryLimitMb: 256, published: false, createdAt: '2026-10-13T10:00:00Z',
        }),
    });

    await userEvent.type(screen.getByLabelText('Title'), 'Echo Lines');
    await userEvent.type(screen.getByLabelText('Statement (Markdown)'), 'Echo.');
    await userEvent.click(screen.getByRole('button', { name: 'Create problem' }));

    expect(await screen.findByRole('status')).toHaveTextContent('Problem created.');
    expect(screen.getByRole('heading', { name: 'Edit problem: Echo Lines' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Save changes' })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Add test cases' })).toHaveAttribute('href', '/admin/problems/echo-lines/testcases');
  });
});
