import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import type { ChangeEvent } from 'react';
import { MemoryRouter, Route, Routes } from 'react-router';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import type { ContestDetail, LeaderboardEntry, ProblemDetail } from '../api/types';
import { AuthProvider } from '../auth/AuthContext';
import { tokenStore } from '../auth/tokenStore';
import { authResponse, fetchRouter, jsonBody, jsonResponse } from '../test/fakes';
import { ContestPage } from './ContestPage';

vi.mock('../components/CodeEditor', () => ({
  default: ({ value, onChange }: { value: string; onChange: (v: string) => void }) => (
    <textarea aria-label="Code editor" value={value} onChange={(e: ChangeEvent<HTMLTextAreaElement>) => onChange(e.target.value)} />
  ),
}));

const HOUR = 3_600_000;
const PROBLEMS = [
  { label: 'A', problemId: 1, slug: 'two-sum', title: 'Two Sum', difficulty: 'EASY' as const },
  { label: 'B', problemId: 2, slug: 'fizzbuzz', title: 'FizzBuzz', difficulty: 'EASY' as const },
];
const TWO_SUM: ProblemDetail = {
  id: 1, slug: 'two-sum', title: 'Two Sum', statementMd: 'Find **two** numbers.', difficulty: 'EASY',
  timeLimitMs: 2000, memoryLimitMb: 256, samples: [{ ordinal: 1, input: '4 9\n2 7 11 15\n', expectedOutput: '0 1' }],
};

function contest(state: 'UPCOMING' | 'RUNNING' | 'ENDED'): ContestDetail {
  const now = Date.now();
  const [start, end] = state === 'UPCOMING' ? [now + HOUR, now + 2 * HOUR] : state === 'RUNNING' ? [now - HOUR, now + HOUR] : [now - 3 * HOUR, now - 2 * HOUR];
  return {
    id: 1, title: 'Weekly Cup', startTime: new Date(start).toISOString(), endTime: new Date(end).toISOString(),
    penaltyMinutes: 20, status: state, problemsVisible: state !== 'UPCOMING', problems: state === 'UPCOMING' ? [] : PROBLEMS,
  };
}

function setup(state: 'UPCOMING' | 'RUNNING' | 'ENDED', options: { entries?: LeaderboardEntry[]; submit?: () => Response } = {}) {
  const router = fetchRouter({
    'GET /api/contests/1': () => jsonResponse(200, contest(state)),
    'GET /api/contests/1/leaderboard': () => jsonResponse(200, { contestId: 1, entries: options.entries ?? [] }),
    'GET /api/problems/two-sum': () => jsonResponse(200, TWO_SUM),
    'GET /api/submissions/me': () => jsonResponse(200, { content: [], page: 0, size: 20, totalElements: 0, totalPages: 0 }),
    'POST /api/contests/1/join': () => jsonResponse(200, { contestId: 1, userId: 1, alreadyJoined: false }),
    'POST /api/submissions': options.submit ?? (() => jsonResponse(201, { id: 99, status: 'PENDING' })),
    'GET /api/submissions/99': () => jsonResponse(200, {
      id: 99, userId: 1, problemId: 1, contestId: 1, language: 'JAVA', sourceCode: 'x', status: 'PENDING', verdictDetail: null,
      failedTestOrdinal: null, timeMs: null, memoryKb: null, createdAt: new Date().toISOString(), judgedAt: null,
    }),
  });
  vi.stubGlobal('fetch', router.fn);
  render(
    <MemoryRouter initialEntries={['/contests/1']}>
      <AuthProvider>
        <Routes>
          <Route path="/contests/:id" element={<ContestPage />} />
        </Routes>
      </AuthProvider>
    </MemoryRouter>,
  );
  return router;
}

beforeEach(() => {
  tokenStore.save(authResponse());
});

afterEach(() => vi.unstubAllGlobals());

describe('ContestPage', () => {
  it('before the start shows only the countdown and a Join button, never a problem list', async () => {
    setup('UPCOMING');

    expect(await screen.findByRole('timer')).toHaveTextContent(/Starts in/);
    expect(screen.getByText(/Problems will appear here when the contest starts/)).toBeInTheDocument();
    expect(screen.queryByRole('tablist')).not.toBeInTheDocument();
    expect(screen.queryByText('Two Sum')).not.toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Join contest' })).toBeEnabled();
  });

  it('while running and joined shows problem tabs and submits with the contest id', async () => {
    const router = setup('RUNNING', { entries: [{ rank: 1, userId: 1, username: 'alice', solved: 0, penaltyMinutes: 0 }] });

    expect(await screen.findByRole('tab', { name: 'A. Two Sum' })).toHaveAttribute('aria-selected', 'true');
    expect(screen.getByRole('tab', { name: 'B. FizzBuzz' })).toBeInTheDocument();
    expect(await screen.findByText('Joined')).toBeInTheDocument();
    expect(screen.getByRole('timer')).toHaveTextContent(/Ends in/);
    expect(screen.queryByText(/You have not joined/)).not.toBeInTheDocument();

    await userEvent.click(await screen.findByRole('button', { name: 'Submit' }));

    await waitFor(() => expect(router.calls.some((c) => c.key === 'POST /api/submissions')).toBe(true));
    const body = jsonBody<Record<string, unknown>>(router.calls.find((c) => c.key === 'POST /api/submissions')?.init);
    expect(body).toMatchObject({ problemId: 1, language: 'JAVA', contestId: 1 });
  });

  it('while running but not joined warns, shows the api 403 clearly, and joining clears the warning', async () => {
    setup('RUNNING', { submit: () => jsonResponse(403, { status: 403, message: 'You have not joined this contest' }) });

    expect(await screen.findByText(/You have not joined this contest yet/)).toBeInTheDocument();
    await userEvent.click(await screen.findByRole('button', { name: 'Submit' }));
    expect(await screen.findByRole('alert')).toHaveTextContent('You have not joined this contest. Join the contest to submit.');

    await userEvent.click(screen.getByRole('button', { name: 'Join contest' }));
    expect(await screen.findByText('Joined')).toBeInTheDocument();
    expect(screen.queryByText(/You have not joined this contest yet/)).not.toBeInTheDocument();
  });

  it('shows the api 400 for a contest that is not running', async () => {
    setup('RUNNING', {
      submit: () => jsonResponse(400, { status: 400, message: 'Validation failed', fieldErrors: [{ field: 'contestId', message: 'contest is not running' }] }),
    });

    await userEvent.click(await screen.findByRole('button', { name: 'Submit' }));

    expect(await screen.findByRole('alert')).toHaveTextContent('Contest is not running.');
  });

  it('after the end is read-only: tabs and statement, no Join and no Submit', async () => {
    setup('ENDED');

    expect(await screen.findByText(/This contest has ended/)).toBeInTheDocument();
    expect(await screen.findByRole('tab', { name: 'A. Two Sum' })).toBeInTheDocument();
    const panel = screen.getByRole('tabpanel');
    expect(await within(panel).findByText(/Read-only: this contest has ended/)).toBeInTheDocument();
    expect(within(panel).getByText('two')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Submit' })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Join contest' })).not.toBeInTheDocument();
    expect(screen.queryByRole('timer')).not.toBeInTheDocument();
  });
});
