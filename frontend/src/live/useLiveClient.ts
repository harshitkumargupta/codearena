import { useContext } from 'react';
import type { LiveClient } from './liveClient';
import { LiveContext } from './liveContextValue';

export function useLiveClient(): LiveClient | null {
  return useContext(LiveContext);
}
