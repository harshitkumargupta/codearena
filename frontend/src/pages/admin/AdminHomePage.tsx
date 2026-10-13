import { useEffect, useState } from 'react';
import { Link } from 'react-router';
import { fetchAllPublishedProblems } from '../../api/allProblems';
import { ApiError } from '../../api/client';
import type { ProblemSummary } from '../../api/types';
import { DifficultyBadge } from '../../components/DifficultyBadge';
import ui from '../../components/ui.module.css';
import styles from './admin.module.css';

export function AdminHomePage() {
  const [problems, setProblems] = useState<ProblemSummary[] | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;
    fetchAllPublishedProblems()
      .then((all) => {
        if (!cancelled) setProblems(all);
      })
      .catch((e: unknown) => {
        if (!cancelled) setError(e instanceof ApiError ? e.message : 'Could not load problems.');
      });
    return () => {
      cancelled = true;
    };
  }, []);

  return (
    <div className={ui.card}>
      <h1>Admin</h1>
      <div className={styles.actions}>
        <Link className={ui.button} to="/admin/problems/new">
          New problem
        </Link>
        <Link className={`${ui.button} ${ui.secondary}`} to="/admin/contests/new">
          New contest
        </Link>
      </div>
      <h2>Published problems</h2>
      <p className={ui.hint}>
        Unpublished problems are not listed: the api has no admin problem list, so they can only be edited right after
        creating them.
      </p>
      {error && <p className={ui.error}>{error}</p>}
      {!error && problems === null && <p className={ui.hint}>Loading…</p>}
      {problems && problems.length === 0 && <p className={ui.hint}>No published problems yet.</p>}
      {problems && problems.length > 0 && (
        <ul className={styles.list}>
          {problems.map((p) => (
            <li key={p.id}>
              <span>
                {p.title} <DifficultyBadge difficulty={p.difficulty} />
              </span>
              <span className={styles.actions}>
                <Link to={`/admin/problems/${p.slug}/edit`}>Edit</Link>
                <Link to={`/admin/problems/${p.slug}/testcases`}>Test cases</Link>
              </span>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}
