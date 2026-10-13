import { useEffect, useRef } from 'react';
import { formatDuration } from '../time/contestClock';
import { useNow } from '../time/useNow';

interface Props {
  target: string;
  label: string;
  onElapsed?: () => void;
}

export function Countdown({ target, label, onElapsed }: Props) {
  const now = useNow(1000);
  const remaining = Date.parse(target) - now;
  const fired = useRef(false);
  const callback = useRef(onElapsed);

  useEffect(() => {
    callback.current = onElapsed;
  }, [onElapsed]);

  useEffect(() => {
    fired.current = false;
  }, [target]);

  useEffect(() => {
    if (remaining <= 0 && !fired.current) {
      fired.current = true;
      callback.current?.();
    }
  }, [remaining]);

  return (
    <p role="timer" aria-live="off" aria-label={`${label} ${formatDuration(remaining)}`}>
      {label} <strong data-testid="countdown">{formatDuration(remaining)}</strong>
    </p>
  );
}
