import { useEffect, useMemo, type ReactNode } from 'react';
import { WS_URL } from '../config';
import { tokenStore } from '../auth/tokenStore';
import { useAuth } from '../auth/useAuth';
import type { LiveClient } from './liveClient';
import { LiveContext } from './liveContextValue';
import { StompLiveClient } from './stompLiveClient';

const defaultFactory = (): LiveClient => new StompLiveClient(WS_URL, () => tokenStore.getToken());

export function LiveProvider({ children, factory = defaultFactory }: { children: ReactNode; factory?: () => LiveClient }) {
  const { session } = useAuth();
  const token = session?.token ?? null;
  const client = useMemo(() => (token ? factory() : null), [token, factory]);

  useEffect(() => {
    if (!client) return undefined;
    client.start();
    return () => client.close();
  }, [client]);

  return <LiveContext.Provider value={client}>{children}</LiveContext.Provider>;
}
