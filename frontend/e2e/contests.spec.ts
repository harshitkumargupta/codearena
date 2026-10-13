import { mkdirSync, writeFileSync } from 'node:fs';
import { expect, test, type Browser, type Page } from '@playwright/test';
import { setEditorCode } from './helpers';

const OUT = 'e2e/output';
const CURATED = '../docs/screenshots';

const JAVA_TWO_SUM = `import java.util.*;

public class Main {
    public static void main(String[] args) {
        // remember each value's index so the complement is found in O(1)
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
const PYTHON_ANY = '# unjoined attempt from the e2e run\nprint(0, 1)\n';

declare global {
  interface Window {
    __sameDocument?: boolean;
  }
}

interface Actor {
  name: string;
  page: Page;
  console: string[];
}

async function actor(browser: Browser, name: string): Promise<Actor> {
  const context = await browser.newContext({ viewport: { width: 1440, height: 900 } });
  const page = await context.newPage();
  const lines: string[] = [];
  page.on('console', (msg) => lines.push(`[${name}] [${msg.type()}] ${msg.text()}`));
  page.on('pageerror', (error) => lines.push(`[${name}] [pageerror] ${error.message}`));
  return { name, page, console: lines };
}

function localInput(time: number): string {
  const d = new Date(time);
  const pad = (n: number) => String(n).padStart(2, '0');
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}T${pad(d.getHours())}:${pad(d.getMinutes())}:${pad(d.getSeconds())}`;
}

async function curated(page: Page, file: string) {
  await page.screenshot({ path: `${CURATED}/${file}`, type: 'jpeg', quality: 70 });
}

async function login(page: Page, username: string, password: string) {
  await page.goto('/login');
  await page.getByLabel('Username').fill(username);
  await page.getByLabel('Password').fill(password);
  await page.getByRole('button', { name: 'Log in' }).click();
  await expect(page.getByText(`Signed in as ${username}`)).toBeVisible();
}

async function register(page: Page, username: string) {
  await page.goto('/register');
  await page.getByLabel('Username').fill(username);
  await page.getByLabel('Email').fill(`${username}@example.com`);
  await page.getByLabel('Password').fill('password123');
  await page.getByRole('button', { name: 'Register' }).click();
  await expect(page.getByText(`Signed in as ${username}`)).toBeVisible();
}


async function createContest(page: Page, title: string, start: number, end: number, problems: string[]) {
  await page.goto('/admin/contests/new');
  await page.getByLabel('Title').fill(title);
  await page.getByLabel(/^Start/).fill(localInput(start));
  await page.getByLabel(/^End/).fill(localInput(end));
  for (let i = 0; i < problems.length; i++) {
    if (i > 0) await page.getByRole('button', { name: 'Add problem' }).click();
    await page.getByLabel(`Problem ${'ABCDEFGHIJ'[i]}`, { exact: true }).selectOption({ label: problems[i] ?? '' });
  }
  await page.getByRole('button', { name: 'Create contest' }).click();
  await expect(page).toHaveURL(/\/contests\/\d+$/);
  return Number(page.url().split('/').pop());
}

test('admin builds contests; two users compete with a live leaderboard', async ({ browser }) => {
  mkdirSync(CURATED, { recursive: true });
  const ts = Date.now();
  const problemTitle = `Echo Lines ${ts}`;
  const aliceName = `pw_a_${ts}`;
  const bobName = `pw_b_${ts}`;

  const admin = await actor(browser, 'admin');
  await admin.page.goto('/login');
  await admin.page.getByLabel('Username').fill('admin');
  await curated(admin.page, '01-login.jpg');
  await login(admin.page, 'admin', 'admin-pass-123');
  await expect(admin.page.getByRole('link', { name: 'Admin', exact: true })).toBeVisible();

  await admin.page.goto('/admin/problems/new');
  await admin.page.getByLabel('Title').fill(problemTitle);
  await admin.page.getByLabel('Statement (Markdown)').fill(
    'Read **one line** of text and print it back unchanged.\n\n### Input\nA single line.\n\n### Output\nThe same line.',
  );
  await admin.page.getByRole('checkbox', { name: /Published/ }).check();
  await expect(admin.page.getByTestId('statement-preview').locator('strong')).toHaveText('one line');
  await curated(admin.page, '05-admin-problem-form.jpg');
  await admin.page.getByRole('button', { name: 'Create problem' }).click();
  await expect(admin.page.getByRole('status')).toHaveText('Problem created.');

  await admin.page.getByRole('link', { name: 'Add test cases' }).click();
  await admin.page.getByLabel('Input file for test case 1').setInputFiles({ name: 'sample.in', mimeType: 'text/plain', buffer: Buffer.from('hello arena\n') });
  await expect(admin.page.locator('#tc-input-0')).toHaveValue('hello arena\n');
  await admin.page.locator('#tc-output-0').fill('hello arena');
  await admin.page.getByRole('checkbox', { name: /Sample/ }).first().check();
  await admin.page.getByRole('button', { name: 'Add another' }).click();
  await admin.page.locator('#tc-input-1').fill('CodeArena\n');
  await admin.page.locator('#tc-output-1').fill('CodeArena');
  await admin.page.getByRole('button', { name: 'Upload 2 test cases' }).click();
  const added = admin.page.getByTestId('added-test-cases');
  await expect(added.locator('tbody tr')).toHaveCount(2);
  await expect(added).toContainText('Yes');
  await expect(added).toContainText('No (hidden)');
  await expect(added).not.toContainText('CodeArena');
  await curated(admin.page, '06-admin-test-cases.jpg');

  const now = Date.now();
  const liveContest = await createContest(admin.page, `PW Live Cup ${ts}`, now + 45_000, now + 2 * 3_600_000, ['Two Sum', problemTitle]);
  const runningContest = await createContest(admin.page, `PW Running Cup ${ts}`, now - 600_000, now + 3_600_000, ['Two Sum']);
  const endedContest = await createContest(admin.page, `PW Ended Cup ${ts}`, now - 3 * 3_600_000, now - 2 * 3_600_000, ['Two Sum']);

  const alice = await actor(browser, 'alice');
  const bob = await actor(browser, 'bob');
  await register(alice.page, aliceName);
  await register(bob.page, bobName);

  for (const user of [alice, bob]) {
    await user.page.goto(`/contests/${liveContest}`);
    await expect(user.page.getByRole('timer')).toContainText('Starts in');
    await expect(user.page.getByText(/Problems will appear here when the contest starts/)).toBeVisible();
    await expect(user.page.getByRole('tablist')).toHaveCount(0);
    await expect(user.page.getByText('Two Sum')).toHaveCount(0);
  }
  await curated(bob.page, '04-contest-countdown.jpg');
  await bob.page.screenshot({ path: `${OUT}/contest-before-start.png` });
  for (const user of [alice, bob]) {
    await user.page.getByRole('button', { name: 'Join contest' }).click();
    await expect(user.page.getByText('Joined', { exact: true })).toBeVisible();
  }

  await bob.page.evaluate(() => {
    window.__sameDocument = true;
  });
  for (const user of [alice, bob]) {
    await expect(user.page.getByRole('tab', { name: 'A. Two Sum' })).toBeVisible({ timeout: 120_000 });
  }
  const bobRows = bob.page.getByTestId('leaderboard').getByTestId('leaderboard-row');
  await expect(bobRows).toHaveCount(2);

  await setEditorCode(alice.page, JAVA_TWO_SUM);
  await alice.page.getByRole('button', { name: 'Submit' }).click();
  await expect(alice.page.getByTestId('verdict')).toHaveAttribute('data-status', 'ACCEPTED', { timeout: 120_000 });
  const aliceTop = alice.page.getByTestId('leaderboard').getByTestId('leaderboard-row').first();
  await expect(aliceTop.locator('td').nth(2)).toHaveText('1', { timeout: 30_000 });
  await alice.page.evaluate(() => window.scrollTo(0, 0));
  await curated(alice.page, '02-contest-problem-accepted.jpg');

  await expect(bobRows.nth(0).locator('td').nth(1)).toHaveText(aliceName, { timeout: 30_000 });
  await expect(bobRows.nth(0).locator('td').nth(2)).toHaveText('1');
  await expect(bobRows.nth(1).locator('td').nth(1)).toHaveText(`${bobName} (you)`);
  await expect(bobRows.nth(1).locator('td').nth(2)).toHaveText('0');
  await expect(bobRows.nth(1)).toHaveAttribute('aria-current', 'true');
  expect(await bob.page.evaluate(() => window.__sameDocument)).toBe(true);
  await expect(bob.page.getByTestId('leaderboard')).toContainText('Live');
  await curated(bob.page, '03-contest-leaderboard.jpg');

  await bob.page.goto(`/contests/${runningContest}`);
  await expect(bob.page.getByText(/You have not joined this contest yet/)).toBeVisible();
  await bob.page.getByLabel('Language').selectOption('PYTHON');
  await setEditorCode(bob.page, PYTHON_ANY);
  await bob.page.getByRole('button', { name: 'Submit' }).click();
  await expect(bob.page.getByRole('alert').filter({ hasText: 'not joined' })).toHaveText(
    'You have not joined this contest. Join the contest to submit.',
  );
  await bob.page.screenshot({ path: `${OUT}/contest-not-joined-rejected.png` });

  await bob.page.goto(`/contests/${endedContest}`);
  await expect(bob.page.getByText(/This contest has ended/)).toBeVisible();
  await expect(bob.page.getByRole('tab', { name: 'A. Two Sum' })).toBeVisible();
  await expect(bob.page.getByText(/Read-only: this contest has ended/)).toBeVisible();
  await expect(bob.page.getByRole('button', { name: 'Submit' })).toHaveCount(0);
  await expect(bob.page.getByRole('button', { name: 'Join contest' })).toHaveCount(0);
  await bob.page.screenshot({ path: `${OUT}/contest-ended-read-only.png` });

  const tokens: string[] = [];
  for (const user of [admin, alice, bob]) {
    tokens.push(
      await user.page.evaluate(() => {
        const raw = window.localStorage.getItem('codearena.session');
        return raw ? (JSON.parse(raw) as { token: string }).token : '';
      }),
    );
  }
  const consoleLines = [...admin.console, ...alice.console, ...bob.console];
  writeFileSync(
    `${OUT}/contests-report.txt`,
    [`contests: live=${liveContest} running=${runningContest} ended=${endedContest}`, `console lines: ${consoleLines.length}`, ...consoleLines].join('\n') + '\n',
  );
  for (const token of tokens) expect(token.length).toBeGreaterThan(20);
  for (const line of consoleLines) {
    for (const token of tokens) expect(line).not.toContain(token.split('.')[2] ?? token);
    expect(line).not.toContain("remember each value's index");
    expect(line).not.toContain('unjoined attempt from the e2e run');
    expect(line).not.toContain('seen.get(target - x)');
  }
});
