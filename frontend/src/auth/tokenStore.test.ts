import { describe, expect, it } from 'vitest';
import { authResponse } from '../test/fakes';
import { tokenStore } from './tokenStore';

describe('tokenStore', () => {
  it('stores the session with an expiry slightly before the token expires', () => {
    const session = tokenStore.save(authResponse({ expiresInSeconds: 7200 }), 1_000_000);

    expect(session.expiresAt).toBe(1_000_000 + 7_200_000 - 30_000);
    expect(tokenStore.getSession(1_000_001)?.token).toBe('header.payload.signature');
  });

  it('drops an expired session', () => {
    tokenStore.save(authResponse({ expiresInSeconds: 60 }), 0);

    expect(tokenStore.getSession(60_000)).toBeNull();
    expect(window.localStorage.getItem('codearena.session')).toBeNull();
  });

  it('ignores corrupt storage', () => {
    window.localStorage.setItem('codearena.session', '{not json');

    expect(tokenStore.getSession()).toBeNull();
  });
});
