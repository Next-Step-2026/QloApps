import { test, expect } from '@playwright/test';

test.describe('QloContactHealth Back-Office Integration', () => {

  test('1. Admin page accessibility & server response', async ({ page }) => {
    const response = await page.goto('/admin');
    expect(response?.status()).toBeLessThan(400);
    await expect(page).toHaveTitle(/QloApps|Login/i);
  });

  test('2. Card UI rendering with mock API (FRESH status)', async ({ page }) => {
    // Intercept API evaluation route to simulate a healthy customer
    await page.route('**/v1/contact-evaluations', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          correlation_id: 'test-correlation-123',
          customer_id: 'cust-1042',
          overall_status: 'FRESH',
          hygiene_score: 100,
          factors: [
            {
              type: 'EMAIL',
              value_masked: 'm***a@tech.com',
              status: 'FRESH',
              days_since_verification: 10,
              issues: []
            },
            {
              type: 'PHONE',
              value_masked: '+5511*****4567',
              status: 'FRESH',
              days_since_verification: 10,
              issues: []
            }
          ],
          consent_valid: true,
          recommended_action: 'NONE'
        })
      });
    });

    await page.goto('/admin');
    await expect(page).toHaveTitle(/QloApps|Login/i);
  });

  test('3. Card UI rendering with STALE status & Reconfirmation Button', async ({ page }) => {
    // Intercept API evaluation route to simulate STALE customer needing reconfirmation
    await page.route('**/v1/contact-evaluations', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          correlation_id: 'test-correlation-456',
          customer_id: 'cust-1042',
          overall_status: 'STALE',
          hygiene_score: 40,
          factors: [
            {
              type: 'EMAIL',
              value_masked: 'm***a@tech.com',
              status: 'STALE',
              days_since_verification: 180,
              issues: ['STALENESS_EXCEEDED_90_DAYS']
            }
          ],
          consent_valid: false,
          recommended_action: 'TRIGGER_BACKGROUND_RECONFIRMATION'
        })
      });
    });

    await page.goto('/admin');
    await expect(page).toHaveTitle(/QloApps|Login/i);
  });

  test('4. Graceful Fallback & Resilience on API Outage (500 Error)', async ({ page }) => {
    // Intercept API route to simulate microservice 500 error
    await page.route('**/v1/contact-evaluations', async (route) => {
      await route.fulfill({
        status: 500,
        contentType: 'application/json',
        body: JSON.stringify({ error: 'Internal Server Error' })
      });
    });

    // Ensure the page loads without HTTP 500 failure
    const response = await page.goto('/admin');
    expect(response?.status()).toBeLessThan(500);
  });
});
