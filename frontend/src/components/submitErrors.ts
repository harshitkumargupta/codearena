import type { ApiError } from '../api/client';

const FIELD_LABELS: Record<string, string> = { sourceCode: 'Source code', language: 'Language' };
const SENTENCE_FIELDS = new Set(['contestId', 'problemId']);

function sentence(text: string): string {
  const trimmed = text.trim();
  const capitalized = trimmed.charAt(0).toUpperCase() + trimmed.slice(1);
  return /[.!?]$/.test(capitalized) ? capitalized : `${capitalized}.`;
}

export function describeSubmitError(error: ApiError): string {
  if (error.fieldErrors.length === 0) {
    return error.status === 403 ? `${sentence(error.message)} Join the contest to submit.` : sentence(error.message);
  }
  return error.fieldErrors
    .map((f) => (SENTENCE_FIELDS.has(f.field) ? sentence(f.message) : `${FIELD_LABELS[f.field] ?? f.field}: ${f.message}`))
    .join(' ');
}
