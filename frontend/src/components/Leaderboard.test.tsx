import { render, screen, within } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import type { LeaderboardEntry } from '../api/types';
import { LeaderboardTable } from './Leaderboard';

const ENTRIES: LeaderboardEntry[] = [
  { rank: 1, userId: 6, username: 'alice', solved: 2, penaltyMinutes: 21 },
  { rank: 1, userId: 7, username: 'bob', solved: 2, penaltyMinutes: 21 },
  { rank: 3, userId: 8, username: 'carol', solved: 1, penaltyMinutes: 5 },
];

describe('LeaderboardTable', () => {
  it('shows tied users with the same rank and the next rank skipping', () => {
    render(<LeaderboardTable entries={ENTRIES} currentUserId={null} />);

    const rows = screen.getAllByTestId('leaderboard-row');
    expect(rows.map((r) => within(r).getAllByRole('cell').map((c) => c.textContent))).toEqual([
      ['1', 'alice', '2', '21'],
      ['1', 'bob', '2', '21'],
      ['3', 'carol', '1', '5'],
    ]);
  });

  it('highlights the current user', () => {
    render(<LeaderboardTable entries={ENTRIES} currentUserId={7} />);

    const rows = screen.getAllByTestId('leaderboard-row');
    expect(rows[1]).toHaveAttribute('aria-current', 'true');
    expect(rows[1]).toHaveTextContent('bob (you)');
    expect(rows[0]).not.toHaveAttribute('aria-current');
  });

  it('has an empty state', () => {
    render(<LeaderboardTable entries={[]} currentUserId={1} />);
    expect(screen.getByText('No participants yet.')).toBeInTheDocument();
  });
});
