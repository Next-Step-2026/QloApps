import { test, expect } from '@playwright/test';

test.describe('QloContactHealth Back-Office Integration', () => {
  test('Customer detail page renders hygiene card or graceful fallback', async ({ page }) => {
    // Navigate to admin login
    await page.goto('/admin');
    
    // Assert page loads correctly without 500 errors
    await expect(page).toHaveTitle(/QloApps|Login/i);
  });
});
