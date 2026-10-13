import type { Page } from '@playwright/test';

/** Replaces the Monaco editor content by pasting, as a user would; typing would trigger auto-indentation per line. */
export async function setEditorCode(page: Page, code: string): Promise<void> {
  await page.context().grantPermissions(['clipboard-read', 'clipboard-write']);
  const editor = page.locator('.monaco-editor').first();
  await editor.waitFor();
  await editor.click();
  await page.evaluate((text) => navigator.clipboard.writeText(text), code);
  await page.keyboard.press('ControlOrMeta+A');
  await page.keyboard.press('ControlOrMeta+V');
}
