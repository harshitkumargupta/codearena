import ui from '../components/ui.module.css';

export function FieldError({ id, message }: { id: string; message: string | undefined }) {
  if (!message) return null;
  return (
    <span id={id} className={ui.error}>
      {message}
    </span>
  );
}
