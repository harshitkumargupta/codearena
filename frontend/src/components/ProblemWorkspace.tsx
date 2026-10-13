import { lazy, Suspense, useState, type ReactNode } from 'react';
import { ApiError } from '../api/client';
import { api } from '../api/endpoints';
import { isFinal, LANGUAGES, type Language, type ProblemDetail } from '../api/types';
import { loadDraft, saveDraft } from '../editor/drafts';
import { sourceSizeError } from '../editor/sourceSize';
import { LANGUAGE_LABELS } from '../editor/templates';
import { useLiveClient } from '../live/useLiveClient';
import { useSubmissionLive } from '../live/useSubmissionLive';
import { DifficultyBadge } from './DifficultyBadge';
import { Markdown } from './Markdown';
import styles from './ProblemWorkspace.module.css';
import { describeSubmitError } from './submitErrors';
import { SubmissionHistory } from './SubmissionHistory';
import { TutorPanel } from './TutorPanel';
import ui from './ui.module.css';
import { VerdictPanel } from './VerdictPanel';

const CodeEditor = lazy(() => import('./CodeEditor'));

interface Props {
  problem: ProblemDetail;
  contestId?: number;
  readOnly?: boolean;
  notice?: ReactNode;
  label?: string;
}

export function ProblemWorkspace({ problem, contestId, readOnly = false, notice, label }: Props) {
  const scope = `${contestId ?? 'practice'}:${problem.id}`;
  const [language, setLanguage] = useState<Language>('JAVA');
  const [source, setSource] = useState(() => loadDraft(scope, 'JAVA'));
  const [submitting, setSubmitting] = useState(false);
  const [submitError, setSubmitError] = useState<ApiError | null>(null);
  const [currentId, setCurrentId] = useState<number | null>(null);
  const [historyKey, setHistoryKey] = useState(0);
  const live = useLiveClient();
  const current = useSubmissionLive(currentId, live);
  const currentFinal = current.submission !== null && isFinal(current.submission.status);

  const sizeError = sourceSizeError(source);

  const changeLanguage = (next: Language) => {
    saveDraft(scope, language, source);
    setLanguage(next);
    setSource(loadDraft(scope, next));
  };

  const changeSource = (value: string) => {
    setSource(value);
    saveDraft(scope, language, value);
  };

  const submit = async () => {
    if (sizeError) return;
    setSubmitting(true);
    setSubmitError(null);
    try {
      const created = await api.submissions.submit({
        problemId: problem.id,
        language,
        sourceCode: source,
        ...(contestId !== undefined ? { contestId } : {}),
      });
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
        <h1 id="problem-title">
          {label && <span className={styles.label}>{label}. </span>}
          {problem.title}
        </h1>
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
        {notice}
        {!readOnly && (
          <section className={ui.card} aria-label="Solution">
            <div className={styles.toolbar}>
              <label htmlFor={`language-${scope}`}>Language</label>
              <select id={`language-${scope}`} value={language} onChange={(e) => changeLanguage(e.target.value as Language)}>
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
                <CodeEditor language={language} value={source} onChange={changeSource} />
              </Suspense>
            </div>
            {sizeError && (
              <p className={ui.error} role="alert">
                {sizeError}
              </p>
            )}
            {submitError && (
              <p className={ui.error} role="alert">
                {describeSubmitError(submitError)}
              </p>
            )}
          </section>
        )}

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
