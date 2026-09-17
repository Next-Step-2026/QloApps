# Assistant Service C++ (QLO-FEAT-001)

Serviço local de ultra-baixa latência em **C++17** para classificação semântica de intenções e extração de entidades em consultas de reservas hoteleiras no **QloApps**, concebido sob os princípios de **Clean Architecture** e **Domain-Driven Design (DDD)**.

---

## 🏛️ Arquitetura do Software

O serviço é modularizado em camadas desacopladas com responsabilidades bem delimitadas:

```text
assistant-service-cpp/
├── api/
│   └── openapi.yaml                 # Contrato OpenAPI 3.1.0 formal
├── include/
│   ├── domain/                      # Camada de Domínio (DDD)
│   │   ├── intent_types.hpp         # Tipos e estruturas de intenções e slots
│   │   ├── problem_details.hpp      # Modelo RFC 7807 Problem Details
│   │   ├── classifier.hpp           # Classificador semântico de intenções
│   │   └── slot_extractor.hpp       # Extrator de parâmetros e entidades
│   ├── validation/                  # Camada de Validação e Regras de Negócio
│   │   └── request_validator.hpp    # Validação estrita de contrato e payloads
│   ├── http/                        # Camada de Apresentação / Adaptadores HTTP
│   │   └── http_server.hpp          # Servidor cpp-httplib e roteamento REST
│   ├── text_normalizer.hpp          # Normalização de texto UTF-8 (ASCII folding)
│   ├── date_resolver.hpp            # Aritmética de datas temporais relativas
│   ├── httplib.h                    # cpp-httplib (v0.18.3, header-only)
│   └── json.hpp                     # nlohmann/json (v3.11.3, header-only)
├── src/
│   └── main.cpp                     # Ponto de entrada, configuração e tratamento de sinais
├── tests/
│   ├── test_classifier.cpp          # Testes unitários do motor classificador
│   ├── test_api.sh                  # Suíte de 25 testes de integração HTTP / RFC 7807
│   ├── run_contract_tests.sh        # Testes de conformidade de contrato (Schemathesis)
│   ├── k6_load_test.js              # Script de teste de carga k6 (SLA P95 < 20ms)
│   └── run_load_test.sh             # Runner automatizado de testes de carga
├── Doxyfile                         # Configuração Doxygen para geração de docs
└── Makefile                         # Build, compilação, testes e limpeza
```

---

## 🚀 Compilação e Execução

### Compilar o Serviço
```bash
make
```

### Iniciar o Serviço
```bash
./assistant_service
```
O serviço escutará exclusivamente em loopback: `http://127.0.0.1:8101`.

---

## 🧪 Pirâmide de Testes Automatizados

### 1. Testes Unitários e de Integração da API
Executa 4 testes unitários do classificador e 25 cenários de borda e conformidade RFC 7807:
```bash
make test
```

### 2. Testes de Contrato OpenAPI 3.1 (Schemathesis)
Executa fuzzing e validação de conformidade estrita contra a especificação `api/openapi.yaml`:
```bash
./tests/run_contract_tests.sh
```

### 3. Testes de Carga e Validação de SLAs (k6)
Executa 10 a 20 usuários simultâneos (VUs) validando o SLA de latência **P95 < 20ms** e taxa de erro < 1%:
```bash
./tests/run_load_test.sh
```

---

## 📡 Especificação de Endpoints

### 1. Verificação de Saúde
- **Rota:** `GET /healthz`
- **Resposta:** `200 OK`
```json
{
  "status": "UP"
}
```

### 2. Interpretação de Intenções de Reserva
- **Rota:** `POST /v1/assist/interpret`
- **Headers Obrigatórios:**
  - `Content-Type: application/json; charset=utf-8`
  - `X-Correlation-ID: req-xxxxxxxxxxxx`
- **Exemplo de Requisição:**
```json
{
  "query": "Tem quarto deluxe para 2 adultos depois de amanhã?",
  "reference_date": "2026-08-27",
  "locale": "pt-BR"
}
```
- **Exemplo de Resposta de Sucesso (`200 OK`):**
```json
{
  "intent": "AVAILABILITY_QUERY",
  "confidence": 0.95,
  "explanation": "Identificado tipo de quarto 'deluxe', contagem de hospedes (2) e data relativa 'depois de amanha' calculada como 2026-08-29 baseada em 2026-08-27.",
  "correlation_id": "req-xxxxxxxxxxxx",
  "slots": {
    "room_type": "deluxe",
    "check_in": "2026-08-29",
    "check_out": "2026-08-30",
    "guests": 2
  }
}
```

---

## 🛡️ Tratamento de Erros e Contingência (RFC 7807)

Quando um payload ou header viola as regras de contrato, o serviço responde com `application/problem+json`:
```json
{
  "type": "https://hotel.local/errors/invalid-payload",
  "title": "Invalid Request Payload",
  "status": 400,
  "detail": "Field 'reference_date' is required and must follow YYYY-MM-DD format.",
  "code": "MISSING_REFERENCE_DATE",
  "field": "reference_date"
}
```
