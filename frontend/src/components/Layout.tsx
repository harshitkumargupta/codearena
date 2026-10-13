import { Link, NavLink, Outlet } from 'react-router';
import { useAuth } from '../auth/useAuth';
import styles from './Layout.module.css';
import ui from './ui.module.css';

export function Layout() {
  const { session, logout } = useAuth();
  return (
    <>
      <header className={styles.header}>
        <Link to="/" className={styles.brand}>
          CodeArena
        </Link>
        <nav className={styles.nav} aria-label="Main">
          <NavLink to="/problems">Problems</NavLink>
        </nav>
        <div className={styles.user}>
          {session ? (
            <>
              <span>
                Signed in as <strong>{session.user.username}</strong>
              </span>
              <button type="button" className={`${ui.button} ${ui.secondary}`} onClick={logout}>
                Log out
              </button>
            </>
          ) : (
            <>
              <Link to="/login">Log in</Link>
              <Link to="/register">Register</Link>
            </>
          )}
        </div>
      </header>
      <main className={styles.main}>
        <Outlet />
      </main>
    </>
  );
}
