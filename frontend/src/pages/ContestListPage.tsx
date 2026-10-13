import { useEffect, useState } from 'react';
import { Link, useSearchParams } from 'react-router';
import { ApiError } from '../api/client';
import { api } from '../api/endpoints';
import type { ContestSummary, PageResponse } from '../api/types';
import { ContestStatusBadge } from '../components/ContestStatusBadge';
import ui from '../components/ui.module.css';
import { formatDateTime, formatDuration, statusAt } from '../time/contestClock';
import { useNow } from '../time/useNow';
import listStyles from './ProblemListPage.module.css';

const PAGE_SIZE = 20;

export function ContestListPage() {
  const [params, setParams] = useSearchParams();
  const page = Math.max(0, Number(params.get('page') ?? 0) || 0);
  const [data, setData] = useState<PageResponse<ContestSummary> | null>(null);
  const [error, setError] = useState<string | null>(null);
  const now = useNow(15_000);

  useEffect(() => {
    let cancelled = false;
    api.contests
      .list({ page, size: PAGE_SIZE })
      .then((result) => {
        if (!cancelled) {
          setData(result);
          setError(null);
        }
      })
      .catch((e: unknown) => {
        if (!cancelled) setError(e instanceof ApiError ? e.message : 'Could not load contests.');
      });
    return () => {
      cancelled = true;
    };
  }, [page]);

  const go = (p: number) => setParams(p > 0 ? { page: String(p) } : {});

  return (
    <div className={ui.card}>
      <div className={listStyles.toolbar}>
        <h1>Contests</h1>
      </div>
      {error && <p className={ui.error}>{error}</p>}
      {!error && data === null && <p className={ui.hint}>Loading…</p>}
      {data && data.content.length === 0 && <p className={ui.hint}>No contests yet.</p>}
      {data && data.content.length > 0 && (
        <ul className={listStyles.list}>
          {data.content.map((c) => (
            <li key={c.id} className={listStyles.item}>
              <div>
                <Link to={`/contests/${c.id}`}>{c.title}</Link>
                <div className={ui.hint}>
                  {formatDateTime(c.startTime)} – {formatDateTime(c.endTime)} · {formatDuration(Date.parse(c.endTime) - Date.parse(c.startTime))} ·
                  penalty {c.penaltyMinutes} min
                </div>
              </div>
              <ContestStatusBadge status={statusAt(c, now)} />
            </li>
          ))}
        </ul>
      )}
      {data && data.totalPages > 1 && (
        <nav className={listStyles.pager} aria-label="Pages">
          <button type="button" className={`${ui.button} ${ui.secondary}`} disabled={page === 0} onClick={() => go(page - 1)}>
            Previous
          </button>
          <span>
            Page {data.page + 1} of {data.totalPages}
          </span>
          <button type="button" className={`${ui.button} ${ui.secondary}`} disabled={page + 1 >= data.totalPages} onClick={() => go(page + 1)}>
            Next
          </button>
        </nav>
      )}
    </div>
  );
}
