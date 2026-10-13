import { useState, type FormEvent } from 'react';
import { Link, useNavigate } from 'react-router';
import { ApiError } from '../api/client';
import { useAuth } from '../auth/useAuth';
import ui from '../components/ui.module.css';
import styles from './AuthPage.module.css';

const FIELDS = [
  { name: 'username', label: 'Username', type: 'text', autoComplete: 'username', hint: '3–30 letters, digits or underscores' },
  { name: 'email', label: 'Email', type: 'email', autoComplete: 'email', hint: undefined },
  { name: 'password', label: 'Password', type: 'password', autoComplete: 'new-password', hint: 'At least 8 characters' },
] as const;

type FieldName = (typeof FIELDS)[number]['name'];

export function RegisterPage() {
  const { register } = useAuth();
  const navigate = useNavigate();
  const [values, setValues] = useState<Record<FieldName, string>>({ username: '', email: '', password: '' });
  const [error, setError] = useState<ApiError | null>(null);
  const [busy, setBusy] = useState(false);

  const onSubmit = async (event: FormEvent) => {
    event.preventDefault();
    setBusy(true);
    setError(null);
    try {
      await register(values);
      void navigate('/problems', { replace: true });
    } catch (e) {
      setError(e instanceof ApiError ? e : new ApiError(0, 'Registration failed.'));
    } finally {
      setBusy(false);
    }
  };

  return (
    <div className={`${ui.card} ${styles.wrapper}`}>
      <h1 className={styles.title}>Create an account</h1>
      <form onSubmit={(e) => void onSubmit(e)} noValidate>
        {FIELDS.map((field) => {
          const fieldError = error?.fieldError(field.name);
          const describedBy = [field.hint ? `${field.name}-hint` : null, fieldError ? `${field.name}-error` : null]
            .filter(Boolean)
            .join(' ');
          return (
            <div className={ui.field} key={field.name}>
              <label htmlFor={`register-${field.name}`}>{field.label}</label>
              <input
                id={`register-${field.name}`}
                type={field.type}
                autoComplete={field.autoComplete}
                value={values[field.name]}
                aria-invalid={fieldError ? true : undefined}
                aria-describedby={describedBy || undefined}
                onChange={(e) => setValues({ ...values, [field.name]: e.target.value })}
              />
              {field.hint && (
                <span id={`${field.name}-hint`} className={ui.hint}>
                  {field.hint}
                </span>
              )}
              {fieldError && (
                <span id={`${field.name}-error`} className={ui.error}>
                  {fieldError}
                </span>
              )}
            </div>
          );
        })}
        {error && error.fieldErrors.length === 0 && (
          <p className={ui.error} role="alert">
            {error.message}
          </p>
        )}
        <button type="submit" className={ui.button} disabled={busy}>
          {busy ? 'Creating account…' : 'Register'}
        </button>
      </form>
      <p>
        Already registered? <Link to="/login">Log in</Link>
      </p>
    </div>
  );
}
