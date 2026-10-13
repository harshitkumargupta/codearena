import { createContext } from 'react';
import type { LoginRequest, RegisterRequest } from '../api/types';
import type { Session } from './tokenStore';

export interface AuthContextValue {
  session: Session | null;
  login: (request: LoginRequest) => Promise<void>;
  register: (request: RegisterRequest) => Promise<void>;
  logout: () => void;
}

export const AuthContext = createContext<AuthContextValue | null>(null);

export const SESSION_EXPIRED = 'expired';
