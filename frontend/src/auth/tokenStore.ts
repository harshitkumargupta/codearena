import type { AuthResponse, UserResponse } from '../api/types';

const STORAGE_KEY = 'codearena.session';
const EXPIRY_SKEW_MS = 30_000;

export interface Session {
  token: string;
  expiresAt: number;
  user: UserResponse;
}

type Listener = (session: Session | null) => void;
const listeners = new Set<Listener>();

function isSession(value: unknown): value is Session {
  if (typeof value !== 'object' || value === null) return false;
  const v = value as Record<string, unknown>;
  return typeof v.token === 'string' && typeof v.expiresAt === 'number' && typeof v.user === 'object';
}

function read(): Session | null {
  try {
    const raw = window.localStorage.getItem(STORAGE_KEY);
    if (!raw) return null;
    const parsed: unknown = JSON.parse(raw);
    return isSession(parsed) ? parsed : null;
  } catch {
    return null;
  }
}

function notify(session: Session | null): void {
  listeners.forEach((listener) => listener(session));
}

export const tokenStore = {
  getSession(now: number = Date.now()): Session | null {
    const session = read();
    if (session && now >= session.expiresAt) {
      tokenStore.clear();
      return null;
    }
    return session;
  },

  getToken(): string | null {
    return tokenStore.getSession()?.token ?? null;
  },

  save(auth: AuthResponse, now: number = Date.now()): Session {
    const session: Session = {
      token: auth.accessToken,
      expiresAt: now + auth.expiresInSeconds * 1000 - EXPIRY_SKEW_MS,
      user: auth.user,
    };
    try {
      window.localStorage.setItem(STORAGE_KEY, JSON.stringify(session));
    } catch {
      // Storage can be unavailable (private mode); the session then lasts for this page only.
    }
    notify(session);
    return session;
  },

  clear(): void {
    try {
      window.localStorage.removeItem(STORAGE_KEY);
    } catch {
      // Nothing to clear when storage is unavailable.
    }
    notify(null);
  },

  subscribe(listener: Listener): () => void {
    listeners.add(listener);
    return () => listeners.delete(listener);
  },
};
