# Assistant Service C++ (QLO-FEAT-001)

Serviço local de baixa latência em C++ para inferência e classificação semântica de intenções e entidades de reservas para o QloApps.

## Estrutura de Diretórios

```text
assistant-service-cpp/
├── CMakeLists.txt
├── Makefile
├── README.md
├── .gitignore
├── include/
│   ├── httplib.h       # cpp-httplib (v0.18.3, header-only)
│   └── json.hpp        # nlohmann/json (v3.11.3, header-only)
├── src/
│   └── main.cpp        # Ponto de entrada do serviço HTTP
└── tests/              # Testes unitários (a serem expandidos)
```

## Compilação e Execução

### Opção 1: Via Make
```bash
make
./assistant_service
```

### Opção 2: Linha Direta com g++
```bash
g++ -std=c++17 -Wall -Wextra -O2 -pthread -Iinclude src/main.cpp -o assistant_service
./assistant_service
```

O serviço iniciará na interface de loopback: `http://127.0.0.1:8101`.

## Endpoints

### 1. Healthcheck
- **Método:** `GET /healthz`
- **Resposta:** `{"status":"UP"}`

### 2. Interpretação de Consulta
- **Método:** `POST /v1/assist/interpret`
- **Headers:**
  - `Content-Type: application/json`
  - `X-Correlation-ID: <uuid>` (opcional, gerado pelo cliente)
- **Payload:**
  ```json
  {
    "query": "tem quarto suite para depois de amanha?",
    "reference_date": "2026-08-27"
  }
  ```
