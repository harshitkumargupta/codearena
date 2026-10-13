import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { tokenStore } from '../auth/tokenStore';
import { authResponse, jsonResponse } from '../test/fakes';
import { ApiError, parseRetryAfter, request, setUnauthorizedHandler } from './client';

const fetchMock = vi.fn<typeof fetch>();

beforeEach(() => {
  vi.stubGlobal('fetch', fetchMock);
});

afterEach(() => {
  vi.unstubAllGlobals();
  fetchMock.mockReset();
});

describe('request', () => {
  it('sends the bearer token when logged in', async () => {
    tokenStore.save(authResponse());
    fetchMock.mockResolvedValue(jsonResponse(200, { ok: true }));

    await request('GET', '/api/auth/me');

    const init = fetchMock.mock.calls[0]?.[1];
    expect((init?.headers as Record<string, string>).Authorization).toBe('Bearer header.payload.signature');
  });

  it('clears the session and calls the 401 handler when an authenticated request is rejected', async () => {
    const handler = vi.fn();
    setUnauthorizedHandler(handler);
    tokenStore.save(authResponse());
    fetchMock.mockResolvedValue(jsonResponse(401, { status: 401, message: 'Authentication required' }));

    await expect(request('GET', '/api/submissions/me')).rejects.toMatchObject({ status: 401 });

    expect(tokenStore.getSession()).toBeNull();
    expect(handler).toHaveBeenCalledTimes(1);
  });

  it('does not treat a failed login as an expired session', async () => {
    const handler = vi.fn();
    setUnauthorizedHandler(handler);
    fetchMock.mockResolvedValue(jsonResponse(401, { status: 401, message: 'Invalid username or password' }));

    await expect(request('POST', '/api/auth/login', { body: {}, auth: false })).rejects.toThrow(
      'Invalid username or password',
    );

    expect(handler).not.toHaveBeenCalled();
  });

  it('exposes field errors and Retry-After', async () => {
    fetchMock.mockResolvedValue(
      jsonResponse(429, { status: 429, message: 'Daily limit reached', fieldErrors: [{ field: 'level', message: 'x' }] }, { 'Retry-After': '90' }),
    );

    const error = await request('POST', '/api/tutor/hint', { body: {} }).catch((e: unknown) => e);

    expect(error).toBeInstanceOf(ApiError);
    expect((error as ApiError).retryAfterSeconds).toBe(90);
    expect((error as ApiError).fieldError('level')).toBe('x');
  });

  it('turns network failures into a readable error', async () => {
    fetchMock.mockRejectedValue(new TypeError('Failed to fetch'));

    await expect(request('GET', '/api/problems')).rejects.toMatchObject({ status: 0, message: expect.stringContaining('Cannot reach') as string });
  });
});

describe('parseRetryAfter', () => {
  it('reads seconds and HTTP dates', () => {
    expect(parseRetryAfter('287')).toBe(287);
    expect(parseRetryAfter(new Date(10_000).toUTCString(), 0)).toBe(10);
    expect(parseRetryAfter(null)).toBeNull();
    expect(parseRetryAfter('soon')).toBeNull();
  });
});
