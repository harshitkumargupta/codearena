import { vi } from 'vitest';
import type { AuthResponse, Verdict } from '../api/types';
import type { LiveClient, LiveState, LiveSubscription } from '../live/liveClient';
import type { SubmissionView } from '../live/submissionView';

export function authResponse(overrides: Partial<AuthResponse> = {}): AuthResponse {
  return {
    accessToken: 'header.payload.signature',
    tokenType: 'Bearer',
    expiresInSeconds: 7200,
    user: { id: 1, username: 'alice', email: 'alice@example.com', role: 'USER', createdAt: '2026-10-13T10:00:00Z' },
    ...overrides,
  };
}

export function jsonResponse(status: number, body: unknown, headers: Record<string, string> = {}): Response {
  return new Response(body === undefined ? null : JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json', ...headers },
  });
}

export function submissionView(status: Verdict, overrides: Partial<SubmissionView> = {}): SubmissionView {
  return {
    id: 42,
    problemId: 1,
    language: 'JAVA',
    status,
    failedTestOrdinal: null,
    timeMs: null,
    memoryKb: null,
    createdAt: '2026-10-13T10:00:00Z',
    judgedAt: null,
    detailAvailable: false,
    verdictDetail: null,
    ...overrides,
  };
}

export interface FakeSubscription {
  destination: string;
  onMessage: (body: unknown) => void;
  onSubscribed: () => void;
  active: boolean;
}

export class FakeLiveClient implements LiveClient {
  state: LiveState = 'connecting';
  readonly subscriptions: FakeSubscription[] = [];
  readonly events: string[];
  private readonly listeners = new Set<(state: LiveState) => void>();

  constructor(events: string[] = []) {
    this.events = events;
  }

  start = vi.fn();
  close = vi.fn();

  subscribe(destination: string, onMessage: (body: unknown) => void, onSubscribed: () => void): LiveSubscription {
    const sub: FakeSubscription = { destination, onMessage, onSubscribed, active: true };
    this.subscriptions.push(sub);
    if (this.state === 'connected') {
      this.events.push(`SUBSCRIBE ${destination}`);
      onSubscribed();
    }
    return {
      unsubscribe: () => {
        sub.active = false;
      },
    };
  }

  onStateChange(listener: (state: LiveState) => void): () => void {
    this.listeners.add(listener);
    return () => this.listeners.delete(listener);
  }

  connect(): void {
    this.setState('connected');
    this.subscriptions.filter((s) => s.active).forEach((s) => {
      this.events.push(`SUBSCRIBE ${s.destination}`);
      s.onSubscribed();
    });
  }

  fail(): void {
    this.setState('failed');
  }

  push(body: unknown): void {
    this.subscriptions.filter((s) => s.active).forEach((s) => s.onMessage(body));
  }

  private setState(state: LiveState): void {
    this.state = state;
    this.listeners.forEach((l) => l(state));
  }
}
