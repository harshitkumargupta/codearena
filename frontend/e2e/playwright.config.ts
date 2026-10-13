import { defineConfig } from '@playwright/test';

export default defineConfig({
  testDir: '.',
  outputDir: './output/test-results',
  timeout: 180_000,
  reporter: 'list',
  use: {
    baseURL: process.env.E2E_BASE_URL ?? 'http://localhost:5173',
    headless: true,
    viewport: { width: 1440, height: 900 },
  },
});
