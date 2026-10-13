import { memo, useCallback, useEffect } from 'react';
import { api } from '../api/endpoints';
import type { LeaderboardEntry, LeaderboardResponse } from '../api/types';
import { useLiveClient } from '../live/useLiveClient';
import { useTopicSnapshot } from '../live/useTopicSnapshot';
import styles from './Leaderboard.module.css';
import ui from './ui.module.css';

export const LEADERBOARD_POLL_MS = 5000;

const Row = memo(function Row({ entry, me }: { entry: LeaderboardEntry; me: boolean }) {
  return (
    <tr className={me ? styles.me : undefined} aria-current={me ? 'true' : undefined} data-testid="leaderboard-row">
      <td className={styles.number}>{entry.rank}</td>
      <td className={styles.user}>
        {entry.username}
        {me && <span className="visually-hidden"> (you)</span>}
      </td>
      <td className={styles.number}>{entry.solved}</td>
      <td className={styles.number}>{entry.penaltyMinutes}</td>
    </tr>
  );
});

interface TableProps {
  entries: LeaderboardEntry[];
  currentUserId: number | null;
}

export function LeaderboardTable({ entries, currentUserId }: TableProps) {
  if (entries.length === 0) return <p className={ui.hint}>No participants yet.</p>;
  return (
    <div className={styles.scroll}>
    <table className={styles.table}>
      <thead>
        <tr>
          <th scope="col" className={styles.number}>
            Rank
          </th>
          <th scope="col">User</th>
          <th scope="col" className={styles.number}>
            Solved
          </th>
          <th scope="col" className={styles.number}>
            Penalty
          </th>
        </tr>
      </thead>
      <tbody>
        {entries.map((entry) => (
          <Row key={entry.userId} entry={entry} me={entry.userId === currentUserId} />
        ))}
      </tbody>
    </table>
    </div>
  );
}

function isLeaderboard(contestId: number) {
  return (body: unknown): body is LeaderboardResponse =>
    typeof body === 'object' && body !== null && (body as { contestId?: unknown }).contestId === contestId
    && Array.isArray((body as { entries?: unknown }).entries);
}

interface Props {
  contestId: number;
  currentUserId: number | null;
  onMembership?: (isMember: boolean) => void;
}

export function LiveLeaderboard({ contestId, currentUserId, onMembership }: Props) {
  const live = useLiveClient();
  const fetchBoard = useCallback(() => api.contests.leaderboard(contestId), [contestId]);
  const guard = useCallback((body: unknown) => isLeaderboard(contestId)(body), [contestId]);
  const { data, transport, error } = useTopicSnapshot(
    `/topic/contests/${contestId}/leaderboard`,
    fetchBoard,
    guard,
    live,
    LEADERBOARD_POLL_MS,
  );
  const isMember = data !== null && currentUserId !== null && data.entries.some((e) => e.userId === currentUserId);

  useEffect(() => {
    if (isMember) onMembership?.(true);
  }, [isMember, onMembership]);

  return (
    <section className={ui.card} aria-labelledby="leaderboard-heading" data-testid="leaderboard">
      <div className={styles.header}>
        <h2 id="leaderboard-heading">Leaderboard</h2>
        <span className={styles.transport}>{transport === 'live' ? 'Live' : 'Refreshing every few seconds'}</span>
      </div>
      {error && <p className={ui.error}>{error}</p>}
      {!error && data === null && <p className={ui.hint}>Loading…</p>}
      {data && <LeaderboardTable entries={data.entries} currentUserId={currentUserId} />}
    </section>
  );
}
