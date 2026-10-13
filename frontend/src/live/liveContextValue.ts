import { createContext } from 'react';
import type { LiveClient } from './liveClient';

export const LiveContext = createContext<LiveClient | null>(null);
