import { lazy, Suspense, useEffect, useState } from 'react';
import { Link, useParams } from 'react-router';
import { ApiError } from '../api/client';
import { api } from '../api/endpoints';
import { isFinal, LANGUAGES, type Language, type ProblemDetail } from '../api/types';
import { DifficultyBadge } from '../components/DifficultyBadge';
import { Markdown } from '../components/Markdown';
import { SubmissionHistory } from '../components/SubmissionHistory';
import { TutorPanel } from '../components/TutorPanel';
import ui from '../components/ui.module.css';
import { VerdictPanel } from '../components/VerdictPanel';
import { sourceSizeError } from '../editor/sourceSize';
import { LANGUAGE_LABELS, STARTER_TEMPLATES } from '../editor/templates';
import { useLiveClient } from '../live/useLiveClient';
import { useSubmissionLive } from '../live/useSubmissionLive';
import styles from './ProblemPage.module.css';

const CodeEditor = lazy(() => import('../components/CodeEditor'));

export function ProblemPage() {
  const { slug = '' } = useParams();
  const [loaded, setLoaded] = useState<{ slug: string; problem: ProblemDetail | null; error: string | null } | null>(null);
  const [language, setLanguage] = useState<Language>('JAVA');
  const [drafts, setDrafts] = useState<Record<Language, string>>({ ...STARTER_TEMPLATES });
  const [submitting, setSubmitting] = useState(false);
  const [submitError, setSubmitError] = useState<ApiError | null>(null);
  const [currentId, setCurrentId] = useState<number | null>(null);
  const [historyKey, setHistoryKey] = useState(0);
  const live = useLiveClient();
  const current = useSubmissionLive(currentId, live);
  const currentFinal = current.submission !== null && isFinal(current.submission.status);

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

  const problem = loaded?.slug === slug ? loaded.problem : null;
  const loadError = loaded?.slug === slug ? loaded.error : null;

  if (loadError) {
    return (
      <div className={ui.card}>
        <p className={ui.error}>{loadError}</p>
        <Link to="/problems">Back to problems</Link>
      </div>
    );
  }
  if (!problem) return <p className={ui.hint}>Loading problem…</p>;

  const source = drafts[language];
  const sizeError = sourceSizeError(source);
  const sourceFieldError = submitError?.fieldError('sourceCode');

  const submit = async () => {
    if (sizeError) return;
    setSubmitting(true);
    setSubmitError(null);
    try {
      const created = await api.submissions.submit({ problemId: problem.id, language, sourceCode: source });
      setCurrentId(created.id);
      setHistoryKey((k) => k + 1);
    } catch (e) {
      setSubmitError(e instanceof ApiError ? e : new ApiError(0, 'Submission failed.'));
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className={styles.layout}>
      <article className={`${ui.card} ${styles.statement}`} aria-labelledby="problem-title">
        <h1 id="problem-title">{problem.title}</h1>
        <div className={styles.meta}>
          <DifficultyBadge difficulty={problem.difficulty} />
          <span>Time limit {problem.timeLimitMs} ms</span>
          <span>Memory limit {problem.memoryLimitMb} MB</span>
        </div>
        <Markdown>{problem.statementMd}</Markdown>
        {problem.samples.length > 0 && <h2>Samples</h2>}
        {problem.samples.map((sample) => (
          <div key={sample.ordinal} className={styles.sample}>
            <strong>Sample {sample.ordinal}</strong>
            <div>Input</div>
            <pre>{sample.input}</pre>
            <div>Expected output</div>
            <pre>{sample.expectedOutput}</pre>
          </div>
        ))}
      </article>

      <div className={styles.work}>
        <section className={ui.card} aria-label="Solution">
          <div className={styles.toolbar}>
            <label htmlFor="language">Language</label>
            <select id="language" value={language} onChange={(e) => setLanguage(e.target.value as Language)}>
              {LANGUAGES.map((l) => (
                <option key={l} value={l}>
                  {LANGUAGE_LABELS[l]}
                </option>
              ))}
            </select>
            <button
              type="button"
              className={`${ui.button} ${styles.submit}`}
              disabled={submitting || !!sizeError || source.trim() === ''}
              onClick={() => void submit()}
            >
              {submitting ? 'Submitting…' : 'Submit'}
            </button>
          </div>
          <div className={styles.editor}>
            <Suspense fallback={<p className={ui.hint}>Loading editor…</p>}>
              <CodeEditor language={language} value={source} onChange={(value) => setDrafts((d) => ({ ...d, [language]: value }))} />
            </Suspense>
          </div>
          {sizeError && (
            <p className={ui.error} role="alert">
              {sizeError}
            </p>
          )}
          {sourceFieldError && (
            <p className={ui.error} role="alert">
              Source code: {sourceFieldError}
            </p>
          )}
          {submitError && !sourceFieldError && (
            <p className={ui.error} role="alert">
              {submitError.fieldErrors.length > 0
                ? submitError.fieldErrors.map((f) => `${f.field}: ${f.message}`).join('; ')
                : submitError.message}
            </p>
          )}
        </section>

        {currentId !== null && <VerdictPanel {...current} />}
        <TutorPanel problemId={problem.id} submission={current.submission} />
        <SubmissionHistory
          problemId={problem.id}
          refreshKey={`${historyKey}:${currentId ?? ''}:${currentFinal}`}
          selectedId={currentId}
          onSelect={setCurrentId}
        />
      </div>
    </div>
  );
}
