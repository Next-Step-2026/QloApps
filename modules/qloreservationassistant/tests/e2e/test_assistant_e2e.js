/**
 * RFC-001 Copiloto Observável de Intenções de Consulta de Reservas
 * Testes End-to-End (E2E) com Playwright
 *
 * Valida a jornada do atendente no Back-Office do QloApps:
 * 1. Autenticação administrativa no QloApps
 * 2. Navegação até a aba do Copiloto de Reservas
 * 3. Execução do Cenário 1: Consulta de Disponibilidade (AVAILABILITY_QUERY)
 * 4. Execução do Cenário 2: Consulta de Políticas (POLICY_QUERY)
 * 5. Verificação da Trilha de Auditoria e propagação do X-Correlation-ID
 * 6. Captura de evidências visuais (screenshots)
 */

const { chromium } = require('playwright');
const path = require('path');
const fs = require('fs');

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
  if (isHeaded) {
    console.log('  -> [MODO VISUAL ATIVO] Abrindo janela do navegador com visualizacao em tempo real (slowMo: 800ms)...\n');
  }

  const browser = await chromium.launch({
    headless: !isHeaded,
    slowMo: isHeaded ? 800 : 0,
    args: ['--no-sandbox', '--disable-setuid-sandbox']
  });

  const context = await browser.newContext({
    viewport: { width: 1280, height: 900 },
    locale: 'pt-BR'
  });

  const page = await context.newPage();

  try {
    // 1. Autenticação no Back-Office
    console.log('[ETAPA 1] Autenticacao no Back-Office do QloApps');
    await page.goto(`${BASE_URL}/index.php`, { waitUntil: 'networkidle' });

    // Verifica se já está na tela de login
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

    // 2. Navegação para a aba do Copiloto de Reservas
    console.log('\n[ETAPA 2] Navegacao para a aba do Copiloto de Reservas');
    // Localiza o link no menu lateral ou acessa diretamente
    const assistantLink = await page.$('a[href*="AdminReservationAssistant"]');
    if (assistantLink) {
      const href = await assistantLink.getAttribute('href');
      const targetUrl = href.startsWith('http') ? href : `${BASE_URL}/${href}`;
      console.log('  -> Acessando URL do Copiloto:', targetUrl);
      await page.goto(targetUrl, { waitUntil: 'networkidle' });
    } else {
      console.error('  [ERRO] Link do Copiloto nao encontrado no menu.');
    }

    const pageContent = await page.content();
    assert(pageContent.includes('Copiloto de Atendimento e Consulta de Reservas'), 'Painel do Copiloto carregado com sucesso');
    assert(await page.$('input[name="user_query"]') !== null, 'Campo de pergunta do hóspede presente');
    assert(await page.$('input[name="reference_date"]') !== null, 'Campo de data de referência presente');

    // 3. Cenário BDD 1: Consulta de Disponibilidade com Expressão Temporal Relativa
    console.log('\n[ETAPA 3] BDD Cenario 1: Consulta de Disponibilidade');
    await page.fill('input[name="reference_date"]', '2026-08-27');
    await page.fill('input[name="user_query"]', 'Tem quarto deluxe para 2 adultos depois de amanhã?');
    await Promise.all([
      page.waitForNavigation({ waitUntil: 'networkidle' }),
      page.click('button[name="submitQueryAssistant"]')
    ]);

    const availabilityContent = await page.content();
    assert(availabilityContent.includes('AVAILABILITY_QUERY'), 'Intenção AVAILABILITY_QUERY reconhecida e renderizada');
    assert(availabilityContent.includes('deluxe'), 'Tipo de quarto "deluxe" extraído no card de slots');
    assert(availabilityContent.includes('2026-08-29'), 'Data de check-in calculada corretamente (+2 dias)');
    assert(availabilityContent.includes('2 pessoa(s)'), 'Contagem de 2 hóspedes identificada');
    assert(availabilityContent.includes('req-'), 'X-Correlation-ID exibido para rastreabilidade');

    await page.screenshot({ path: path.join(SCREENSHOTS_DIR, '01_disponibilidade_deluxe.png') });
    console.log('  -> Screenshot salvo: screenshots/01_disponibilidade_deluxe.png');

    // 4. Cenário BDD 2: Consulta de Políticas de Cancelamento
    console.log('\n[ETAPA 4] BDD Cenario 2: Consulta de Politica de Cancelamento');
    await page.fill('input[name="user_query"]', 'Qual a politica de cancelamento de reservas e regras de no-show?');
    await Promise.all([
      page.waitForNavigation({ waitUntil: 'networkidle' }),
      page.click('button[name="submitQueryAssistant"]')
    ]);

    const policyContent = await page.content();
    assert(policyContent.includes('POLICY_QUERY'), 'Intenção POLICY_QUERY reconhecida');
    assert(policyContent.includes('cancellation'), 'Categoria de política "cancellation" identificada');

    await page.screenshot({ path: path.join(SCREENSHOTS_DIR, '02_politica_cancelamento.png') });
    console.log('  -> Screenshot salvo: screenshots/02_politica_cancelamento.png');

    // 5. Cenário BDD 3: Trilha de Auditoria da Sessão (Observabilidade)
    console.log('\n[ETAPA 5] BDD Cenario 3: Trilha de Auditoria da Sessao');
    assert(policyContent.includes('Histórico de Auditoria da Sessão'), 'Tabela de histórico de auditoria visível');
    assert(policyContent.includes('Tem quarto deluxe para 2 adultos depois de amanhã?'), 'Primeira consulta presente no histórico');
    assert(policyContent.includes('Qual a politica de cancelamento'), 'Segunda consulta presente no histórico');

    await page.screenshot({ path: path.join(SCREENSHOTS_DIR, '03_trilha_auditoria.png') });
    console.log('  -> Screenshot salvo: screenshots/03_trilha_auditoria.png');

    // 6. Cenário BDD 4: Modo de Contingência com Serviço C++ Offline (Degradação Graciosa)
    console.log('\n[ETAPA 6] BDD Cenario 4: Modo de Contingencia (Servico C++ Offline)');
    const { execSync } = require('child_process');
    console.log('  -> Simulando indisponibilidade do servico C++...');
    try {
      execSync('pkill -15 assistant_servi || true');
      // Aguarda 500ms para porta fechar
      await page.waitForTimeout(500);

      await page.fill('input[name="user_query"]', 'Tem quarto deluxe para o próximo final de semana?');
      await Promise.all([
        page.waitForNavigation({ waitUntil: 'networkidle' }),
        page.click('button[name="submitQueryAssistant"]')
      ]);

      const offlineContent = await page.content();
      assert(offlineContent.includes('Serviço de inferência local indisponível'), 'Alerta de contingência (HTTP 503) exibido');
      assert(offlineContent.includes('modo de contingência'), 'Orientação de contingência comunicada ao atendente');

      await page.screenshot({ path: path.join(SCREENSHOTS_DIR, '04_contingencia_offline.png') });
      console.log('  -> Screenshot salvo: screenshots/04_contingencia_offline.png');
    } finally {
      console.log('  -> Restaurando servico C++ em background...');
      const { spawn } = require('child_process');
      const cppDir = path.resolve(__dirname, '../../../../assistant-service-cpp');
      const child = spawn('./assistant_service', [], {
        cwd: cppDir,
        detached: true,
        stdio: 'ignore'
      });
      child.unref();
      await page.waitForTimeout(1000);
      console.log('  -> Servico C++ restaurado com sucesso.');
    }

  } catch (err) {
    console.error(`[ERRO INESPERADO] ${err.message}`);
    await page.screenshot({ path: path.join(SCREENSHOTS_DIR, 'error_state.png') });
    failed++;
  } finally {
    await browser.close();
  }

  console.log('\n=== RESUMO DOS TESTES E2E ===');
  console.log(`Total: ${passed + failed} | Passaram: ${passed} | Falharam: ${failed}`);

  if (failed > 0) {
    process.exit(1);
  }
}

runE2ETests();
