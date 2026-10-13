import { MAX_SOURCE_BYTES } from '../config';

export function sourceSizeError(source: string): string | null {
  const bytes = new TextEncoder().encode(source).length;
  if (bytes <= MAX_SOURCE_BYTES) return null;
  return `Source code is ${(bytes / 1024).toFixed(1)} KB; the limit is 64 KB.`;
}
