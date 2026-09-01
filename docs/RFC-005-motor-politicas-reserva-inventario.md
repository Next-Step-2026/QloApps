# RFC-005 — Motor Determinístico de Políticas de Exceção de Reserva

## 1. Identificação e Informações do Projeto

- **Código da Feature:** `QLO-FEAT-005`
- **Nome da Feature:** Motor Determinístico de Políticas de Exceção, Estadia Mínima e Overbooking
- **Engenharia Responsável:** Engenharia de Políticas, Tarifação & Motores de Regras
- **Stack do Desenvolvedor:** Python 3.10+ (FastAPI / motor determinístico de regras)
- **Stack de Apresentação:** Módulo QloApps (`qloreservationpolicy`, PHP 8.1+ / Smarty 3.x / HTML / CSS)
- **Porta de Execução Local:** `http://127.0.0.1:8105`
- **Prazo de Execução:** Milestone de 2 Semanas (10 dias úteis de sprint focado)

---

## 2. Contexto de Negócio e Motivação Operacional

Em períodos de alta demanda (feriados, grandes eventos, alta temporada), hotéis precisam aplicar regras operacionais rigorosas para maximizar receita e evitar prejuízos: exigência de estadia mínima (*minimum stay*, ex.: 2 noites em finais de semana), antecedência mínima para tarifas promocionais (*advance booking*, ex.: compra com 3 dias de antecedência) e controle rígido do teto de overbooking tolerado (ex.: permitir até 5% acima da capacidade física para absorver cancelamentos e no-shows históricos).

Quando essas regras estão dispersas em múltiplos módulos do QloApps ou são aplicadas manualmente pelo recepcionista, ocorrem erros graves de colisão de reservas e descumprimento de políticas comerciais.

O **Motor de Políticas de Exceção** soluciona essa dor implementando um serviço determinístico puro e isolado que recebe os fatos contextuais de um pedido de reserva (noites, dias de antecedência, ocupação atual vs. capacidade total) e retorna vereditos explicáveis (`ALLOW` ou `DENY` com código de motivo padronizado), garantindo que todas as decisões sejam consistentes e auditáveis.

---

## 3. Histórias de Usuário e Personas

### Personas

- **Leonardo (Gerente de Revenue / Tarifas):** Define as regras de estadia mínima e limites de overbooking e precisa simular o impacto de regras antes de ativá-las para a equipe.
- **Beatriz (Supervisora de Reservas):** Analisa solicitações especiais de clientes e precisa de uma validação rápida do sistema indicando se uma reserva de exceção pode ser aprovada.

### Histórias de Usuário

1. **Validação de Estadia Mínima:**
   - *Como* recepcionista registrando uma reserva para um pacote de Réveillon,
   - *Quero* que o sistema bloqueie tentativas de reserva de apenas 1 noite quando a política exigir mínimo de 3 noites,
   - *Para que* o hotel não perca receita bloqueando quartos para períodos fracionados.
2. **Controle de Teto de Overbooking:**
   - *Como* gerente de reservas,
   - *Quero* permitir overbooking controlado de até 5% da capacidade física do hotel, mas bloquear qualquer tentativa que ultrapasse essa margem,
   - *Para que* o hotel maximize a ocupação sem gerar risco de realocação forçada de hóspedes.
3. **Simulador de Fatos e Políticas:**
   - *Como* supervisor,
   - *Quero* testar um conjunto de dados hipotéticos (fatos de reserva) no painel do QloApps e ver a justificativa detalhada do veredito (`ALLOW` / `DENY`).

---

## 4. Escopo Operacional e Limites da Entrega

### No Escopo (In-Scope para o MVP)

- Módulo QloApps (`qloreservationpolicy`) com tela administrativa ("Simulador e Auditor de Políticas").
- Interface para seleção de política e preenchimento dos fatos contextuais correspondentes.
- Serviço local em Python escutando em `127.0.0.1:8105` com endpoint `POST /v1/policy-evaluations`.
- Implementação de 3 políticas fechadas e determinísticas:
  1. `MINIMUM_STAY`: Valida se `requested_nights >= required_minimum_nights`.
  2. `ADVANCE_BOOKING`: Valida se `days_in_advance >= min_advance_days`.
  3. `OVERBOOKING_LIMIT`: Valida se `(current_occupied + requested_units) <= (total_capacity * (1 + max_overbooking_rate))`.
- Retorno padronizado contendo decisão (`ALLOW` ou `DENY`), código de razão (`reason_code`) e justificativa em linguagem natural.
- Resiliência com timeout de 600ms e tratamento no PHP caso o serviço Python esteja desligado.

### Fora do Escopo (Out-of-Scope para Versão 2)

- Criador visual de DSL ou editor de código de regras dinâmicas em tempo de execução.
- Alteração direta de tabelas do QloApps ou cancelamento automático de reservas existentes.
- Simulação estocástica em lote (Monte Carlo) sobre histórico anual de reservas.

---

## 5. Mapa de Entidades, Ciclo de Vida e Estados

### 5.1. Mapeamento de Tabelas Relacionais do QloApps (MySQL)
O motor de políticas apoia a validação de regras associadas a:
- `ps_htl_order_restrict_date`: Configurações de datas de restrição e antecedência mínima (`min_booking_offset`).
- `ps_htl_room_type`: Quantidade de unidades disponíveis por categoria e capacidade total.

### Diagrama de Estados da Avaliação de Política

```text
  [Fatos de Reserva Submetidos]
                 │
                 ▼
     [Validação de Schema dos Fatos] ──(Campos Ausentes)──► [HTTP 400 Bad Request]
                 │
                 ▼
     [Roteamento da Política Solicitada]
        ├── MINIMUM_STAY      ──► (requested_nights >= min_nights)
        ├── ADVANCE_BOOKING   ──► (days_in_advance >= min_days)
        └── OVERBOOKING_LIMIT ──► (ocupados + pedidos <= capacidade_max)
                 │
                 ├── (Condição Satisfeita) ──► [Decisão: ALLOW (reason_code específico)]
                 │
                 └── (Condição Violada)    ──► [Decisão: DENY (justificativa de recusa)]
```

---

## 6. Topologia de Comunicação e Fluxo de Dados Ponta a Ponta

```text
[Navegador / Gerente de Reservas]
         │
         │ 1. Seleciona política e informa fatos contextuais
         ▼
[QloApps Back-Office: Módulo qloreservationpolicy (PHP/Smarty)]
         │
         │ 2. Valida tipos de dados e despacha JSON
         ▼ (HTTP POST síncrono em loopback, timeout 600ms)
[Serviço Local Python: http://127.0.0.1:8105/v1/policy-evaluations]
   ├── 3.1. Validador de Schema de Fatos (Pydantic)
   ├── 3.2. Roteador de Regras (MinimumStay / AdvanceBooking / OverbookingLimit)
   ├── 3.3. Avaliador Determinístico Puro (Condições Lógicas)
   └── 3.4. Formatador de Justificativa e Código de Motivo
         │
         │ 3.5. Retorna JSON com Decisão (ALLOW/DENY) e Justificativa
         ▼
[QloApps Módulo PHP]
         │
         │ 4. Renderiza badges coloridos e detalhe da regra aplicada
         ▼
[Navegador / Gerente de Reservas]
```

### Estrutura de Diretórios Recomendada

```text
projeto/
├── qloreservationpolicy/              # Módulo PHP para QloApps
│   ├── qloreservationpolicy.php       # Registro do módulo e menus
│   ├── config.xml                     # Metadados
│   ├── controllers/
│   │   └── admin/
│   │       └── AdminReservationPolicyController.php # Controller administrativo
│   └── views/
│       └── templates/
│           └── admin/
│               └── policy_simulator.tpl # View Smarty com formulário e badges
│
└── policy-service-python/             # Serviço Local Python
    ├── requirements.txt               # fastapi, uvicorn, pydantic, pytest
    ├── app/
    │   ├── __init__.py
    │   ├── main.py                    # Servidor FastAPI e roteamento
    │   ├── schemas.py                 # Schemas Pydantic de entrada e saída
    │   └── engine.py                  # Lógica pura de avaliação das 3 políticas
    └── tests/
        └── test_engine.py             # Testes unitários com pytest
```

---

## 7. Regras de Negócio Detalhadas e Tabela de Casos de Borda

| ID | Regra de Negócio | Condição de Entrada | Comportamento Esperado | Caso de Borda / Tratamento |
| --- | --- | --- | --- | --- |
| **RN-001** | **Validação de Estadia Mínima (ALLOW)** | `policy = "MINIMUM_STAY"` e `requested_nights >= required_minimum_nights` | Retornar `decision = "ALLOW"`, `reason_code = "MINIMUM_STAY_MET"`. | `requested_nights` deve ser inteiro positivo $\ge 1$. |
| **RN-002** | **Violação de Estadia Mínima (DENY)** | `policy = "MINIMUM_STAY"` e `requested_nights < required_minimum_nights` | Retornar `decision = "DENY"`, `reason_code = "NIGHTS_BELOW_MINIMUM"`. | Justificativa deve indicar quantas noites faltam para atingir o mínimo. |
| **RN-003** | **Validação de Antecedência Mínima (ALLOW)** | `policy = "ADVANCE_BOOKING"` e `days_in_advance >= min_advance_days` | Retornar `decision = "ALLOW"`, `reason_code = "ADVANCE_WINDOW_MET"`. | `days_in_advance = 0` representa reserva para o mesmo dia (*same-day*). |
| **RN-004** | **Violação de Antecedência (DENY)** | `policy = "ADVANCE_BOOKING"` e `days_in_advance < min_advance_days` | Retornar `decision = "DENY"`, `reason_code = "ADVANCE_WINDOW_VIOLATED"`. | Informar a antecedência mínima exigida pela tarifa. |
| **RN-005** | **Teto de Overbooking Autorizado (ALLOW)** | `policy = "OVERBOOKING_LIMIT"` e $(ocupados + solicitados) \le (capacidade \times (1 + taxa))$ | Retornar `decision = "ALLOW"`, `reason_code = "WITHIN_OVERBOOKING_BUFFER"`. | Arredondar capacidade máxima permitida para baixo (ex.: $50 \times 1.05 = 52.5 \rightarrow 52$). |
| **RN-006** | **Teto de Overbooking Excedido (DENY)** | `policy = "OVERBOOKING_LIMIT"` e $(ocupados + solicitados) > (capacidade \times (1 + taxa))$ | Retornar `decision = "DENY"`, `reason_code = "OVERBOOKING_CAPACITY_EXCEEDED"`. | Bloqueio estrito para evitar colisão física de quartos. |

---

## 8. Especificação Completa do Contrato de API (OpenAPI / RFC 7807)

### Endpoint Local

- **URL:** `http://127.0.0.1:8105/v1/policy-evaluations`
- **Método:** `POST`
- **Headers Obrigatórios:**
  - `Content-Type: application/json`
  - `X-Correlation-ID: <uuid-v4>`

### Schema de Entrada (Request Body)

```json
{
  "$schema": "http://json-schema.org/draft-07/schema#",
  "type": "object",
  "required": ["policy", "facts"],
  "properties": {
    "policy": {
      "type": "string",
      "enum": ["MINIMUM_STAY", "ADVANCE_BOOKING", "OVERBOOKING_LIMIT"]
    },
    "facts": {
      "type": "object",
      "description": "Dicionário de fatos contextuais específicos para a política selecionada."
    }
  }
}
```

### Exemplo 1: Avaliação de Estadia Mínima Reprovada (Request / Response)

**Request:**

```json
{
  "policy": "MINIMUM_STAY",
  "facts": {
    "requested_nights": 1,
    "required_minimum_nights": 2,
    "room_type": "deluxe"
  }
}
```

**Response (200 OK):**

```json
{
  "correlation_id": "b2c3d4e5-f6a7-8b9c-0d1e-2f3a4b5c6d7e",
  "policy": "MINIMUM_STAY",
  "decision": "DENY",
  "reason_code": "NIGHTS_BELOW_MINIMUM",
  "explanation": "Estadia de 1 noite solicitada é inferior ao mínimo obrigatório de 2 noites para a categoria deluxe."
}
```

### Exemplo 2: Avaliação de Overbooking Autorizado (Request / Response)

**Request:**

```json
{
  "policy": "OVERBOOKING_LIMIT",
  "facts": {
    "total_capacity": 50,
    "current_occupied": 51,
    "requested_units": 1,
    "max_overbooking_rate": 0.05
  }
}
```

**Response (200 OK):**

```json
{
  "correlation_id": "b2c3d4e5-f6a7-8b9c-0d1e-2f3a4b5c6d7e",
  "policy": "OVERBOOKING_LIMIT",
  "decision": "ALLOW",
  "reason_code": "WITHIN_OVERBOOKING_BUFFER",
  "explanation": "Ocupação resultante (52/50 = 104%) dentro do limite máximo permitido de 105% (52 vagas)."
}
```

### Schema de Erro Estruturado (RFC 7807 — 400 Bad Request)

```json
{
  "type": "https://hotel.local/errors/invalid-policy-facts",
  "title": "Fatos de Política Inválidos",
  "status": 400,
  "detail": "Campos obrigatórios ausentes para a política MINIMUM_STAY: 'requested_nights'.",
  "instance": "/v1/policy-evaluations"
}
```

### Matriz de Códigos HTTP

| Código | Nome | Condição | Ação do QloApps |
| --- | --- | --- | --- |
| `200` | OK | Avaliação executada com decisão retornada. | Renderiza selo visual (Permitido / Negado) e explicação. |
| `400` | Bad Request | Política desconhecida ou fatos ausentes. | Exibe mensagem de erro de validação. |
| `503` | Service Unavailable | Serviço Python offline na porta 8105. | Renderiza aviso de contingência no painel. |

---

## 9. Massa de Dados de Teste e Cenários de Fixture

```json
[
  {
    "description": "Estadia mínima atendida (3 noites quando exige 2)",
    "input": {
      "policy": "MINIMUM_STAY",
      "facts": {
        "requested_nights": 3,
        "required_minimum_nights": 2,
        "room_type": "standard"
      }
    },
    "expected_output": {
      "decision": "ALLOW",
      "reason_code": "MINIMUM_STAY_MET"
    }
  },
  {
    "description": "Violação de antecedência mínima (mesmo dia quando exige 3 dias)",
    "input": {
      "policy": "ADVANCE_BOOKING",
      "facts": {
        "days_in_advance": 0,
        "min_advance_days": 3
      }
    },
    "expected_output": {
      "decision": "DENY",
      "reason_code": "ADVANCE_WINDOW_VIOLATED"
    }
  },
  {
    "description": "Excesso de capacidade de overbooking (53 vagas solicitadas com teto de 52)",
    "input": {
      "policy": "OVERBOOKING_LIMIT",
      "facts": {
        "total_capacity": 50,
        "current_occupied": 52,
        "requested_units": 1,
        "max_overbooking_rate": 0.05
      }
    },
    "expected_output": {
      "decision": "DENY",
      "reason_code": "OVERBOOKING_CAPACITY_EXCEEDED"
    }
  }
]
```

---

## 10. Critérios de Aceitação em BDD (Gherkin: Given-When-Then)

```gherkin
Feature: Avaliação Determinística de Políticas de Exceção de Reserva

  Scenario: Solicitação com número de noites suficiente é autorizada
    Given que o motor de políticas Python está ativo em "http://127.0.0.1:8105"
    When uma requisição de política "MINIMUM_STAY" é enviada com 3 noites solicitadas e 2 obrigatórias
    Then o status HTTP da resposta deve ser 200
    And o campo "decision" deve ser "ALLOW"
    And o campo "reason_code" deve ser "MINIMUM_STAY_MET"

  Scenario: Solicitação de reserva sem antecedência mínima é bloqueada
    Given que o motor de políticas está ativo
    When uma requisição "ADVANCE_BOOKING" é enviada com 0 dias de antecedência e mínimo de 3 dias
    Then o status HTTP deve ser 200
    And o campo "decision" deve ser "DENY"
    And o campo "reason_code" deve ser "ADVANCE_WINDOW_VIOLATED"

  Scenario: Overbooking dentro da margem permitida de 5% é aprovado
    Given que o hotel possui capacidade 50 e 51 quartos ocupados
    When chega um pedido para 1 quarto na política "OVERBOOKING_LIMIT" com taxa máxima de 0.05
    Then o status HTTP deve ser 200
    And o campo "decision" deve ser "ALLOW"
    And o campo "reason_code" deve ser "WITHIN_OVERBOOKING_BUFFER"

  Scenario: Queda do motor de políticas no ambiente local
    Given que o serviço Python na porta 8105 está finalizado
    When o gerente aciona uma simulação no painel do QloApps
    Then o módulo PHP deve capturar o timeout em no máximo 600ms
    And a tela deve exibir aviso informativo mantendo as regras nativas de segurança
```

---

## 11. Guia de Integração com o QloApps (Módulo PHP / Smarty)

### Classe Principal do Módulo (`qloreservationpolicy.php`)
```php
<?php
// modules/qloreservationpolicy/qloreservationpolicy.php

if (!defined('_PS_VERSION_')) {
    exit;
}

class QloReservationPolicy extends Module
{
    public function __construct()
    {
        $this->name = 'qloreservationpolicy';
        $this->tab = 'hotel_reservation';
        $this->version = '1.0.0';
        $this->author = 'QloApps Engineering';
        $this->need_instance = 0;
        $this->bootstrap = true;

        parent::__construct();

        $this->displayName = $this->l('Motor de Políticas de Reserva');
        $this->description = $this->l('Validador determinístico de regras de estadia mínima, antecedência e overbooking.');
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
        $tab->class_name = 'AdminReservationPolicy';
        $tab->name = array();
        foreach (Language::getLanguages(true) as $lang) {
            $tab->name[$lang['id_lang']] = 'Políticas de Reserva';
        }
        $tab->id_parent = (int) Tab::getIdFromClassName('AdminParentOrders');
        $tab->module = $this->name;
        return $tab->add();
    }

    private function uninstallTab()
    {
        $idTab = (int) Tab::getIdFromClassName('AdminReservationPolicy');
        if ($idTab) {
            $tab = new Tab($idTab);
            return $tab->delete();
        }
        return true;
    }
}
```

### Controller PHP (`AdminReservationPolicyController.php`)

```php
<?php
// modules/qloreservationpolicy/controllers/admin/AdminReservationPolicyController.php

class AdminReservationPolicyController extends ModuleAdminController
{
    public function __construct()
    {
        $this->bootstrap = true;
        parent::__construct();
    }

    public function initContent()
    {
        parent::initContent();

        $evalData     = null;
        $errorMessage = null;

        if (Tools::isSubmit('submitPolicySimulation')) {
            $policyType = Tools::getValue('policy_type');
            $corrId     = Tools::passwdGen(16, 'ALPHANUMERIC');

            $facts = [];
            if ($policyType === 'MINIMUM_STAY') {
                $facts = [
                    'requested_nights'        => (int) Tools::getValue('requested_nights'),
                    'required_minimum_nights' => (int) Tools::getValue('required_minimum_nights', 2),
                    'room_type'               => Tools::getValue('room_type', 'standard')
                ];
            } elseif ($policyType === 'ADVANCE_BOOKING') {
                $facts = [
                    'days_in_advance'  => (int) Tools::getValue('days_in_advance'),
                    'min_advance_days' => (int) Tools::getValue('min_advance_days', 3)
                ];
            } elseif ($policyType === 'OVERBOOKING_LIMIT') {
                $facts = [
                    'total_capacity'       => (int) Tools::getValue('total_capacity', 50),
                    'current_occupied'     => (int) Tools::getValue('current_occupied'),
                    'requested_units'      => (int) Tools::getValue('requested_units', 1),
                    'max_overbooking_rate' => (float) Tools::getValue('max_overbooking_rate', 0.05)
                ];
            }

            $payload = json_encode([
                'policy' => $policyType,
                'facts'  => $facts
            ]);

            $ch = curl_init('http://127.0.0.1:8105/v1/policy-evaluations');
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
                $evalData = json_decode($response, true);
            } else {
                $errorMessage = 'Serviço de políticas local offline (HTTP ' . $httpCode . ').';
            }
        }

        $this->context->smarty->assign([
            'policyEvaluation' => $evalData,
            'policyError'      => $errorMessage
        ]);

        $this->setTemplate('policy_simulator.tpl');
    }
}
```

### Template Smarty (`policy_simulator.tpl`)

```html
<div class="panel">
    <div class="panel-heading">
        <i class="icon-legal"></i> Simulador & Auditor de Políticas de Reserva
    </div>

    {if $policyError}
        <div class="alert alert-warning">
            <i class="icon-warning-sign"></i> {$policyError}
        </div>
    {/if}

    <form method="post" action="" class="form-horizontal">
        <div class="form-group">
            <label class="control-label col-lg-3">Política a Avaliar:</label>
            <div class="col-lg-4">
                <select name="policy_type" class="form-control" onchange="togglePolicyFields(this.value)">
                    <option value="MINIMUM_STAY">Estadia Mínima (Minimum Stay)</option>
                    <option value="ADVANCE_BOOKING">Antecedência Mínima (Advance Booking)</option>
                    <option value="OVERBOOKING_LIMIT">Limite de Overbooking Autorizado</option>
                </select>
            </div>
        </div>

        <div id="fields_min_stay" class="form-group">
            <label class="control-label col-lg-3">Noites Solicitadas / Mínimo:</label>
            <div class="col-lg-2">
                <input type="number" name="requested_nights" value="1" min="1" class="form-control" placeholder="Solicitadas" />
            </div>
            <div class="col-lg-2">
                <input type="number" name="required_minimum_nights" value="2" min="1" class="form-control" placeholder="Mínimo Exigido" />
            </div>
        </div>

        <div class="form-group">
            <div class="col-lg-offset-3 col-lg-4">
                <button type="submit" name="submitPolicySimulation" class="btn btn-primary btn-block">
                    <i class="icon-check"></i> Avaliar Regra
                </button>
            </div>
        </div>
    </form>

    {if $policyEvaluation}
        <hr />
        <div class="well">
            <h4><i class="icon-certificate"></i> Resultado da Avaliação:</h4>
            <p><strong>Veredito:</strong> 
                {if $policyEvaluation.decision == 'ALLOW'}
                    <span class="label label-success">AUTORIZADO (ALLOW)</span>
                {else}
                    <span class="label label-danger">NEGADO (DENY)</span>
                {/if}
            </p>
            <p><strong>Código de Motivo:</strong> <code>{$policyEvaluation.reason_code}</code></p>
            <p><strong>Justificativa:</strong> {$policyEvaluation.explanation}</p>
        </div>
    {/if}
</div>
```

---

### 11.4. Código Inicial Standalone do Serviço Python (`main.py`)
```python
# policy-service-python/app/main.py
from fastapi import FastAPI, HTTPException, Header
from pydantic import BaseModel, Field
from typing import Dict, Any, Optional
import uvicorn

app = FastAPI(title="Reservation Policy Engine")

class PolicyEvaluationRequest(BaseModel):
    policy: str = Field(..., description="Nome da política")
    facts: Dict[str, Any]

@app.get("/healthz")
def health():
    return {"status": "UP"}

@app.post("/v1/policy-evaluations")
def evaluate_policy(req: PolicyEvaluationRequest, x_correlation_id: Optional[str] = Header(default="corr-demo")):
    policy = req.policy
    facts = req.facts
    
    if policy == "MINIMUM_STAY":
        req_nights = int(facts.get("requested_nights", 1))
        min_nights = int(facts.get("required_minimum_nights", 2))
        allowed = (req_nights >= min_nights)
        return {
            "correlation_id": x_correlation_id,
            "policy": policy,
            "decision": "ALLOW" if allowed else "DENY",
            "reason_code": "MINIMUM_STAY_MET" if allowed else "NIGHTS_BELOW_MINIMUM",
            "explanation": f"Estadia de {req_nights} noite(s) " + ("atende" if allowed else "e inferior") + f" ao minimo exigido de {min_nights} noite(s)."
        }
    elif policy == "ADVANCE_BOOKING":
        days_adv = int(facts.get("days_in_advance", 0))
        min_days = int(facts.get("min_advance_days", 3))
        allowed = (days_adv >= min_days)
        return {
            "correlation_id": x_correlation_id,
            "policy": policy,
            "decision": "ALLOW" if allowed else "DENY",
            "reason_code": "ADVANCE_WINDOW_MET" if allowed else "ADVANCE_WINDOW_VIOLATED",
            "explanation": f"Antecedencia de {days_adv} dia(s) " + ("atende" if allowed else "e insuficiente frente") + f" ao requisito de {min_days} dia(s)."
        }
    elif policy == "OVERBOOKING_LIMIT":
        cap = int(facts.get("total_capacity", 50))
        occ = int(facts.get("current_occupied", 0))
        req_u = int(facts.get("requested_units", 1))
        rate = float(facts.get("max_overbooking_rate", 0.05))
        max_allowed = int(cap * (1.0 + rate))
        allowed = ((occ + req_u) <= max_allowed)
        return {
            "correlation_id": x_correlation_id,
            "policy": policy,
            "decision": "ALLOW" if allowed else "DENY",
            "reason_code": "WITHIN_OVERBOOKING_BUFFER" if allowed else "OVERBOOKING_CAPACITY_EXCEEDED",
            "explanation": f"Ocupacao resultante ({occ + req_u}/{cap}) " + ("dentro do teto" if allowed else "excede o teto maximo") + f" permitido de {max_allowed} vagas."
        }
    else:
        raise HTTPException(status_code=400, detail=f"Politica desconhecida: {policy}")

if __name__ == "__main__":
    uvicorn.run(app, host="127.0.0.1", port=8105)
```

## 12. Observabilidade, Logs Estruturados e SLAs Operacionais

### Níveis de Serviço (SLAs / SLOs)

- **Latência P95:** $< 5\text{ ms}$ para avaliação da regra lógica em Python.
- **Latência Total Ponta a Ponta (PHP + cURL + Python):** $< 50\text{ ms}$.
- **Timeout Máximo do Cliente:** $600\text{ ms}$.

### Formato de Log Estruturado (Stdout do Serviço Python)

```json
{
  "timestamp": "2026-08-27T10:35:45.891Z",
  "level": "INFO",
  "correlation_id": "b2c3d4e5-f6a7-8b9c-0d1e-2f3a4b5c6d7e",
  "event": "POLICY_EVALUATED",
  "policy": "MINIMUM_STAY",
  "decision": "DENY",
  "reason_code": "NIGHTS_BELOW_MINIMUM",
  "duration_ms": 0.84
}
```

---

## 13. Matriz de Riscos, Segurança e Privacidade

| Ameaça Identificada | Impacto | Nível | Controle Técnico Aplicado |
| --- | --- | --- | --- |
| **Injeção de Tipos Negativos em Fatos** | Comportamento imprevisível | Médio | Validador estrito Pydantic rejeita números negativos (`conint(ge=0)`). |
| **Inconsistência por Estado Global** | Decisões não reproduzíveis | Crítico | Motor de regras implementado como funções puras sem variáveis mutáveis globais. |
| **Exposição de Porta em Interface Aberta** | Acesso não autenticado | Alto | Servidor FastAPI/Uvicorn configurado estritamente em `127.0.0.1:8105`. |

---

## 14. Guia de Diagnóstico e Resolução de Problemas (Troubleshooting FAQ)

### FAQ Técnico

1. **Erro: `cURL error 7: Failed to connect to 127.0.0.1 port 8105`**
   - *Causa:* O servidor Python FastAPI não foi iniciado.
   - *Solução:* Execute `uvicorn app.main:app --host 127.0.0.1 --port 8105`.
2. **Erro: `400 Bad Request` com mensagem `Fatos de Política Inválidos`**
   - *Causa:* Um campo obrigatório para a política selecionada foi omitido no payload.
   - *Solução:* Verifique se todos os campos da política estão preenchidos antes de submeter.
3. **Decisão Allow retornada incorretamente para estadia curta**
   - *Causa:* Inversão da comparação condicional (`>` vs `>=`).
   - *Solução:* Certifique-se de que a regra avalia `requested_nights >= required_minimum_nights`.

---

## 15. Plano de Execução Diário (Cronograma de 10 Dias Úteis)

### Semana 1: Serviço Python e Regras de Políticas (10h)

- **Dia 1 (2h):** Setup do ambiente virtual Python, dependências (`fastapi`, `pydantic`, `pytest`).
- **Dia 2 (2h):** Modelagem dos schemas Pydantic de entrada (`PolicyEvaluationRequest`) e saída (`PolicyEvaluationResponse`).
- **Dia 3 (2h):** Implementação das regras puras para `MinimumStay` e `AdvanceBooking`.
- **Dia 4 (2h):** Implementação do cálculo de limite de `OverbookingLimit` com arredondamento seguro.
- **Dia 5 (2h):** Criação da rota HTTP na porta 8105 e bateria de testes unitários com pytest cobrindo as fixtures.

### Semana 2: Módulo QloApps e Painel de Simulação (10h)

- **Dia 6 (2h):** Scaffolding do módulo `qloreservationpolicy` e registro do menu administrativo no QloApps.
- **Dia 7 (2h):** Desenvolvimento da view Smarty com formulário de simulação e seleção de políticas.
- **Dia 8 (2h):** Implementação do cliente cURL no PHP com timeout de 600ms e tratamento de erros 400/503.
- **Dia 9 (2h):** Renderização visual dos vereditos (badges verde/vermelho, código de razão e explicação).
- **Dia 10 (2h):** Validação dos 4 cenários BDD, teste de contingência (serviço offline) e gravação da demo.

---

### 15.1. Quickstart de 1 Linha (Execução & Teste cURL)
```bash
# 1. Iniciar o motor de políticas Python:
uvicorn app.main:app --host 127.0.0.1 --port 8105

# 2. Em outro terminal, simular uma política de estadia mínima via cURL:
curl -s -X POST http://127.0.0.1:8105/v1/policy-evaluations   -H "Content-Type: application/json"   -H "X-Correlation-ID: test-policy-01"   -d '{"policy": "MINIMUM_STAY", "facts": {"requested_nights": 1, "required_minimum_nights": 2, "room_type": "deluxe"}}' | jq .
```

## 16. Definição de Pronto (Definition of Done — DoD Checklist)

- [ ] Serviço Python inicia e responde em `http://127.0.0.1:8105/healthz`.
- [ ] Testes unitários do pytest cobrem as 3 políticas com 100% de aprovação.
- [ ] Módulo QloApps instala sem conflitos e adiciona o painel de simulação no back-office.
- [ ] Simulação de políticas exibe corretamente os vereditos Allow e Deny com suas justificativas.
- [ ] QloApps exibe mensagem de contingência adequada caso o serviço Python esteja desligado.
- [ ] Documentação de execução e fixtures testadas com sucesso.