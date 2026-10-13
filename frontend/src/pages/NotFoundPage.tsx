import { Link } from 'react-router';
import ui from '../components/ui.module.css';

export function NotFoundPage() {
  return (
    <div className={ui.card}>
      <h1>Page not found</h1>
      <Link to="/problems">Go to problems</Link>
    </div>
  );
}
