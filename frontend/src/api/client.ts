import { API_BASE_URL } from '../config';
import { tokenStore } from '../auth/tokenStore';
import type { ApiErrorBody, ApiFieldError } from './types';

export class ApiError extends Error {
  readonly status: number;
  readonly fieldErrors: ApiFieldError[];
  readonly retryAfterSeconds: number | null;

  constructor(status: number, message: string, fieldErrors: ApiFieldError[] = [], retryAfterSeconds: number | null = null) {
    super(message);
    this.name = 'ApiError';
    this.status = status;
    this.fieldErrors = fieldErrors;
    this.retryAfterSeconds = retryAfterSeconds;
  }

  fieldError(field: string): string | undefined {
    return this.fieldErrors.find((e) => e.field === field)?.message;
  }
}

let unauthorizedHandler: () => void = () => undefined;

export function setUnauthorizedHandler(handler: () => void): () => void {
  unauthorizedHandler = handler;
  return () => {
    if (unauthorizedHandler === handler) unauthorizedHandler = () => undefined;
  };
}

type Query = Record<string, string | number | undefined | null>;

interface RequestOptions {
  body?: unknown;
  query?: Query;
  auth?: boolean;
}

const DEFAULT_MESSAGES: Record<number, string> = {
  401: 'Please log in to continue.',
  403: 'You are not allowed to do that.',
  404: 'Not found.',
  429: 'Too many requests. Please wait and try again.',
  503: 'The service is temporarily unavailable. Please try again later.',
};

function buildUrl(path: string, query?: Query): string {
  const params = new URLSearchParams();
  Object.entries(query ?? {}).forEach(([key, value]) => {
    if (value !== undefined && value !== null && value !== '') params.set(key, String(value));
  });
  const qs = params.toString();
  return `${API_BASE_URL}${path}${qs ? `?${qs}` : ''}`;
}

export function parseRetryAfter(header: string | null, now: number = Date.now()): number | null {
  if (!header) return null;
  const seconds = Number(header);
  if (Number.isFinite(seconds)) return Math.max(0, Math.ceil(seconds));
  const date = Date.parse(header);
  return Number.isNaN(date) ? null : Math.max(0, Math.ceil((date - now) / 1000));
}

async function readErrorBody(response: Response): Promise<Partial<ApiErrorBody> | null> {
  try {
    return (await response.json()) as Partial<ApiErrorBody>;
  } catch {
    return null;
  }
}

export async function request<T>(method: string, path: string, options: RequestOptions = {}): Promise<T> {
  const token = options.auth === false ? null : tokenStore.getToken();
  const headers: Record<string, string> = { Accept: 'application/json' };
  if (options.body !== undefined) headers['Content-Type'] = 'application/json';
  if (token) headers.Authorization = `Bearer ${token}`;

  let response: Response;
  try {
    response = await fetch(buildUrl(path, options.query), {
      method,
      headers,
      body: options.body === undefined ? undefined : JSON.stringify(options.body),
    });
  } catch {
    throw new ApiError(0, 'Cannot reach the server. Check your connection and try again.');
  }

  if (response.status === 401 && token) {
    tokenStore.clear();
    unauthorizedHandler();
  }
  if (!response.ok) {
    const body = await readErrorBody(response);
    throw new ApiError(
      response.status,
      body?.message ?? DEFAULT_MESSAGES[response.status] ?? `Request failed (HTTP ${response.status}).`,
      body?.fieldErrors ?? [],
      parseRetryAfter(response.headers.get('Retry-After')),
    );
  }
  if (response.status === 204) return undefined as T;
  return (await response.json()) as T;
}
