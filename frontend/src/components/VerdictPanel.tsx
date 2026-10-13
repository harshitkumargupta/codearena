import { isFinal } from '../api/types';
import type { SubmissionView } from '../live/submissionView';
import type { Transport } from '../live/useSubmissionLive';
import ui from './ui.module.css';
import { VerdictBadge } from './VerdictBadge';
import styles from './VerdictPanel.module.css';
import { formatMemory, VERDICT_LABELS } from './verdicts';

interface Props {
  submission: SubmissionView | null;
  transport: Transport;
  error: string | null;
}

export function VerdictPanel({ submission, transport, error }: Props) {
  if (!submission) {
    return (
      <section className={`${ui.card} ${styles.panel}`} aria-live="polite" data-testid="verdict" data-status="LOADING">
        <p className={ui.hint}>{error ?? 'Loading submission…'}</p>
      </section>
    );
  }
  const { status } = submission;
  const final = isFinal(status);
  const memory = formatMemory(submission.memoryKb);
  return (
    <section
      className={`${ui.card} ${styles.panel} ${styles[status]}`}
      aria-live="polite"
      aria-label={`Submission ${submission.id}: ${VERDICT_LABELS[status]}`}
      data-testid="verdict"
      data-status={status}
    >
      <div className={styles.header}>
        <h3 className={styles.title}>Submission #{submission.id}</h3>
        <VerdictBadge status={status} />
        {!final && <span>{status === 'PENDING' ? 'Waiting for a judge…' : 'Judging…'}</span>}
        <span className={styles.transport}>{transport === 'polling' ? 'Live updates unavailable, checking periodically' : 'Live'}</span>
      </div>
      {final && (
        <ul className={styles.facts}>
          {submission.timeMs !== null && (
            <li>
              Time <strong>{submission.timeMs} ms</strong>
            </li>
          )}
          {memory && (
            <li>
              Memory <strong>{memory}</strong>
            </li>
          )}
          {submission.failedTestOrdinal !== null && status !== 'ACCEPTED' && (
            <li>
              Failed on test <strong>#{submission.failedTestOrdinal}</strong>
            </li>
          )}
        </ul>
      )}
      {final && submission.detailAvailable && (
        <>
          {submission.verdictDetail ? (
            <pre className={styles.detail} aria-label={status === 'COMPILATION_ERROR' ? 'Compiler output' : 'Details'}>
              {submission.verdictDetail}
            </pre>
          ) : (
            <p className={ui.hint}>Loading details…</p>
          )}
        </>
      )}
      {error && <p className={ui.error}>{error}</p>}
    </section>
  );
}
