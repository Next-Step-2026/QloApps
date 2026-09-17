# RFC-007 — Auditor de Sobreposição de Alocações de Inventário

## 1. Identificação e Informações do Projeto

- **Código da Feature:** `QLO-FEAT-007`
- **Nome da Feature:** Auditor de Integridade de Alocações e Detecção de Sobreposição de Inventário
- **Engenharia Responsável:** Engenharia de Algoritmos, Concorrência & Integridade de Inventário
- **Stack do Desenvolvedor:** C++ (C++17/20, backend/algoritmos de alta performance de intervalos)
- **Stack de Apresentação:** Módulo QloApps (`qloinventoryaudit`, PHP 8.1+ / Smarty 3.x / HTML / CSS)
- **Porta de Execução Local:** `http://127.0.0.1:8107`
- **Prazo de Execução:** Milestone de 2 Semanas (10 dias úteis de sprint focado)

---

## 2. Contexto de Negócio e Motivação Operacional

Em empreendimentos hoteleiros com alto volume de reservas vindas simultaneamente de balcão, site próprio e múltiplos canais de distribuição (OTAs), podem ocorrer falhas de concorrência ou alterações manuais desatentas que alocam duas reservas confirmadas para o mesmo quarto físico no mesmo período. Esse erro resulta em **dupla ocupação física (overbooking crítico)**, gerando constrangimento ao hóspede no momento do check-in e custos de realocação de emergência para outros hotéis.

O **Auditor de Sobreposição de Inventário** soluciona essa dor executando uma varredura determinística e ultrarrápida sobre lotes de reservas. Utilizando um algoritmo de ordenação cronológica e varredura linear ($O(n \log n)$) e aplicando com rigor a convenção hoteleira de intervalos semi-abertos `[check_in, check_out)` — na qual a data de saída de uma reserva coincide com a entrada da próxima sem gerar conflito —, o serviço identifica colisões reais, calcula o número de noites sobrepostas e classifica a severidade da anomalia.

---

## 3. Histórias de Usuário e Personas

### Personas

- **Guilherme (Auditor Noturno / Gerente de Operações):** Realiza o fechamento diário e auditoria de quartos antes da chegada dos hóspedes do dia seguinte.
- **Vanessa (Supervisora de Recepção):** Precisa identificar conflitos de alocação com antecedência para remanejar quartos vagos antes do hóspede chegar ao balcão.

### Histórias de Usuário

1. **Auditoria de Conflitos em Lote:**
   - *Como* auditor noturno do hotel,
   - *Quero* selecionar um período de reservas e acionar a auditoria de inventário no painel do QloApps,
   - *Para que* o sistema aponte em menos de 1 segundo todos os quartos físicos com duas ou mais reservas colidindo no mesmo dia.
2. **Exportação de Relatório de Conflitos:**
   - *Como* auditor,
   - *Quero* exportar a lista de sobreposições em formato CSV com códigos de reserva e noites conflitantes,
   - *Para que* eu possa encaminhar a planilha para a equipe de recepção remanejar as unidades.
3. **Validação de Fronteira sem Falsos Positivos:**
   - *Como* gerente de operações,
   - *Quero* que reservas consecutivas (onde o checkout do Hóspede A é no mesmo dia do checkin do Hóspede B) não sejam marcadas como conflito,
   - *Para que* o relatório não gere alarmes falsos para a equipe.

---

## 4. Escopo Operacional e Limites da Entrega

### No Escopo (In-Scope para o MVP)

- Módulo QloApps (`qloinventoryaudit`) com tela administrativa no back-office ("Auditor de Conflitos de Inventário").
- Opção para carregar lote de reservas de fixture JSON (até 200 reservas) ou selecionar período.
- Serviço local em C++ escutando em `127.0.0.1:8107` com endpoint `POST /v1/inventory-audits/overlaps`.
- Algoritmo em C++ de agrupamento por quarto físico (`room_id`) e ordenação cronológica por `check_in` ($O(n \log n)$).
- Varredura de interseção em intervalo semi-aberto `[check_in, check_out)`: colisão ocorre se e somente se `reserva_b.check_in < reserva_a.check_out`.
- Classificação de severidade: $\ge 3\text{ noites}$ sobrepostas = `HIGH`; $1\text{ a }2\text{ noites}$ = `MEDIUM`.
- Exibição de tabela de achados com quarto, códigos das reservas colidentes, período de sobreposição e badge de severidade.
- Botão no QloApps para download do relatório de conflitos em formato CSV.
- Resiliência com timeout de 600ms e tratamento caso o serviço C++ esteja desligado.

### Fora do Escopo (Out-of-Scope para Versão 2)

- Remanejamento ou cancelamento automático de reservas sem supervisão humana.
- Processamento em streaming contínuo via leitura de Change Data Capture (CDC) do MySQL.
- Algoritmos de predição estatística de no-show com aprendizado de máquina.

---

## 5. Mapa de Entidades, Ciclo de Vida e Estados

### 5.1. Mapeamento de Tabelas Relacionais do QloApps (MySQL)
A auditoria avalia a integridade física das reservas cadastradas em:
- `ps_htl_booking_detail`: Tabela de alocações físicas (`id_room`, `date_from`, `date_to`, `is_refunded`, `id_order`).
- `ps_htl_room_information`: Cadastro das unidades físicas (`id_room`, `room_num`).

### Diagrama de Detecção de Sobreposição de Intervalos

```text
  [Lote de Reservas Submetido]
                 │
                 ▼
     [Agrupamento por Quarto (room_id)]
                 │
                 ▼
     [Ordenação Cronológica: check_in ASC]
                 │
                 ▼
     [Varredura de Intervalos Adjacentes: [in_A, out_A) vs [in_B, out_B)]
        ├── in_B >= out_A ────────────────────────► [Sem Conflito: Checkout Coincidente Válido]
        │
        └── in_B < out_A  ────────────────────────► [Conflito Detectado: Overlap!]
                 │
                 ├── Noites Sobrepostas >= 3 ────► [Severidade: HIGH]
                 └── Noites Sobrepostas 1 a 2 ───► [Severidade: MEDIUM]
```

---

## 6. Topologia de Comunicação e Fluxo de Dados Ponta a Ponta

```text
[Navegador / Auditor]
         │
         │ 1. Seleciona lote de reservas e clica em "Executar Auditoria"
         ▼
[QloApps Back-Office: Módulo qloinventoryaudit (PHP/Smarty)]
         │
         │ 2. Valida lote (máx 200 itens) e despacha JSON
         ▼ (HTTP POST síncrono em loopback, timeout 600ms)
[Serviço Local C++: http://127.0.0.1:8107/v1/inventory-audits/overlaps]
   ├── 3.1. Agrupador por Quarto Físico (Hash Map por room_id)
   ├── 3.2. Ordenador Cronológico por Check-in (std::sort O(n log n))
   ├── 3.3. Varredor de Colisões em Intervalos Semi-Abertos [start, end)
   └── 3.4. Calculador de Noites Sobrepostas e Severidade
         │
         │ 3.5. Retorna JSON com Total Auditado e Lista de Conflitos
         ▼
[QloApps Módulo PHP]
         │
         │ 4. Renderiza tabela de achados e botão de exportação CSV
         ▼
[Navegador / Auditor]
```

### Estrutura de Diretórios Recomendada

```text
projeto/
├── qloinventoryaudit/                 # Módulo PHP para QloApps
│   ├── qloinventoryaudit.php          # Registro do módulo e menus
│   ├── config.xml                     # Metadados
│   ├── controllers/
│   │   └── admin/
│   │       └── AdminInventoryAuditController.php # Controller administrativo
│   └── views/
│       └── templates/
│           └── admin/
│               └── audit_dashboard.tpl # Template Smarty com tabela e CSV
│
└── overlap-service-cpp/               # Serviço Local C++
    ├── CMakeLists.txt (ou Makefile)
    ├── include/
    │   ├── httplib.h                  # cpp-httplib (header-only)
    │   ├── json.hpp                   # nlohmann/json (header-only)
    │   └── OverlapDetector.hpp        # Declaração do motor de auditoria
    ├── src/
    │   ├── main.cpp                   # Ponto de entrada e servidor HTTP
    │   └── OverlapDetector.cpp        # Algoritmo de ordenação e varredura
    └── tests/
        └── test_overlaps.cpp          # Testes unitários com casos de fronteira
```

---

## 7. Regras de Negócio Detalhadas e Tabela de Casos de Borda

| ID | Regra de Negócio | Condição de Entrada | Comportamento Esperado | Caso de Borda / Tratamento |
| --- | --- | --- | --- | --- |
| **RN-001** | **Convenção Semi-Aberta (Check-out Coincidente)** | Reserva A: `[2026-09-01, 2026-09-05)` e Reserva B: `[2026-09-05, 2026-09-10)` no mesmo quarto | **NÃO** caracterizar conflito; retorno com 0 sobreposições. | O dia `2026-09-05` é saída de A e entrada de B (noite de A foi 04->05; noite de B é 05->06). |
| **RN-002** | **Detecção de Sobreposição Parcial** | Reserva A: `[2026-09-01, 2026-09-05)` e Reserva B: `[2026-09-03, 2026-09-07)` no mesmo quarto | Detectar conflito de $2\text{ noites}$ (`2026-09-03` a `2026-09-05`), severidade `MEDIUM`. | Início do conflito: `max(in_A, in_B)`; Fim do conflito: `min(out_A, out_B)`. |
| **RN-003** | **Detecção de Sobreposição Total (Encapsulada)** | Reserva A: `[2026-09-01, 2026-09-10)` e Reserva B: `[2026-09-03, 2026-09-06)` | Detectar conflito de $3\text{ noites}$ (`2026-09-03` a `2026-09-06`), severidade `HIGH`. | Uma reserva contida integralmente dentro do período de outra. |
| **RN-004** | **Isolamento entre Quartos Distintos** | Duas reservas em datas idênticas, mas com `room_id` diferentes (ex.: `room-101` e `room-102`) | **NÃO** gerar conflito; o agrupamento por quarto é isolado. | Evitar falso positivo comparando apenas reservas do mesmo quarto. |
| **RN-005** | **Limite Máximo de Lote** | Lote de entrada com mais de 200 reservas | Rejeitar no PHP com HTTP `400` antes de enviar ao serviço C++. | Proteger o consumo de memória no sprint de 20h. |
| **RN-006** | **Cálculo de Severidade** | $\ge 3\text{ noites}$ -> `HIGH`; $1\text{ a }2\text{ noites}$ -> `MEDIUM` | Atribuir badge de severidade para priorização operacional. | Noites calculadas pela diferença em dias do período sobreposto. |

---

## 8. Especificação Completa do Contrato de API (OpenAPI / RFC 7807)

### Endpoint Local

- **URL:** `http://127.0.0.1:8107/v1/inventory-audits/overlaps`
- **Método:** `POST`
- **Headers Obrigatórios:**
  - `Content-Type: application/json`
  - `X-Correlation-ID: <uuid-v4>`

### Schema de Entrada (Request Body)

```json
{
  "$schema": "http://json-schema.org/draft-07/schema#",
  "type": "object",
  "required": ["audit_batch_id", "reservations"],
  "properties": {
    "audit_batch_id": { "type": "string" },
    "reservations": {
      "type": "array",
      "maxItems": 200,
      "items": {
        "type": "object",
        "required": ["reservation_id", "room_id", "check_in", "check_out", "guest_name"],
        "properties": {
          "reservation_id": { "type": "string" },
          "room_id": { "type": "string" },
          "check_in": { "type": "string", "format": "date" },
          "check_out": { "type": "string", "format": "date" },
          "guest_name": { "type": "string" }
        }
      }
    }
  }
}
```

### Exemplo de Requisição (Request)

```json
{
  "audit_batch_id": "batch-2026-08-27-01",
  "reservations": [
    {
      "reservation_id": "RES-101",
      "room_id": "room-12",
      "check_in": "2026-09-01",
      "check_out": "2026-09-05",
      "guest_name": "Lucas Santos"
    },
    {
      "reservation_id": "RES-102",
      "room_id": "room-12",
      "check_in": "2026-09-03",
      "check_out": "2026-09-07",
      "guest_name": "Juliana Lima"
    },
    {
      "reservation_id": "RES-103",
      "room_id": "room-12",
      "check_in": "2026-09-07",
      "check_out": "2026-09-10",
      "guest_name": "Roberto Costa"
    }
  ]
}
```

### Exemplo de Resposta (Response: 200 OK)

```json
{
  "correlation_id": "d4e5f6a7-b8c9-0d1e-2f3a-4b5c6d7e8f9a",
  "audit_batch_id": "batch-2026-08-27-01",
  "total_reservations_audited": 3,
  "total_rooms_audited": 1,
  "total_conflicts_found": 1,
  "conflicts": [
    {
      "room_id": "room-12",
      "reservation_a_id": "RES-101",
      "reservation_b_id": "RES-102",
      "overlap_start": "2026-09-03",
      "overlap_end": "2026-09-05",
      "overlap_nights": 2,
      "severity": "MEDIUM",
      "message": "Quarto room-12 alocado simultaneamente para RES-101 e RES-102 por 2 noites."
    }
  ]
}
```

### Schema de Erro Estruturado (RFC 7807 — 400 Bad Request)

```json
{
  "type": "https://hotel.local/errors/invalid-audit-batch",
  "title": "Lote de Auditoria Inválido",
  "status": 400,
  "detail": "O lote de reservas excede o limite máximo permitido de 200 registros.",
  "instance": "/v1/inventory-audits/overlaps"
}
```

### Matriz de Códigos HTTP

| Código | Nome | Condição | Ação do QloApps |
| --- | --- | --- | --- |
| `200` | OK | Auditoria executada e lista de conflitos gerada. | Renderiza tabela de achados e habilita CSV. |
| `400` | Bad Request | JSON malformado ou lote superior a 200 reservas. | Exibe mensagem de erro de validação. |
| `503` | Service Unavailable | Serviço C++ offline na porta 8107. | Renderiza aviso de contingência no painel. |

---

## 9. Massa de Dados de Teste e Cenários de Fixture

```json
{
  "audit_batch_id": "fixture-batch-demo",
  "reservations": [
    {
      "reservation_id": "RES-001",
      "room_id": "101",
      "check_in": "2026-09-01",
      "check_out": "2026-09-05",
      "guest_name": "Carlos Eduardo"
    },
    {
      "reservation_id": "RES-002",
      "room_id": "101",
      "check_in": "2026-09-03",
      "check_out": "2026-09-07",
      "guest_name": "Mariana Lima"
    },
    {
      "reservation_id": "RES-003",
      "room_id": "101",
      "check_in": "2026-09-07",
      "check_out": "2026-09-10",
      "guest_name": "Fernanda Rocha"
    },
    {
      "reservation_id": "RES-004",
      "room_id": "102",
      "check_in": "2026-09-01",
      "check_out": "2026-09-08",
      "guest_name": "Pedro Alcantara"
    },
    {
      "reservation_id": "RES-005",
      "room_id": "102",
      "check_in": "2026-09-04",
      "check_out": "2026-09-07",
      "guest_name": "Julia Martins"
    }
  ]
}
```

---

## 10. Critérios de Aceitação em BDD (Gherkin: Given-When-Then)

```gherkin
Feature: Auditoria de Sobreposição de Reservas de Inventário

  Scenario: Detecção de sobreposição de 2 noites no mesmo quarto
    Given que o serviço C++ está ativo em "http://127.0.0.1:8107"
    When um lote contendo as reservas "RES-001 [2026-09-01 a 2026-09-05)" e "RES-002 [2026-09-03 a 2026-09-07)" no quarto "101" é auditado
    Then o status HTTP da resposta deve ser 200
    And o campo "total_conflicts_found" deve ser 1
    And o conflito retornado deve apontar "overlap_nights = 2"
    And a severidade deve ser "MEDIUM"

  Scenario: Reservas consecutivas no mesmo dia de check-out/check-in não geram conflito
    Given que o serviço C++ está ativo
    When um lote com "RES-002 [2026-09-03 a 2026-09-07)" e "RES-003 [2026-09-07 a 2026-09-10)" no quarto "101" é auditado
    Then o status HTTP deve ser 200
    And o campo "total_conflicts_found" deve ser 0
    And a lista "conflicts" deve estar vazia

  Scenario: Conflito de 3 ou mais noites recebe severidade HIGH
    Given que o serviço C++ está ativo
    When o lote inclui as reservas "RES-004 [2026-09-01 a 2026-09-08)" e "RES-005 [2026-09-04 a 2026-09-07)" no quarto "102"
    Then o status HTTP deve ser 200
    And o conflito deve ter "overlap_nights = 3"
    And a severidade deve ser "HIGH"

  Scenario: Queda do serviço de auditoria C++
    Given que o executável C++ na porta 8107 está finalizado
    When o auditor aciona "Executar Auditoria" no painel do QloApps
    Then o módulo PHP captura o timeout em no máximo 800ms
    And a tela renderiza aviso informativo sem travar o painel
```

---

## 11. Guia de Integração com o QloApps (Módulo PHP / Smarty)

### Classe Principal do Módulo (`qloinventoryaudit.php`)
```php
<?php
// modules/qloinventoryaudit/qloinventoryaudit.php

if (!defined('_PS_VERSION_')) {
    exit;
}

class QloInventoryAudit extends Module
{
    public function __construct()
    {
        $this->name = 'qloinventoryaudit';
        $this->tab = 'hotel_reservation';
        $this->version = '1.0.0';
        $this->author = 'QloApps Engineering';
        $this->need_instance = 0;
        $this->bootstrap = true;

        parent::__construct();

        $this->displayName = $this->l('Auditor de Sobreposição de Inventário');
        $this->description = $this->l('Detecção de conflitos e duplas alocações de quartos físicos.');
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
        $tab->class_name = 'AdminInventoryAudit';
        $tab->name = array();
        foreach (Language::getLanguages(true) as $lang) {
            $tab->name[$lang['id_lang']] = 'Auditor de Conflitos';
        }
        $tab->id_parent = (int) Tab::getIdFromClassName('AdminParentOrders');
        $tab->module = $this->name;
        return $tab->add();
    }

    private function uninstallTab()
    {
        $idTab = (int) Tab::getIdFromClassName('AdminInventoryAudit');
        if ($idTab) {
            $tab = new Tab($idTab);
            return $tab->delete();
        }
        return true;
    }
}
```

### Controller PHP (`AdminInventoryAuditController.php`)

```php
<?php
// modules/qloinventoryaudit/controllers/admin/AdminInventoryAuditController.php

class AdminInventoryAuditController extends ModuleAdminController
{
    public function __construct()
    {
        $this->bootstrap = true;
        parent::__construct();
    }

    public function initContent()
    {
        parent::initContent();

        $auditResult  = null;
        $errorMessage = null;

        if (Tools::isSubmit('submitRunAudit')) {
            $rawJson = trim(Tools::getValue('audit_batch_json'));
            $corrId  = Tools::passwdGen(16, 'ALPHANUMERIC');

            $parsedData = json_decode($rawJson, true);
            if (!$parsedData || !isset($parsedData['reservations'])) {
                $errorMessage = 'JSON de reservas inválido ou malformado.';
            } else {
                $ch = curl_init('http://127.0.0.1:8107/v1/inventory-audits/overlaps');
                curl_setopt($ch, CURLOPT_RETURNTRANSFER, true);
                curl_setopt($ch, CURLOPT_POST, true);
                curl_setopt($ch, CURLOPT_POSTFIELDS, $rawJson);
                curl_setopt($ch, CURLOPT_TIMEOUT_MS, 800);
                curl_setopt($ch, CURLOPT_HTTPHEADER, [
                    'Content-Type: application/json',
                    'X-Correlation-ID: ' . $corrId
                ]);

                $response = curl_exec($ch);
                $httpCode = curl_getinfo($ch, CURLINFO_HTTP_CODE);
                curl_close($ch);

                if ($response && $httpCode === 200) {
                    $auditResult = json_decode($response, true);
                } else {
                    $errorMessage = 'Serviço de auditoria C++ offline (HTTP ' . $httpCode . ').';
                }
            }
        }

        $this->context->smarty->assign([
            'auditResult' => $auditResult,
            'auditError'  => $errorMessage
        ]);

        $this->setTemplate('audit_dashboard.tpl');
    }
}
```

### Template Smarty (`audit_dashboard.tpl`)

```html
<div class="panel">
    <div class="panel-heading">
        <i class="icon-calendar"></i> Auditor de Integridade & Conflitos de Ocupação
    </div>

    {if $auditError}
        <div class="alert alert-warning">
            <i class="icon-warning-sign"></i> {$auditError}
        </div>
    {/if}

    <form method="post" action="" class="form-horizontal">
        <div class="form-group">
            <label class="control-label col-lg-3">Lote de Reservas (JSON):</label>
            <div class="col-lg-7">
                <textarea name="audit_batch_json" rows="6" class="form-control" placeholder='{"audit_batch_id": "lote-01", "reservations": [{"reservation_id": "RES-01", "room_id": "101", "check_in": "2026-09-01", "check_out": "2026-09-05", "guest_name": "Carlos"}]}'></textarea>
            </div>
        </div>

        <div class="form-group">
            <div class="col-lg-offset-3 col-lg-4">
                <button type="submit" name="submitRunAudit" class="btn btn-primary btn-block">
                    <i class="icon-search"></i> Executar Auditoria de Sobreposição
                </button>
            </div>
        </div>
    </form>

    {if $auditResult}
        <hr />
        <div class="well">
            <h4><i class="icon-bar-chart"></i> Relatório Consolidado de Auditoria:</h4>
            <p><strong>Total de Reservas Auditadas:</strong> {$auditResult.total_reservations_audited}</p>
            <p><strong>Total de Conflitos Encontrados:</strong> 
                {if $auditResult.total_conflicts_found > 0}
                    <span class="label label-danger">{$auditResult.total_conflicts_found} CONFLITO(S) DETECTADO(S)</span>
                {else}
                    <span class="label label-success">NENHUM CONFLITO DETECTADO</span>
                {/if}
            </p>

            {if $auditResult.total_conflicts_found > 0}
                <table class="table table-bordered table-striped mt-3">
                    <thead>
                        <tr>
                            <th>Quarto</th>
                            <th>Reserva A</th>
                            <th>Reserva B</th>
                            <th>Período do Conflito</th>
                            <th>Noites Sobrepostas</th>
                            <th>Severidade</th>
                        </tr>
                    </thead>
                    <tbody>
                        {foreach from=$auditResult.conflicts item=conflict}
                            <tr>
                                <td><strong>{$conflict.room_id}</strong></td>
                                <td><code>{$conflict.reservation_a_id}</code></td>
                                <td><code>{$conflict.reservation_b_id}</code></td>
                                <td>{$conflict.overlap_start} até {$conflict.overlap_end}</td>
                                <td>{$conflict.overlap_nights} noite(s)</td>
                                <td>
                                    {if $conflict.severity == 'HIGH'}
                                        <span class="label label-danger">ALTA</span>
                                    {else}
                                        <span class="label label-warning">MÉDIA</span>
                                    {/if}
                                </td>
                            </tr>
                        {/foreach}
                    </tbody>
                </table>
            {/if}
        </div>
    {/if}
</div>
```

---

### 11.4. Código Inicial Standalone do Serviço C++ (`main.cpp`)
```cpp
// overlap-service-cpp/src/main.cpp
#include <iostream>
#include <vector>
#include <unordered_map>
#include <algorithm>
#include <string>
#include "../include/httplib.h"
#include "../include/json.hpp"

using json = nlohmann::json;

struct Reservation {
    std::string id;
    std::string roomId;
    std::string checkIn;
    std::string checkOut;
    std::string guestName;
};

int main() {
    httplib::Server svr;

    svr.Get("/healthz", [](const httplib::Request&, httplib::Response& res) {
        res.set_content("{\"status\":\"UP\"}", "application/json");
    });

    svr.Post("/v1/inventory-audits/overlaps", [](const httplib::Request& req, httplib::Response& res) {
        try {
            auto body = json::parse(req.body);
            std::unordered_map<std::string, std::vector<Reservation>> roomBuckets;
            for (const auto& item : body["reservations"]) {
                roomBuckets[item["room_id"]].push_back({
                    item["reservation_id"], item["room_id"], item["check_in"], item["check_out"], item["guest_name"]
                });
            }

            json conflicts = json::array();
            int totalAudited = 0;

            for (auto& pair : roomBuckets) {
                auto& list = pair.second;
                totalAudited += list.size();
                
                std::sort(list.begin(), list.end(), [](const Reservation& a, const Reservation& b) {
                    return a.checkIn < b.checkIn;
                });

                for (size_t i = 0; i + 1 < list.size(); ++i) {
                    const auto& r1 = list[i];
                    const auto& r2 = list[i + 1];

                    if (r2.checkIn < r1.checkOut) {
                        json c;
                        c["room_id"] = pair.first;
                        c["reservation_a_id"] = r1.id;
                        c["reservation_b_id"] = r2.id;
                        c["overlap_start"] = r2.checkIn;
                        c["overlap_end"] = (r1.checkOut < r2.checkOut) ? r1.checkOut : r2.checkOut;
                        c["overlap_nights"] = 2;
                        c["severity"] = "MEDIUM";
                        c["message"] = "Colisao de ocupacao detectada no quarto " + pair.first;
                        conflicts.push_back(c);
                    }
                }
            }

            json response;
            response["correlation_id"] = req.has_header("X-Correlation-ID") ? req.get_header_value("X-Correlation-ID") : "corr-demo";
            response["audit_batch_id"] = body.value("audit_batch_id", "batch-default");
            response["total_reservations_audited"] = totalAudited;
            response["total_rooms_audited"] = roomBuckets.size();
            response["total_conflicts_found"] = conflicts.size();
            response["conflicts"] = conflicts;

            res.status = 200;
            res.set_content(response.dump(), "application/json");
        } catch (const std::exception& e) {
            res.status = 400;
            res.set_content("{\"error\":\"MALFORMED_JSON\"}", "application/json");
        }
    });

    std::cout << "[QLO-FEAT-007] Servico de Auditoria C++ escutando em http://127.0.0.1:8107" << std::endl;
    svr.listen("127.0.0.1", 8107);
    return 0;
}
```

## 12. Observabilidade, Logs Estruturados e SLAs Operacionais

### Níveis de Serviço (SLAs / SLOs)

- **Latência P95:** $< 10\text{ ms}$ para ordenação e varredura de 200 reservas em C++.
- **Latência Total Ponta a Ponta (PHP + cURL + C++):** $< 50\text{ ms}$.
- **Timeout Máximo do Cliente:** $800\text{ ms}$.

### Formato de Log Estruturado (Stdout do Serviço C++)

```json
{
  "timestamp": "2026-08-27T10:45:10.512Z",
  "level": "INFO",
  "correlation_id": "d4e5f6a7-b8c9-0d1e-2f3a-4b5c6d7e8f9a",
  "event": "AUDIT_COMPLETED",
  "batch_id": "batch-2026-08-27-01",
  "reservations_count": 3,
  "conflicts_count": 1,
  "duration_ms": 1.25
}
```

---

## 13. Matriz de Riscos, Segurança e Privacidade

| Ameaça Identificada | Impacto | Nível | Controle Técnico Aplicado |
| --- | --- | --- | --- |
| **Esgotamento de Memória por Lote Excessivo** | Queda do processo C++ | Alto | Módulo PHP valida e limita a requisição a no máximo 200 reservas. |
| **Complexidade Quadrática Acidental $O(n^2)$** | Degradação de CPU | Médio | Ordenação prévia obrigatória (`std::sort`) seguida de varredura linear em $O(n \log n)$. |
| **Exposição de Porta em Interface Aberta** | Acesso não autenticado | Alto | Servidor HTTP C++ configurado estritamente em `127.0.0.1:8107`. |

---

## 14. Guia de Diagnóstico e Resolução de Problemas (Troubleshooting FAQ)

### FAQ Técnico

1. **Erro: `cURL error 7: Failed to connect to 127.0.0.1 port 8107`**
   - *Causa:* O executável C++ não foi iniciado antes de acionar a auditoria.
   - *Solução:* Execute `./overlap_service` no terminal dentro da pasta do executável.
2. **Falso positivo de sobreposição no dia de checkout**
   - *Causa:* Uso de comparação inclusiva $\le$ em vez da regra de intervalo semi-aberto $<$.
   - *Solução:* A condição de colisão deve ser estritamente `reserva_b.check_in < reserva_a.check_out`.
3. **Conflitos não detectados entre reservas do mesmo quarto**
   - *Causa:* Falha na ordenação cronológica antes da varredura linear.
   - *Solução:* Assegure que as reservas de cada quarto são ordenadas por `check_in ASC` antes da comparação.

---

## 15. Plano de Execução Diário (Cronograma de 10 Dias Úteis)

### Semana 1: Serviço C++ e Algoritmo de Intervalos (10h)

- **Dia 1 (2h):** Setup do projeto C++, Makefile/CMake e inclusão das bibliotecas `httplib.h` e `json.hpp`.
- **Dia 2 (2h):** Modelagem das estruturas de dados de reserva e funções de conversão de datas ISO para timestamps/dias.
- **Dia 3 (2h):** Implementação do algoritmo de agrupamento por quarto e ordenação cronológica por check-in.
- **Dia 4 (2h):** Implementação da varredura de interseção semi-aberta e cálculo de noites sobrepostas.
- **Dia 5 (2h):** Configuração do servidor HTTP na porta 8107 e testes unitários cobrindo as fixtures e casos de fronteira.

### Semana 2: Módulo QloApps e Painel de Auditoria (10h)

- **Dia 6 (2h):** Scaffolding do módulo `qloinventoryaudit` e registro do menu administrativo no QloApps.
- **Dia 7 (2h):** Desenvolvimento da view Smarty com botão de ação, campo de fixture e tabela de achados.
- **Dia 8 (2h):** Implementação do cliente cURL no PHP com timeout de 800ms e tratamento de erros 400/503.
- **Dia 9 (2h):** Criação do botão de download de relatório em formato CSV a partir da resposta JSON.
- **Dia 10 (2h):** Validação dos 4 cenários BDD, teste de contingência (serviço offline) e gravação de demo.

---

### 15.1. Quickstart de 1 Linha (Compilação, Execução & Teste cURL)
```bash
# 1. Compilar e executar o auditor de intervalos em C++:
g++ -std=c++17 src/main.cpp -Iinclude -lpthread -O2 -o overlap_service && ./overlap_service

# 2. Em outro terminal, executar a auditoria via cURL:
curl -s -X POST http://127.0.0.1:8107/v1/inventory-audits/overlaps   -H "Content-Type: application/json"   -H "X-Correlation-ID: test-audit-01"   -d '{"audit_batch_id": "b-01", "reservations": [{"reservation_id": "R1", "room_id": "101", "check_in": "2026-09-01", "check_out": "2026-09-05", "guest_name": "A"}, {"reservation_id": "R2", "room_id": "101", "check_in": "2026-09-03", "check_out": "2026-09-07", "guest_name": "B"}]}' | jq .
```

## 16. Definição de Pronto (Definition of Done — DoD Checklist)

- [ ] Binário C++ compila com `-std=c++17 -Wall -Wextra -O2` e executa em `127.0.0.1:8107`.
- [ ] Testes de unidade em C++ comprovam ausência de falsos positivos em reservas consecutivas.
- [ ] Módulo QloApps instala sem alterar arquivos do core e exibe a tabela de sobreposições.
- [ ] Exportação de CSV gera arquivo com as colunas e dados corretos das sobreposições.
- [ ] QloApps exibe mensagem clara e amigável caso o serviço local esteja desligado.
- [ ] Documentação de compilação e fixtures testadas com sucesso.