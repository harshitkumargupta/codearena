import type { Verdict } from '../api/types';

export const VERDICT_LABELS: Record<Verdict, string> = {
  PENDING: 'Pending',
  RUNNING: 'Running',
  ACCEPTED: 'Accepted',
  WRONG_ANSWER: 'Wrong answer',
  TIME_LIMIT_EXCEEDED: 'Time limit exceeded',
  MEMORY_LIMIT_EXCEEDED: 'Memory limit exceeded',
  RUNTIME_ERROR: 'Runtime error',
  COMPILATION_ERROR: 'Compilation error',
  INTERNAL_ERROR: 'Judge error',
};

export function formatMemory(kb: number | null): string | null {
  if (kb === null) return null;
  return kb >= 1024 ? `${(kb / 1024).toFixed(1)} MB` : `${kb} KB`;
}
