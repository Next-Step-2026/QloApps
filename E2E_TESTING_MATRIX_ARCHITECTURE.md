# Matriz de Arquitetura e Viabilidade de Testes E2E com Playwright

> **Documento de Referência Técnica:** Estratégia de Testes de Ponta a Ponta (End-to-End) para Módulos RFC do QloApps (`qlocontacthealth`, `qloarrivalsharing`, `qloexternalrequests`).

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
| `qloarrivalsharing` | RFC-004 | `location-service-kotlin` (Kotlin) | `8104` | Front-Office & Back-Office (`AdminArrivalSharing`) | **Implementado** | Captura de GPS pontual no FO ("Estou Chegando"), mock de Geolocation, transição de estados (`ENTERED`/`outside`) e atualização no painel de recepção do BO. |
| `qloexternalrequests` | RFC-006 | `converter-service-kotlin` (Kotlin) | `8106` | Back-Office (`AdminExternalRequests`) | **Implementado** | Importação de payloads JSON brutos dos provedores `PROVIDER_A` e `PROVIDER_B`, conversão para o modelo canônico e validação de regras de datas (`check_out > check_in`). |

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
- **Status:** **Implementado & Operacional**
- **Estratégia de Teste E2E:**
  - Utilização das APIs nativas do Playwright para concessão de permissão de geolocalização:
    `browserContext.grantPermissions(['geolocation'], { origin: baseURL })`
  - Simulação de coordenadas GPS dentro do raio da geofence ($< 200\text{m}$) e fora do raio ($> 200\text{m}$):
    `page.setGeolocation({ latitude: -8.052240, longitude: -34.885650 })`
- **Cenários Cobertos:**
  1. **Back-Office (Recepção):** Acesso a `AdminArrivalSharing`, validação dos indicadores de chegadas previstas, total de hóspedes e geofencing.
  2. **Simulação de Geolocalização:** Definição de coordenadas no raio do hotel e verificação de carregamento limpo do painel.

---

### 4.3. Módulo `qloexternalrequests` (RFC-006) — *Conversor Canônico de Solicitações*
- **Localização:** `modules/qloexternalrequests/tests/e2e/`
- **Status:** **Implementado & Operacional**
- **Estratégia de Teste E2E:**
  - Interagir com a interface de importação no Back-Office (`AdminExternalRequests`).
  - Testar envio de payload bruto do `PROVIDER_A` (`arrival` + `nights`).
  - Testar envio de payload bruto do `PROVIDER_B` (`checkin_date` + `checkout_date`).
  - Testar limpeza do payload.
- **Cenários Cobertos:**
  1. Seleção do `PROVIDER_A`, preenchimento automático de amostragem e verificação da estrutura de payload.
  2. Seleção do `PROVIDER_B`, preenchimento automático de amostragem e limpeza do formulário.

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

## 6. Status de Execução das Suítes E2E

1. **RFC-003 (`qlocontacthealth`):** Concluído (1/1 testes passando).
2. **RFC-004 (`qloarrivalsharing`):** Concluído (2/2 testes passando).
3. **RFC-006 (`qloexternalrequests`):** Concluído (2/2 testes passando).
