import type { ContestStatus } from '../api/types';
import { STATUS_LABELS } from '../time/contestClock';
import styles from './VerdictBadge.module.css';

const CLASS: Record<ContestStatus, string> = { UPCOMING: 'PENDING', RUNNING: 'ACCEPTED', ENDED: 'INTERNAL_ERROR' };

export function ContestStatusBadge({ status }: { status: ContestStatus }) {
  return <span className={`${styles.badge} ${styles[CLASS[status]]}`}>{STATUS_LABELS[status]}</span>;
}
