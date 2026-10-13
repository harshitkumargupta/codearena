import { act, renderHook, waitFor } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import type { SubmissionResponse, SubmissionStatusUpdate, Verdict } from '../api/types';
import { FakeLiveClient, jsonResponse } from '../test/fakes';
import { CONNECT_TIMEOUT_MS, POLL_INTERVAL_MS, useSubmissionLive } from './useSubmissionLive';

const events: string[] = [];
let responses: SubmissionResponse[] = [];
const fetchMock = vi.fn<typeof fetch>();

function response(status: Verdict, overrides: Partial<SubmissionResponse> = {}): SubmissionResponse {
  return {
    id: 42, userId: 1, problemId: 1, contestId: null, language: 'PYTHON', sourceCode: 'print(1)', status,
    verdictDetail: null, failedTestOrdinal: null, timeMs: null, memoryKb: null,
    createdAt: '2026-10-13T10:00:00Z', judgedAt: null, ...overrides,
  };
}

function update(status: Verdict, overrides: Partial<SubmissionStatusUpdate> = {}): SubmissionStatusUpdate {
  return {
    id: 42, userId: 1, problemId: 1, contestId: null, language: 'PYTHON', status, failedTestOrdinal: null,
    timeMs: null, memoryKb: null, createdAt: '2026-10-13T10:00:00Z', judgedAt: null, detailAvailable: false,
    ...overrides,
  };
}

beforeEach(() => {
  events.length = 0;
  responses = [];
  fetchMock.mockImplementation((input) => {
    const url = typeof input === 'string' ? input : input instanceof URL ? input.href : input.url;
    events.push(`GET ${url.replace(/^.*\/api/, '/api')}`);
    return Promise.resolve(jsonResponse(200, responses.shift() ?? response('PENDING')));
  });
  vi.stubGlobal('fetch', fetchMock);
});

afterEach(() => {
  vi.unstubAllGlobals();
  vi.useRealTimers();
});

describe('useSubmissionLive', () => {
  it('subscribes first and only then GETs the current state', async () => {
    const live = new FakeLiveClient(events);
    responses = [response('PENDING')];
    const { result } = renderHook(() => useSubmissionLive(42, live));

    expect(events).toEqual([]);
    act(() => live.connect());

    await waitFor(() => expect(result.current.submission?.status).toBe('PENDING'));
    expect(events).toEqual(['SUBSCRIBE /topic/submissions/42', 'GET /api/submissions/42']);
  });

  it('repeats subscribe-then-GET after a reconnect', async () => {
    const live = new FakeLiveClient(events);
    responses = [response('PENDING'), response('RUNNING')];
    const { result } = renderHook(() => useSubmissionLive(42, live));
    act(() => live.connect());
    await waitFor(() => expect(result.current.submission?.status).toBe('PENDING'));

    act(() => live.connect());

    await waitFor(() => expect(result.current.submission?.status).toBe('RUNNING'));
    expect(events).toEqual([
      'SUBSCRIBE /topic/submissions/42', 'GET /api/submissions/42',
      'SUBSCRIBE /topic/submissions/42', 'GET /api/submissions/42',
    ]);
  });

  it('keeps a final verdict when a late RUNNING arrives', async () => {
    const live = new FakeLiveClient(events);
    responses = [response('PENDING')];
    const { result } = renderHook(() => useSubmissionLive(42, live));
    act(() => live.connect());
    await waitFor(() => expect(result.current.submission).not.toBeNull());

    act(() => live.push(update('ACCEPTED', { timeMs: 40 })));
    act(() => live.push(update('RUNNING')));

    expect(result.current.submission?.status).toBe('ACCEPTED');
    expect(result.current.submission?.timeMs).toBe(40);
    expect(live.subscriptions[0]?.active).toBe(false);
  });

  it('a pushed final verdict beats a stale GET that resolves later', async () => {
    const live = new FakeLiveClient(events);
    let resolveGet: (r: Response) => void = () => undefined;
    fetchMock.mockImplementationOnce(() => new Promise((resolve) => { resolveGet = resolve; }));
    const { result } = renderHook(() => useSubmissionLive(42, live));
    act(() => live.connect());

    act(() => live.push(update('WRONG_ANSWER', { failedTestOrdinal: 2 })));
    await act(() => {
      resolveGet(jsonResponse(200, response('PENDING')));
      return Promise.resolve();
    });

    expect(result.current.submission?.status).toBe('WRONG_ANSWER');
  });

  it('fetches the compiler output when a final push says detailAvailable', async () => {
    const live = new FakeLiveClient(events);
    responses = [response('RUNNING'), response('COMPILATION_ERROR', { verdictDetail: "Main.java:1: error: ';' expected" })];
    const { result } = renderHook(() => useSubmissionLive(42, live));
    act(() => live.connect());
    await waitFor(() => expect(result.current.submission?.status).toBe('RUNNING'));

    act(() => live.push(update('COMPILATION_ERROR', { detailAvailable: true })));

    await waitFor(() => expect(result.current.submission?.verdictDetail).toContain("';' expected"));
  });

  it('falls back to polling when the socket cannot connect in time', async () => {
    vi.useFakeTimers();
    const live = new FakeLiveClient(events);
    responses = [response('RUNNING'), response('ACCEPTED', { timeMs: 33 })];
    const { result } = renderHook(() => useSubmissionLive(42, live));

    await act(async () => vi.advanceTimersByTimeAsync(CONNECT_TIMEOUT_MS));
    expect(result.current.transport).toBe('polling');
    expect(result.current.submission?.status).toBe('RUNNING');

    await act(async () => vi.advanceTimersByTimeAsync(POLL_INTERVAL_MS));
    expect(result.current.submission?.status).toBe('ACCEPTED');
    const gets = events.length;

    await act(async () => vi.advanceTimersByTimeAsync(POLL_INTERVAL_MS * 3));
    expect(events.length).toBe(gets);
  });

  it('starts polling immediately when the client reports failure', async () => {
    const live = new FakeLiveClient(events);
    responses = [response('RUNNING')];
    const { result } = renderHook(() => useSubmissionLive(42, live));

    act(() => live.fail());

    await waitFor(() => expect(result.current.transport).toBe('polling'));
    expect(events).toEqual(['GET /api/submissions/42']);
  });
});
