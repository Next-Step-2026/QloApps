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

test.describe('QloActionableSearch E2E Flow (RFC-005/008)', () => {

  test('Navegação Back-Office: Login -> Painel de Busca Léxica de Entidades', async ({ page }) => {
    // 1. Realizar Login
    await loginToAdmin(page);

    // 2. Navegar para o Controller AdminActionableSearch
    await page.goto('/admin/index.php?controller=AdminActionableSearch');
    await page.waitForLoadState('networkidle');
    await page.waitForTimeout(1500);

    // 3. Validar elementos principais da página
    const panelHeading = page.locator('.panel-heading:has-text("Fast Lexical Search Engine & Entity Recognition")');
    await expect(panelHeading).toBeVisible();

    const searchInput = page.locator('input#search_query');
    await expect(searchInput).toBeVisible();

    const submitBtn = page.locator('button[name="submitSearchQuery"]');
    await expect(submitBtn).toBeVisible();
  });

  test('Submissão de busca por entidade de catálogo', async ({ page }) => {
    await loginToAdmin(page);
    await page.goto('/admin/index.php?controller=AdminActionableSearch');
    await page.waitForLoadState('networkidle');

    // Preencher campo de busca
    await page.fill('input#search_query', 'suite master vista mar');
    await page.click('button[name="submitSearchQuery"]');
    await page.waitForLoadState('networkidle');

    // Verificar se o painel respondeu (seja com resultados ou com o aviso de contingência)
    const hasResultsOrContingency = await page.locator('.well, .alert-warning').first().isVisible();
    expect(hasResultsOrContingency).toBeTruthy();
  });

});
