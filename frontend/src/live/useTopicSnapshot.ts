import { useEffect, useRef, useState } from 'react';
import { ApiError } from '../api/client';
import type { LiveClient, LiveSubscription } from './liveClient';
import { CONNECT_TIMEOUT_MS, type Transport } from './useSubmissionLive';

export interface TopicSnapshot<T> {
  data: T | null;
  transport: Transport;
  error: string | null;
}

/**
 * Keeps the latest full snapshot published on a topic: subscribe first, then GET once (and again after every
 * reconnect). A GET result is dropped if a push arrived while it was in flight, because the push is newer. Polls the
 * GET while the socket is not connected.
 */
export function useTopicSnapshot<T>(
  destination: string | null,
  fetchSnapshot: () => Promise<T>,
  isSnapshot: (body: unknown) => body is T,
  live: LiveClient | null,
  pollIntervalMs: number,
): TopicSnapshot<T> {
  const [state, setState] = useState<TopicSnapshot<T>>({ data: null, transport: 'live', error: null });
  const fetchRef = useRef(fetchSnapshot);
  const guardRef = useRef(isSnapshot);

  useEffect(() => {
    fetchRef.current = fetchSnapshot;
    guardRef.current = isSnapshot;
  }, [fetchSnapshot, isSnapshot]);

  useEffect(() => {
    if (destination === null) return undefined;
    let disposed = false;
    let pushes = 0;
    let pollTimer: number | undefined;
    let subscription: LiveSubscription | null = null;

    const fetchOnce = async () => {
      const pushesBefore = pushes;
      try {
        const data = await fetchRef.current();
        if (!disposed && pushes === pushesBefore) setState((s) => ({ ...s, data, error: null }));
      } catch (e) {
        if (!disposed) setState((s) => ({ ...s, error: e instanceof ApiError ? e.message : 'Could not load the latest data.' }));
      }
    };

    const stopPolling = () => {
      if (pollTimer !== undefined) window.clearInterval(pollTimer);
      pollTimer = undefined;
    };

    const startPolling = () => {
      if (disposed || pollTimer !== undefined) return;
      setState((s) => ({ ...s, transport: 'polling' }));
      void fetchOnce();
      pollTimer = window.setInterval(() => void fetchOnce(), pollIntervalMs);
    };

    if (!live) {
      startPolling();
      return () => {
        disposed = true;
        stopPolling();
      };
    }

    subscription = live.subscribe(
      destination,
      (body) => {
        if (disposed || !guardRef.current(body)) return;
        pushes++;
        setState((s) => ({ ...s, data: body, error: null }));
      },
      () => {
        stopPolling();
        setState((s) => ({ ...s, transport: 'live' }));
        void fetchOnce();
      },
    );
    const stopListening = live.onStateChange((s) => {
      if (s === 'failed') startPolling();
    });
    if (live.state === 'failed') startPolling();
    const connectTimer = window.setTimeout(() => {
      if (live.state !== 'connected') startPolling();
    }, CONNECT_TIMEOUT_MS);

    return () => {
      disposed = true;
      subscription?.unsubscribe();
      stopListening();
      stopPolling();
      window.clearTimeout(connectTimer);
    };
  }, [destination, live, pollIntervalMs]);

  return state;
}
