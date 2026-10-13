const rawApiUrl = import.meta.env.VITE_API_URL ?? 'http://localhost:8080';

export const API_BASE_URL = rawApiUrl.replace(/\/+$/, '');

function deriveWebSocketUrl(apiBase: string): string {
  if (apiBase === '') {
    const scheme = window.location.protocol === 'https:' ? 'wss' : 'ws';
    return `${scheme}://${window.location.host}/ws`;
  }
  return `${apiBase.replace(/^http/, 'ws')}/ws`;
}

export const WS_URL = import.meta.env.VITE_WS_URL ?? deriveWebSocketUrl(API_BASE_URL);

export const MAX_SOURCE_BYTES = 64 * 1024;
