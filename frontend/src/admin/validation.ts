import type { ContestRequest, Difficulty, ProblemRequest, TestCaseRequest } from '../api/types';

export type FormErrors<K extends string> = Partial<Record<K, string>>;

export const SLUG_PATTERN = /^[a-z0-9]+(-[a-z0-9]+)*$/;
export const LIMITS = {
  title: 200,
  slug: 100,
  statement: 50_000,
  timeMs: { min: 100, max: 10_000 },
  memoryMb: { min: 32, max: 1024 },
  penalty: { min: 0, max: 1000 },
  contestDays: 30,
  contestProblems: 26,
  testCaseBytes: 1_048_576,
  testCasesPerUpload: 100,
} as const;

export const CONTEST_LABELS = 'ABCDEFGHIJKLMNOPQRSTUVWXYZ'.split('');

export interface ProblemForm {
  title: string;
  slug: string;
  statementMd: string;
  difficulty: Difficulty;
  timeLimitMs: string;
  memoryLimitMb: string;
  published: boolean;
}

export type ProblemField = keyof ProblemForm;

export function slugify(title: string): string {
  return title
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, '-')
    .replace(/^-+|-+$/g, '')
    .slice(0, LIMITS.slug);
}

function integerIn(value: string, min: number, max: number, label: string): string | undefined {
  if (!/^\d+$/.test(value.trim())) return `${label} must be a whole number`;
  const n = Number(value);
  return n < min || n > max ? `${label} must be between ${min} and ${max}` : undefined;
}

export function validateProblem(form: ProblemForm): FormErrors<ProblemField> {
  const errors: FormErrors<ProblemField> = {};
  if (!form.title.trim()) errors.title = 'Title is required';
  else if (form.title.length > LIMITS.title) errors.title = `At most ${LIMITS.title} characters`;
  if (!form.slug) errors.slug = 'Slug is required';
  else if (form.slug.length > LIMITS.slug) errors.slug = `At most ${LIMITS.slug} characters`;
  else if (!SLUG_PATTERN.test(form.slug)) errors.slug = 'Use lowercase letters and digits separated by single hyphens';
  if (!form.statementMd.trim()) errors.statementMd = 'Statement is required';
  else if (form.statementMd.length > LIMITS.statement) errors.statementMd = `At most ${LIMITS.statement} characters`;
  const time = integerIn(form.timeLimitMs, LIMITS.timeMs.min, LIMITS.timeMs.max, 'Time limit');
  if (time) errors.timeLimitMs = time;
  const memory = integerIn(form.memoryLimitMb, LIMITS.memoryMb.min, LIMITS.memoryMb.max, 'Memory limit');
  if (memory) errors.memoryLimitMb = memory;
  return errors;
}

export function toProblemRequest(form: ProblemForm): ProblemRequest {
  return {
    title: form.title.trim(),
    slug: form.slug,
    statementMd: form.statementMd,
    difficulty: form.difficulty,
    timeLimitMs: Number(form.timeLimitMs),
    memoryLimitMb: Number(form.memoryLimitMb),
    published: form.published,
  };
}

export interface ContestProblemRow {
  label: string;
  problemId: string;
}

export interface ContestForm {
  title: string;
  start: string;
  end: string;
  penaltyMinutes: string;
  problems: ContestProblemRow[];
}

export type ContestField = 'title' | 'start' | 'end' | 'penaltyMinutes' | 'problems';

/** Parses a datetime-local value ("2026-10-13T18:30" or with seconds) as local time. */
export function parseLocalDateTime(value: string): number | null {
  if (!/^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}(:\d{2})?$/.test(value)) return null;
  const time = new Date(value).getTime();
  return Number.isNaN(time) ? null : time;
}

export function validateContest(form: ContestForm): FormErrors<ContestField> {
  const errors: FormErrors<ContestField> = {};
  if (!form.title.trim()) errors.title = 'Title is required';
  else if (form.title.length > LIMITS.title) errors.title = `At most ${LIMITS.title} characters`;
  const start = parseLocalDateTime(form.start);
  const end = parseLocalDateTime(form.end);
  if (start === null) errors.start = 'Start time is required';
  if (end === null) errors.end = 'End time is required';
  if (start !== null && end !== null) {
    if (end <= start) errors.end = 'End must be after the start';
    else if (end - start > LIMITS.contestDays * 86_400_000) errors.end = `A contest may last at most ${LIMITS.contestDays} days`;
  }
  const penalty = integerIn(form.penaltyMinutes, LIMITS.penalty.min, LIMITS.penalty.max, 'Penalty');
  if (penalty) errors.penaltyMinutes = penalty;
  if (form.problems.length === 0) errors.problems = 'Add at least one problem';
  else if (form.problems.length > LIMITS.contestProblems) errors.problems = `At most ${LIMITS.contestProblems} problems`;
  else if (form.problems.some((p) => !p.problemId)) errors.problems = 'Choose a problem for every label';
  else if (new Set(form.problems.map((p) => p.label)).size !== form.problems.length) errors.problems = 'Labels must be unique';
  else if (new Set(form.problems.map((p) => p.problemId)).size !== form.problems.length) {
    errors.problems = 'A problem can appear only once';
  }
  return errors;
}

export function toContestRequest(form: ContestForm): ContestRequest {
  return {
    title: form.title.trim(),
    startTime: new Date(parseLocalDateTime(form.start) ?? 0).toISOString(),
    endTime: new Date(parseLocalDateTime(form.end) ?? 0).toISOString(),
    penaltyMinutes: Number(form.penaltyMinutes),
    problems: form.problems.map((p) => ({ label: p.label, problemId: Number(p.problemId) })),
  };
}

export function utf8Bytes(text: string): number {
  return new TextEncoder().encode(text).length;
}

export function validateTestCases(rows: TestCaseRequest[]): string | null {
  if (rows.length === 0) return 'Add at least one test case';
  if (rows.length > LIMITS.testCasesPerUpload) return `At most ${LIMITS.testCasesPerUpload} test cases per upload`;
  const index = rows.findIndex((r) => utf8Bytes(r.input) > LIMITS.testCaseBytes || utf8Bytes(r.expectedOutput) > LIMITS.testCaseBytes);
  if (index >= 0) return `Test case ${index + 1} is larger than 1 MB`;
  const empty = rows.findIndex((r) => r.input === '' && r.expectedOutput === '');
  if (empty >= 0) return `Test case ${empty + 1} has no input and no expected output`;
  return null;
}
