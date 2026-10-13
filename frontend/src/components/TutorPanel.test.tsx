import { act, render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { jsonResponse, submissionView } from '../test/fakes';
import { TutorPanel } from './TutorPanel';

const fetchMock = vi.fn<typeof fetch>();

beforeEach(() => vi.stubGlobal('fetch', fetchMock));
afterEach(() => {
  vi.unstubAllGlobals();
  vi.useRealTimers();
  fetchMock.mockReset();
});

describe('TutorPanel', () => {
  it('shows a hint as markdown without rendering raw HTML', async () => {
    fetchMock.mockResolvedValue(
      jsonResponse(200, { kind: 'HINT', level: 1, text: 'Use a **hash map**. <img src=x onerror=alert(1)>', fallback: false }),
    );
    render(<TutorPanel problemId={1} submission={null} />);

    await userEvent.click(screen.getByRole('button', { name: 'Hint 1: nudge' }));

    expect(await screen.findByText('hash map')).toContainHTML('<strong>hash map</strong>');
    expect(document.querySelector('img')).toBeNull();
  });

  it('on 429 shows the server message and a Retry-After countdown, then re-enables the button', async () => {
    vi.useFakeTimers({ shouldAdvanceTime: true });
    fetchMock.mockResolvedValue(
      jsonResponse(429, { status: 429, message: 'Level 3 hints for this problem are on cooldown' }, { 'Retry-After': '3' }),
    );
    const user = userEvent.setup({ advanceTimers: (ms) => vi.advanceTimersByTime(ms) });
    render(<TutorPanel problemId={1} submission={null} />);

    await user.click(screen.getByRole('button', { name: 'Hint 3: walkthrough' }));

    const alert = await screen.findByRole('alert');
    expect(alert).toHaveTextContent('Level 3 hints for this problem are on cooldown Try again in 0:03.');
    expect(screen.getByRole('button', { name: 'Hint 3: walkthrough' })).toBeDisabled();
    expect(screen.getByRole('button', { name: 'Hint 1: nudge' })).toBeEnabled();

    await act(async () => vi.advanceTimersByTimeAsync(1000));
    expect(alert).toHaveTextContent('Try again in 0:02.');
    await act(async () => vi.advanceTimersByTimeAsync(1000));
    expect(alert).toHaveTextContent('Try again in 0:01.');
    await act(async () => vi.advanceTimersByTimeAsync(1000));
    expect(alert).toHaveTextContent('You can try again now.');
    expect(screen.getByRole('button', { name: 'Hint 3: walkthrough' })).toBeEnabled();
  });

  it('on 429 without a readable Retry-After shows the message without inventing a countdown', async () => {
    fetchMock.mockResolvedValue(jsonResponse(429, { status: 429, message: 'Daily limit of 30 tutor answers reached' }));
    render(<TutorPanel problemId={1} submission={null} />);

    await userEvent.click(screen.getByRole('button', { name: 'Hint 1: nudge' }));

    const alert = await screen.findByRole('alert');
    expect(alert).toHaveTextContent('Daily limit of 30 tutor answers reached');
    expect(alert).not.toHaveTextContent('Try again in');
  });

  it('on 503 says the model is unavailable', async () => {
    fetchMock.mockResolvedValue(
      jsonResponse(503, { status: 503, message: 'The AI tutor is unavailable right now. Please try again in a few minutes.' }),
    );
    render(<TutorPanel problemId={1} submission={null} />);

    await userEvent.click(screen.getByRole('button', { name: 'Hint 2: approach' }));

    expect(await screen.findByRole('alert')).toHaveTextContent('The AI tutor is unavailable right now.');
    expect(screen.getByRole('button', { name: 'Hint 2: approach' })).toBeEnabled();
  });

  it('shows 409 messages from the server', async () => {
    fetchMock.mockResolvedValue(jsonResponse(409, { status: 409, message: 'Only a judged failing submission can be explained' }));
    render(<TutorPanel problemId={1} submission={submissionView('WRONG_ANSWER')} />);

    await userEvent.click(screen.getByRole('button', { name: 'Explain failure' }));

    expect(await screen.findByRole('alert')).toHaveTextContent('Only a judged failing submission can be explained');
  });

  it.each(['PENDING', 'RUNNING', 'ACCEPTED', 'INTERNAL_ERROR'] as const)('disables Explain failure for %s', (status) => {
    render(<TutorPanel problemId={1} submission={submissionView(status)} />);
    expect(screen.getByRole('button', { name: 'Explain failure' })).toBeDisabled();
  });

  it('enables Explain failure for a final failure and sends the submission id', async () => {
    fetchMock.mockResolvedValue(jsonResponse(200, { kind: 'EXPLANATION', level: null, text: 'Off by one.', fallback: false }));
    render(<TutorPanel problemId={1} submission={submissionView('RUNTIME_ERROR', { id: 77 })} />);

    await userEvent.click(screen.getByRole('button', { name: 'Explain failure' }));

    expect(await screen.findByText('Off by one.')).toBeInTheDocument();
    expect(fetchMock.mock.calls[0]?.[1]?.body).toBe(JSON.stringify({ submissionId: 77 }));
  });

  it('marks a fallback answer', async () => {
    fetchMock.mockResolvedValue(jsonResponse(200, { kind: 'HINT', level: 3, text: 'Re-read the constraints.', fallback: true }));
    render(<TutorPanel problemId={1} submission={null} />);

    await userEvent.click(screen.getByRole('button', { name: 'Hint 3: walkthrough' }));

    expect(await screen.findByText(/held back an answer/)).toBeInTheDocument();
  });
});
