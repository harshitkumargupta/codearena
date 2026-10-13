import { isFinal, type Language, type SubmissionResponse, type SubmissionStatusUpdate, type Verdict } from '../api/types';

export interface SubmissionView {
  id: number;
  problemId: number;
  language: Language;
  status: Verdict;
  failedTestOrdinal: number | null;
  timeMs: number | null;
  memoryKb: number | null;
  createdAt: string;
  judgedAt: string | null;
  detailAvailable: boolean;
  verdictDetail: string | null;
}

export function fromResponse(s: SubmissionResponse): SubmissionView {
  return {
    id: s.id,
    problemId: s.problemId,
    language: s.language,
    status: s.status,
    failedTestOrdinal: s.failedTestOrdinal,
    timeMs: s.timeMs,
    memoryKb: s.memoryKb,
    createdAt: s.createdAt,
    judgedAt: s.judgedAt,
    detailAvailable: !!s.verdictDetail,
    verdictDetail: s.verdictDetail,
  };
}

export function fromUpdate(u: SubmissionStatusUpdate, previous: SubmissionView | null): SubmissionView {
  return {
    id: u.id,
    problemId: u.problemId,
    language: u.language,
    status: u.status,
    failedTestOrdinal: u.failedTestOrdinal,
    timeMs: u.timeMs,
    memoryKb: u.memoryKb,
    createdAt: u.createdAt,
    judgedAt: u.judgedAt,
    detailAvailable: u.detailAvailable,
    verdictDetail: previous && previous.status === u.status ? previous.verdictDetail : null,
  };
}

const rank = (status: Verdict): number => (isFinal(status) ? 2 : status === 'RUNNING' ? 1 : 0);

/** A final status always wins; otherwise the more advanced status wins (docs/websocket.md). */
export function merge(current: SubmissionView | null, next: SubmissionView): SubmissionView {
  if (!current || current.id !== next.id) return next;
  if (rank(next.status) < rank(current.status)) return current;
  if (rank(next.status) === rank(current.status) && isFinal(current.status) && !next.verdictDetail && current.verdictDetail) {
    return { ...next, verdictDetail: current.verdictDetail, detailAvailable: true };
  }
  return next;
}
