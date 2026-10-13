import { request } from './client';
import type {
  AuthResponse,
  Difficulty,
  ExplainFailureRequest,
  HintRequest,
  LoginRequest,
  PageResponse,
  ProblemDetail,
  ProblemSummary,
  RegisterRequest,
  SubmissionResponse,
  SubmissionSummary,
  SubmitRequest,
  SubmitResponse,
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
  tutor: {
    hint: (body: HintRequest) => request<TutorResponse>('POST', '/api/tutor/hint', { body }),
    explainFailure: (body: ExplainFailureRequest) =>
      request<TutorResponse>('POST', '/api/tutor/explain-failure', { body }),
  },
};
