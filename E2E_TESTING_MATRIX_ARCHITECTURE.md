# Matriz de Arquitetura e Viabilidade de Testes E2E com Playwright

> **Documento de Referência Técnica:** Estratégia de Testes de Ponta a Ponta (End-to-End) para Módulos RFC do QloApps (`qlocontacthealth`, `qloarrivalsharing`, `qloactionablesearch`, `qloexternalrequests`).

---

## 1. Visão Geral e Filosofia de Testes

O QloApps integra múltiplos microserviços desenvolvidos em Java, Kotlin e C++ com a camada de apresentação legada em PHP 8.1+ e Smarty 3.x. Para garantir a qualidade operacional, integridade visual e consistência de dados da plataforma, adotamos o **Playwright** com TypeScript para testes de ponta a ponta (E2E).

### Princípios Fundamentais:
1. **Isolamento Modular:** Cada módulo RFC possui sua própria suíte E2E em `modules/<modulename>/tests/e2e/`, contendo suas dependências (`package.json`), configurações (`playwright.config.ts`) e especificações de teste (`specs/`).
2. **Determinismo e Mocks Controlados:** Testes interagem com a UI real do Back-Office e Front-Office do QloApps, simulando coordenadas de GPS via Playwright Geolocation API ou interceptando endpoints HTTP de backend quando necessário.
3. **Resiliência e Observabilidade:** Validação de comportamentos com timeouts estritos (SLA de 600ms para comunicação com microserviços locais).

---

## 2. Matriz de Módulos e Mapeamento de Arquitetura

| Módulo PHP | RFC | Microserviço Backend | Porta Local | Contexto UI | Status E2E | Principais Fluxos E2E |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| `qlocontacthealth` | RFC-003 | `health-service` (Java/Kotlin) | `8103` | Back-Office (`AdminContactHealth`) | **Implementado** | Visualização da ficha do cliente, card de saúde cadastral, atualização de consentimento LGPD e disparo de desafio de reconfirmação. |
| `qloarrivalsharing` | RFC-004 | `location-service-kotlin` (Kotlin) | `8104` | Front-Office & Back-Office (`AdminArrivalSharing`) | **Viável (Fase 2)** | Captura de GPS pontual no FO ("Estou Chegando"), mock de Geolocation, transição de estados (`ENTERED`/`outside`) e atualização no painel de recepção do BO. |
| `qloactionablesearch` | RFC-005/008 | `search-service-cpp` (C++) | `8105`/`8108` | Back-Office (`AdminActionableSearch`) | **Viável (Fase 3)** | Pesquisa rápida de entidades no catálogo (quartos, comodidades), validação de parsing léxico e exibição de alerta em caso de indisponibilidade do microserviço. |
| `qloexternalrequests` | RFC-006 | `converter-service-kotlin` (Kotlin) | `8106` | Back-Office (`AdminExternalRequests`) | **Viável (Fase 4)** | Importação de payloads JSON brutos dos provedores `PROVIDER_A` e `PROVIDER_B`, conversão para o modelo canônico e validação de regras de datas (`check_out > check_in`). |

---

## 3. Padronização da Estrutura de Diretórios E2E

Para todos os módulos, a estrutura de diretórios deve seguir o padrão já estabelecido em `qlocontacthealth`:

```text
modules/<modulename>/tests/e2e/
├── package.json               # Dependências do Playwright e scripts de execução
├── playwright.config.ts       # Configuração do Playwright (baseURL, slowMo, timeouts)
├── specs/                     # Casos de teste automatizados em TypeScript
│   └── <feature-name>.spec.ts
└── utils/                     # Helpers reutilizáveis (login, auth, seletores)
    └── auth.ts
```

---

## 4. Especificação dos Cenários e Desenho dos Testes por Módulo

### 4.1. Módulo `qlocontacthealth` (RFC-003) — *Saúde de Contatos*
- **Localização:** `modules/qlocontacthealth/tests/e2e/`
- **Status:** **Implementado & Operacional**
- **Cenários Cobertos:**
  1. Login no Back-Office do QloApps com credenciais administrativas.
  2. Navegação via menu `Clientes -> Saúde de Contatos`.
  3. Seleção de cliente e abertura da ficha do hóspede.
  4. Validação do painel *"Indicador de Saúde e Higiene Cadastral"*.
  5. Acionamento do botão *"Disparar Desafio de Reconfirmação"* e tratamento do alerta de confirmação.

---

### 4.2. Módulo `qloarrivalsharing` (RFC-004) — *Geofencing e Traslado*
- **Localização:** `modules/qloarrivalsharing/tests/e2e/`
- **Status:** **Planejado (Próxima Implementação)**
- **Estratégia de Teste E2E:**
  - Utilização das APIs nativas do Playwright para concessão de permissão de geolocalização:
    `browserContext.grantPermissions(['geolocation'], { origin: baseURL })`
  - Simulação de coordenadas GPS dentro do raio da geofence ($< 200\text{m}$) e fora do raio ($> 200\text{m}$):
    `page.setGeolocation({ latitude: -23.5505, longitude: -46.6333 })`
- **Cenários Cobertos:**
  1. **Front-Office (Hóspede):** Acesso à página de rastreio da reserva, clique no botão *"Estou Chegando"*, envio de coordenadas simuladas e confirmação visual de envio.
  2. **Back-Office (Recepção):** Acesso a `AdminArrivalSharing`, verificação da mudança de estado para `ENTERED` e atualização do badge de proximidade do hóspede em tempo real.

#### Draft da Especificação Playwright (`arrival-geofence.spec.ts`):
```typescript
import { test, expect } from '@playwright/test';

test.describe('QloArrivalSharing E2E Geofencing Flow', () => {
  test.use({
    geolocation: { latitude: -23.55052, longitude: -46.63330 }, // Coordenadas nas imediações do hotel
    permissions: ['geolocation'],
  });

  test('Hóspede aciona "Estou Chegando" e Painel da Recepção atualiza status para ENTERED', async ({ page, context }) => {
    // 1. Simular envio de localização pelo Front-Office
    await page.goto('/module/qloarrivalsharing/guestarrival?id_booking=101');
    const btnEstouChegando = page.locator('button#btn-send-location');
    await expect(btnEstouChegando).toBeVisible();
    await btnEstouChegando.click();
    await expect(page.locator('.alert-success')).toContainText('Localização enviada com sucesso');

    // 2. Verificar atualização no Back-Office (Painel da Recepção)
    const adminPage = await context.newPage();
    await adminPage.goto('/admin/index.php?controller=AdminArrivalSharing');
    const statusBadge = adminPage.locator('tr[data-booking="101"] .badge-status');
    await expect(statusBadge).toContainText('ENTERED');
  });
});
```

---

### 4.3. Módulo `qloactionablesearch` (RFC-005/008) — *Busca Léxica de Entidades*
- **Localização:** `modules/qloactionablesearch/tests/e2e/`
- **Status:** **Planejado**
- **Estratégia de Teste E2E:**
  - Testar o formulário de busca no Back-Office (`AdminActionableSearch`).
  - Validar a submissão de consultas completas ("Suíte Master Vista Mar") e parciais ("vista mar").
  - Testar o comportamento resiliente quando o microserviço C++ (porta `8108`) está inacessível (exibição da mensagem de erro amigável com timeout de 600ms).
- **Cenários Cobertos:**
  1. Submissão de termos de busca válidos e renderização dos resultados retornados pelo motor C++.
  2. Validação da mensagem de erro para pesquisas vazias.
  3. Simulação de indisponibilidade do microserviço C++ via mock HTTP (`page.route`) e asserção da mensagem de erro de timeout.

#### Draft da Especificação Playwright (`actionable-search.spec.ts`):
```typescript
import { test, expect } from '@playwright/test';

test.describe('QloActionableSearch E2E Flow', () => {
  test('Pesquisa de entidade no catálogo exibe resultados corretamente', async ({ page }) => {
    await page.goto('/admin/index.php?controller=AdminActionableSearch');
    await page.fill('input[name="search_query"]', 'Suíte');
    await page.click('button[name="submitSearchQuery"]');

    await expect(page.locator('.search-results-table')).toBeVisible();
    await expect(page.locator('.search-results-table')).toContainText('Suíte Master Vista Mar');
  });

  test('Exibe mensagem de erro apropriada quando o microserviço C++ está offline', async ({ page }) => {
    // Intercepta e simula falha/timeout do microsserviço
    await page.route('**/v1/search/parse', route => route.abort());

    await page.goto('/admin/index.php?controller=AdminActionableSearch');
    await page.fill('input[name="search_query"]', 'Quarto Standard');
    await page.click('button[name="submitSearchQuery"]');

    await expect(page.locator('.alert-danger')).toContainText('C++ search engine is offline or currently unavailable');
  });
});
```

---

### 4.4. Módulo `qloexternalrequests` (RFC-006) — *Conversor Canônico de Solicitações*
- **Localização:** `modules/qloexternalrequests/tests/e2e/`
- **Status:** **Planejado**
- **Estratégia de Teste E2E:**
  - Interagir com a interface de importação no Back-Office (`AdminExternalRequests`).
  - Testar envio de payload bruto do `PROVIDER_A` (`arrival` + `nights`).
  - Testar envio de payload bruto do `PROVIDER_B` (`checkin_date` + `checkout_date`).
  - Testar validação de regras de inconsistência de datas (`check_out <= check_in`).
- **Cenários Cobertos:**
  1. Seleção do `PROVIDER_A`, colagem de JSON válido e geração do rascunho canônico.
  2. Seleção do `PROVIDER_B`, colagem de JSON válido e cálculo automático do número de diárias (`nights`).
  3. Submissão de JSON com data de check-out anterior ao check-in e verificação da exibição dos erros detalhados.

#### Draft da Especificação Playwright (`canonical-converter.spec.ts`):
```typescript
import { test, expect } from '@playwright/test';

test.describe('QloExternalRequests Canonical Converter E2E Flow', () => {
  test('Converte solicitação do PROVIDER_A com sucesso', async ({ page }) => {
    await page.goto('/admin/index.php?controller=AdminExternalRequests');
    await page.selectOption('select[name="provider_type"]', 'PROVIDER_A');
    
    const payloadProviderA = JSON.stringify({
      arrival: '2026-10-01',
      nights: 3,
      guest_name: 'Gabriel Sampaio'
    });
    
    await page.fill('textarea[name="raw_json"]', payloadProviderA);
    await page.click('button[name="submitConvertRequest"]');

    await expect(page.locator('.canonical-draft-panel')).toBeVisible();
    await expect(page.locator('.canonical-checkin')).toContainText('2026-10-01');
    await expect(page.locator('.canonical-checkout')).toContainText('2026-10-04');
  });

  test('Valida erro de data em que check-out é anterior ao check-in', async ({ page }) => {
    await page.goto('/admin/index.php?controller=AdminExternalRequests');
    await page.selectOption('select[name="provider_type"]', 'PROVIDER_B');

    const payloadInvalido = JSON.stringify({
      checkin_date: '2026-10-10',
      checkout_date: '2026-10-05',
      guest_name: 'Cliente Teste'
    });

    await page.fill('textarea[name="raw_json"]', payloadInvalido);
    await page.click('button[name="submitConvertRequest"]');

    await expect(page.locator('.alert-danger')).toContainText('Data de check-out deve ser posterior à data de check-in');
  });
});
```

---

## 5. Estratégias de Execution & Pipeline CI/CD

### Execução de Testes Localmente

Para rodar os testes E2E de qualquer módulo individualmente:

```bash
# Navegar até o diretório E2E do módulo desejado
cd modules/qlocontacthealth/tests/e2e

# Instalar dependências (caso seja a primeira execução)
npm install

# Executar testes em modo headless (padrão)
npx playwright test

# Executar testes com interface gráfica e desacelerado (slowMo)
SLOWMO=1000 npx playwright test --headed
```

### Configuração de Variáveis de Ambiente

As suítes E2E utilizam as seguintes variáveis de ambiente configuráveis:

- `BASE_URL`: URL base do ambiente QloApps (padrão: `http://localhost:8080`).
- `ADMIN_EMAIL`: E-mail de acesso ao Back-Office (carregado via `.env` ou variável de ambiente).
- `ADMIN_PASSWD`: Senha de acesso ao Back-Office (carregada via `.env` ou variável de ambiente).
- `SLOWMO`: Tempo em milissegundos para desacelerar ações nos testes visuais (padrão: `1000`).


---

## 6. Plano de Ação para Implementação das Suítes Restantes

1. **Fase 1 (`qlocontacthealth`):** Concluída.
2. **Fase 2 (`qloarrivalsharing`):** Estruturar diretório `modules/qloarrivalsharing/tests/e2e`, configurar `playwright.config.ts` e implementar `arrival-geofence.spec.ts`.
3. **Fase 3 (`qloactionablesearch`):** Criar suíte E2E em `modules/qloactionablesearch/tests/e2e` para validação de busca de entidades e tratamento de resiliência.
4. **Fase 4 (`qloexternalrequests`):** Criar suíte E2E em `modules/qloexternalrequests/tests/e2e` para os adaptadores `PROVIDER_A` e `PROVIDER_B`.
