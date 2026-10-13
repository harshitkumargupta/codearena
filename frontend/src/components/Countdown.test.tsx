import { act, render, screen } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { formatDuration } from '../time/contestClock';
import { Countdown } from './Countdown';

afterEach(() => vi.useRealTimers());

describe('Countdown', () => {
  it('counts down every second and fires onElapsed once at zero', async () => {
    vi.useFakeTimers();
    vi.setSystemTime(new Date('2026-10-13T10:00:00Z'));
    const onElapsed = vi.fn();
    render(<Countdown target="2026-10-13T10:00:03Z" label="Starts in" onElapsed={onElapsed} />);

    expect(screen.getByTestId('countdown')).toHaveTextContent('00:00:03');
    await act(async () => vi.advanceTimersByTimeAsync(1000));
    expect(screen.getByTestId('countdown')).toHaveTextContent('00:00:02');
    expect(onElapsed).not.toHaveBeenCalled();

    await act(async () => vi.advanceTimersByTimeAsync(2000));
    expect(screen.getByTestId('countdown')).toHaveTextContent('00:00:00');
    expect(onElapsed).toHaveBeenCalledTimes(1);

    await act(async () => vi.advanceTimersByTimeAsync(3000));
    expect(onElapsed).toHaveBeenCalledTimes(1);
    expect(screen.getByRole('timer')).toHaveAccessibleName('Starts in 00:00:00');
  });

  it('formats days, hours, minutes and seconds', () => {
    expect(formatDuration(0)).toBe('00:00:00');
    expect(formatDuration(3_725_000)).toBe('01:02:05');
    expect(formatDuration(90_061_000)).toBe('1d 01:01:01');
    expect(formatDuration(-5000)).toBe('00:00:00');
  });
});
