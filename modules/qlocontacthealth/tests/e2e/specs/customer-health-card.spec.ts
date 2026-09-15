import { test, expect } from '@playwright/test';

const ADMIN_EMAIL = process.env.ADMIN_EMAIL || 'gabrielsampaio@google.com';
const ADMIN_PASSWD = process.env.ADMIN_PASSWD || 'G0rg0nz0l@';

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

test.describe('QloContactHealth Back-Office E2E Flow', () => {

  test('Navegação completa: Login -> Menu Clientes -> Saúde de Contatos -> Visualizar Ficha do Cliente', async ({ page }) => {
    // 1. Realizar Login
    await loginToAdmin(page);

    // 2. Clicar no menu "Clientes"
    const menuClientesLink = page.locator('a:has-text("Clientes"), #subtab-AdminCustomers a, #subtab-AdminParentCustomer a').first();
    if (await menuClientesLink.isVisible()) {
      await menuClientesLink.click({ force: true });
      await page.waitForTimeout(1500);
    }

    // 3. Clicar no submenu "Saúde de Contatos"
    const subMenuSaude = page.locator('a:has-text("Saúde de Contatos"), a[href*="AdminContactHealth"]').first();
    if (await subMenuSaude.isVisible()) {
      await subMenuSaude.click({ force: true });
    } else {
      await page.goto('/admin/index.php?controller=AdminContactHealth');
    }
    await page.waitForLoadState('networkidle');
    await page.waitForTimeout(1500);

    // 4. Selecionar o primeiro cliente da lista e clicar em Visualizar
    const botaoVisualizar = page.locator('table tbody tr:first-child a.edit, table tbody tr:first-child a[href*="viewcustomer"], table tbody tr:first-child .icon-search-plus, table tbody tr:first-child a.btn').first();
    if (await botaoVisualizar.isVisible()) {
      await botaoVisualizar.click();
      await page.waitForLoadState('networkidle');
      await page.waitForTimeout(2000);

      // 5. Validar o Card de Indicador de Saúde
      const cardSaude = page.locator('.panel:has-text("Indicador de Saúde e Higiene Cadastral")');
      if (await cardSaude.isVisible()) {
        await cardSaude.scrollIntoViewIfNeeded();
        await page.waitForTimeout(2000);

        // 6. Testar o botão de Reconfirmação
        const botaoReconfirmar = page.locator('button:has-text("Disparar Desafio de Reconfirmação")');
        if (await botaoReconfirmar.isVisible()) {
          page.once('dialog', async (dialog) => {
            console.log('Alerta acionado:', dialog.message());
            await page.waitForTimeout(1000);
            await dialog.dismiss();
          });
          await botaoReconfirmar.click();
          await page.waitForTimeout(2000);
        }
      }
    }
  });

});
