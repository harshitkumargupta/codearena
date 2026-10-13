import { useEffect, useState } from 'react';
import { Link } from 'react-router';
import { fetchAllPublishedProblems } from '../api/allProblems';
import { ApiError } from '../api/client';
import { api } from '../api/endpoints';
import type { PageResponse, ProblemSummary, SubmissionSummary, UserResponse } from '../api/types';
import ui from '../components/ui.module.css';
import { VerdictBadge } from '../components/VerdictBadge';
import { formatMemory } from '../components/verdicts';
import { LANGUAGE_LABELS } from '../editor/templates';
import { formatDateTime } from '../time/contestClock';
import tableStyles from '../components/SubmissionHistory.module.css';

const RECENT = 20;

interface ProfileData {
  user: UserResponse;
  submissions: PageResponse<SubmissionSummary>;
  problems: Map<number, ProblemSummary>;
}

export function ProfilePage() {
  const [data, setData] = useState<ProfileData | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;
    Promise.all([api.auth.me(), api.submissions.mine({ size: RECENT }), fetchAllPublishedProblems().catch(() => [])])
      .then(([user, submissions, problems]) => {
        if (!cancelled) setData({ user, submissions, problems: new Map(problems.map((p) => [p.id, p])) });
      })
      .catch((e: unknown) => {
        if (!cancelled) setError(e instanceof ApiError ? e.message : 'Could not load your profile.');
      });
    return () => {
      cancelled = true;
    };
  }, []);

  if (error) return <p className={ui.error}>{error}</p>;
  if (!data) return <p className={ui.hint}>Loading profile…</p>;
  const { user, submissions, problems } = data;

  return (
    <>
      <section className={ui.card} style={{ marginBottom: '1.25rem' }} aria-labelledby="profile-heading">
        <h1 id="profile-heading">{user.username}</h1>
        <dl>
          <dt>Role</dt>
          <dd>{user.role === 'ADMIN' ? 'Administrator' : 'User'}</dd>
          <dt>Email</dt>
          <dd>{user.email}</dd>
          <dt>Member since</dt>
          <dd>{formatDateTime(user.createdAt)}</dd>
          <dt>Total submissions</dt>
          <dd>{submissions.totalElements}</dd>
          <dt>Problems solved</dt>
          <dd className={ui.hint}>Not available yet: the api does not report a solved count.</dd>
        </dl>
      </section>
      <section className={ui.card} aria-labelledby="recent-heading">
        <h2 id="recent-heading" className={tableStyles.heading}>
          Recent submissions
        </h2>
        {submissions.content.length === 0 ? (
          <p className={ui.hint}>
            No submissions yet. <Link to="/problems">Pick a problem</Link>.
          </p>
        ) : (
          <table className={tableStyles.table}>
            <thead>
              <tr>
                <th scope="col">#</th>
                <th scope="col">Problem</th>
                <th scope="col">Submitted</th>
                <th scope="col">Language</th>
                <th scope="col">Status</th>
                <th scope="col">Time</th>
                <th scope="col">Memory</th>
              </tr>
            </thead>
            <tbody>
              {submissions.content.map((s) => {
                const problem = problems.get(s.problemId);
                return (
                  <tr key={s.id}>
                    <td>{s.id}</td>
                    <td>{problem ? <Link to={`/problems/${problem.slug}`}>{problem.title}</Link> : `Problem #${s.problemId}`}</td>
                    <td>{formatDateTime(s.createdAt)}</td>
                    <td>{LANGUAGE_LABELS[s.language]}</td>
                    <td>
                      <VerdictBadge status={s.status} />
                    </td>
                    <td>{s.timeMs !== null ? `${s.timeMs} ms` : '–'}</td>
                    <td>{formatMemory(s.memoryKb) ?? '–'}</td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        )}
      </section>
    </>
  );
}
