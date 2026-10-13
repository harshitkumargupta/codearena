import { useEffect, useState } from 'react';
import { ApiError } from '../api/client';
import { api } from '../api/endpoints';
import { EXPLAINABLE_VERDICTS, type HintLevel, type TutorResponse } from '../api/types';
import type { SubmissionView } from '../live/submissionView';
import { formatCountdown } from './countdown';
import { Markdown } from './Markdown';
import styles from './TutorPanel.module.css';
import ui from './ui.module.css';

type Action = 'hint-1' | 'hint-2' | 'hint-3' | 'explain';

const LEVELS: { level: HintLevel; label: string }[] = [
  { level: 1, label: 'Hint 1: nudge' },
  { level: 2, label: 'Hint 2: approach' },
  { level: 3, label: 'Hint 3: walkthrough' },
];

interface Props {
  problemId: number;
  submission: SubmissionView | null;
}

interface Failure {
  action: Action;
  message: string;
  retryAfter: number | null;
}

function describe(error: unknown): { message: string; retryAfter: number | null } {
  if (!(error instanceof ApiError)) return { message: 'Something went wrong. Please try again.', retryAfter: null };
  if (error.status === 429) {
    return { message: error.message || 'Too many tutor requests.', retryAfter: error.retryAfterSeconds };
  }
  if (error.status === 503) {
    return { message: error.message || 'The AI tutor is unavailable right now. Please try again later.', retryAfter: null };
  }
  return { message: error.message, retryAfter: null };
}

export function TutorPanel({ problemId, submission }: Props) {
  const [busy, setBusy] = useState<Action | null>(null);
  const [answer, setAnswer] = useState<TutorResponse | null>(null);
  const [failure, setFailure] = useState<Failure | null>(null);
  const [secondsLeft, setSecondsLeft] = useState(0);

  useEffect(() => {
    if (secondsLeft <= 0) return undefined;
    const timer = window.setTimeout(() => setSecondsLeft((s) => s - 1), 1000);
    return () => window.clearTimeout(timer);
  }, [secondsLeft]);

  const waiting = (action: Action) => failure?.action === action && secondsLeft > 0;
  const canExplain = submission !== null && EXPLAINABLE_VERDICTS.includes(submission.status);

  const run = async (action: Action, call: () => Promise<TutorResponse>) => {
    setBusy(action);
    setFailure(null);
    try {
      setAnswer(await call());
    } catch (error) {
      const { message, retryAfter } = describe(error);
      setFailure({ action, message, retryAfter });
      setSecondsLeft(retryAfter ?? 0);
    } finally {
      setBusy(null);
    }
  };

  return (
    <section className={ui.card} aria-labelledby="tutor-heading">
      <h2 id="tutor-heading" className={styles.heading}>
        AI tutor
      </h2>
      <p className={ui.hint}>Hints get more detailed with each level. Level 3 has a per-problem cooldown.</p>
      <div className={styles.buttons}>
        {LEVELS.map(({ level, label }) => {
          const action: Action = `hint-${level}`;
          return (
            <button
              key={level}
              type="button"
              className={`${ui.button} ${ui.secondary}`}
              disabled={busy !== null || waiting(action)}
              onClick={() =>
                void run(action, () =>
                  api.tutor.hint({ problemId, level, ...(submission ? { submissionId: submission.id } : {}) }),
                )
              }
            >
              {busy === action ? 'Thinking…' : label}
            </button>
          );
        })}
        <button
          type="button"
          className={`${ui.button} ${ui.secondary}`}
          disabled={!canExplain || busy !== null || waiting('explain')}
          title={canExplain ? undefined : 'Available after a judged failing submission'}
          onClick={() => {
            if (submission) void run('explain', () => api.tutor.explainFailure({ submissionId: submission.id }));
          }}
        >
          {busy === 'explain' ? 'Thinking…' : 'Explain failure'}
        </button>
      </div>
      {failure && (
        <div role="alert" className={ui.error}>
          {failure.message}
          {failure.retryAfter !== null && secondsLeft > 0 && <> Try again in {formatCountdown(secondsLeft)}.</>}
          {failure.retryAfter !== null && secondsLeft <= 0 && <> You can try again now.</>}
        </div>
      )}
      {answer && (
        <div className={`${styles.answer} ${answer.fallback ? styles.fallback : ''}`} data-testid="tutor-answer">
          {answer.fallback && <p className={ui.hint}>The tutor held back an answer that would have shown code.</p>}
          <Markdown>{answer.text}</Markdown>
        </div>
      )}
    </section>
  );
}
