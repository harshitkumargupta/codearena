import type { Language } from '../api/types';
import { STARTER_TEMPLATES } from './templates';

const drafts = new Map<string, string>();

const key = (scope: string, language: Language) => `${scope}:${language}`;

export function loadDraft(scope: string, language: Language): string {
  return drafts.get(key(scope, language)) ?? STARTER_TEMPLATES[language];
}

export function saveDraft(scope: string, language: Language, source: string): void {
  drafts.set(key(scope, language), source);
}
