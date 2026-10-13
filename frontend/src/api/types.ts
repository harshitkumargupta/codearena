export type Role = 'USER' | 'ADMIN';
export type Difficulty = 'EASY' | 'MEDIUM' | 'HARD';
export type Language = 'JAVA' | 'PYTHON';
export type Verdict =
  | 'PENDING'
  | 'RUNNING'
  | 'ACCEPTED'
  | 'WRONG_ANSWER'
  | 'TIME_LIMIT_EXCEEDED'
  | 'MEMORY_LIMIT_EXCEEDED'
  | 'RUNTIME_ERROR'
  | 'COMPILATION_ERROR'
  | 'INTERNAL_ERROR';
export type HintKind = 'HINT' | 'EXPLANATION';
export type HintLevel = 1 | 2 | 3;

export const DIFFICULTIES: readonly Difficulty[] = ['EASY', 'MEDIUM', 'HARD'];
export const LANGUAGES: readonly Language[] = ['JAVA', 'PYTHON'];
export const EXPLAINABLE_VERDICTS: readonly Verdict[] = [
  'WRONG_ANSWER',
  'TIME_LIMIT_EXCEEDED',
  'MEMORY_LIMIT_EXCEEDED',
  'RUNTIME_ERROR',
  'COMPILATION_ERROR',
];

export function isFinal(status: Verdict): boolean {
  return status !== 'PENDING' && status !== 'RUNNING';
}

export interface UserResponse {
  id: number;
  username: string;
  email: string;
  role: Role;
  createdAt: string;
}

export interface AuthResponse {
  accessToken: string;
  tokenType: string;
  expiresInSeconds: number;
  user: UserResponse;
}

export interface RegisterRequest {
  username: string;
  email: string;
  password: string;
}

export interface LoginRequest {
  username: string;
  password: string;
}

export interface PageResponse<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface ProblemSummary {
  id: number;
  slug: string;
  title: string;
  difficulty: Difficulty;
}

export interface SampleTestCase {
  ordinal: number;
  input: string;
  expectedOutput: string;
}

export interface ProblemDetail {
  id: number;
  slug: string;
  title: string;
  statementMd: string;
  difficulty: Difficulty;
  timeLimitMs: number;
  memoryLimitMb: number;
  samples: SampleTestCase[];
}

export interface SubmitRequest {
  problemId: number;
  language: Language;
  sourceCode: string;
  contestId?: number;
}

export interface SubmitResponse {
  id: number;
  status: Verdict;
}

export interface SubmissionResponse {
  id: number;
  userId: number;
  problemId: number;
  contestId: number | null;
  language: Language;
  sourceCode: string;
  status: Verdict;
  verdictDetail: string | null;
  failedTestOrdinal: number | null;
  timeMs: number | null;
  memoryKb: number | null;
  createdAt: string;
  judgedAt: string | null;
}

export interface SubmissionSummary {
  id: number;
  problemId: number;
  language: Language;
  status: Verdict;
  timeMs: number | null;
  memoryKb: number | null;
  createdAt: string;
}

export interface SubmissionStatusUpdate {
  id: number;
  userId: number;
  problemId: number;
  contestId: number | null;
  language: Language;
  status: Verdict;
  failedTestOrdinal: number | null;
  timeMs: number | null;
  memoryKb: number | null;
  createdAt: string;
  judgedAt: string | null;
  detailAvailable: boolean;
}

export interface ApiFieldError {
  field: string;
  message: string;
}

export interface ApiErrorBody {
  timestamp: string;
  status: number;
  error: string;
  message: string;
  path: string;
  fieldErrors?: ApiFieldError[];
}

export interface HintRequest {
  problemId: number;
  level: HintLevel;
  submissionId?: number;
}

export interface ExplainFailureRequest {
  submissionId: number;
}

export interface TutorResponse {
  kind: HintKind;
  level: number | null;
  text: string;
  fallback: boolean;
}

export type ContestStatus = 'UPCOMING' | 'RUNNING' | 'ENDED';

export interface ContestSummary {
  id: number;
  title: string;
  startTime: string;
  endTime: string;
  penaltyMinutes: number;
  status: ContestStatus;
}

export interface ContestProblemView {
  label: string;
  problemId: number;
  slug: string;
  title: string;
  difficulty: Difficulty;
}

export interface ContestDetail extends ContestSummary {
  problemsVisible: boolean;
  problems: ContestProblemView[];
}

export interface JoinResponse {
  contestId: number;
  userId: number;
  alreadyJoined: boolean;
}

export interface LeaderboardEntry {
  rank: number;
  userId: number;
  username: string;
  solved: number;
  penaltyMinutes: number;
}

export interface LeaderboardResponse {
  contestId: number;
  entries: LeaderboardEntry[];
}

export interface ProblemRequest {
  slug: string;
  title: string;
  statementMd: string;
  difficulty: Difficulty;
  timeLimitMs: number;
  memoryLimitMb: number;
  published: boolean;
}

export interface AdminProblemResponse extends ProblemRequest {
  id: number;
  createdAt: string;
}

export interface TestCaseRequest {
  input: string;
  expectedOutput: string;
  sample: boolean;
}

export interface TestCaseSummary {
  id: number;
  ordinal: number;
  sample: boolean;
}

export interface ContestProblemRequest {
  problemId: number;
  label: string;
}

export interface ContestRequest {
  title: string;
  startTime: string;
  endTime: string;
  penaltyMinutes?: number;
  problems: ContestProblemRequest[];
}
