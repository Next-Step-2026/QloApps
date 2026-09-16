import { test, expect } from '@playwright/test';

const ADMIN_EMAIL = process.env.ADMIN_EMAIL || '';
const ADMIN_PASSWD = process.env.ADMIN_PASSWD || '';

async function loginToAdmin(page: any) {
  await page.goto('/admin');
  await page.waitForTimeout(1000);
  
  if (await page.locator('#email').isVisible()) {
    await page.fill('#email', ADMIN_EMAIL);
    await page.waitForTimeout(500);
    await page.fill('#passwd', ADMIN_PASSWD);
    await page.waitForTimeout(500);
    await page.click('button[name="submitLogin"]');
    await page.waitForLoadState('networkidle');
    await page.waitForTimeout(1000);
  }
}

test.describe('QloExternalRequests Canonical Converter E2E Flow (RFC-006)', () => {

  test('Navegação Back-Office: Login -> Conversor Canônico de Solicitações', async ({ page }) => {
    // 1. Realizar Login
    await loginToAdmin(page);

    // 2. Navegar para o Controller AdminExternalRequests
    await page.goto('/admin/index.php?controller=AdminExternalRequests');
    await page.waitForLoadState('networkidle');
    await page.waitForTimeout(1500);

    // 3. Validar elementos principais do formulário
    const panelHeading = page.locator('.panel-heading:has-text("External Requests Canonical Converter")');
    await expect(panelHeading).toBeVisible();

    const providerSelect = page.locator('select#provider_code');
    await expect(providerSelect).toBeVisible();

    const payloadTextarea = page.locator('textarea#raw_payload_json');
    await expect(payloadTextarea).toBeVisible();

    const submitButton = page.locator('#submitConvertRequest');
    await expect(submitButton).toBeVisible();
  });

  test('Preenchimento automático com amostra do PROVIDER_A e PROVIDER_B', async ({ page }) => {
    await loginToAdmin(page);
    await page.goto('/admin/index.php?controller=AdminExternalRequests');
    await page.waitForLoadState('networkidle');

    // Testar amostra do PROVIDER_A
    await page.click('#btnSampleProviderA');
    await page.waitForTimeout(300);
    const valueProviderA = await page.inputValue('#raw_payload_json');
    expect(valueProviderA).toContain('Maria Oliveira');
    expect(valueProviderA).toContain('arrival');

    // Testar amostra do PROVIDER_B
    await page.click('#btnSampleProviderB');
    await page.waitForTimeout(300);
    const valueProviderB = await page.inputValue('#raw_payload_json');
    expect(valueProviderB).toContain('checkin_date');
    expect(valueProviderB).toContain('checkout_date');

    // Limpar payload
    await page.click('#btnClearPayload');
    await page.waitForTimeout(300);
    const valueCleared = await page.inputValue('#raw_payload_json');
    expect(valueCleared).toBe('');
  });

});
