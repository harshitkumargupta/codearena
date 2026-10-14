// Screenshot of the provisioned CodeArena dashboard for a time range.
// Usage: GF_PASS=... node loadtest/grafana-shot.cjs <fromMs> <toMs> <out.png>
// Uses the Playwright install from frontend/node_modules (npm ci in frontend/ first).
const path = require('path');
const { chromium } = require(path.join(__dirname, '..', 'frontend', 'node_modules', '@playwright/test'));

(async () => {
  const [from, to, out] = process.argv.slice(2);
  const base = process.env.GRAFANA_URL || 'http://localhost:3000';
  const browser = await chromium.launch();
  const page = await browser.newPage({ viewport: { width: 1600, height: 1560 } });
  await page.goto(`${base}/login`);
  await page.getByLabel(/username/i).first().fill(process.env.GF_USER || 'admin');
  await page.getByLabel(/password/i).first().fill(process.env.GF_PASS);
  await page.getByRole('button', { name: /log in/i }).click();
  await page.waitForURL((u) => !u.pathname.startsWith('/login'));
  await page.goto(`${base}/d/codearena-overview/codearena-overview?orgId=1&from=${from}&to=${to}&kiosk&theme=dark`);
  await page.waitForTimeout(4000);
  await page.mouse.wheel(0, 2000);
  await page.waitForTimeout(3000);
  await page.mouse.wheel(0, -2000);
  await page.waitForTimeout(10000);
  await page.screenshot({ path: out, type: out.endsWith('.jpg') ? 'jpeg' : 'png', quality: out.endsWith('.jpg') ? 80 : undefined });
  await browser.close();
})().catch((e) => {
  console.error(e.message);
  process.exit(1);
});
