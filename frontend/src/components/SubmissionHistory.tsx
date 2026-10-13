import { useEffect, useState } from 'react';
import { ApiError } from '../api/client';
import { api } from '../api/endpoints';
import { isFinal, type SubmissionSummary } from '../api/types';
import { useLiveClient } from '../live/useLiveClient';
import { useSubmissionLive } from '../live/useSubmissionLive';
import { LANGUAGE_LABELS } from '../editor/templates';
import styles from './SubmissionHistory.module.css';
import ui from './ui.module.css';
import { VerdictBadge } from './VerdictBadge';
import { formatMemory } from './verdicts';

interface Props {
  problemId: number;
  refreshKey: string;
  selectedId: number | null;
  onSelect: (id: number) => void;
}

function LiveStatus({ row }: { row: SubmissionSummary }) {
  const live = useLiveClient();
  const { submission } = useSubmissionLive(row.id, live);
  return <VerdictBadge status={submission?.status ?? row.status} />;
}

export function SubmissionHistory({ problemId, refreshKey, selectedId, onSelect }: Props) {
  const [rows, setRows] = useState<SubmissionSummary[] | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;
    api.submissions
      .mine({ problemId, size: 20 })
      .then((page) => {
        if (!cancelled) {
          setRows(page.content);
          setError(null);
        }
      })
      .catch((e: unknown) => {
        if (!cancelled) setError(e instanceof ApiError ? e.message : 'Could not load your submissions.');
      });
    return () => {
      cancelled = true;
    };
  }, [problemId, refreshKey]);

  return (
    <section className={ui.card} aria-labelledby="history-heading">
      <h2 id="history-heading" className={styles.heading}>
        Your submissions
      </h2>
      {error && <p className={ui.error}>{error}</p>}
      {!error && rows === null && <p className={ui.hint}>Loading…</p>}
      {rows !== null && rows.length === 0 && <p className={ui.hint}>No submissions yet.</p>}
      {rows !== null && rows.length > 0 && (
        <table className={styles.table}>
          <thead>
            <tr>
              <th scope="col">#</th>
              <th scope="col">Submitted</th>
              <th scope="col">Language</th>
              <th scope="col">Status</th>
              <th scope="col">Time</th>
              <th scope="col">Memory</th>
            </tr>
          </thead>
          <tbody>
            {rows.map((row) => (
              <tr key={row.id} className={`${styles.row} ${row.id === selectedId ? styles.selected : ''}`}>
                <td>
                  <button type="button" onClick={() => onSelect(row.id)} aria-label={`Show submission ${row.id}`}>
                    {row.id}
                  </button>
                </td>
                <td>{new Date(row.createdAt).toLocaleString()}</td>
                <td>{LANGUAGE_LABELS[row.language]}</td>
                <td>{isFinal(row.status) ? <VerdictBadge status={row.status} /> : <LiveStatus row={row} />}</td>
                <td>{row.timeMs !== null ? `${row.timeMs} ms` : '–'}</td>
                <td>{formatMemory(row.memoryKb) ?? '–'}</td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </section>
  );
}
