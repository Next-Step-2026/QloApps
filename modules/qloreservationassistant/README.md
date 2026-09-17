# Módulo QloApps: Copiloto de Consulta de Reservas (`qloreservationassistant`)

Módulo administrativo para o QloApps concebido segundo a especificação **RFC-001** (`QLO-FEAT-001`), permitindo que atendentes interpretem semanticamente dúvidas em linguagem natural de hóspedes no back-office com latência ultra-baixa, observabilidade ponta a ponta e degradação graciosa.

---

## 🏗️ Arquitetura do Módulo

O módulo segue rigorosamente as diretrizes e padrões de desenvolvimento do ecossistema QloApps / PrestaShop 1.6:

```text
modules/qloreservationassistant/
├── qloreservationassistant.php          # Classe principal do módulo e registro de Tab no Back-Office
├── controllers/
│   └── admin/
│       └── AdminReservationAssistantController.php # Controller administrativo (cURL, timeout 600ms, RFC 7807)
├── views/
│   └── templates/
│       └── admin/
│           └── assistant_view.tpl       # Template Smarty com cards Bootstrap, badges e histórico de auditoria
└── tests/
    ├── test_module.php                  # Bateria de testes unitários e de integração (17 verificações)
    └── e2e/
        ├── package.json                 # Dependências e script de execução do Playwright
        ├── test_assistant_e2e.js        # Testes E2E simulando login e navegação real no Back-Office
        └── screenshots/                 # Evidências visuais de execução automática
```

---

## ⚙️ Características Técnicas Principais

1. **Comunicação Segura em Loopback:**
   - Comunica-se exclusivamente com o serviço C++ em `http://127.0.0.1:8101/v1/assist/interpret`.
   - Nenhuma informação transita por servidores ou LLMs públicos em nuvem.

2. **Resiliência e Timeout Rígido:**
   - Timeout de conexão e leitura delimitado em **600ms** via cURL.
   - Fallback de degradação graciosa (HTTP 503): se o serviço de inferência estiver indisponível ou ultrapassar 600ms, o painel informa ao atendente sem quebrar a tela ou gerar exceções não tratadas.

3. **Observabilidade e Rastreabilidade Ponta a Ponta:**
   - Gera e propaga o cabeçalho `X-Correlation-ID` em cada requisição (`req-xxxxxxxxxxxx`).
   - Mantém um histórico de auditoria dos últimos 5 atendimentos na sessão do atendente.

4. **Isolamento de Código:**
   - Zero modificações no core do QloApps. Todas as alterações e tabelas residem exclusivamente no módulo.

---

## 🧪 Como Executar os Testes

### 1. Testes Unitários e de Integração PHP
Valida registro do módulo, controller, degradação offline e parsing de resposta:
```bash
php modules/qloreservationassistant/tests/test_module.php
```

### 2. Testes End-to-End (Playwright)
Executa a navegação completa no navegador Chromium (headless), testando os 4 cenários BDD e capturando screenshots:
```bash
node modules/qloreservationassistant/tests/e2e/test_assistant_e2e.js
```
