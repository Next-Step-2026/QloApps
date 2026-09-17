# RFC-003 — Avaliador de Saúde e Higiene de Contatos de Hóspedes

## 1. Identificação e Informações do Projeto

- **Código da Feature:** `QLO-FEAT-003`
- **Nome da Feature:** Avaliador de Saúde, Higiene e Conformidade de Contatos de Hóspedes
- **Engenharia Responsável:** Engenharia de Identidade, Segurança & Governança de Dados
- **Stack do Desenvolvedor:** Kotlin / Java (Kotlin 1.9+, Ktor/Javalin / lógica de validação de fatores)
- **Stack de Apresentação:** Módulo QloApps (`qlocontacthealth`, PHP 8.1+ / Smarty 3.x / HTML / CSS)
- **Porta de Execução Local:** `http://127.0.0.1:8103`
- **Prazo de Execução:** Milestone de 2 Semanas (10 dias úteis de sprint focado)

---

## 2. Contexto de Negócio e Motivação Operacional

Em redes hoteleiras, a base de contatos de clientes degrada-se rapidamente: hóspedes mudam de número de celular, trocam de e-mail corporativo ou revogam termos de consentimento de privacidade (LGPD/GDPR). Quando o hotel tenta disparar comunicações críticas — como confirmação de reserva, vouchers de check-in, cobrança de no-show ou pesquisas de satisfação —, ocorrem altas taxas de rejeição (*bounce*), reclamações de spam e vulnerabilidades em processos de recuperação de conta.

O **Avaliador de Saúde de Contatos** atua no momento em que o atendente abre a ficha do cliente no QloApps. Ele analisa as informações de contato cadastradas, verifica a conformidade dos formatos internacionais, calcula o decaimento temporal (*staleness*) desde a última validação e checa a vigência do termo de consentimento, gerando um score numérico de higiene (0 a 100) com badges visuais que alertam a recepção para solicitar a atualização cadastral antes do check-in.

---

## 3. Histórias de Usuário e Personas

### Personas

- **Camila (Recepcionista do Hotel):** Realiza check-in e atendimento no balcão e precisa saber em menos de 2 segundos se os dados de contato do hóspede estão confiáveis para envio de nota fiscal e voucher.
- **André (Encarregado de Proteção de Dados / DPO):** Precisa garantir que contatos com consentimento expirado não sejam utilizados para comunicações promocionais e que haja registro imutável de auditoria.

### Histórias de Usuário

1. **Identificação Visual de Contato Obsoleto:**
   - *Como* recepcionista atendendo um hóspede no check-in,
   - *Quero* ver um indicador visual (badge amarelo ou vermelho) na ficha do cliente caso o telefone ou e-mail não sejam confirmados há mais de 90 dias,
   - *Para que* eu possa confirmar verbalmente o número de celular atualizado e registrá-lo no sistema.
2. **Bloqueio por Consentimento Expirado:**
   - *Como* DPO do hotel,
   - *Quero* que o sistema aponte explicitamente quando o prazo de consentimento LGPD de um hóspede tiver expirado,
   - *Para que* a equipe operacional não utilize o contato indevidamente.
3. **Simulação de Desafio de Reconfirmação:**
   - *Como* recepcionista,
   - *Quero* clicar em *"Simular Validação de Contato"*,
   - *Para que* o sistema gere um evento auditável de envio de token de verificação sem disparar mensagens de rede reais no ambiente local.

---

## 4. Escopo Operacional e Limites da Entrega

### No Escopo (In-Scope para o MVP)

- Módulo QloApps (`qlocontacthealth`) integrado à tela de detalhes de clientes (`AdminCustomersController`).
- Envio dos dados de contato (e-mail, telefone, data da última confirmação e expiração de consentimento) via HTTP POST para o serviço Kotlin.
- Serviço local em Kotlin escutando em `127.0.0.1:8103` na rota `/v1/contact-evaluations`.
- Validador sintático de e-mail (RFC 5322) e telefone (formato internacional E.164, ex.: `+5511999998888`).
- Algoritmo de decaimento temporal: contatos validados há $\le 30\text{ dias}$ são `FRESH`; entre $31$ e $90\text{ dias}$ são `AGING`; $> 90\text{ dias}$ são `STALE`.
- Verificação de validade de consentimento baseada na data de referência (`reference_date`).
- Cálculo ponderado do score de higiene de 0 a 100 pontos.
- Card visual Smarty com badges coloridos (Verde/Amarelo/Vermelho), score e botão para simular envio de desafio.
- Resiliência com timeout de 600ms e tratamento gracioso de falha caso o serviço Kotlin esteja desligado.

### Fora do Escopo (Out-of-Scope para Versão 2)

- Envio real de SMS ou e-mails transacionais via gateways de nuvem (Twilio, AWS SES, SendGrid).
- Persistência distribuída em banco Spanner ou sincronização multi-região.
- Deduplicação fuzzy automática de cadastros duplicados.

---

## 5. Mapa de Entidades, Ciclo de Vida e Estados

### 5.1. Mapeamento de Tabelas Relacionais do QloApps (MySQL)
A avaliação de higiene analisa dados armazenados nas tabelas de clientes e endereços:
- `ps_customer`: Cadastro do cliente (`id_customer`, `email`, `firstname`, `lastname`, `date_add`, `date_upd`).
- `ps_address`: Telefones de contato vinculados (`phone`, `phone_mobile`, `id_customer`).

### Diagrama de Estados do Fator de Contato

```text
  [Contato Cadastrado / Atualizado]
                 │
                 ▼
          [Status: FRESH] ──(Idade <= 30 dias, Formato OK, Consentimento Ativo)
                 │
                 ▼ (Tempo > 30 dias)
          [Status: AGING] ──(Penalidade: -15 pontos no score)
                 │
                 ▼ (Tempo > 90 dias)
          [Status: STALE] ──(Penalidade: -30 pontos / Ação: Reconfirmar)
                 │
                 ├──(Consentimento Vencido)──► [Status: CONSENT_EXPIRED (Score máx: 40)]
                 │
                 └──(Regex Inválido)        ──► [Status: INVALID_FORMAT (Score: 0)]
```

---

## 6. Topologia de Comunicação e Fluxo de Dados Ponta a Ponta

```text
[Navegador / Atendente]
         │
         │ 1. Abre ficha do cliente no QloApps
         ▼
[QloApps Back-Office: Módulo qlocontacthealth (PHP/Smarty)]
         │
         │ 2. Recupera e-mail e telefone cadastrados e empacota JSON
         ▼ (HTTP POST síncrono em loopback, timeout 600ms)
[Serviço Local Kotlin: http://127.0.0.1:8103/v1/contact-evaluations]
   ├── 3.1. Validador de Formato (RFC 5322 / E.164)
   ├── 3.2. Calculador de Envelhecimento (Dias desde last_verified_at)
   ├── 3.3. Avaliador de Vigência de Consentimento LGPD
   └── 3.4. Calculador Ponderado de Score de Higiene
         │
         │ 3.5. Retorna JSON com Score, Status por Fator e Ação Sugerida
         ▼
[QloApps Módulo PHP]
         │
         │ 4. Renderiza card de saúde com badges e botão de revalidação
         ▼
[Navegador / Atendente]
```

### Estrutura de Diretórios Recomendada

```text
projeto/
├── qlocontacthealth/                  # Módulo PHP para QloApps
│   ├── qlocontacthealth.php           # Registro do módulo e hooks de cliente
│   ├── config.xml                     # Metadados
│   ├── controllers/
│   │   └── admin/
│   │       └── AdminContactHealthController.php # Controller administrativo
│   └── views/
│       └── templates/
│           └── admin/
│               └── contact_health_card.tpl # Template Smarty com badges
│
└── contact-health-service-kotlin/     # Serviço Local Kotlin
    ├── build.gradle.kts               # Configuração Gradle (Ktor, kotlinx.serialization, JUnit 5)
    ├── src/
    │   ├── main/kotlin/com/hotel/contacthealth/
    │   │   ├── Application.kt         # Servidor Ktor e roteamento HTTP
    │   │   ├── model/
    │   │   │   ├── ContactModels.kt   # Data classes de Request e Response
    │   │   │   └── FactorEnums.kt     # Enums de status e canais
    │   │   └── service/
    │   │       └── HygieneEvaluator.kt # Regras de cálculo de score e staleness
    │   └── test/kotlin/com/hotel/contacthealth/
    │       └── HygieneEvaluatorTest.kt # Testes unitários automatizados
```

---

## 7. Regras de Negócio Detalhadas e Tabela de Casos de Borda

| ID | Regra de Negócio | Condição de Entrada | Comportamento Esperado | Caso de Borda / Tratamento |
| --- | --- | --- | --- | --- |
| **RN-001** | **Validação de Formato de E-mail** | E-mail fora do padrão RFC 5322 (ex.: sem `@` ou sem domínio válido) | Marcar fator com `status = "INVALID_FORMAT"`, deduzir 40 pontos do score. | Rejeitar strings com espaços ou quebras de linha. |
| **RN-002** | **Validação de Formato de Telefone** | Telefone não inicia com `+` seguido de código de país e DDD (E.164) | Marcar fator com `status = "INVALID_FORMAT"`, deduzir 30 pontos. | Aceitar formato internacional limpo ou com hífens/espaços normalizados. |
| **RN-003** | **Decaimento Temporal (Fresh)** | `last_verified_at` com $\le 30\text{ dias}$ em relação a `reference_date` | Marcar fator com `status = "FRESH"`, sem dedução de pontos. | Se `last_verified_at` for nulo, tratar como $> 90\text{ dias}$ (`STALE`). |
| **RN-004** | **Decaimento Temporal (Aging / Stale)** | $31$ a $90\text{ dias}$ -> `AGING` (-15 pts); $> 90\text{ dias}$ -> `STALE` (-30 pts) | Marcar fator com status correspondente e sugerir `recommended_action = "TRIGGER_BACKGROUND_RECONFIRMATION"`. | Cálculo de dias exato baseado na diferença de `LocalDate`. |
| **RN-005** | **Vigência de Consentimento LGPD** | `consent_expires_at` anterior a `reference_date` | Definir `consent_valid = false`, `overall_status = "CONSENT_EXPIRED"` e limitar score a no máximo 40. | Contato não pode ser usado para marketing mesmo se e-mail for válido. |
| **RN-006** | **Composição do Score de Higiene** | Base inicial: 100 pontos; subtrair penalidades de cada fator | Score final no intervalo $[0..100]$. | Se o score calculado for negativo, fixar em 0. |

---

## 8. Especificação Completa do Contrato de API (OpenAPI / RFC 7807)

### Endpoint Local

- **URL:** `http://127.0.0.1:8103/v1/contact-evaluations`
- **Método:** `POST`
- **Headers Obrigatórios:**
  - `Content-Type: application/json`
  - `X-Correlation-ID: <uuid-v4>`

### Schema de Entrada (Request Body)

```json
{
  "$schema": "http://json-schema.org/draft-07/schema#",
  "type": "object",
  "required": ["customer_id", "email", "phone", "reference_date"],
  "properties": {
    "customer_id": { "type": "string", "description": "Identificador do cliente no QloApps." },
    "email": { "type": "string", "format": "email", "description": "Endereço de e-mail do cliente." },
    "phone": { "type": "string", "description": "Telefone no padrão E.164." },
    "last_verified_at": { "type": ["string", "null"], "format": "date-time" },
    "consent_expires_at": { "type": ["string", "null"], "format": "date-time" },
    "reference_date": { "type": "string", "format": "date" }
  }
}
```

### Exemplo de Requisição (Request)

```json
{
  "customer_id": "cust-1042",
  "email": "carlos.silva@empresa.com.br",
  "phone": "+5511987654321",
  "last_verified_at": "2026-04-10T10:00:00Z",
  "consent_expires_at": "2026-12-31T23:59:59Z",
  "reference_date": "2026-08-27T00:00:00Z"
}
```

### Exemplo de Resposta (Response: 200 OK)

```json
{
  "correlation_id": "e3b0c442-98fc-1c14-9afb-4c8996fb9242",
  "customer_id": "cust-1042",
  "overall_status": "AGING",
  "hygiene_score": 75,
  "factors": [
    {
      "type": "EMAIL",
      "value_masked": "c***a@empresa.com.br",
      "status": "AGING",
      "days_since_verification": 139,
      "issues": ["STALENESS_EXCEEDED_90_DAYS"]
    },
    {
      "type": "PHONE",
      "value_masked": "+55 11 *****-4321",
      "status": "FRESH",
      "days_since_verification": 139,
      "issues": []
    }
  ],
  "consent_valid": true,
  "recommended_action": "TRIGGER_BACKGROUND_RECONFIRMATION"
}
```

### Matriz de Respostas e Códigos de Status HTTP

| Código HTTP | Significado | Condição de Ocorrência | Formato do Payload |
| --- | --- | --- | --- |
| `200 OK` | Sucesso | Contato avaliado e score gerado. | JSON com `hygiene_score`, `factors` e `recommended_action`. |
| `400 Bad Request` | Payload Inválido | JSON malformado ou campos obrigatórios ausentes. | `{"error": "INVALID_PAYLOAD", "message": "Campos obrigatórios ausentes."}` |
| `503 Service Unavailable` | Serviço Indisponível | Serviço Kotlin inativo (capturado pelo PHP). | Renderizado pelo Smarty como aviso sem travar o painel. |

---

## 9. Massa de Dados de Teste e Cenários de Fixture

```json
[
  {
    "description": "Cliente com contato recente e consentimento válido",
    "input": {
      "customer_id": "cust-001",
      "email": "marina.costa@tech.com",
      "phone": "+5511991234567",
      "last_verified_at": "2026-08-15T10:00:00Z",
      "consent_expires_at": "2027-01-01T00:00:00Z",
      "reference_date": "2026-08-27"
    },
    "expected_output": {
      "overall_status": "FRESH",
      "hygiene_score_min": 90,
      "consent_valid": true
    }
  },
  {
    "description": "Cliente com contato desatualizado há mais de 180 dias",
    "input": {
      "customer_id": "cust-002",
      "email": "joao.antigo@provedor.com.br",
      "phone": "+5521988887777",
      "last_verified_at": "2026-01-10T10:00:00Z",
      "consent_expires_at": "2026-12-31T00:00:00Z",
      "reference_date": "2026-08-27"
    },
    "expected_output": {
      "overall_status": "STALE",
      "hygiene_score_max": 70,
      "recommended_action": "TRIGGER_BACKGROUND_RECONFIRMATION"
    }
  },
  {
    "description": "Cliente com termo de consentimento expirado",
    "input": {
      "customer_id": "cust-003",
      "email": "paulo.silva@empresa.com",
      "phone": "+5531977776666",
      "last_verified_at": "2026-08-20T10:00:00Z",
      "consent_expires_at": "2026-06-01T00:00:00Z",
      "reference_date": "2026-08-27"
    },
    "expected_output": {
      "overall_status": "CONSENT_EXPIRED",
      "hygiene_score_max": 40,
      "consent_valid": false
    }
  }
]
```

---

## 10. Critérios de Aceitação em BDD (Gherkin: Given-When-Then)

```gherkin
Feature: Avaliação de Saúde e Higiene de Contatos de Hóspedes

  Scenario: Contato recente e válido recebe selo FRESH e pontuação alta
    Given que o serviço Kotlin está ativo em "http://127.0.0.1:8103"
    When uma requisição de avaliação é enviada para o cliente "cust-001" com validação em "2026-08-15"
    Then o status HTTP da resposta deve ser 200
    And o campo "overall_status" deve ser "FRESH"
    And o campo "hygiene_score" deve ser maior ou igual a 90
    And o campo "consent_valid" deve ser verdadeiro

  Scenario: Contato com mais de 90 dias sem validação é marcado como STALE
    Given que o serviço Kotlin está ativo
    When uma requisição é enviada para "cust-002" com última validação há 229 dias
    Then o status HTTP deve ser 200
    And o campo "overall_status" deve ser "STALE"
    And o campo "recommended_action" deve ser "TRIGGER_BACKGROUND_RECONFIRMATION"

  Scenario: Consentimento expirado bloqueia status saudável
    Given que o serviço Kotlin está ativo
    When uma requisição é enviada com "consent_expires_at" anterior a "reference_date"
    Then o status HTTP deve ser 200
    And o campo "overall_status" deve ser "CONSENT_EXPIRED"
    And o campo "consent_valid" deve ser falso
    And o campo "hygiene_score" deve ser menor ou igual a 40

  Scenario: Resiliência em caso de serviço Kotlin offline
    Given que o serviço Kotlin na porta 8103 foi finalizado
    When o atendente abre a ficha do cliente no back-office do QloApps
    Then o módulo PHP deve capturar o timeout em no máximo 600ms
    And os dados nativos do cliente devem ser exibidos sem quebrar a tela
```

---

## 11. Guia de Integração com o QloApps (Módulo PHP / Smarty)

### Classe Principal do Módulo (`qlocontacthealth.php`)
```php
<?php
// modules/qlocontacthealth/qlocontacthealth.php

if (!defined('_PS_VERSION_')) {
    exit;
}

class QloContactHealth extends Module
{
    public function __construct()
    {
        $this->name = 'qlocontacthealth';
        $this->tab = 'administration';
        $this->version = '1.0.0';
        $this->author = 'QloApps Engineering';
        $this->need_instance = 0;
        $this->bootstrap = true;

        parent::__construct();

        $this->displayName = $this->l('Avaliador de Saúde de Contatos');
        $this->description = $this->l('Métricas de higiene, staleness e conformidade de contatos de hóspedes.');
    }

    public function install()
    {
        return parent::install() && $this->installTab() && $this->registerHook('displayAdminCustomers');
    }

    public function uninstall()
    {
        return $this->uninstallTab() && parent::uninstall();
    }

    private function installTab()
    {
        $tab = new Tab();
        $tab->active = 1;
        $tab->class_name = 'AdminContactHealth';
        $tab->name = array();
        foreach (Language::getLanguages(true) as $lang) {
            $tab->name[$lang['id_lang']] = 'Saúde de Contatos';
        }
        $tab->id_parent = (int) Tab::getIdFromClassName('AdminParentCustomer');
        $tab->module = $this->name;
        return $tab->add();
    }

    private function uninstallTab()
    {
        $idTab = (int) Tab::getIdFromClassName('AdminContactHealth');
        if ($idTab) {
            $tab = new Tab($idTab);
            return $tab->delete();
        }
        return true;
    }
}
```

### Controller PHP (`AdminContactHealthController.php`)

```php
<?php
// modules/qlocontacthealth/controllers/admin/AdminContactHealthController.php

class AdminContactHealthController extends ModuleAdminController
{
    public function renderView()
    {
        $customerId = (int) Tools::getValue('id_customer');
        $customer   = new Customer($customerId);
        $addressId  = (int) Address::getFirstCustomerAddressId($customerId);
        $address    = new Address($addressId);
        $corrId     = Tools::passwdGen(16, 'ALPHANUMERIC');

        $payload = json_encode([
            'customer_id'        => 'cust-' . $customer->id,
            'email'              => $customer->email,
            'phone'              => $address->phone_mobile ? $address->phone_mobile : '+5511999990000',
            'last_verified_at'   => $customer->date_upd . 'Z',
            'consent_expires_at' => date('Y-m-d\TH:i:s\Z', strtotime('+1 year', strtotime($customer->date_add))),
            'reference_date'     => date('Y-m-d')
        ]);

        $ch = curl_init('http://127.0.0.1:8103/v1/contact-evaluations');
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
            $healthData = json_decode($response, true);
            $this->context->smarty->assign('contactHealth', $healthData);
        } else {
            $this->context->smarty->assign('healthWarning', 'Indicadores de saúde de contato indisponíveis no momento.');
        }

        return $this->context->smarty->fetch($this->getTemplatePath() . 'contact_health_card.tpl');
    }
}
```

### Template Smarty (`contact_health_card.tpl`)

```html
<div class="panel">
    <div class="panel-heading">
        <i class="icon-user-md"></i> Indicador de Saúde e Higiene Cadastral
    </div>

    {if $healthWarning}
        <div class="alert alert-warning">
            <i class="icon-warning-sign"></i> {$healthWarning}
        </div>
    {/if}

    {if $contactHealth}
        <div class="row">
            <div class="col-lg-4">
                <h4>Score de Higiene:</h4>
                <div class="progress">
                    <div class="progress-bar {if $contactHealth.hygiene_score >= 80}progress-bar-success{elseif $contactHealth.hygiene_score >= 50}progress-bar-warning{else}progress-bar-danger{/if}" 
                         style="width: {$contactHealth.hygiene_score}%">
                        {$contactHealth.hygiene_score}/100
                    </div>
                </div>
                <p><strong>Status Geral:</strong> <span class="label {if $contactHealth.overall_status == 'FRESH'}label-success{elseif $contactHealth.overall_status == 'AGING'}label-warning{else}label-danger{/if}">{$contactHealth.overall_status}</span></p>
                <p><strong>Consentimento LGPD:</strong> {if $contactHealth.consent_valid}<span class="badge badge-success">VIGENTE</span>{else}<span class="badge badge-danger">EXPIRADO</span>{/if}</p>
            </div>

            <div class="col-lg-8">
                <h4>Fatores de Contato:</h4>
                <table class="table table-bordered">
                    <thead>
                        <tr>
                            <th>Canal</th>
                            <th>Valor Mascarado</th>
                            <th>Status</th>
                            <th>Última Validação</th>
                        </tr>
                    </thead>
                    <tbody>
                        {foreach from=$contactHealth.factors item=factor}
                            <tr>
                                <td><strong>{$factor.type}</strong></td>
                                <td><code>{$factor.value_masked}</code></td>
                                <td>
                                    <span class="label {if $factor.status == 'FRESH'}label-success{elseif $factor.status == 'AGING'}label-warning{else}label-danger{/if}">
                                        {$factor.status}
                                    </span>
                                </td>
                                <td>{$factor.days_since_verification} dias atrás</td>
                            </tr>
                        {/foreach}
                    </tbody>
                </table>

                {if $contactHealth.recommended_action}
                    <button type="button" class="btn btn-warning" onclick="alert('Desafio de revalidação simulado enviado com sucesso!');">
                        <i class="icon-envelope"></i> Disparar Desafio de Reconfirmação
                    </button>
                {/if}
            </div>
        </div>
    {/if}
</div>
```

---

### 11.4. Código Inicial Standalone do Serviço Kotlin (`Application.kt`)
```kotlin
// contact-health-service-kotlin/src/main/kotlin/com/hotel/contacthealth/Application.kt
package com.hotel.contacthealth

import io.ktor.server.application.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.http.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.LocalDate
import java.time.temporal.ChronoUnit

@Serializable
data class ContactEvaluationRequest(
    val customer_id: String,
    val email: String,
    val phone: String,
    val last_verified_at: String? = null,
    val consent_expires_at: String? = null,
    val reference_date: String
)

fun main() {
    embeddedServer(Netty, port = 8103, host = "127.0.0.1") {
        routing {
            get("/healthz") {
                call.respondText("{\"status\":\"UP\"}", ContentType.Application.Json)
            }
            post("/v1/contact-evaluations") {
                try {
                    val rawBody = call.receiveText()
                    val req = Json { ignoreUnknownKeys = true }.decodeFromString<ContactEvaluationRequest>(rawBody)
                    val refDate = LocalDate.parse(req.reference_date)
                    val lastVerified = if (req.last_verified_at != null) LocalDate.parse(req.last_verified_at.substring(0, 10)) else null
                    val consentExpires = if (req.consent_expires_at != null) LocalDate.parse(req.consent_expires_at.substring(0, 10)) else null
                    
                    val daysSince = if (lastVerified != null) ChronoUnit.DAYS.between(lastVerified, refDate) else 180
                    val consentValid = consentExpires == null || !consentExpires.isBefore(refDate)
                    
                    val emailStatus = if (daysSince <= 30) "FRESH" else if (daysSince <= 90) "AGING" else "STALE"
                    val overallStatus = if (!consentValid) "CONSENT_EXPIRED" else emailStatus
                    
                    var score = 100
                    if (daysSince in 31..90) score -= 15
                    if (daysSince > 90) score -= 30
                    if (!consentValid) score = Math.min(score, 40)
                    
                    val maskedEmail = req.email.replaceFirst(Regex("^([^@]{1})[^@]+(@.+)"), "$1***$2")
                    val maskedPhone = req.phone.replace(Regex("\\d(?=\\d{4})"), "*")
                    
                    val responseJson = """
                    {
                      "correlation_id": "${call.request.headers["X-Correlation-ID"] ?: "corr-demo"}",
                      "customer_id": "${req.customer_id}",
                      "overall_status": "$overallStatus",
                      "hygiene_score": $score,
                      "factors": [
                        {"type": "EMAIL", "value_masked": "$maskedEmail", "status": "$emailStatus", "days_since_verification": $daysSince},
                        {"type": "PHONE", "value_masked": "$maskedPhone", "status": "FRESH", "days_since_verification": $daysSince}
                      ],
                      "consent_valid": $consentValid,
                      "recommended_action": "${if (overallStatus != "FRESH") "TRIGGER_BACKGROUND_RECONFIRMATION" else "NONE"}"
                    }
                    """.trimIndent()
                    call.respondText(responseJson, ContentType.Application.Json, HttpStatusCode.OK)
                } catch (e: Exception) {
                    call.respondText("{\"error\":\"INVALID_PAYLOAD\",\"message\":\"${e.message}\"}", ContentType.Application.Json, HttpStatusCode.BadRequest)
                }
            }
        }
    }.start(wait = true)
}
```

## 12. Observabilidade, Logs Estruturados e SLAs Operacionais

### Níveis de Serviço (SLAs / SLOs)

- **Latência P95:** $< 25\text{ ms}$ para cálculo de regras em Kotlin.
- **Latência Total Ponta a Ponta (PHP + cURL + Kotlin):** $< 85\text{ ms}$.
- **Timeout Máximo do Cliente:** $600\text{ ms}$.

### Formato de Log Estruturado (Stdout do Serviço Kotlin)

```json
{
  "timestamp": "2026-08-27T10:25:00.104Z",
  "level": "INFO",
  "correlation_id": "e3b0c442-98fc-1c14-9afb-4c8996fb9242",
  "event": "CONTACT_EVALUATED",
  "customer_id": "cust-1042",
  "overall_status": "AGING",
  "hygiene_score": 75,
  "duration_ms": 3.12
}
```

---

## 13. Matriz de Riscos, Segurança e Privacidade

| Ameaça Identificada | Impacto | Nível | Controle Técnico Aplicado |
| --- | --- | --- | --- |
| **Vazamento de PII em Logs de Console** | Não conformidade LGPD/GDPR | Alto | Serviço Kotlin mascara e-mails (`c***a@dominio.com`) e telefones (`+55 11 *****-4321`) em todos os logs. |
| **Injeção de Cabeçalhos em E-mails (Header Injection)** | Vulnerabilidade de segurança | Médio | Validador regex RFC 5322 rejeita estritamente quebras de linha (`\r`, `\n`) em e-mails. |
| **Exposição da Porta na Rede Pública** | Acesso não autorizado | Alto | Servidor Ktor configurado para fazer bind exclusivamente em `127.0.0.1:8103`. |

---

## 14. Guia de Diagnóstico e Resolução de Problemas (Troubleshooting FAQ)

### FAQ Técnico

1. **Erro: `cURL error 7: Failed to connect to 127.0.0.1 port 8103`**
   - *Causa:* O servidor Kotlin Ktor não foi iniciado.
   - *Solução:* Execute `./gradlew run` na pasta do serviço Kotlin antes de abrir a tela de clientes.
2. **Telefones válidos marcados como `INVALID_FORMAT`**
   - *Causa:* O número não inclui o prefixo internacional `+55` ou o código de área (DDD).
   - *Solução:* Certifique-se de que o telefone esteja cadastrado no padrão E.164 (ex.: `+5511999998888`).
3. **Score com pontuação negativa**
   - *Causa:* Múltiplas penalidades aplicadas sem corte inferior.
   - *Solução:* O método `calculateScore()` deve aplicar `max(0, calculatedScore)`.

---

## 15. Plano de Execução Diário (Cronograma de 10 Dias Úteis)

### Semana 1: Serviço Kotlin e Regras de Higiene (10h)

- **Dia 1 (2h):** Setup do projeto Gradle/Kotlin com Ktor Server e kotlinx.serialization.
- **Dia 2 (2h):** Modelagem das classes de dados (`ContactEvaluationRequest`, `ContactEvaluationResponse`, `FactorStatus`).
- **Dia 3 (2h):** Implementação dos validadores de formato de e-mail e telefone e do cálculo de decaimento temporal.
- **Dia 4 (2h):** Implementação do validador de consentimento LGPD e cálculo ponderado do score de higiene.
- **Dia 5 (2h):** Criação do servidor HTTP na porta 8103 e testes unitários JUnit 5 cobrindo as fixtures fornecidas.

### Semana 2: Módulo QloApps e Painel de Clientes (10h)

- **Dia 6 (2h):** Criação da estrutura de pastas do módulo `qlocontacthealth` e integração com a tela de clientes.
- **Dia 7 (2h):** Desenvolvimento da view Smarty com card de saúde, badges coloridos e barras de pontuação.
- **Dia 8 (2h):** Implementação do cliente cURL no PHP com timeout de 600ms e propagação do `X-Correlation-ID`.
- **Dia 9 (2h):** Criação do botão mock para simulação de desafio de revalidação com registro no log da sessão.
- **Dia 10 (2h):** Validação dos 4 cenários BDD, teste de queda de serviço e preparação do roteiro de apresentação.

---

### 15.1. Quickstart de 1 Linha (Execução & Teste cURL)
```bash
# 1. Executar o serviço Kotlin via Gradle:
./gradlew run

# 2. Em outro terminal, testar a avaliação de higiene via cURL:
curl -s -X POST http://127.0.0.1:8103/v1/contact-evaluations   -H "Content-Type: application/json"   -H "X-Correlation-ID: test-contact-01"   -d '{"customer_id": "cust-101", "email": "carlos.silva@empresa.com", "phone": "+5511999998888", "last_verified_at": "2026-04-10", "consent_expires_at": "2026-12-31", "reference_date": "2026-08-27"}' | jq .
```

## 16. Definição de Pronto (Definition of Done — DoD Checklist)

- [ ] Serviço Kotlin compila e responde com sucesso em `http://127.0.0.1:8103/healthz`.
- [ ] Testes automatizados do JUnit 5 cobrem todos os cenários de higiene com 100% de sucesso.
- [ ] Módulo QloApps instala sem conflitos e exibe os indicadores na tela de clientes.
- [ ] Badges de saúde refletem com precisão as regras de envelhecimento e validade de consentimento.
- [ ] QloApps exibe mensagem de contingência adequada caso o serviço Kotlin esteja desligado.
- [ ] Documentação de compilação e fixtures testadas com sucesso.