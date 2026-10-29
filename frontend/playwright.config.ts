import { defineConfig, devices } from '@playwright/test';

/**
 * End-to-end suite for the full Docker Compose stack (`docker compose up -d --build`).
 * BASE_URL: frontend (nginx) · API_URL: api-gateway · MAILPIT_URL: Mailpit UI/API.
 */
export default defineConfig({
  testDir: './e2e',
  timeout: 120_000,
  expect: { timeout: 15_000 },
  fullyParallel: false,
  workers: 1,
  retries: process.env['CI'] ? 1 : 0,
  reporter: [
    ['list'],
    ['html', { outputFolder: 'playwright-report', open: 'never' }],
    ['json', { outputFile: 'playwright-report/results.json' }],
  ],
  outputDir: 'test-results',
  use: {
    baseURL: process.env['BASE_URL'] ?? 'http://localhost:3000',
    viewport: { width: 1280, height: 800 },
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
    video: (process.env['E2E_VIDEO'] as 'on' | 'off' | 'retain-on-failure') ?? 'retain-on-failure',
  },
  projects: [{ name: 'chromium', use: { ...devices['Desktop Chrome'], viewport: { width: 1280, height: 800 } } }],
});
