import { writeFileSync } from 'node:fs';
import { expect, test, type Page } from '@playwright/test';

const OUT = 'e2e/output';

const JAVA_ACCEPTED = `import java.util.*;

public class Main {
    public static void main(String[] args) {
        String marker = "PWMARK_JAVA_SOURCE";
        Scanner in = new Scanner(System.in);
        int n = in.nextInt();
        long target = in.nextLong();
        Map<Long, Integer> seen = new HashMap<>();
        for (int i = 0; i < n; i++) {
            long x = in.nextLong();
            Integer j = seen.get(target - x);
            if (j != null) {
                System.out.println(j + " " + i);
                return;
            }
            seen.put(x, i);
        }
    }
}
`;

const PYTHON_WRONG = `marker = "PWMARK_PY_SOURCE"
input()
input()
print(0, 0)
`;

declare global {
  interface Window {
    __statuses: string[];
  }
}

async function setEditorCode(page: Page, code: string) {
  const editor = page.locator('.monaco-editor').first();
  await editor.waitFor();
  await editor.click();
  await page.keyboard.press('ControlOrMeta+A');
  await page.keyboard.press('Delete');
  await page.keyboard.insertText(code);
}

async function submitAndWait(page: Page, finalStatus: string) {
  await page.evaluate(() => {
    window.__statuses = [];
  });
  await page.getByRole('button', { name: 'Submit' }).click();
  const verdict = page.getByTestId('verdict');
  await expect(verdict).toHaveAttribute('data-status', finalStatus, { timeout: 120_000 });
  return page.evaluate(() => window.__statuses);
}

test('register, log in, open a problem and see live verdicts', async ({ page }) => {
  const consoleLines: string[] = [];
  page.on('console', (msg) => consoleLines.push(`[${msg.type()}] ${msg.text()}`));
  page.on('pageerror', (error) => consoleLines.push(`[pageerror] ${error.message}`));
  await page.addInitScript(() => {
    window.__statuses = [];
    const record = () => {
      const status = document.querySelector('[data-testid=verdict]')?.getAttribute('data-status');
      if (status && status !== 'LOADING' && window.__statuses.at(-1) !== status) window.__statuses.push(status);
    };
    document.addEventListener('DOMContentLoaded', () => {
      new MutationObserver(record).observe(document.body, { subtree: true, childList: true, attributes: true, attributeFilter: ['data-status'] });
    });
  });

  const username = `pw_${Date.now()}`;
  const password = 'password123';

  await page.goto('/register');
  await page.getByLabel('Username').fill(username);
  await page.getByLabel('Email').fill(`${username}@example.com`);
  await page.getByLabel('Password').fill(password);
  await page.getByRole('button', { name: 'Register' }).click();
  await expect(page).toHaveURL(/\/problems$/);
  await expect(page.getByText(`Signed in as ${username}`)).toBeVisible();

  await page.getByRole('button', { name: 'Log out' }).click();
  await page.goto('/problems/two-sum');
  await expect(page).toHaveURL(/\/login$/);
  await page.getByLabel('Username').fill(username);
  await page.getByLabel('Password').fill(password);
  await page.screenshot({ path: `${OUT}/01-login.png` });
  await page.getByRole('button', { name: 'Log in' }).click();
  await expect(page).toHaveURL(/\/problems\/two-sum$/);

  await page.goto('/problems');
  await expect(page.getByRole('link', { name: 'Two Sum' })).toBeVisible();
  await page.screenshot({ path: `${OUT}/02-problem-list.png` });
  await page.getByLabel('Difficulty').selectOption('MEDIUM');
  await expect(page.getByRole('link', { name: 'Maximum Subarray' })).toBeVisible();
  await expect(page.getByRole('link', { name: 'Two Sum' })).toHaveCount(0);
  await page.getByLabel('Difficulty').selectOption('');

  await page.getByRole('link', { name: 'Two Sum' }).click();
  await expect(page.getByRole('heading', { name: 'Two Sum', level: 1 })).toBeVisible();
  await expect(page.getByText('Sample 1')).toBeVisible();
  await page.locator('.monaco-editor').first().waitFor();
  await page.screenshot({ path: `${OUT}/03-problem-page.png` });

  await setEditorCode(page, JAVA_ACCEPTED);
  const javaStatuses = await submitAndWait(page, 'ACCEPTED');
  await expect(page.getByTestId('verdict')).toContainText('Accepted');
  await expect(page.getByTestId('verdict')).toContainText(' ms');
  await page.screenshot({ path: `${OUT}/04-java-accepted.png` });

  await page.getByLabel('Language').selectOption('PYTHON');
  await setEditorCode(page, PYTHON_WRONG);
  const pythonStatuses = await submitAndWait(page, 'WRONG_ANSWER');
  await expect(page.getByTestId('verdict')).toContainText('Wrong answer');
  await expect(page.getByTestId('verdict')).toContainText('Failed on test #1');
  await expect(page.getByRole('button', { name: 'Explain failure' })).toBeEnabled();
  await page.screenshot({ path: `${OUT}/05-python-wrong-answer.png` });

  const rows = page.locator('#history-heading + table tbody tr');
  await expect(rows).toHaveCount(2);
  await expect(rows.nth(0)).toContainText('Wrong answer');
  await expect(rows.nth(0)).toContainText('Python 3.12');
  await expect(rows.nth(1)).toContainText('Accepted');
  await expect(rows.nth(1)).toContainText('Java 21');
  await page.screenshot({ path: `${OUT}/06-history.png`, fullPage: true });

  const token = await page.evaluate(() => {
    const raw = window.localStorage.getItem('codearena.session');
    return raw ? (JSON.parse(raw) as { token: string }).token : '';
  });
  const report = [
    `user: ${username}`,
    `java statuses seen: ${javaStatuses.join(' -> ')}`,
    `python statuses seen: ${pythonStatuses.join(' -> ')}`,
    `console lines: ${consoleLines.length}`,
    ...consoleLines,
  ].join('\n');
  writeFileSync(`${OUT}/report.txt`, `${report}\n`);

  expect(token.length).toBeGreaterThan(20);
  for (const line of consoleLines) {
    expect(line).not.toContain(token);
    expect(line).not.toContain(token.split('.')[2] ?? token);
    expect(line).not.toContain('PWMARK_');
    expect(line).not.toContain('seen.get(target - x)');
  }
});
