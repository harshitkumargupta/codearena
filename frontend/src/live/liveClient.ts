export type LiveState = 'connecting' | 'connected' | 'failed';

export interface LiveSubscription {
  unsubscribe(): void;
}

/**
 * Server-to-client STOMP connection. {@link subscribe} sends SUBSCRIBE as soon as the socket is connected and then
 * calls {@code onSubscribed}; after every reconnect it re-subscribes and calls {@code onSubscribed} again, so callers
 * can GET the current state each time (the subscribe-then-GET pattern in docs/websocket.md).
 */
export interface LiveClient {
  readonly state: LiveState;
  subscribe(destination: string, onMessage: (body: unknown) => void, onSubscribed: () => void): LiveSubscription;
  onStateChange(listener: (state: LiveState) => void): () => void;
  start(): void;
  close(): void;
}
