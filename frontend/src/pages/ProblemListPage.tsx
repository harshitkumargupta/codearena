import { useEffect, useState } from 'react';
import { Link, useSearchParams } from 'react-router';
import { ApiError } from '../api/client';
import { api } from '../api/endpoints';
import { DIFFICULTIES, type Difficulty, type PageResponse, type ProblemSummary } from '../api/types';
import { DifficultyBadge } from '../components/DifficultyBadge';
import ui from '../components/ui.module.css';
import styles from './ProblemListPage.module.css';

const PAGE_SIZE = 20;

function parseDifficulty(value: string | null): Difficulty | null {
  return DIFFICULTIES.find((d) => d === value) ?? null;
}

export function ProblemListPage() {
  const [params, setParams] = useSearchParams();
  const difficulty = parseDifficulty(params.get('difficulty'));
  const page = Math.max(0, Number(params.get('page') ?? 0) || 0);
  const [data, setData] = useState<PageResponse<ProblemSummary> | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;
    api.problems
      .list({ page, size: PAGE_SIZE, difficulty })
      .then((result) => {
        if (!cancelled) {
          setData(result);
          setError(null);
        }
      })
      .catch((e: unknown) => {
        if (!cancelled) setError(e instanceof ApiError ? e.message : 'Could not load problems.');
      });
    return () => {
      cancelled = true;
    };
  }, [page, difficulty]);

  const update = (next: { difficulty?: Difficulty | null; page?: number }) => {
    const search = new URLSearchParams();
    const d = next.difficulty === undefined ? difficulty : next.difficulty;
    if (d) search.set('difficulty', d);
    const p = next.page ?? 0;
    if (p > 0) search.set('page', String(p));
    setParams(search);
  };

  return (
    <div className={ui.card}>
      <div className={styles.toolbar}>
        <h1>Problems</h1>
        <div className={ui.field} style={{ marginBottom: 0 }}>
          <label htmlFor="difficulty-filter">Difficulty</label>
          <select
            id="difficulty-filter"
            value={difficulty ?? ''}
            onChange={(e) => update({ difficulty: parseDifficulty(e.target.value), page: 0 })}
          >
            <option value="">All</option>
            {DIFFICULTIES.map((d) => (
              <option key={d} value={d}>
                {d.charAt(0) + d.slice(1).toLowerCase()}
              </option>
            ))}
          </select>
        </div>
      </div>
      {error && <p className={ui.error}>{error}</p>}
      {!error && data === null && <p className={ui.hint}>Loading…</p>}
      {data && data.content.length === 0 && <p className={ui.hint}>No problems match this filter.</p>}
      {data && data.content.length > 0 && (
        <>
          <ul className={styles.list}>
            {data.content.map((p) => (
              <li key={p.id} className={styles.item}>
                <Link to={`/problems/${p.slug}`}>{p.title}</Link>
                <DifficultyBadge difficulty={p.difficulty} />
              </li>
            ))}
          </ul>
          {data.totalPages > 1 && (
            <nav className={styles.pager} aria-label="Pages">
              <button type="button" className={`${ui.button} ${ui.secondary}`} disabled={page === 0} onClick={() => update({ page: page - 1 })}>
                Previous
              </button>
              <span>
                Page {data.page + 1} of {data.totalPages}
              </span>
              <button
                type="button"
                className={`${ui.button} ${ui.secondary}`}
                disabled={page + 1 >= data.totalPages}
                onClick={() => update({ page: page + 1 })}
              >
                Next
              </button>
            </nav>
          )}
        </>
      )}
    </div>
  );
}
