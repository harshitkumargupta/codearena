import { useCallback, useEffect, useMemo, useState, type ReactNode } from 'react';
import { useNavigate } from 'react-router';
import { setUnauthorizedHandler } from '../api/client';
import { api } from '../api/endpoints';
import { AuthContext, SESSION_EXPIRED, type AuthContextValue } from './authContextValue';
import { tokenStore, type Session } from './tokenStore';

export function AuthProvider({ children }: { children: ReactNode }) {
  const navigate = useNavigate();
  const [session, setSession] = useState<Session | null>(() => tokenStore.getSession());

  useEffect(() => tokenStore.subscribe(setSession), []);

  const expire = useCallback(() => {
    tokenStore.clear();
    void navigate('/login', { replace: true, state: { reason: SESSION_EXPIRED } });
  }, [navigate]);

  useEffect(() => setUnauthorizedHandler(expire), [expire]);

  useEffect(() => {
    if (!session) return undefined;
    const timer = window.setTimeout(expire, Math.max(0, session.expiresAt - Date.now()));
    return () => window.clearTimeout(timer);
  }, [session, expire]);

  useEffect(() => {
    const onStorage = () => setSession(tokenStore.getSession());
    window.addEventListener('storage', onStorage);
    return () => window.removeEventListener('storage', onStorage);
  }, []);

  const value = useMemo<AuthContextValue>(
    () => ({
      session,
      login: async (request) => {
        tokenStore.save(await api.auth.login(request));
      },
      register: async (request) => {
        tokenStore.save(await api.auth.register(request));
      },
      logout: () => {
        tokenStore.clear();
        void navigate('/');
      },
    }),
    [session, navigate],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}
