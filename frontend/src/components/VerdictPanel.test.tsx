import { render, screen, within } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import type { Verdict } from '../api/types';
import { submissionView } from '../test/fakes';
import { VerdictPanel } from './VerdictPanel';

function renderPanel(status: Verdict, overrides = {}, transport: 'live' | 'polling' = 'live') {
  render(<VerdictPanel submission={submissionView(status, overrides)} transport={transport} error={null} />);
  return screen.getByTestId('verdict');
}

describe('VerdictPanel', () => {
  it('shows a loading state before the first update', () => {
    render(<VerdictPanel submission={null} transport="live" error={null} />);
    expect(screen.getByText('Loading submission…')).toBeInTheDocument();
  });

  it.each([
    ['PENDING', 'Pending', 'Waiting for a judge…'],
    ['RUNNING', 'Running', 'Judging…'],
  ] as const)('shows %s as in progress', (status, label, text) => {
    const panel = renderPanel(status);
    expect(panel).toHaveAttribute('data-status', status);
    expect(within(panel).getByText(label)).toBeInTheDocument();
    expect(within(panel).getByText(text)).toBeInTheDocument();
    expect(within(panel).queryByText(/Time/)).not.toBeInTheDocument();
  });

  it('shows accepted with time and memory and no failed test', () => {
    const panel = renderPanel('ACCEPTED', { timeMs: 87, memoryKb: 42_000 });
    expect(within(panel).getByText('Accepted')).toBeInTheDocument();
    expect(within(panel).getByText('87 ms')).toBeInTheDocument();
    expect(within(panel).getByText('41.0 MB')).toBeInTheDocument();
    expect(within(panel).queryByText(/Failed on test/)).not.toBeInTheDocument();
  });

  it.each([
    ['WRONG_ANSWER', 'Wrong answer'],
    ['TIME_LIMIT_EXCEEDED', 'Time limit exceeded'],
    ['MEMORY_LIMIT_EXCEEDED', 'Memory limit exceeded'],
    ['RUNTIME_ERROR', 'Runtime error'],
  ] as const)('shows %s with the failed test number', (status, label) => {
    const panel = renderPanel(status, { failedTestOrdinal: 3, timeMs: 12, memoryKb: 900 });
    expect(within(panel).getByText(label)).toBeInTheDocument();
    expect(within(panel).getByText('#3')).toBeInTheDocument();
    expect(within(panel).getByText('900 KB')).toBeInTheDocument();
  });

  it('shows compiler output for a compilation error', () => {
    const panel = renderPanel('COMPILATION_ERROR', {
      detailAvailable: true,
      verdictDetail: "/input/Main.java:3: error: ';' expected",
    });
    expect(within(panel).getByText('Compilation error')).toBeInTheDocument();
    expect(within(panel).getByLabelText('Compiler output')).toHaveTextContent("error: ';' expected");
  });

  it('says details are loading when detailAvailable is true but not fetched yet', () => {
    const panel = renderPanel('COMPILATION_ERROR', { detailAvailable: true, verdictDetail: null });
    expect(within(panel).getByText('Loading details…')).toBeInTheDocument();
  });

  it('shows a judge error with its detail', () => {
    const panel = renderPanel('INTERNAL_ERROR', { detailAvailable: true, verdictDetail: 'Judging failed after 3 attempts' });
    expect(within(panel).getByText('Judge error')).toBeInTheDocument();
    expect(within(panel).getByText('Judging failed after 3 attempts')).toBeInTheDocument();
  });

  it('tells the user when it fell back to polling', () => {
    const panel = renderPanel('RUNNING', {}, 'polling');
    expect(within(panel).getByText(/checking periodically/)).toBeInTheDocument();
  });
});
