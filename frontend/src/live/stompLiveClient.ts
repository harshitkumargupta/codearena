import { Client, type StompSubscription } from '@stomp/stompjs';
import type { LiveClient, LiveState, LiveSubscription } from './liveClient';

const RECONNECT_DELAY_MS = 3000;
const MAX_FAILED_ATTEMPTS = 3;

interface Registration {
  destination: string;
  onMessage: (body: unknown) => void;
  onSubscribed: () => void;
  stomp: StompSubscription | null;
}

export class StompLiveClient implements LiveClient {
  private readonly client: Client;
  private readonly registrations = new Map<number, Registration>();
  private readonly listeners = new Set<(state: LiveState) => void>();
  private nextId = 1;
  private failedAttempts = 0;
  private currentState: LiveState = 'connecting';

  constructor(url: string, getToken: () => string | null) {
    this.client = new Client({
      brokerURL: url,
      reconnectDelay: RECONNECT_DELAY_MS,
      heartbeatIncoming: 0,
      heartbeatOutgoing: 0,
      beforeConnect: (client) => {
        const token = getToken();
        if (!token) {
          this.setState('failed');
          void client.deactivate();
          return;
        }
        client.connectHeaders = { Authorization: `Bearer ${token}` };
      },
      onConnect: () => {
        this.failedAttempts = 0;
        this.setState('connected');
        this.registrations.forEach((registration) => this.activate(registration));
      },
      onStompError: () => this.recordFailure(),
      onWebSocketError: () => this.recordFailure(),
      onWebSocketClose: () => {
        this.registrations.forEach((registration) => {
          registration.stomp = null;
        });
        if (this.currentState === 'connected') this.setState('connecting');
      },
    });
  }

  start(): void {
    this.client.activate();
  }

  get state(): LiveState {
    return this.currentState;
  }

  subscribe(destination: string, onMessage: (body: unknown) => void, onSubscribed: () => void): LiveSubscription {
    const id = this.nextId++;
    const registration: Registration = { destination, onMessage, onSubscribed, stomp: null };
    this.registrations.set(id, registration);
    if (this.client.connected) this.activate(registration);
    return {
      unsubscribe: () => {
        const current = this.registrations.get(id);
        this.registrations.delete(id);
        if (current?.stomp && this.client.connected) current.stomp.unsubscribe();
      },
    };
  }

  onStateChange(listener: (state: LiveState) => void): () => void {
    this.listeners.add(listener);
    return () => this.listeners.delete(listener);
  }

  close(): void {
    this.registrations.clear();
    void this.client.deactivate();
  }

  private activate(registration: Registration): void {
    registration.stomp = this.client.subscribe(registration.destination, (frame) => {
      try {
        registration.onMessage(JSON.parse(frame.body) as unknown);
      } catch {
        // Ignore frames that are not JSON; every server payload is.
      }
    });
    registration.onSubscribed();
  }

  private recordFailure(): void {
    this.failedAttempts++;
    if (this.failedAttempts >= MAX_FAILED_ATTEMPTS) this.setState('failed');
  }

  private setState(state: LiveState): void {
    if (state === this.currentState) return;
    this.currentState = state;
    this.listeners.forEach((listener) => listener(state));
  }
}
