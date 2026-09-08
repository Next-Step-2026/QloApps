# QLO-FEAT-003: Avaliador de Saúde e Higiene de Contatos de Hóspedes

Microsserviço local autônomo desenvolvido em **Kotlin/Ktor** para cálculo de métricas de higiene cadastral, staleness (defasagem), validação de formato e conformidade regulatória (LGPD) de contatos de hóspedes do QloApps, conforme a especificação técnica da **RFC-003**.

---

## Estrutura do Projeto

```text
contact-health-service-kotlin/
├── gradle/
│   └── wrapper/
│       ├── gradle-wrapper.jar
│       └── gradle-wrapper.properties
├── src/
│   ├── main/
│   │   └── kotlin/
│   │       └── com/
│   │           └── hotel/
│   │               └── contacthealth/
│   │                   ├── Application.kt          # Ponto de entrada, rotas Ktor e tratamento de erros (StatusPages)
│   │                   ├── ContactModels.kt        # DTOs de Request, Response, Enums e modelos de avaliação
│   │                   ├── ConsentValidator.kt     # Validador de expiração de consentimento regulatório (LGPD)
│   │                   ├── FormatValidators.kt     # Validações de formato (E-mail, E.164) e mascaramento de PII
│   │                   ├── HygieneEvaluator.kt     # Orquestrador da lógica de avaliação de higiene cadastral
│   │                   ├── ScoreCalculator.kt      # Algoritmo de cálculo de pontuação (0-100) e penalidades
│   │                   └── StalenessCalculator.kt  # Cálculo de defasagem temporal (dias desde a última verificação)
│   └── test/
│       └── kotlin/
│           └── com/
│               └── hotel/
│                   └── contacthealth/
│                       ├── Application.kt          # Testes de integração do endpoint /healthz (Ktor Test Host)
│                       └── HygieneEvaluatorTest.kt # Testes unitários com fixtures para cenários de higiene e LGPD
├── build.gradle.kts                                # Configuração do Gradle, plugins e dependências
├── gradle.properties                               # Propriedades de JVM e cache do Gradle
├── gradlew                                         # Executável do Gradle Wrapper (Linux/macOS)
├── gradlew.bat                                     # Executável do Gradle Wrapper (Windows)
├── settings.gradle.kts                             # Configuração do projeto Gradle
└── README.md                                       # Esta documentação
```

---

## Pré-requisitos e Ambiente

* **JDK**: Versão 17 ou 21 instalada e configurada (`JAVA_HOME`)
* **Gradle**: 8.7+ (gerenciado via Gradle Wrapper incluso `./gradlew`)
* **Porta Local**: `8103` livre na interface `127.0.0.1`

---

## Como Executar

### 1. Inicializar o Microsserviço
Navegue até a pasta do serviço e execute:

```bash
cd contact-health-service-kotlin
./gradlew run
```

O servidor Ktor inicializará utilizando a engine Netty escutando em:
`http://127.0.0.1:8103`

### 2. Executar os Testes Automatizados
Para rodar toda a suíte de testes unitários e de integração:

```bash
./gradlew test
```

Para inspecionar o log detalhado dos testes:

```bash
./gradlew test --info
```

---

## Regras de Negócio e Algoritmo de Avaliação

O serviço avalia a qualidade e conformidade dos dados de contato de acordo com os seguintes critérios:

### 1. Defasagem Temporal (Staleness)
Calculado com base na diferença em dias entre a `reference_date` e o `last_verified_at` (caso não informado, assume 180 dias por padrão):
- **<= 30 dias**: Status `FRESH` (sem penalidade).
- **> 30 e <= 90 dias**: Status `AGING` (penalidade de -15 pontos por fator; issue: `STALENESS_EXCEEDED_30_DAYS`).
- **> 90 dias**: Status `STALE` (penalidade de -30 pontos por fator; issue: `STALENESS_EXCEEDED_90_DAYS`).

### 2. Validação de Formato e Mascaramento (PII / LGPD)
- **E-mail**: Validado contra expressão regular de formato padrão RFC. Se inválido: Status `INVALID_FORMAT` (penalidade de -40 pontos; issue: `INVALID_EMAIL_FORMAT`).
  - *Mascaramento*: Preserva o primeiro e último caractere da parte local (ex: `m***a@tech.com`).
- **Telefone**: Deve seguir o padrão internacional E.164 (`+` seguido de 8 a 15 dígitos). Se inválido: Status `INVALID_FORMAT` (penalidade de -30 pontos; issue: `INVALID_E164_PHONE_FORMAT`).
  - *Mascaramento*: Mascara dígitos intermediários preservando os 4 últimos dígitos (ex: `+5511*****4567`).

### 3. Consentimento Regulatório (LGPD)
- Se a data `consent_expires_at` for anterior à `reference_date`, o consentimento é marcado como expirado (`consent_valid = false`).
- Quando expirado:
  - O `overall_status` é classificado como `CONSENT_EXPIRED`.
  - O `hygiene_score` é limitado a um teto máximo de **40 pontos**.

### 4. Cálculo do Score Final (`hygiene_score`)
- Inicia em **100 pontos**.
- Aplica as deduções acumuladas de e-mail e telefone.
- Aplica o teto de 40 pontos caso o consentimento esteja expirado.
- Garante que a pontuação final fique no intervalo de **0 a 100**.

### 5. Status Geral (`overall_status`) e Ação Recomendada (`recommended_action`)
A classificação geral segue a ordem de prioridade:
1. `CONSENT_EXPIRED` (se `consent_valid` for falso)
2. `INVALID_FORMAT` (se qualquer fator for inválido)
3. `STALE` (se qualquer fator estiver obsoleto)
4. `AGING` (se qualquer fator estiver envelhecido)
5. `FRESH` (contatos recentes e válidos)

**Ação Recomendada:**
- `NONE`: Se o `overall_status` for `FRESH`.
- `TRIGGER_BACKGROUND_RECONFIRMATION`: Se o `overall_status` for qualquer outro status diferente de `FRESH`.

---

## Endpoints da API

### 1. Verificação de Saúde do Serviço (`GET /healthz`)

**Requisição:**
```bash
curl -i -X GET http://127.0.0.1:8103/healthz
```

**Resposta esperada (HTTP 200 OK):**
```json
{
  "status": "UP"
}
```

---

### 2. Avaliação de Contato de Hóspede (`POST /v1/contact-evaluations`)

Realiza a validação completa de formato, defasagem e consentimento de um contato.

#### Headers:
- `Content-Type: application/json`
- `X-Correlation-ID: <string>` *(opcional, se não enviado um UUID será gerado automaticamente)*

#### Parâmetros do Payload (JSON):
| Campo | Tipo | Obrigatório | Descrição |
|---|---|---|---|
| `customer_id` | String | Sim | Identificador do cliente/hóspede no sistema |
| `email` | String | Sim | Endereço de e-mail do cliente |
| `phone` | String | Sim | Número de telefone com código de país no padrão E.164 |
| `last_verified_at` | String (ISO-8601/Date) | Não | Data/hora da última verificação do contato |
| `consent_expires_at` | String (ISO-8601/Date) | Não | Data/hora de expiração do consentimento de contato (LGPD) |
| `reference_date` | String (YYYY-MM-DD) | Sim | Data base de referência para cálculo da defasagem e consentimento |

---

### Exemplos de Chamadas (cURL)

#### Cenário A: Contato Recente e Válido (`FRESH`)

**Requisição:**
```bash
curl -i -X POST http://127.0.0.1:8103/v1/contact-evaluations \
  -H "Content-Type: application/json" \
  -H "X-Correlation-ID: req-fresh-001" \
  -d '{
    "customer_id": "cust-001",
    "email": "marina.costa@tech.com",
    "phone": "+5511991234567",
    "last_verified_at": "2026-08-15T10:00:00Z",
    "consent_expires_at": "2027-01-01T00:00:00Z",
    "reference_date": "2026-08-27"
  }'
```

**Resposta esperada (HTTP 200 OK):**
```json
{
  "correlation_id": "req-fresh-001",
  "customer_id": "cust-001",
  "overall_status": "FRESH",
  "hygiene_score": 100,
  "factors": [
    {
      "type": "EMAIL",
      "value_masked": "m***a@tech.com",
      "status": "FRESH",
      "days_since_verificaton": 12,
      "issues": []
    },
    {
      "type": "PHONE",
      "value_masked": "+5511*****4567",
      "status": "FRESH",
      "days_since_verificaton": 12,
      "issues": []
    }
  ],
  "consent_valid": true,
  "recommended_action": "NONE"
}
```

---

#### Cenário B: Contato Obsoleto (`STALE` > 90 dias)

**Requisição:**
```bash
curl -i -X POST http://127.0.0.1:8103/v1/contact-evaluations \
  -H "Content-Type: application/json" \
  -H "X-Correlation-ID: req-stale-002" \
  -d '{
    "customer_id": "cust-002",
    "email": "joao.antigo@provedor.com.br",
    "phone": "+5521988887777",
    "last_verified_at": "2026-01-10T10:00:00Z",
    "consent_expires_at": "2026-12-31T00:00:00Z",
    "reference_date": "2026-08-27"
  }'
```

**Resposta esperada (HTTP 200 OK):**
```json
{
  "correlation_id": "req-stale-002",
  "customer_id": "cust-002",
  "overall_status": "STALE",
  "hygiene_score": 40,
  "factors": [
    {
      "type": "EMAIL",
      "value_masked": "j***o@provedor.com.br",
      "status": "STALE",
      "days_since_verificaton": 229,
      "issues": [
        "STALENESS_EXCEEDED_90_DAYS"
      ]
    },
    {
      "type": "PHONE",
      "value_masked": "+5521*****7777",
      "status": "STALE",
      "days_since_verificaton": 229,
      "issues": [
        "STALENESS_EXCEEDED_90_DAYS"
      ]
    }
  ],
  "consent_valid": true,
  "recommended_action": "TRIGGER_BACKGROUND_RECONFIRMATION"
}
```

---

#### Cenário C: Consentimento Expirado (`CONSENT_EXPIRED`)

**Requisição:**
```bash
curl -i -X POST http://127.0.0.1:8103/v1/contact-evaluations \
  -H "Content-Type: application/json" \
  -H "X-Correlation-ID: req-consent-003" \
  -d '{
    "customer_id": "cust-003",
    "email": "paulo.silva@empresa.com",
    "phone": "+5531977776666",
    "last_verified_at": "2026-08-20T10:00:00Z",
    "consent_expires_at": "2026-06-01T00:00:00Z",
    "reference_date": "2026-08-27"
  }'
```

**Resposta esperada (HTTP 200 OK):**
```json
{
  "correlation_id": "req-consent-003",
  "customer_id": "cust-003",
  "overall_status": "CONSENT_EXPIRED",
  "hygiene_score": 40,
  "factors": [
    {
      "type": "EMAIL",
      "value_masked": "p***a@empresa.com",
      "status": "FRESH",
      "days_since_verificaton": 7,
      "issues": []
    },
    {
      "type": "PHONE",
      "value_masked": "+5531*****6666",
      "status": "FRESH",
      "days_since_verificaton": 7,
      "issues": []
    }
  ],
  "consent_valid": false,
  "recommended_action": "TRIGGER_BACKGROUND_RECONFIRMATION"
}
```

---

#### Resposta em Caso de Erro (HTTP 400 Bad Request)

Quando o payload JSON estiver malformado ou campos obrigatórios/datas forem inválidos:

```json
{
  "error": "INVALID_PAYLOAD",
  "message": "Campo 'reference_date' inválido."
}
```

---

## Tecnologias Utilizadas

- **Linguagem:** Kotlin 1.9.24
- **Framework:** Ktor 2.3.12 (`ktor-server-core`, `ktor-server-netty`, `ktor-server-content-negotiation`, `ktor-server-status-pages`)
- **Serialização:** Kotlinx Serialization JSON 1.9.22
- **Logging:** Logback Classic 1.5.6
- **Testes:** JUnit 5 (Platform Engine) + Ktor Server Tests Host