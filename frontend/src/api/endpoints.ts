import { request } from './client';
import type {
  AdminProblemResponse,
  AuthResponse,
  ContestDetail,
  ContestRequest,
  ContestSummary,
  Difficulty,
  ExplainFailureRequest,
  HintRequest,
  JoinResponse,
  LeaderboardResponse,
  LoginRequest,
  PageResponse,
  ProblemDetail,
  ProblemRequest,
  ProblemSummary,
  RegisterRequest,
  SubmissionResponse,
  SubmissionSummary,
  SubmitRequest,
  SubmitResponse,
  TestCaseRequest,
  TestCaseSummary,
  TutorResponse,
  UserResponse,
} from './types';

export const api = {
  auth: {
    login: (body: LoginRequest) => request<AuthResponse>('POST', '/api/auth/login', { body, auth: false }),
    register: (body: RegisterRequest) => request<AuthResponse>('POST', '/api/auth/register', { body, auth: false }),
    me: () => request<UserResponse>('GET', '/api/auth/me'),
  },
  problems: {
    list: (params: { page?: number; size?: number; difficulty?: Difficulty | null }) =>
      request<PageResponse<ProblemSummary>>('GET', '/api/problems', { query: params }),
    get: (slug: string) => request<ProblemDetail>('GET', `/api/problems/${encodeURIComponent(slug)}`),
  },
  submissions: {
    submit: (body: SubmitRequest) => request<SubmitResponse>('POST', '/api/submissions', { body }),
    get: (id: number) => request<SubmissionResponse>('GET', `/api/submissions/${id}`),
    mine: (params: { problemId?: number; page?: number; size?: number }) =>
      request<PageResponse<SubmissionSummary>>('GET', '/api/submissions/me', { query: params }),
  },
  contests: {
    list: (params: { page?: number; size?: number }) =>
      request<PageResponse<ContestSummary>>('GET', '/api/contests', { query: params }),
    get: (id: number) => request<ContestDetail>('GET', `/api/contests/${id}`),
    join: (id: number) => request<JoinResponse>('POST', `/api/contests/${id}/join`),
    leaderboard: (id: number) => request<LeaderboardResponse>('GET', `/api/contests/${id}/leaderboard`),
  },
  admin: {
    createProblem: (body: ProblemRequest) => request<AdminProblemResponse>('POST', '/api/admin/problems', { body }),
    updateProblem: (id: number, body: ProblemRequest) =>
      request<AdminProblemResponse>('PUT', `/api/admin/problems/${id}`, { body }),
    addTestCases: (problemId: number, testCases: TestCaseRequest[]) =>
      request<TestCaseSummary[]>('POST', `/api/admin/problems/${problemId}/testcases`, { body: { testCases } }),
    createContest: (body: ContestRequest) => request<ContestDetail>('POST', '/api/admin/contests', { body }),
  },
  tutor: {
    hint: (body: HintRequest) => request<TutorResponse>('POST', '/api/tutor/hint', { body }),
    explainFailure: (body: ExplainFailureRequest) =>
      request<TutorResponse>('POST', '/api/tutor/explain-failure', { body }),
  },
};
