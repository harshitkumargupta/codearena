import type { ContestStatus, ContestSummary } from '../api/types';

/** Contest status from the client clock; the api exposes no server time, so this can be off by the clock skew. */
export function statusAt(contest: Pick<ContestSummary, 'startTime' | 'endTime'>, now: number): ContestStatus {
  if (now < Date.parse(contest.startTime)) return 'UPCOMING';
  return now < Date.parse(contest.endTime) ? 'RUNNING' : 'ENDED';
}

export function formatDuration(ms: number): string {
  const total = Math.max(0, Math.ceil(ms / 1000));
  const days = Math.floor(total / 86_400);
  const hours = Math.floor((total % 86_400) / 3600);
  const minutes = Math.floor((total % 3600) / 60);
  const seconds = total % 60;
  const hms = [hours, minutes, seconds].map((n) => String(n).padStart(2, '0')).join(':');
  return days > 0 ? `${days}d ${hms}` : hms;
}

export function formatDateTime(iso: string): string {
  return new Date(iso).toLocaleString(undefined, { dateStyle: 'medium', timeStyle: 'short' });
}

export const STATUS_LABELS: Record<ContestStatus, string> = {
  UPCOMING: 'Upcoming',
  RUNNING: 'Running',
  ENDED: 'Ended',
};
