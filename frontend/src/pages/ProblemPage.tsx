import { useEffect, useState } from 'react';
import { Link, useParams } from 'react-router';
import { ApiError } from '../api/client';
import { api } from '../api/endpoints';
import type { ProblemDetail } from '../api/types';
import { ProblemWorkspace } from '../components/ProblemWorkspace';
import ui from '../components/ui.module.css';

export function ProblemPage() {
  const { slug = '' } = useParams();
  const [loaded, setLoaded] = useState<{ slug: string; problem: ProblemDetail | null; error: string | null } | null>(null);

  useEffect(() => {
    let cancelled = false;
    api.problems
      .get(slug)
      .then((problem) => {
        if (!cancelled) setLoaded({ slug, problem, error: null });
      })
      .catch((e: unknown) => {
        const error = e instanceof ApiError && e.status === 404 ? 'Problem not found.' : 'Could not load the problem.';
        if (!cancelled) setLoaded({ slug, problem: null, error });
      });
    return () => {
      cancelled = true;
    };
  }, [slug]);

  const current = loaded?.slug === slug ? loaded : null;
  if (current?.error) {
    return (
      <div className={ui.card}>
        <p className={ui.error}>{current.error}</p>
        <Link to="/problems">Back to problems</Link>
      </div>
    );
  }
  if (!current?.problem) return <p className={ui.hint}>Loading problem…</p>;
  return <ProblemWorkspace key={current.problem.id} problem={current.problem} />;
}
