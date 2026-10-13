import { useState, type FormEvent } from 'react';
import { Link, useLocation, useNavigate } from 'react-router';
import { ApiError } from '../api/client';
import { SESSION_EXPIRED } from '../auth/authContextValue';
import { useAuth } from '../auth/useAuth';
import ui from '../components/ui.module.css';
import styles from './AuthPage.module.css';

interface LocationState {
  from?: string;
  reason?: string;
}

export function LoginPage() {
  const { login } = useAuth();
  const navigate = useNavigate();
  const state = (useLocation().state ?? {}) as LocationState;
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  const onSubmit = async (event: FormEvent) => {
    event.preventDefault();
    setBusy(true);
    setError(null);
    try {
      await login({ username, password });
      void navigate(state.from ?? '/problems', { replace: true });
    } catch (e) {
      setError(e instanceof ApiError ? e.message : 'Login failed.');
    } finally {
      setBusy(false);
    }
  };

  return (
    <div className={`${ui.card} ${styles.wrapper}`}>
      <h1 className={styles.title}>Log in</h1>
      {state.reason === SESSION_EXPIRED && (
        <p className={ui.notice} role="status">
          Your session has expired. Please log in again.
        </p>
      )}
      <form onSubmit={(e) => void onSubmit(e)} noValidate>
        <div className={ui.field}>
          <label htmlFor="login-username">Username</label>
          <input id="login-username" autoComplete="username" value={username} onChange={(e) => setUsername(e.target.value)} required />
        </div>
        <div className={ui.field}>
          <label htmlFor="login-password">Password</label>
          <input
            id="login-password"
            type="password"
            autoComplete="current-password"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            required
          />
        </div>
        {error && (
          <p className={ui.error} role="alert">
            {error}
          </p>
        )}
        <button type="submit" className={ui.button} disabled={busy || !username || !password}>
          {busy ? 'Logging in…' : 'Log in'}
        </button>
      </form>
      <p>
        No account? <Link to="/register">Register</Link>
      </p>
    </div>
  );
}
