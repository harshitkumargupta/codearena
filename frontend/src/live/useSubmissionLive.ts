import { useEffect, useRef, useState } from 'react';
import { ApiError } from '../api/client';
import { api } from '../api/endpoints';
import { isFinal, type SubmissionStatusUpdate } from '../api/types';
import type { LiveClient, LiveSubscription } from './liveClient';
import { fromResponse, fromUpdate, merge, type SubmissionView } from './submissionView';

export const CONNECT_TIMEOUT_MS = 5000;
export const POLL_INTERVAL_MS = 1500;

export type Transport = 'live' | 'polling';

export interface LiveSubmission {
  submission: SubmissionView | null;
  transport: Transport;
  error: string | null;
}

function isStatusUpdate(body: unknown, id: number): body is SubmissionStatusUpdate {
  return typeof body === 'object' && body !== null && (body as { id?: unknown }).id === id
    && typeof (body as { status?: unknown }).status === 'string';
}

/**
 * Follows one submission: subscribe to /topic/submissions/{id} first, then GET it once (and again after every
 * reconnect); falls back to polling GET when the socket cannot connect. Fetches verdictDetail when a final push says
 * detailAvailable.
 */
export function useSubmissionLive(submissionId: number | null, live: LiveClient | null): LiveSubmission {
  const [state, setState] = useState<LiveSubmission>({ submission: null, transport: 'live', error: null });
  const current = useRef<SubmissionView | null>(null);

  useEffect(() => {
    current.current = null;
    setState({ submission: null, transport: 'live', error: null });
    if (submissionId === null) return undefined;

    let disposed = false;
    let pollTimer: number | undefined;
    let detailRequested = false;
    let subscription: LiveSubscription | null = null;

    const apply = (next: SubmissionView) => {
      if (disposed) return;
      const merged = merge(current.current, next);
      current.current = merged;
      setState((s) => ({ ...s, submission: merged, error: null }));
      if (isFinal(merged.status)) {
        stopPolling();
        subscription?.unsubscribe();
        if (merged.detailAvailable && !merged.verdictDetail && !detailRequested) {
          detailRequested = true;
          void fetchOnce();
        }
      }
    };

    const fetchOnce = async () => {
      try {
        apply(fromResponse(await api.submissions.get(submissionId)));
      } catch (e) {
        if (!disposed) setState((s) => ({ ...s, error: e instanceof ApiError ? e.message : 'Could not load the submission.' }));
      }
    };

    const stopPolling = () => {
      if (pollTimer !== undefined) window.clearInterval(pollTimer);
      pollTimer = undefined;
    };

    const startPolling = () => {
      if (disposed || pollTimer !== undefined || (current.current && isFinal(current.current.status))) return;
      setState((s) => ({ ...s, transport: 'polling' }));
      void fetchOnce();
      pollTimer = window.setInterval(() => void fetchOnce(), POLL_INTERVAL_MS);
    };

    if (!live) {
      startPolling();
      return () => {
        disposed = true;
        stopPolling();
      };
    }

    subscription = live.subscribe(
      `/topic/submissions/${submissionId}`,
      (body) => {
        if (isStatusUpdate(body, submissionId)) apply(fromUpdate(body, current.current));
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
  }, [submissionId, live]);

  return state;
}
