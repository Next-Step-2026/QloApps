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

test.describe('QloArrivalSharing E2E Geofencing & Reception Flow (RFC-004)', () => {

  test('Navegação Back-Office: Login -> Painel de Recepção e Monitoramento de Traslados', async ({ page }) => {
    // 1. Realizar Login no Back-Office
    await loginToAdmin(page);

    // 2. Navegar para o Controller AdminArrivalSharing
    await page.goto('/admin/index.php?controller=AdminArrivalSharing');
    await page.waitForLoadState('networkidle');
    await page.waitForTimeout(1500);

    // 3. Validar exibição do Painel de Recepção
    const panelHeading = page.locator('.panel-heading:has-text("Painel de Recepção e Monitoramento de Traslados")');
    await expect(panelHeading).toBeVisible();

    // 4. Validar exibição das Coordenadas da Propriedade (Latitude e Longitude)
    const alertInfo = page.locator('.alert-info:has-text("Serviço de detecção de proximidade ativo")');
    await expect(alertInfo).toBeVisible();

    const hotelCoords = page.locator('.well:has-text("Coordenadas da Propriedade")');
    await expect(hotelCoords).toBeVisible();
    await expect(hotelCoords).toContainText('Latitude: -8.052240');
    await expect(hotelCoords).toContainText('Longitude: -34.885650');
  });

  test('Simulação de Geolocation API com coordenadas no raio do hotel', async ({ page, context }) => {
    // Concede permissão de geolocalização e define coordenadas próximas ao hotel
    await context.grantPermissions(['geolocation']);
    await page.setGeolocation({ latitude: -8.052240, longitude: -34.885650 });

    // Acessar painel e verificar carregamento limpo
    await loginToAdmin(page);
    await page.goto('/admin/index.php?controller=AdminArrivalSharing');
    await expect(page.locator('.panel-heading')).toBeVisible();
  });

});
