import type { Verdict } from '../api/types';
import styles from './VerdictBadge.module.css';
import { VERDICT_LABELS } from './verdicts';

export function VerdictBadge({ status }: { status: Verdict }) {
  return <span className={`${styles.badge} ${styles[status]}`}>{VERDICT_LABELS[status]}</span>;
}
