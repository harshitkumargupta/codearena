import { describe, expect, it } from 'vitest';
import { sourceSizeError } from './sourceSize';

describe('sourceSizeError', () => {
  it('allows exactly 64 KB and rejects one byte more, counting UTF-8 bytes', () => {
    expect(sourceSizeError('x'.repeat(65_536))).toBeNull();
    expect(sourceSizeError('x'.repeat(65_537))).toBe('Source code is 64.0 KB; the limit is 64 KB.');
    expect(sourceSizeError('é'.repeat(32_769))).not.toBeNull();
  });
});
