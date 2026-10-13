import { api } from './endpoints';
import type { ProblemSummary } from './types';

const PAGE_SIZE = 50;
const MAX_PAGES = 20;

export async function fetchAllPublishedProblems(): Promise<ProblemSummary[]> {
  const all: ProblemSummary[] = [];
  for (let page = 0; page < MAX_PAGES; page++) {
    const result = await api.problems.list({ page, size: PAGE_SIZE });
    all.push(...result.content);
    if (page + 1 >= result.totalPages) break;
  }
  return all;
}
