import { defineConfig, devices } from '@playwright/test';

const slowMoMs = process.env.SLOWMO ? parseInt(process.env.SLOWMO, 10) : 1000;

export default defineConfig({
  testDir: './specs',
  fullyParallel: false,
  workers: 1,
  timeout: 60000,
  use: {
    baseURL: process.env.BASE_URL || 'http://localhost:8080',
    trace: 'on-first-retry',
    launchOptions: {
      slowMo: slowMoMs,
    },
  },
  projects: [
    {
      name: 'chromium',
      use: { ...devices['Desktop Chrome'] },
    },
  ],
});
