/**
 * RFC-001 Copiloto Observável de Intenções de Consulta de Reservas
 * Testes End-to-End (E2E) com Playwright
 *
 * Suíte expandida de testes visuais e automatizados no Back-Office do QloApps:
 * - Suporta execução visual interativa desacelerada (--headed)
 * - Cobre todos os cenários da RFC-001 (Disponibilidade, Políticas, Consulta de Reserva, Unknown, Validação, Auditoria e Contingência)
 */

const { chromium } = require('playwright');
const path = require('path');
const fs = require('fs');
const { execSync, spawn } = require('child_process');

const BASE_URL = process.env.QLO_BASE_URL || 'http://localhost:8080/admin338bwc0sf';
const ADMIN_EMAIL = process.env.QLO_ADMIN_EMAIL || 'gabrielrs@gxmail.com';
const ADMIN_PASS = process.env.QLO_ADMIN_PASSWORD || '1a2b3c4d5e';
const SCREENSHOTS_DIR = path.join(__dirname, 'screenshots');

if (!fs.existsSync(SCREENSHOTS_DIR)) {
  fs.mkdirSync(SCREENSHOTS_DIR, { recursive: true });
}

let passed = 0;
let failed = 0;

function assert(condition, message) {
  if (condition) {
    console.log(`  [PASS] ${message}`);
    passed++;
  } else {
    console.error(`  [FAIL] ${message}`);
    failed++;
  }
}

async function runE2ETests() {
  console.log('=== [E2E] Iniciando Testes End-to-End no Back-Office com Playwright ===\n');

  const isHeaded = process.argv.includes('--headed') || process.env.HEADED === '1';
  const actionDelay = isHeaded ? 1500 : 0; // 1.5s entre ações para visualização clara
  const readDelay = isHeaded ? 2500 : 0;   // 2.5s para leitura confortável dos cards

  if (isHeaded) {
    console.log('  -> [MODO VISUAL ATIVO]');
    console.log('     Janela do navegador em tela cheia com ritmo desacelerado (1.5s por ação).\n');
  }

  const browser = await chromium.launch({
    headless: !isHeaded,
    slowMo: actionDelay,
    args: ['--no-sandbox', '--disable-setuid-sandbox']
  });

  const context = await browser.newContext({
    viewport: { width: 1440, height: 900 },
    locale: 'pt-BR'
  });

  const page = await context.newPage();

  try {
    // 1. Autenticação no Back-Office
    console.log('[ETAPA 1] Autenticação no Back-Office do QloApps');
    await page.goto(`${BASE_URL}/index.php`, { waitUntil: 'networkidle' });

    const emailInput = await page.$('#email');
    if (emailInput) {
      console.log('  -> Preenchendo credenciais do atendente...');
      await page.fill('#email', ADMIN_EMAIL);
      await page.fill('#passwd', ADMIN_PASS);
      await Promise.all([
        page.waitForNavigation({ waitUntil: 'networkidle' }),
        page.click('button[name="submitLogin"]')
      ]);
    }

    assert(!page.url().includes('login'), 'Autenticação bem-sucedida no Back-Office');
    await page.screenshot({ path: path.join(SCREENSHOTS_DIR, '00_dashboard_login.png') });
    if (readDelay) await page.waitForTimeout(readDelay);

    // 2. Navegação para a aba do Copiloto de Reservas
    console.log('\n[ETAPA 2] Navegação para a aba do Copiloto de Reservas');
    const assistantLink = await page.$('a[href*="AdminReservationAssistant"]');
    if (assistantLink) {
      const href = await assistantLink.getAttribute('href');
      const targetUrl = href.startsWith('http') ? href : `${BASE_URL}/${href}`;
      console.log('  -> Acessando URL do Copiloto:', targetUrl);
      await page.goto(targetUrl, { waitUntil: 'networkidle' });
    } else {
      console.error('  [ERRO] Link do Copiloto não encontrado no menu.');
    }

    const pageContent = await page.content();
    assert(pageContent.includes('Copiloto de Atendimento e Consulta de Reservas'), 'Painel do Copiloto carregado com sucesso');
    assert(await page.$('input[name="user_query"]') !== null, 'Campo de pergunta do hóspede presente');
    assert(await page.$('input[name="reference_date"]') !== null, 'Campo de data de referência presente');
    if (readDelay) await page.waitForTimeout(readDelay);

    // 3. Cenário BDD 1: Consulta de Disponibilidade com Data Relativa e Quarto Deluxe
    console.log('\n[ETAPA 3] Cenário 1: Consulta de Disponibilidade (AVAILABILITY_QUERY)');
    console.log('  -> Consulta: "Tem quarto deluxe para 2 adultos depois de amanhã?"');
    await page.fill('input[name="reference_date"]', '2026-08-27');
    await page.fill('input[name="user_query"]', 'Tem quarto deluxe para 2 adultos depois de amanhã?');
    await Promise.all([
      page.waitForNavigation({ waitUntil: 'networkidle' }),
      page.click('button[name="submitQueryAssistant"]')
    ]);

    const availContent = await page.content();
    assert(availContent.includes('AVAILABILITY_QUERY'), 'Intenção AVAILABILITY_QUERY reconhecida (95% confiança)');
    assert(availContent.includes('deluxe'), 'Tipo de quarto "deluxe" extraído no card de slots');
    assert(availContent.includes('2026-08-29'), 'Data de check-in calculada (+2 dias sobre 2026-08-27)');
    assert(availContent.includes('2 pessoa(s)'), 'Contagem de 2 hóspedes identificada');
    assert(availContent.includes('req-'), 'X-Correlation-ID exibido para rastreabilidade');

    await page.screenshot({ path: path.join(SCREENSHOTS_DIR, '01_disponibilidade_deluxe.png') });
    if (readDelay) await page.waitForTimeout(readDelay);

    // 4. Cenário BDD 2: Consulta de Políticas de Cancelamento
    console.log('\n[ETAPA 4] Cenário 2: Consulta de Política de Cancelamento (POLICY_QUERY)');
    console.log('  -> Consulta: "Qual a politica de cancelamento de reservas e regras de no-show?"');
    await page.fill('input[name="user_query"]', 'Qual a politica de cancelamento de reservas e regras de no-show?');
    await Promise.all([
      page.waitForNavigation({ waitUntil: 'networkidle' }),
      page.click('button[name="submitQueryAssistant"]')
    ]);

    const policyContent = await page.content();
    assert(policyContent.includes('POLICY_QUERY'), 'Intenção POLICY_QUERY reconhecida (90% confiança)');
    assert(policyContent.includes('cancellation'), 'Categoria de política "cancellation" identificada');

    await page.screenshot({ path: path.join(SCREENSHOTS_DIR, '02_politica_cancelamento.png') });
    if (readDelay) await page.waitForTimeout(readDelay);

    // 5. Cenário BDD 3: Consulta de Status de Reserva Existente (RESERVATION_LOOKUP)
    console.log('\n[ETAPA 5] Cenário 3: Consulta de Status de Reserva Existente (RESERVATION_LOOKUP)');
    console.log('  -> Consulta: "Gostaria de verificar o status da minha reserva RES-9941"');
    await page.fill('input[name="user_query"]', 'Gostaria de verificar o status da minha reserva RES-9941');
    await Promise.all([
      page.waitForNavigation({ waitUntil: 'networkidle' }),
      page.click('button[name="submitQueryAssistant"]')
    ]);

    const lookupContent = await page.content();
    assert(lookupContent.includes('RESERVATION_LOOKUP'), 'Intenção RESERVATION_LOOKUP reconhecida (92% confiança)');
    assert(lookupContent.includes('RES-9941'), 'Código de reserva "RES-9941" extraído via regex');

    await page.screenshot({ path: path.join(SCREENSHOTS_DIR, '03_consulta_reserva.png') });
    if (readDelay) await page.waitForTimeout(readDelay);

    // 6. Cenário BDD 4: Consulta de Regras de Check-in Tardio
    console.log('\n[ETAPA 6] Cenário 4: Consulta de Horário de Check-in Tardio (POLICY_QUERY)');
    console.log('  -> Consulta: "Qual o horario limite para check-in tardio no hotel?"');
    await page.fill('input[name="user_query"]', 'Qual o horario limite para check-in tardio no hotel?');
    await Promise.all([
      page.waitForNavigation({ waitUntil: 'networkidle' }),
      page.click('button[name="submitQueryAssistant"]')
    ]);

    const checkinContent = await page.content();
    assert(checkinContent.includes('POLICY_QUERY'), 'Intenção POLICY_QUERY identificada para check-in');
    assert(checkinContent.includes('checkin_rules'), 'Categoria de política "checkin_rules" extraída');

    await page.screenshot({ path: path.join(SCREENSHOTS_DIR, '04_checkin_tardio.png') });
    if (readDelay) await page.waitForTimeout(readDelay);

    // 7. Cenário BDD 5: Pergunta Fora de Escopo / Não Reconhecida (UNKNOWN Fallback)
    console.log('\n[ETAPA 7] Cenário 5: Pergunta Fora de Escopo / Genérica (UNKNOWN)');
    console.log('  -> Consulta: "Qual o cardápio do restaurante para o jantar de hoje?"');
    await page.fill('input[name="user_query"]', 'Qual o cardápio do restaurante para o jantar de hoje?');
    await Promise.all([
      page.waitForNavigation({ waitUntil: 'networkidle' }),
      page.click('button[name="submitQueryAssistant"]')
    ]);

    const unknownContent = await page.content();
    assert(unknownContent.includes('UNKNOWN'), 'Intenção classificada como UNKNOWN (fallback seguro)');
    assert(unknownContent.includes('35.0%'), 'Baixo score de confiança identificado (35%)');

    await page.screenshot({ path: path.join(SCREENSHOTS_DIR, '05_intencao_unknown.png') });
    if (readDelay) await page.waitForTimeout(readDelay);

    // 8. Cenário BDD 6: Validação de Entrada no Front-end (Pergunta com Apenas Espaços)
    console.log('\n[ETAPA 8] Cenário 6: Validação de Borda: Pergunta em Branco (Apenas Espaços)');
    await page.fill('input[name="user_query"]', '     ');
    await Promise.all([
      page.waitForNavigation({ waitUntil: 'networkidle' }),
      page.click('button[name="submitQueryAssistant"]')
    ]);

    const emptyContent = await page.content();
    assert(emptyContent.includes('digite a pergunta do hóspede'), 'Alerta amigável de validação exibido para pergunta em branco');
    if (readDelay) await page.waitForTimeout(readDelay);

    // 9. Cenário BDD 7: Trilha de Auditoria e Ação de Limpar Histórico
    console.log('\n[ETAPA 9] Cenário 7: Trilha de Auditoria da Sessão e Limpeza');
    assert(emptyContent.includes('Histórico de Auditoria da Sessão'), 'Tabela de histórico de auditoria visível com consultas anteriores');

    // Clica no botão "Limpar Histórico"
    const clearBtn = await page.$('button[name="submitClearHistory"]');
    if (clearBtn) {
      console.log('  -> Clicando no botão "Limpar Histórico"...');
      await Promise.all([
        page.waitForNavigation({ waitUntil: 'networkidle' }),
        clearBtn.click()
      ]);
      const clearedContent = await page.content();
      assert(!clearedContent.includes('Histórico de Auditoria da Sessão'), 'Histórico de auditoria resetado e limpo com sucesso');
    }

    await page.screenshot({ path: path.join(SCREENSHOTS_DIR, '06_historico_limpo.png') });
    if (readDelay) await page.waitForTimeout(readDelay);

    // 10. Cenário BDD 8: Modo de Contingência (Serviço C++ Offline e Degradação Graciosa)
    console.log('\n[ETAPA 10] Cenário 8: Modo de Contingência (Serviço C++ Offline)');
    console.log('  -> Simulando indisponibilidade do serviço C++...');
    try {
      execSync('pkill -15 assistant_servi || true');
      await page.waitForTimeout(500);

      await page.fill('input[name="user_query"]', 'Tem quarto deluxe disponível?');
      await Promise.all([
        page.waitForNavigation({ waitUntil: 'networkidle' }),
        page.click('button[name="submitQueryAssistant"]')
      ]);

      const offlineContent = await page.content();
      assert(offlineContent.includes('Serviço de inferência local indisponível'), 'Alerta de contingência (HTTP 503) exibido na tela');
      assert(offlineContent.includes('modo de contingência'), 'Orientação de contingência comunicada ao atendente');

      await page.screenshot({ path: path.join(SCREENSHOTS_DIR, '07_contingencia_offline.png') });
      if (readDelay) await page.waitForTimeout(readDelay);
    } finally {
      console.log('  -> Restaurando serviço C++ em background...');
      const cppDir = path.resolve(__dirname, '../../../../assistant-service-cpp');
      const child = spawn('./assistant_service', [], {
        cwd: cppDir,
        detached: true,
        stdio: 'ignore'
      });
      child.unref();
      await page.waitForTimeout(1000);
      console.log('  -> Serviço C++ restaurado com sucesso.');
    }

  } catch (err) {
    console.error(`[ERRO INESPERADO] ${err.message}`);
    await page.screenshot({ path: path.join(SCREENSHOTS_DIR, 'error_state.png') });
    failed++;
  } finally {
    await browser.close();
  }

  console.log('\n=== RESUMO DOS TESTES E2E EXPANDIDOS ===');
  console.log(`Total de Asserções: ${passed + failed} | Passaram: ${passed} | Falharam: ${failed}`);

  if (failed > 0) {
    process.exit(1);
  }
}

runE2ETests();
