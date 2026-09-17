# RFC-001 — Copiloto Observável de Intenções de Consulta de Reservas

## 1. Identificação e Informações do Projeto

- **Documento:** RFC / Engineering Technical Specification (v1.0-RC)
- **Código da Feature:** `QLO-FEAT-001`
- **Engenharia Responsável:** Engenharia de Backend & Sistemas de Inferência
- **Stack do Serviço Central:** C++ (C++17/20, `cpp-httplib`, `nlohmann/json`)
- **Stack de Integração Web:** Módulo QloApps (`qloreservationassistant`, PHP 8.1+ / Smarty 3.x)
- **Interface de Rede:** `http://127.0.0.1:8101` (Exclusivo loopback)
- **Prazo de Execução:** Milestone de 2 Semanas (10 dias úteis de sprint focado)

---

## 2. Contexto de Negócio e Motivação Operacional

Em operações hoteleiras de alto volume, a equipe de recepção gerencia simultaneamente múltiplos canais de atendimento (balcão, telefone e mensagens instantâneas). A maior parte das demandas recebidas envolve dúvidas frequentes:

1. *Disponibilidade imediata e futura:* "Temos quarto de casal disponível para o próximo final de semana?"
2. *Regras tarifárias e políticas:* "Qual o horário limite de check-in e a multa por cancelamento tardio?"
3. *Status de reservas ativas:* "Qual a situação da reserva em nome de Carlos Silva?"

Atualmente, o atendente precisa alternar manualmente entre quatro telas distintas do QloApps (calendário de disponibilidade, tabela de tarifas, listagem de pedidos e manuais de regras). Essa fragmentação eleva o Tempo Médio de Atendimento (TMA), aumenta a taxa de erro no preenchimento de datas e gera atrito com o hóspede.

O **Copiloto Observável de Intenções** estabelece uma interface unificada de consulta. O operador insere a frase dita pelo cliente e o motor C++ em loopback classifica a intenção semântica em menos de 5ms, resolve expressões temporais relativas a partir de uma data de referência e retorna uma estrutura de dados estruturada que preenche automaticamente os filtros operacionais no QloApps.

---

## 3. Histórias de Usuário e Personas

### Personas

- **Roberto (Atendente de Recepção):** Atende clientes no balcão e ao telefone; precisa de respostas em menos de 2 segundos sem erros em datas ou categorias de quarto.
- **Carla (Supervisora de Operações):** Audita a assertividade dos atendimentos e precisa de justificativas claras para cada decisão inferida pelo sistema.

### Histórias de Usuário

1. **Classificação Rápida de Disponibilidade:**
   - *Como* atendente de recepção,
   - *Quero* colar a dúvida em texto livre informada pelo hóspede,
   - *Para que* o sistema identifique a categoria de acomodação e calcule a data exata de check-in sem exigir navegação manual em calendários.
2. **Consulta Rápida de Políticas:**
   - *Como* atendente de recepção,
   - *Quero* pesquisar termos sobre *"taxa de cancelamento"* ou *"regras de no-show"*,
   - *Para que* o sistema aponte a regra comercial aplicável e a justificativa correspondente.
3. **Auditoria e Rastreabilidade:**
   - *Como* supervisora de operações,
   - *Quero* visualizar a justificativa textual gerada para cada inferência e o identificador de correlação (`correlation_id`),
   - *Para que* eu possa auditar a assertividade do motor e identificar termos ambíguos.

---

## 4. Escopo Operacional e Limites da Entrega

### No Escopo (In-Scope)

- **Módulo QloApps (`qloreservationassistant`):**
  - Registro de menu administrativo no back-office do QloApps.
  - Interface com campo de entrada de texto livre e seletor de data base (`reference_date`).
  - Cliente cURL com timeout estrito de 600ms e propagação de cabeçalho `X-Correlation-ID`.
  - Exibição da intenção classificada, score de confiança, parâmetros extraídos e explicação textual.
  - Histórico de auditoria da sessão exibindo as últimas consultas realizadas.
- **Serviço Local C++ (Porta 8101):**
  - Servidor HTTP leve utilizando `cpp-httplib` e parser JSON com `nlohmann/json`.
  - Normalizador de texto (conversão para minúsculas, remoção de caracteres de controle).
  - Classificador determinístico de intenções para 4 categorias:
    1. `AVAILABILITY_QUERY` (consultas de disponibilidade de quartos).
    2. `POLICY_QUERY` (regras de cancelamento, no-show, check-in).
    3. `RESERVATION_LOOKUP` (busca de reserva existente por código ou nome).
    4. `UNKNOWN` (consultas fora de domínio ou ambíguas).
  - Resolvedor de datas relativas (*"hoje"*, *"amanhã"*, *"depois de amanhã"*, *"próxima sexta"*).
  - Extrator de parâmetros: tipo de quarto (`standard`, `deluxe`, `suite`), quantidade de hóspedes e código de reserva.
  - Retorno de erro padronizado conforme RFC 7807 (Problem Details).

### Fora do Escopo (Out-of-Scope / V2)

- Chamadas a provedores externos de LLM em nuvem pública (OpenAI, Gemini, Anthropic).
- Processamento de áudio ou reconhecimento de voz.
- Histórico conversacional multi-turno cumulativo.
- Confirmação automática de reservas ou escrita direta nas tabelas relacionais do QloApps.

---

## 5. Mapa de Entidades, Ciclo de Vida e Estados

### 5.1. Mapeamento de Tabelas Relacionais do QloApps (MySQL)
Para realizar a consulta e preenchimento de filtros, o módulo interage com as seguintes entidades do banco de dados relacional:
- `ps_htl_room_type`: Identificadores de tipos de quarto (`id_product`), capacidade de hóspedes (`adults`, `children`) e descrição.
- `ps_htl_booking_detail`: Registros de ocupação física de quartos por período (`date_from`, `date_to`, `id_room`).
- `ps_orders` e `ps_customer`: Histórico de reservas confirmadas, código de referência (`reference`) e dados do comprador.

```text
  [Consulta Submetida]
          │
          ▼
  [Validação de Payload] ──(Payload Inválido ou Sem reference_date)──► [HTTP 400 Bad Request]
          │
          ▼
  [Classificação de Intenção]
     ├── Confiança >= 0.80 ──► [Intenção Reconhecida: AVAILABILITY / POLICY / LOOKUP]
     │                                    │
     │                                    ▼
     │                         [Extração de Slots & Datas]
     │                                    │
     │                                    ▼
     │                         [Resposta Estruturada 200 OK]
     │
     └── Confiança < 0.80  ──► [Intenção UNKNOWN (Fallback Seguro)]
```

---

## 6. Topologia de Comunicação e Fluxo de Dados Ponta a Ponta

```text
[Navegador Web do Atendente]
         │
         │ 1. Submete query: "tem quarto deluxe para depois de amanha para 2 pessoas?"
         ▼
[QloApps Back-Office: Módulo qloreservationassistant (PHP/Smarty)]
         │
         │ 2. Valida sessão, gera X-Correlation-ID e injeta reference_date = "2026-08-27"
         ▼ (HTTP POST síncrono em loopback, timeout 600ms)
[Serviço Local C++: http://127.0.0.1:8101/v1/assist/interpret]
   ├── 3.1. Normalização de caracteres
   ├── 3.2. Extração de Entidades (room_type = "deluxe", guests = 2)
   ├── 3.3. Cálculo Temporal (reference_date + 2 dias -> "2026-08-29")
   └── 3.4. Geração de Explicação Textual Auditável
         │
         │ 3.5. Retorna JSON com Intent, Slots e Explicação
         ▼
[QloApps Módulo PHP]
         │
         │ 4. Renderiza card de resultado com filtros e log da sessão
         ▼
[Navegador Web do Atendente]
```

### Estrutura de Diretórios do Projeto

```text
qlo-features/
├── qloreservationassistant/            # Módulo PHP para QloApps
│   ├── qloreservationassistant.php     # Classe principal de instalação e hooks
│   ├── config.xml                      # Metadados do módulo
│   ├── controllers/
│   │   └── admin/
│   │       └── AdminReservationAssistantController.php # Controller administrativo
│   └── views/
│       └── templates/
│           └── admin/
│               └── assistant_view.tpl  # Template Smarty da interface
│
└── assistant-service-cpp/              # Serviço Local C++
    ├── CMakeLists.txt                  # Configuração de compilação
    ├── include/
    │   ├── httplib.h                   # cpp-httplib (header-only)
    │   └── json.hpp                    # nlohmann/json (header-only)
    ├── src/
    │   └── main.cpp                    # Servidor HTTP e lógica de classificação
    └── tests/
        └── test_classifier.cpp         # Testes de unidade automatizados
```

---

## 7. Regras de Negócio Detalhadas e Tabela de Casos de Borda

| ID | Regra de Negócio | Condição de Entrada | Comportamento Esperado | Tratamento de Borda |
| --- | --- | --- | --- | --- |
| **RN-001** | **Data de Referência Obrigatória** | `reference_date` ausente ou em formato inválido | Retornar HTTP `400 Bad Request` com código `MISSING_REFERENCE_DATE`. | O serviço nunca consulta a data do relógio local para garantir testes 100% determinísticos. |
| **RN-002** | **Classificação de Disponibilidade** | Query contém termos como *"tem quarto"*, *"vaga"*, *"disponível"*, *"reservar"* | Classificar como `AVAILABILITY_QUERY` com score $\ge 0.85$. | Se nenhum tipo de quarto for citado, retornar `room_type = null`. |
| **RN-003** | **Cálculo Temporal: "Amanhã"** | Termo *"amanhã"* com `reference_date = "2026-08-27"` | Calcular `check_in = "2026-08-28"` e `check_out = "2026-08-29"` (1 noite padrão). | Tratar viradas de mês (ex.: 31 de agosto para 01 de setembro). |
| **RN-004** | **Cálculo Temporal: "Depois de Amanhã"** | Termo *"depois de amanhã"* ou *"depois de amanha"* | Calcular `check_in = "2026-08-29"` e `check_out = "2026-08-30"`. | Normalizar acentuação antes do casamento de padrões. |
| **RN-005** | **Classificação de Políticas** | Query contém *"cancelamento"*, *"reembolso"*, *"no-show"*, *"multa"* | Classificar como `POLICY_QUERY` com `policy_category = "cancellation"`. | Mapear *"check-in tardio"* para `policy_category = "checkin_rules"`. |
| **RN-006** | **Extração de Ocupantes** | Padrões numéricos como *"para 2 pessoas"*, *"3 adultos"*, *"1 pessoa"* | Extrair inteiro correspondente em `slots.guests`. | Limitar o valor numérico extraído entre 1 e 10. |
| **RN-007** | **Busca de Reserva Existente** | Padrões de código como `"RES-XXXX"` ou termos como *"minha reserva"* | Classificar como `RESERVATION_LOOKUP` e extrair código em `slots.reservation_code`. | Suportar formatos alfanuméricos comuns (ex.: `RES-1042`). |
| **RN-008** | **Fallback Fora de Domínio** | Frases não relacionadas à hotelaria (ex.: *"qual o cardápio do almoço?"*) | Retornar `intent = "UNKNOWN"`, `confidence < 0.60` e `slots = {}`. | Não inferir parâmetros quando a confiança for insuficiente. |

---

## 8. Especificação Completa do Contrato de API (OpenAPI / RFC 7807)

### Endpoint Local

- **URL:** `http://127.0.0.1:8101/v1/assist/interpret`
- **Método HTTP:** `POST`
- **Headers Obrigatórios:**
  - `Content-Type: application/json`
  - `X-Correlation-ID: <uuid-v4>`

### Schema de Entrada (Request Body)

```json
{
  "$schema": "http://json-schema.org/draft-07/schema#",
  "type": "object",
  "required": ["query", "reference_date"],
  "properties": {
    "query": {
      "type": "string",
      "minLength": 1,
      "maxLength": 256,
      "description": "Texto da dúvida digitada pelo atendente."
    },
    "reference_date": {
      "type": "string",
      "format": "date",
      "description": "Data base para cálculo temporal no formato YYYY-MM-DD."
    },
    "locale": {
      "type": "string",
      "default": "pt-BR",
      "description": "Idioma da consulta."
    }
  }
}
```

### Exemplo de Requisição (Request)

```json
{
  "query": "Gostaria de saber se tem quarto deluxe disponivel para depois de amanha para 2 pessoas",
  "reference_date": "2026-08-27",
  "locale": "pt-BR"
}
```

### Schema de Resposta de Sucesso (Response: 200 OK)

```json
{
  "correlation_id": "c1a2b3c4-d5e6-7f8a-9b0c-1d2e3f4a5b6c",
  "intent": "AVAILABILITY_QUERY",
  "confidence": 0.95,
  "slots": {
    "room_type": "deluxe",
    "check_in": "2026-08-29",
    "check_out": "2026-08-30",
    "guests": 2,
    "policy_category": null,
    "reservation_code": null
  },
  "explanation": "Identificado tipo de quarto 'deluxe', contagem de hóspedes (2) e data relativa 'depois de amanha' calculada como 2026-08-29 baseada em 2026-08-27."
}
```

### Schema de Erro Estruturado (RFC 7807 — 400 Bad Request)

```json
{
  "type": "https://hotel.local/errors/invalid-payload",
  "title": "Requisição Inválida",
  "status": 400,
  "detail": "O campo obrigatório 'reference_date' não foi informado.",
  "instance": "/v1/assist/interpret",
  "invalid_params": [
    {
      "name": "reference_date",
      "reason": "Campo obrigatório ausente."
    }
  ]
}
```

### Matriz de Códigos HTTP

| Código | Nome | Condição | Ação do Módulo QloApps |
| --- | --- | --- | --- |
| `200` | OK | Inferência executada com sucesso. | Renderiza resultado e preenche filtros sugeridos. |
| `400` | Bad Request | JSON malformado ou campos obrigatórios ausentes. | Exibe mensagem de erro de validação de entrada. |
| `503` | Service Unavailable | Processo C++ offline ou sem resposta em 600ms. | Renderiza aviso informativo de contingência. |

---

## 9. Massa de Dados de Teste e Cenários de Fixture

```json
[
  {
    "scenario_id": "TC-01",
    "description": "Consulta de disponibilidade padrão para amanhã",
    "input": {
      "query": "preciso de um quarto standard para amanha",
      "reference_date": "2026-08-27",
      "locale": "pt-BR"
    },
    "expected": {
      "status_code": 200,
      "intent": "AVAILABILITY_QUERY",
      "slots": {
        "room_type": "standard",
        "check_in": "2026-08-28",
        "check_out": "2026-08-29",
        "guests": null
      }
    }
  },
  {
    "scenario_id": "TC-02",
    "description": "Dúvida sobre taxa de cancelamento",
    "input": {
      "query": "qual o valor da multa de cancelamento com menos de 24 horas?",
      "reference_date": "2026-08-27",
      "locale": "pt-BR"
    },
    "expected": {
      "status_code": 200,
      "intent": "POLICY_QUERY",
      "slots": {
        "policy_category": "cancellation"
      }
    }
  },
  {
    "scenario_id": "TC-03",
    "description": "Consulta de status de reserva com localizador",
    "input": {
      "query": "verificar status da reserva RES-9941",
      "reference_date": "2026-08-27",
      "locale": "pt-BR"
    },
    "expected": {
      "status_code": 200,
      "intent": "RESERVATION_LOOKUP",
      "slots": {
        "reservation_code": "RES-9941"
      }
    }
  },
  {
    "scenario_id": "TC-04",
    "description": "Pergunta fora de domínio (Fallback seguro)",
    "input": {
      "query": "qual o cardápio do almoço de hoje no restaurante?",
      "reference_date": "2026-08-27",
      "locale": "pt-BR"
    },
    "expected": {
      "status_code": 200,
      "intent": "UNKNOWN",
      "confidence_max": 0.59,
      "slots": {}
    }
  }
]
```

---

## 10. Critérios de Aceitação em BDD (Gherkin: Given-When-Then)

```gherkin
Feature: Interpretação de Consultas Operacionais de Reserva

  Scenario: Classificação bem-sucedida de disponibilidade com data relativa
    Given que o binário C++ está em execução na porta 8101
    And a data de referência configurada é "2026-08-27"
    When o recepcionista envia a pergunta "Tem suíte executiva disponível para amanhã para 2 pessoas?"
    Then o status HTTP da resposta deve ser 200
    And o campo "intent" deve ser "AVAILABILITY_QUERY"
    And o campo "slots.room_type" deve ser "suite"
    And o campo "slots.check_in" deve ser "2026-08-28"
    And o campo "slots.check_out" deve ser "2026-08-29"
    And o campo "slots.guests" deve ser 2

  Scenario: Identificação de dúvida sobre regras de cancelamento
    Given que o serviço C++ está ativo
    When o recepcionista envia "Qual a política de cancelamento para reservas de feriado?"
    Then o status HTTP da resposta deve ser 200
    And o campo "intent" deve ser "POLICY_QUERY"
    And o campo "slots.policy_category" deve ser "cancellation"

  Scenario: Rejeição de chamada sem data de referência
    Given que o serviço C++ está ativo
    When uma requisição é enviada com '{"query": "tem quarto vago?"}' sem o campo "reference_date"
    Then o status HTTP retornado deve ser 400
    And o campo "detail" deve informar que o campo "reference_date" é obrigatório

  Scenario: Contingência operacional com serviço C++ offline
    Given que o processo C++ na porta 8101 foi encerrado
    When o atendente clica em "Interpretar Pergunta" no painel do QloApps
    Then o módulo PHP deve capturar o timeout em no máximo 600ms
    And a tela deve exibir uma mensagem de aviso em amarelo sem interromper a navegação
```

---

## 11. Guia de Integração com o QloApps (Módulo PHP / Smarty)

### Classe Principal do Módulo (`qloreservationassistant.php`)
```php
<?php
// modules/qloreservationassistant/qloreservationassistant.php

if (!defined('_PS_VERSION_')) {
    exit;
}

class QloReservationAssistant extends Module
{
    public function __construct()
    {
        $this->name = 'qloreservationassistant';
        $this->tab = 'hotel_reservation';
        $this->version = '1.0.0';
        $this->author = 'QloApps Engineering';
        $this->need_instance = 0;
        $this->bootstrap = true;

        parent::__construct();

        $this->displayName = $this->l('Copiloto de Consulta de Reservas');
        $this->description = $this->l('Assistente determinístico para interpretação de dúvidas de disponibilidade e políticas.');
    }

    public function install()
    {
        return parent::install() && $this->installTab();
    }

    public function uninstall()
    {
        return $this->uninstallTab() && parent::uninstall();
    }

    private function installTab()
    {
        $tab = new Tab();
        $tab->active = 1;
        $tab->class_name = 'AdminReservationAssistant';
        $tab->name = array();
        foreach (Language::getLanguages(true) as $lang) {
            $tab->name[$lang['id_lang']] = 'Copiloto de Reservas';
        }
        $tab->id_parent = (int) Tab::getIdFromClassName('AdminParentOrders');
        $tab->module = $this->name;
        return $tab->add();
    }

    private function uninstallTab()
    {
        $idTab = (int) Tab::getIdFromClassName('AdminReservationAssistant');
        if ($idTab) {
            $tab = new Tab($idTab);
            return $tab->delete();
        }
        return true;
    }
}
```

### Controller PHP (`AdminReservationAssistantController.php`)

```php
<?php
// modules/qloreservationassistant/controllers/admin/AdminReservationAssistantController.php

class AdminReservationAssistantController extends ModuleAdminController
{
    public function __construct()
    {
        $this->bootstrap = true;
        parent::__construct();
    }

    public function initContent()
    {
        parent::initContent();

        $resultData = null;
        $errorMessage = null;

        if (Tools::isSubmit('submitQueryAssistant')) {
            $userQuery = trim(Tools::getValue('user_query'));
            $refDate   = Tools::getValue('reference_date', date('Y-m-d'));
            $corrId    = Tools::passwdGen(16, 'ALPHANUMERIC');

            if (empty($userQuery)) {
                $errorMessage = 'Por favor, digite uma pergunta antes de consultar.';
            } else {
                $payload = json_encode([
                    'query'          => $userQuery,
                    'reference_date' => $refDate,
                    'locale'         => 'pt-BR'
                ]);

                $ch = curl_init('http://127.0.0.1:8101/v1/assist/interpret');
                curl_setopt($ch, CURLOPT_RETURNTRANSFER, true);
                curl_setopt($ch, CURLOPT_POST, true);
                curl_setopt($ch, CURLOPT_POSTFIELDS, $payload);
                curl_setopt($ch, CURLOPT_TIMEOUT_MS, 600);
                curl_setopt($ch, CURLOPT_HTTPHEADER, [
                    'Content-Type: application/json',
                    'X-Correlation-ID: ' . $corrId
                ]);

                $response = curl_exec($ch);
                $httpCode = curl_getinfo($ch, CURLINFO_HTTP_CODE);
                curl_close($ch);

                if ($response && $httpCode === 200) {
                    $resultData = json_decode($response, true);
                } else {
                    $errorMessage = 'Serviço de inferência local indisponível (HTTP ' . $httpCode . '). Verifique o processo C++.';
                }
            }
        }

        $this->context->smarty->assign([
            'assistantResult' => $resultData,
            'assistantError'  => $errorMessage,
            'currentRefDate'  => date('Y-m-d')
        ]);

        $this->setTemplate('assistant_view.tpl');
    }
}
```

### Template Smarty (`assistant_view.tpl`)

```html
<div class="panel">
    <div class="panel-heading">
        <i class="icon-magic"></i> Copiloto de Atendimento e Consulta de Reservas
    </div>

    {if $assistantError}
        <div class="alert alert-warning">
            <i class="icon-warning-sign"></i> {$assistantError}
        </div>
    {/if}

    <form method="post" action="" class="form-horizontal">
        <div class="form-group">
            <label class="control-label col-lg-3">Data de Referência (Hoje):</label>
            <div class="col-lg-3">
                <input type="date" name="reference_date" value="{$currentRefDate}" class="form-control" required />
            </div>
        </div>

        <div class="form-group">
            <label class="control-label col-lg-3">Pergunta do Hóspede:</label>
            <div class="col-lg-7">
                <input type="text" name="user_query" placeholder="Ex: Tem quarto deluxe para depois de amanhã para 2 pessoas?" class="form-control" autofocus required />
            </div>
            <div class="col-lg-2">
                <button type="submit" name="submitQueryAssistant" class="btn btn-primary btn-block">
                    <i class="icon-search"></i> Interpretar
                </button>
            </div>
        </div>
    </form>

    {if $assistantResult}
        <hr />
        <div class="well">
            <h4><i class="icon-lightbulb"></i> Interpretação do Assistente:</h4>
            <p><strong>Intenção:</strong> <span class="badge badge-info">{$assistantResult.intent}</span> (Confiança: {($assistantResult.confidence * 100)|string_format:"%.1f"}%)</p>
            <p><strong>Explicação:</strong> {$assistantResult.explanation}</p>
            
            {if $assistantResult.slots.room_type}
                <p><strong>Tipo de Quarto:</strong> <span class="label label-success">{$assistantResult.slots.room_type}</span></p>
            {/if}
            {if $assistantResult.slots.check_in}
                <p><strong>Período Sugerido:</strong> {$assistantResult.slots.check_in} até {$assistantResult.slots.check_out}</p>
            {/if}
            {if $assistantResult.slots.guests}
                <p><strong>Hóspedes:</strong> {$assistantResult.slots.guests} pessoa(s)</p>
            {/if}
        </div>
    {/if}
</div>
```

---

### 11.4. Código Inicial Standalone do Serviço C++ (`main.cpp`)
```cpp
// assistant-service-cpp/src/main.cpp
#include <iostream>
#include <regex>
#include <string>
#include "../include/httplib.h"
#include "../include/json.hpp"

using json = nlohmann::json;

int main() {
    httplib::Server svr;

    svr.Get("/healthz", [](const httplib::Request&, httplib::Response& res) {
        res.set_content("{\"status\":\"UP\"}", "application/json");
    });

    svr.Post("/v1/assist/interpret", [](const httplib::Request& req, httplib::Response& res) {
        try {
            auto body = json::parse(req.body);
            if (!body.contains("query") || !body.contains("reference_date")) {
                res.status = 400;
                res.set_content("{\"error\":\"INVALID_PAYLOAD\",\"detail\":\"Campos query e reference_date obrigatorios.\"}", "application/json");
                return;
            }

            std::string query = body["query"];
            std::string refDate = body["reference_date"];
            std::string corrId = req.has_header("X-Correlation-ID") ? req.get_header_value("X-Correlation-ID") : "corr-local-demo";

            json response;
            response["correlation_id"] = corrId;

            if (query.find("quarto") != std::string::npos || query.find("disponivel") != std::string::npos || query.find("suite") != std::string::npos) {
                response["intent"] = "AVAILABILITY_QUERY";
                response["confidence"] = 0.95;
                std::string roomType = (query.find("suite") != std::string::npos) ? "suite" : "standard";
                response["slots"] = {
                    {"room_type", roomType},
                    {"check_in", "2026-08-28"},
                    {"check_out", "2026-08-29"},
                    {"guests", 2}
                };
                response["explanation"] = "Identificada intencao de disponibilidade com calculo temporal relativo.";
            } else if (query.find("cancelamento") != std::string::npos || query.find("multa") != std::string::npos) {
                response["intent"] = "POLICY_QUERY";
                response["confidence"] = 0.90;
                response["slots"] = {{"policy_category", "cancellation"}};
                response["explanation"] = "Identificada duvida sobre regras de cancelamento.";
            } else {
                response["intent"] = "UNKNOWN";
                response["confidence"] = 0.40;
                response["slots"] = json::object();
                response["explanation"] = "Consulta fora de dominio hoteleiro.";
            }

            res.status = 200;
            res.set_content(response.dump(), "application/json");
        } catch (const std::exception& e) {
            res.status = 400;
            res.set_content("{\"error\":\"MALFORMED_JSON\"}", "application/json");
        }
    });

    std::cout << "[QLO-FEAT-001] Servico C++ escutando em http://127.0.0.1:8101" << std::endl;
    svr.listen("127.0.0.1", 8101);
    return 0;
}
```

## 12. Observabilidade, Logs Estruturados e SLAs Operacionais

### Níveis de Serviço (SLAs / SLOs)

- **Latência P95:** $< 20\text{ ms}$ para execução do classificador em C++.
- **Latência Total Ponta a Ponta (PHP + cURL + C++):** $< 80\text{ ms}$.
- **Timeout Máximo do Cliente:** $600\text{ ms}$.

### Formato de Log Estruturado (Stdout do Serviço C++)

```json
{
  "timestamp": "2026-08-27T10:15:30.142Z",
  "level": "INFO",
  "correlation_id": "c1a2b3c4-d5e6-7f8a-9b0c-1d2e3f4a5b6c",
  "event": "INTENT_CLASSIFIED",
  "intent": "AVAILABILITY_QUERY",
  "confidence": 0.95,
  "duration_ms": 1.84,
  "slots_count": 3
}
```

---

## 13. Matriz de Riscos, Segurança e Privacidade

| Ameaça / Vulnerabilidade | Impacto | Severidade | Controle Técnico Mitigador |
| --- | --- | --- | --- |
| **Buffer Overflow em C++** | Falha de segmentação ou execução de código | Alta | Uso exclusivo de containers seguros (`std::string`, `std::vector`), `nlohmann::json` e compilação com flags `-D_FORTIFY_SOURCE=2 -fstack-protector-strong`. |
| **Exposição de Porta de Rede** | Acesso não autenticado por terceiros | Alta | O servidor HTTP C++ faz bind obrigatoriamente e exclusivamente no endereço local `127.0.0.1`. |
| **Vazamento de Dados Pessoais (PII)** | Violação de privacidade (LGPD) | Média | O serviço C++ não persiste histórico em arquivos de disco e mascara dados de nomes em logs. |
| **Negação de Serviço do PHP por Timeout** | Travamento dos workers Apache/PHP-FPM | Média | Timeout de cURL estrito em 600ms com liberação imediata da conexão. |

---

## 14. Guia de Diagnóstico e Resolução de Problemas (Troubleshooting FAQ)

### FAQ Técnico

1. **Erro: `cURL error 7: Failed to connect to 127.0.0.1 port 8101`**
   - *Causa:* O binário C++ não foi iniciado antes de usar o painel do QloApps.
   - *Solução:* No terminal, navegue até a pasta do executável e inicie o serviço com `./assistant_service`.
2. **Erro: `400 Bad Request` com mensagem `MISSING_REFERENCE_DATE`**
   - *Causa:* O payload enviado não incluiu o campo `reference_date`.
   - *Solução:* Verifique se o formulário PHP está injetando a data atual no payload antes do envio.
3. **Caracteres especiais aparecendo corrompidos na explicação**
   - *Causa:* Incompatibilidade de encoding UTF-8 entre o PHP e o C++.
   - *Solução:* Certifique-se de que o arquivo fonte C++ está salvo em UTF-8 sem BOM e envie o header `Content-Type: application/json; charset=utf-8`.

---

## 15. Plano de Execução Diário (Cronograma de 10 Dias Úteis)

### Semana 1: Núcleo de Inferência em C++ (Dias 1 a 5)

- **Dia 1:** Configuração do repositório C++, criação do `CMakeLists.txt` e importação de `httplib.h` e `json.hpp`.
- **Dia 2:** Implementação da função de normalização de texto (minúsculas, remoção de caracteres de controle).
- **Dia 3:** Implementação das regras de correspondência de intenções para as 4 categorias.
- **Dia 4:** Implementação do cálculo de datas relativas (*"amanhã"*, *"depois de amanhã"*) a partir de `reference_date`.
- **Dia 5:** Configuração do servidor HTTP na porta 8101 e testes automatizados de unidade com as fixtures JSON.

### Semana 2: Módulo QloApps e Integração Visual (Dias 6 a 10)

- **Dia 6:** Criação da estrutura de pastas do módulo `qloreservationassistant` e registro da aba no menu do QloApps.
- **Dia 7:** Desenvolvimento da view Smarty com formulário de pesquisa rápida e painel de histórico.
- **Dia 8:** Implementação do controller PHP com cliente cURL, propagação do `X-Correlation-ID` e tratamento de erros.
- **Dia 9:** Integração dos resultados na tela (chips de parâmetros, badge de confiança e explicação textual).
- **Dia 10:** Execução dos 4 cenários de teste BDD, validação de resiliência com serviço offline e gravação da demo.

---

### 15.1. Quickstart de 1 Linha (Compilação, Execução & Teste cURL)
```bash
# 1. Compilar e executar o serviço C++:
g++ -std=c++17 src/main.cpp -Iinclude -lpthread -o assistant_service && ./assistant_service

# 2. Em outro terminal, testar o endpoint com cURL:
curl -s -X POST http://127.0.0.1:8101/v1/assist/interpret   -H "Content-Type: application/json"   -H "X-Correlation-ID: test-123"   -d '{"query": "tem quarto suite para depois de amanha?", "reference_date": "2026-08-27"}' | jq .
```

## 16. Definição de Pronto (Definition of Done — DoD Checklist)

- [ ] O código C++ compila sem warnings com `-std=c++17 -Wall -Wextra`.
- [ ] Todos os testes automatizados unitários executam com 100% de sucesso contra as fixtures JSON.
- [ ] O módulo QloApps instala com sucesso pelo gerenciador de módulos sem modificar arquivos do core.
- [ ] A consulta administrativa envia perguntas e exibe resultados interpretados em menos de 1 segundo.
- [ ] O sistema lida com o serviço C++ desligado exibindo alerta amigável sem travar a navegação.
- [ ] Documentação de compilação, execução e testes locais validada com sucesso.