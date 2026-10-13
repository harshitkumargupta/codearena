import { act, renderHook, waitFor } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { FakeLiveClient } from '../test/fakes';
import { CONNECT_TIMEOUT_MS } from './useSubmissionLive';
import { useTopicSnapshot } from './useTopicSnapshot';

interface Board {
  version: number;
}

const isBoard = (body: unknown): body is Board => typeof (body as Board | null)?.version === 'number';

afterEach(() => vi.useRealTimers());

describe('useTopicSnapshot', () => {
  it('subscribes first, then GETs the snapshot once', async () => {
    const events: string[] = [];
    const live = new FakeLiveClient(events);
    const fetchBoard = vi.fn(() => {
      events.push('GET leaderboard');
      return Promise.resolve({ version: 1 });
    });
    const { result } = renderHook(() => useTopicSnapshot('/topic/contests/9/leaderboard', fetchBoard, isBoard, live, 5000));

    expect(events).toEqual([]);
    act(() => live.connect());

    await waitFor(() => expect(result.current.data).toEqual({ version: 1 }));
    expect(events).toEqual(['SUBSCRIBE /topic/contests/9/leaderboard', 'GET leaderboard']);
    expect(result.current.transport).toBe('live');
  });

  it('replaces the board on each push and drops a GET that was overtaken by a push', async () => {
    const live = new FakeLiveClient();
    let resolveGet: (b: Board) => void = () => undefined;
    const fetchBoard = vi.fn(() => new Promise<Board>((resolve) => { resolveGet = resolve; }));
    const { result } = renderHook(() => useTopicSnapshot('/topic/contests/9/leaderboard', fetchBoard, isBoard, live, 5000));
    act(() => live.connect());

    act(() => live.push({ version: 5 }));
    await act(async () => {
      resolveGet({ version: 4 });
      await Promise.resolve();
    });
    expect(result.current.data).toEqual({ version: 5 });

    act(() => live.push({ version: 6 }));
    act(() => live.push({ notABoard: true }));
    expect(result.current.data).toEqual({ version: 6 });
  });

  it('polls when the socket does not connect, and stops once it does', async () => {
    vi.useFakeTimers();
    const live = new FakeLiveClient();
    let version = 0;
    const fetchBoard = vi.fn(() => Promise.resolve({ version: ++version }));
    const { result } = renderHook(() => useTopicSnapshot('/topic/contests/9/leaderboard', fetchBoard, isBoard, live, 1000));

    await act(async () => vi.advanceTimersByTimeAsync(CONNECT_TIMEOUT_MS));
    expect(result.current.transport).toBe('polling');
    await act(async () => vi.advanceTimersByTimeAsync(2000));
    expect(fetchBoard).toHaveBeenCalledTimes(3);

    act(() => live.connect());
    await act(async () => vi.advanceTimersByTimeAsync(0));
    const calls = fetchBoard.mock.calls.length;
    expect(result.current.transport).toBe('live');
    await act(async () => vi.advanceTimersByTimeAsync(5000));
    expect(fetchBoard).toHaveBeenCalledTimes(calls);
  });
});
