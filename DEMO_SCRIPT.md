# 🎙️ Roteiro de Demonstração e Apresentação da Entrega
## RFC-001: Copiloto Observável de Intenções de Consulta de Reservas (`QLO-FEAT-001`)

**Data da Apresentação:** Sexta-feira  
**Branch Principal:** `develop`  
**Responsável Técnico:** Gabriel Rodrigues

---

## 🎯 1. Visão Geral e Arquitetura da Solução

### 1.1 O Desafio
Atendentes de recepção e canais de reserva do QloApps recebem frequentemente mensagens informais de hóspedes contendo termos coloquiais e datas relativas (ex: *"tem quarto deluxe para depois de amanhã para 2 pessoas?"*).  
O objetivo da **RFC-001** é dotar o back-office de um copiloto capaz de:
1. Classificar a intenção da mensagem com confiança quantificada.
2. Extrair entidades estruturadas (tipo de quarto, período de check-in/out calculado, número de hóspedes e categorias de política).
3. Entregar resposta com **latência ultra-baixa (< 20ms)** rodando 100% on-premises em loopback local (`127.0.0.1:8101`), sem risco de vazamento de dados para nuvens externas.
4. Fornecer **observabilidade completa** (`X-Correlation-ID` rastreável) e **degradação graciosa (HTTP 503)** se o serviço de inferência falhar.

### 1.2 A Arquitetura Implementada
```text
┌────────────────────────────────────────────────────────────┐
│                    QloApps Back-Office                     │
│               (PHP 8 / PrestaShop Admin Tab)               │
│                                                            │
│   AdminReservationAssistantController                      │
│   ├── Timeout estrito de 600ms via cURL                    │
│   ├── Injeção de cabeçalho X-Correlation-ID                │
│   └── Histórico auditável de sessão na interface Smarty    │
└──────────────────────────┬─────────────────────────────────┘
                           │ HTTP POST /v1/assist/interpret
                           │ Loopback 127.0.0.1:8101
┌──────────────────────────▼─────────────────────────────────┐
│              C++17 Assistant Core Service                  │
│       (Clean Architecture / Domain-Driven Design)          │
│                                                            │
│   ├── presentation:  HTTP Server (cpp-httplib)             │
│   ├── validation:    Request & Header RFC 7807 Validator  │
│   ├── domain:        Semantic Classifier & Slot Extractor  │
│   └── infrastructure:Text Normalizer & Relative Date Engine│
└────────────────────────────────────────────────────────────┘
```

---

## 📊 2. Matriz de Ferramentas de Testes e Evidências

A feature foi homologada em todos os níveis da pirâmide de testes através das ferramentas oficiais exigidas:

| Nível de Teste | Ferramenta | Escopo do Teste | Resultado Obtido |
| :--- | :--- | :--- | :--- |
| **Carga & SLA** | `k6` | 10 a 20 VUs simultâneos, 1.660+ requisições | **P95 de 4.7ms** (SLA exigido < 20ms) \| **0.00% erros** |
| **Contrato** | `Schemathesis` | Fuzzing contra contrato OpenAPI 3.1.0 | **77/77 testes aprovados** (100% conformidade) |
| **E2E Back-Office** | `Playwright` | Jornada do atendente no navegador Chromium | **16/16 verificações aprovadas**, com screenshots |
| **Unitário (C++)** | `C++ / g++` | Classificação de intenções e lógica de datas | **4/4 testes unitários + 25 testes de API** |
| **Unitário (PHP)** | `PHP CLI` | Controller, template Smarty e contingência | **17/17 testes aprovados** |

---

## 🎬 3. Roteiro Passo a Passo para Apresentação ao Vivo

### Pré-requisitos para a Demonstração:
1. Certificar-se de que o QloApps está rodando na porta 8080:
   ```bash
   # Acesso Back-Office: http://localhost:8080/admin338bwc0sf/
   # Login: gabrielrs@gxmail.com / Senha: 1a2b3c4d5e
   ```
2. Iniciar o serviço C++ em background:
   ```bash
   cd assistant-service-cpp && ./assistant_service &
   ```

---

### 🔹 Cenário 1: Consulta de Disponibilidade com Data Relativa
**Objetivo:** Demonstrar interpretação de linguagem natural, cálculo de data relativa e extração de slots.

* **Fala do Apresentador:**  
  *"Vejam como o atendente recebe uma mensagem coloquial de um hóspede no WhatsApp ou chat e a cola diretamente no painel do Copiloto no Back-Office."*
* **Ação no Painel:**  
  1. No menu lateral, clique em **Orders -> Copiloto de Reservas**.
  2. Defina a Data de Referência: `2026-08-27`.
  3. No campo *Pergunta do Hóspede*, digite:  
     `Tem quarto deluxe para 2 adultos depois de amanhã?`
  4. Clique no botão **Interpretar**.
* **O que destacar na tela:**
  - Badge verde: **AVAILABILITY_QUERY** (Confiança: 95.0%).
  - Caixa de Slots:
    - Tipo de Quarto: `deluxe`
    - Período Sugerido: `2026-08-29` até `2026-08-30` (o motor somou +2 dias sobre 27/08/2026 automaticamente).
    - Hóspedes: `2 pessoa(s)`.
  - Cabeçalho de Rastreabilidade exibindo o `X-Correlation-ID` único gerado.
* **Evidência Visual Capturada:**  
  `modules/qloreservationassistant/tests/e2e/screenshots/01_disponibilidade_deluxe.png`

---

### 🔹 Cenário 2: Consulta de Políticas do Hotel
**Objetivo:** Demonstrar discernimento entre regras contratuais e intenções de reserva.

* **Fala do Apresentador:**  
  *"Agora o hóspede tem uma dúvida sobre taxas e cancelamento. O assistente não deve tentar agendar um quarto, mas sim classificar como política."*
* **Ação no Painel:**  
  1. No campo *Pergunta do Hóspede*, digite:  
     `Qual a politica de cancelamento de reservas e regras de no-show?`
  2. Clique em **Interpretar**.
* **O que destacar na tela:**
  - Badge azul: **POLICY_QUERY** (Confiança: 90.0%).
  - Categoria de Política: `cancellation`.
  - Explicação semântica contextual gerada na tela.
* **Evidência Visual Capturada:**  
  `modules/qloreservationassistant/tests/e2e/screenshots/02_politica_cancelamento.png`

---

### 🔹 Cenário 3: Trilha de Auditoria e Observabilidade
**Objetivo:** Mostrar conformidade com governança e auditoria operacional.

* **Fala do Apresentador:**  
  *"Toda consulta efetuada fica gravada no histórico da sessão com timestamp, intenção inferida, score de confiança e Correlation ID, permitindo cruzar qualquer atendimento com logs de rede."*
* **O que destacar na tela:**
  - Tabela inferior: **Histórico de Auditoria da Sessão (Últimas 5 Consultas)**.
  - As duas consultas anteriores aparecem tabuladas com seus respectivos `req-...`.
  - Presença do botão *Limpar Histórico* caso o atendente queira resetar a sessão.
* **Evidência Visual Capturada:**  
  `modules/qloreservationassistant/tests/e2e/screenshots/03_trilha_auditoria.png`

---

### 🔹 Cenário 4: Alta Disponibilidade e Modo de Contingência (Serviço Offline)
**Objetivo:** Demonstrar resiliência e degradação graciosa sob falha sem crash na UI.

* **Fala do Apresentador:**  
  *"O que acontece se o serviço C++ for reiniciado ou estiver indisponível? A aplicação QloApps NUNCA trava e não exibe erros brutos de PHP na tela do atendente."*
* **Ação no Terminal e Painel:**  
  1. No terminal, encerre temporariamente o serviço C++:
     ```bash
     pkill -15 assistant_servi
     ```
  2. No painel web, envie qualquer pergunta: `Tem quarto disponível para o final de semana?` e clique em **Interpretar**.
* **O que destacar na tela:**
  - Alerta amarelo suave:  
    `Serviço de inferência local indisponível (HTTP 503). O Copiloto está operando em modo de contingência.`
  - A interface permanece 100% estável e preserva o histórico anterior sem gerar tela branca ou timeouts demorados (resposta imediata em < 50ms).
* **Evidência Visual Capturada:**  
  `modules/qloreservationassistant/tests/e2e/screenshots/04_contingencia_offline.png`

---

## 🛠️ 4. Comandos Rápidos para Reproduzir as Evidências

Caso a banca examinadora solicite rodar os testes durante a apresentação:

```bash
# 1. Testes Unitários e Integração C++ (4 unit + 25 API)
make -C assistant-service-cpp test

# 2. Testes de Carga com k6 (SLA P95 < 20ms)
./assistant-service-cpp/tests/run_load_test.sh

# 3. Testes de Contrato OpenAPI 3.1 com Schemathesis
./assistant-service-cpp/tests/run_contract_tests.sh

# 4. Testes Unitários do Módulo PHP (17 testes)
php modules/qloreservationassistant/tests/test_module.php

# 5. Testes E2E com Playwright no Navegador
node modules/qloreservationassistant/tests/e2e/test_assistant_e2e.js
```
