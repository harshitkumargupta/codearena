import { describe, expect, it } from 'vitest';
import { slugify, toContestRequest, validateContest, validateProblem, validateTestCases, type ContestForm, type ProblemForm } from './validation';

const PROBLEM: ProblemForm = {
  title: 'Two Sum', slug: 'two-sum', statementMd: 'Find two numbers.', difficulty: 'EASY', timeLimitMs: '2000', memoryLimitMb: '256', published: true,
};

const CONTEST: ContestForm = {
  title: 'Weekly', start: '2026-10-20T18:00', end: '2026-10-20T20:00', penaltyMinutes: '20',
  problems: [{ label: 'A', problemId: '1' }, { label: 'B', problemId: '2' }],
};

describe('validateProblem', () => {
  it('accepts a valid problem', () => {
    expect(validateProblem(PROBLEM)).toEqual({});
  });

  it.each([
    ['Two Sum', 'slug'],
    ['two--sum', 'slug'],
    ['-two', 'slug'],
    ['two_sum', 'slug'],
  ])('rejects slug %s', (slug, field) => {
    expect(validateProblem({ ...PROBLEM, slug })).toHaveProperty(field);
  });

  it('checks required fields and limit ranges like the api', () => {
    const errors = validateProblem({ ...PROBLEM, title: ' ', statementMd: '', timeLimitMs: '50', memoryLimitMb: '2048' });
    expect(errors).toEqual({
      title: 'Title is required',
      statementMd: 'Statement is required',
      timeLimitMs: 'Time limit must be between 100 and 10000',
      memoryLimitMb: 'Memory limit must be between 32 and 1024',
    });
    expect(validateProblem({ ...PROBLEM, timeLimitMs: '1.5' }).timeLimitMs).toBe('Time limit must be a whole number');
  });

  it('suggests slugs from titles', () => {
    expect(slugify('  Valid Parentheses (II)! ')).toBe('valid-parentheses-ii');
  });
});

describe('validateContest', () => {
  it('accepts a valid contest and converts local times to ISO UTC', () => {
    expect(validateContest(CONTEST)).toEqual({});
    const request = toContestRequest(CONTEST);
    expect(request.startTime).toBe(new Date('2026-10-20T18:00').toISOString());
    expect(request.problems).toEqual([{ label: 'A', problemId: 1 }, { label: 'B', problemId: 2 }]);
    expect(request.penaltyMinutes).toBe(20);
  });

  it('rejects an end before the start and contests over 30 days', () => {
    expect(validateContest({ ...CONTEST, end: '2026-10-20T17:00' }).end).toBe('End must be after the start');
    expect(validateContest({ ...CONTEST, end: '2026-11-25T18:00' }).end).toBe('A contest may last at most 30 days');
  });

  it('rejects missing, duplicate and unchosen problems', () => {
    expect(validateContest({ ...CONTEST, problems: [] }).problems).toBe('Add at least one problem');
    expect(validateContest({ ...CONTEST, problems: [{ label: 'A', problemId: '' }] }).problems).toBe('Choose a problem for every label');
    expect(validateContest({ ...CONTEST, problems: [{ label: 'A', problemId: '1' }, { label: 'A', problemId: '2' }] }).problems).toBe('Labels must be unique');
    expect(validateContest({ ...CONTEST, problems: [{ label: 'A', problemId: '1' }, { label: 'B', problemId: '1' }] }).problems).toBe('A problem can appear only once');
  });

  it('checks title, times and penalty', () => {
    expect(validateContest({ ...CONTEST, title: '', start: '', penaltyMinutes: '1001' })).toEqual({
      title: 'Title is required',
      start: 'Start time is required',
      penaltyMinutes: 'Penalty must be between 0 and 1000',
    });
  });
});

describe('validateTestCases', () => {
  it('requires at least one non-empty test case within 1 MB', () => {
    expect(validateTestCases([])).toBe('Add at least one test case');
    expect(validateTestCases([{ input: '', expectedOutput: '', sample: false }])).toBe('Test case 1 has no input and no expected output');
    expect(validateTestCases([{ input: 'x'.repeat(1_048_577), expectedOutput: '1', sample: false }])).toBe('Test case 1 is larger than 1 MB');
    expect(validateTestCases([{ input: '', expectedOutput: '42', sample: true }])).toBeNull();
  });
});
