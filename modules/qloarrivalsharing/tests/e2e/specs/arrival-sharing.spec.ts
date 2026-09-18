import { test, expect } from '@playwright/test';

const ADMIN_EMAIL = process.env.ADMIN_EMAIL || '';
const ADMIN_PASSWD = process.env.ADMIN_PASSWD || '';
const KOTLIN_SERVICE_URL = 'http://127.0.0.1:8104';

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

test.describe('QloArrivalSharing - Suíte Completa e Exaustiva de Testes E2E (RFC-004)', () => {

  test('1. Contrato HTTP Direto do Microserviço Kotlin (/healthz e /v1/location-events)', async ({ request }) => {
    // 1.1. Healthcheck (/healthz)
    const healthRes = await request.get(`${KOTLIN_SERVICE_URL}/healthz`);
    expect(healthRes.status()).toBe(200);
    const healthJson = await healthRes.json();
    expect(healthJson.status).toBe('UP');
    expect(healthJson.service).toBe('location-service-kotlin');

    // 1.2. Rejeição de Coordenadas Geográficas Inválidas (RN-001)
    const invalidRes = await request.post(`${KOTLIN_SERVICE_URL}/v1/location-events`, {
      headers: {
        'Content-Type': 'application/json',
        'X-Correlation-ID': 'a1b2c3d4-e5f6-4a8b-9c0d-1e2f3a4b5c6d'
      },
      data: {
        hotel_id: 'htl-01',
        hotel_lat: -8.052240,
        hotel_lng: -34.885650,
        guest_lat: 95.0, // Latitude inválida (> 90.0)
        guest_lng: -34.886100,
        geofence_radius_m: 200.0,
        previous_state: 'outside'
      }
    });
    expect(invalidRes.status()).toBe(400);
    const invalidJson = await invalidRes.json();
    expect(invalidJson.code).toBe('INVALID_COORDINATES');
  });

  test('2. Rejeição de Raio Geofence Inválido no Microserviço Kotlin (RN-006)', async ({ request }) => {
    const invalidRadiusRes = await request.post(`${KOTLIN_SERVICE_URL}/v1/location-events`, {
      headers: {
        'Content-Type': 'application/json',
        'X-Correlation-ID': 'a1b2c3d4-e5f6-4a8b-9c0d-1e2f3a4b5c6d'
      },
      data: {
        hotel_id: 'htl-01',
        hotel_lat: -8.052240,
        hotel_lng: -34.885650,
        guest_lat: -8.053100,
        guest_lng: -34.886100,
        geofence_radius_m: 0.0, // Raio inválido (<= 0)
        previous_state: 'outside'
      }
    });
    expect(invalidRadiusRes.status()).toBe(400);
    const radiusJson = await invalidRadiusRes.json();
    expect(radiusJson.code).toBe('INVALID_RADIUS');
  });

  test('3. Cálculo de Proximidade com Distância Zero (RN-002: Hóspede na Posição Exata do Hotel)', async ({ request }) => {
    const zeroDistRes = await request.post(`${KOTLIN_SERVICE_URL}/v1/location-events`, {
      headers: {
        'Content-Type': 'application/json',
        'X-Correlation-ID': 'a1b2c3d4-e5f6-4a8b-9c0d-1e2f3a4b5c6d'
      },
      data: {
        hotel_id: 'htl-01',
        hotel_lat: -8.052240,
        hotel_lng: -34.885650,
        guest_lat: -8.052240, // Mesma posição do hotel
        guest_lng: -34.885650,
        geofence_radius_m: 200.0,
        previous_state: 'outside'
      }
    });
    expect(zeroDistRes.status()).toBe(200);
    const zeroJson = await zeroDistRes.json();
    expect(zeroJson.distance_meters).toBe(0.0);
    expect(zeroJson.current_state).toBe('inside');
    expect(zeroJson.transition).toBe('ENTERED');
    expect(zeroJson.alert_triggered).toBe(true);
  });

  test('4. Painel de Recepção do Back-Office (KPIs e Lista de Chegadas Previstas)', async ({ page }) => {
    await loginToAdmin(page);
    await page.goto('/admin/index.php?controller=AdminArrivalSharing');
    await page.waitForLoadState('networkidle');

    // Validar título do painel principal
    const panelHeading = page.locator('.panel-heading:has-text("Monitoramento de Chegadas e Traslados")');
    await expect(panelHeading).toBeVisible();

    // Validar os 4 cards de KPIs
    await expect(page.locator('h4:has-text("Data de Hoje")')).toBeVisible();
    await expect(page.locator('h4:has-text("Chegadas Previstas")')).toBeVisible();
    await expect(page.locator('h4:has-text("Total de Hóspedes")')).toBeVisible();
    await expect(page.locator('.panel h4:has-text("Geofence")')).toBeVisible();

    // Validar existência da reserva semeada no monitoramento de chegadas
    await expect(page.locator('tr:has-text("#99999")')).toBeVisible();
    await expect(page.locator('a:has-text("Link Hóspede")')).toBeVisible();
  });

  test('5. Configuração do Raio de Geofence via Modal no Back-Office (RN-006)', async ({ page }) => {
    await loginToAdmin(page);
    await page.goto('/admin/index.php?controller=AdminArrivalSharing');
    await page.waitForLoadState('networkidle');

    // Abrir Modal de Geofence
    await page.click('a[data-target="#modalGeofenceConfig"]');
    await expect(page.locator('#modalGeofenceConfig')).toBeVisible();

    // Alterar para 250 metros
    await page.fill('#modalGeofenceConfig input[name="geofence_radius"]', '250');
    await page.click('#modalGeofenceConfig button[name="submitGeofenceRadius"]');
    await page.waitForLoadState('networkidle');

    // Verificar se o indicador foi atualizado para 250 m
    await expect(page.locator('.panel:has-text("Geofence") h3:has-text("250 m")')).toBeVisible();

    // Reverter de volta para 200m para manter ambiente limpo
    await page.click('a[data-target="#modalGeofenceConfig"]');
    await page.fill('#modalGeofenceConfig input[name="geofence_radius"]', '200');
    await page.click('#modalGeofenceConfig button[name="submitGeofenceRadius"]');
    await page.waitForLoadState('networkidle');
    await expect(page.locator('.panel:has-text("Geofence") h3:has-text("200 m")')).toBeVisible();
  });

  test('6. Motor de Cálculo Geodésico - Transição ENTERED (Chegando ~108m, Alerta Disparado)', async ({ page, context }) => {
    await context.grantPermissions(['geolocation']);
    await context.setGeolocation({ latitude: -8.052240, longitude: -34.885650 });

    await loginToAdmin(page);
    await page.goto('/admin/index.php?controller=AdminArrivalSharing');
    await page.waitForLoadState('networkidle');

    // Expandir simulador se estiver recolhido
    const btnToggle = page.locator('#btnToggleSimulation');
    if (await btnToggle.isVisible()) {
      const classes = (await page.locator('#simulationCollapse').getAttribute('class')) || '';
      if (!classes.includes('in')) {
        await btnToggle.click();
        await page.waitForTimeout(500);
      }
    }

    // Preencher coordenadas de aproximação (~107.7m do hotel)
    await page.fill('#sim_guest_lat', '-8.053100');
    await page.fill('#sim_guest_lng', '-34.886100');
    await page.selectOption('#sim_previous_state', 'outside');
    await page.fill('#sim_radius', '200');

    // Submeter verificação para o microserviço Kotlin
    await page.click('button[name="submitCheckLocation"]');
    await page.waitForLoadState('networkidle');
    await page.waitForTimeout(1000);

    // Validação estrita dos campos calculados
    const resultPanel = page.locator('#simulationCollapse .panel:has-text("Distância Calculada")');
    await expect(resultPanel).toBeVisible();
    await expect(resultPanel).toContainText('ENTERED');
    await expect(resultPanel).toContainText('inside');
    await expect(resultPanel).toContainText('Disparado');
  });

  test('7. Motor de Cálculo Geodésico - Transições NO_CHANGE (Longe) e EXITED (Saindo)', async ({ page }) => {
    await loginToAdmin(page);
    await page.goto('/admin/index.php?controller=AdminArrivalSharing');
    await page.waitForLoadState('networkidle');

    // Garantir simulador expandido
    const classes = (await page.locator('#simulationCollapse').getAttribute('class')) || '';
    if (!classes.includes('in')) {
      await page.click('#btnToggleSimulation');
      await page.waitForTimeout(500);
    }

    // 7.1. Testar Cenário Preset 2: Hóspede Longe (~1.500m -> NO_CHANGE)
    await page.click('button:has-text("2. Hóspede Longe")');
    await page.click('button[name="submitCheckLocation"]');
    await page.waitForLoadState('networkidle');

    const resultPanel2 = page.locator('#simulationCollapse .panel:has-text("Distância Calculada")');
    await expect(resultPanel2).toBeVisible();
    await expect(resultPanel2).toContainText('NO_CHANGE');
    await expect(resultPanel2).toContainText('outside');
    await expect(resultPanel2).toContainText('Sem Disparo');

    // 7.2. Testar Cenário Preset 3: Hóspede Saindo (~800m, inside -> EXITED)
    await page.click('button:has-text("3. Hóspede Saindo")');
    await page.click('button[name="submitCheckLocation"]');
    await page.waitForLoadState('networkidle');

    const resultPanel3 = page.locator('#simulationCollapse .panel:has-text("Distância Calculada")');
    await expect(resultPanel3).toBeVisible();
    await expect(resultPanel3).toContainText('EXITED');
    await expect(resultPanel3).toContainText('outside');
    await expect(resultPanel3).toContainText('Sem Disparo');
  });

  test('8. Tela do Hóspede (Front-Office) - Validação de Segurança e Token Inválido', async ({ page }) => {
    // Acessar com token inválido
    await page.goto('/index.php?fc=module&module=qloarrivalsharing&controller=arrivaltracking&id_order=99999&token=invalid_token_999');
    await page.waitForLoadState('networkidle');

    // Validar mensagem de erro e bloqueio de acesso
    await expect(page.locator('h3:has-text("Acesso Não Disponível")')).toBeVisible();
    await expect(page.locator('p:has-text("Link de acompanhamento inválido ou expirado.")')).toBeVisible();
  });

  test('9. Fluxo E2E do Hóspede no Front-Office - Consentimento LGPD & Notificação GPS', async ({ page, context }) => {
    // Definir permissões de GPS próximas ao hotel (~107.7m)
    await context.grantPermissions(['geolocation']);
    await context.setGeolocation({ latitude: -8.053100, longitude: -34.886100 });

    // Obter link do hóspede a partir da tabela do Back-Office
    await loginToAdmin(page);
    await page.goto('/admin/index.php?controller=AdminArrivalSharing');
    await page.waitForLoadState('networkidle');

    const guestHref = await page.locator('tr:has-text("#99999") a:has-text("Link Hóspede")').getAttribute('href');
    expect(guestHref).toBeTruthy();

    // Acessar a tela do hóspede
    await page.goto(guestHref!);
    await page.waitForLoadState('networkidle');

    // Validar dados do hóspede e hotel
    await expect(page.locator('h2:has-text("Hotel Prime Recife")')).toBeVisible();
    await expect(page.locator('h3:has-text("Olá, John D.!")')).toBeVisible();

    // Validar consentimento LGPD: botão desativado até marcar a checkbox
    const btnSend = page.locator('#btnSendArrival');
    await expect(btnSend).toBeDisabled();

    await page.check('#checkGpsConsent');
    await expect(btnSend).toBeEnabled();

    // Clicar em "Estou Chegando!"
    await btnSend.click();

    // Validar caixa de sucesso de notificação enviada à recepção
    const alertSuccess = page.locator('#guestAlertSuccess');
    await expect(alertSuccess).toBeVisible({ timeout: 10000 });
    await expect(alertSuccess).toContainText('Recepção Notificada!');
    await expect(alertSuccess).toContainText('Você chegou às imediações do hotel');
  });

  test('10. Integração Ponta-a-Ponta Front-Office -> Back-Office (Atualização do Badge na Recepção)', async ({ page }) => {
    // Acessar a página de recepção no Back-Office após o envio do hóspede
    await loginToAdmin(page);
    await page.goto('/admin/index.php?controller=AdminArrivalSharing');
    await page.waitForLoadState('networkidle');

    // Validar que o status da reserva #99999 foi atualizado na tabela para "Chegou às Imediações"
    const trackingCell = page.locator('tr:has-text("#99999") td.tracking-cell');
    await expect(trackingCell).toBeVisible();
    await expect(trackingCell).toContainText('Chegou às Imediações');
  });

});
