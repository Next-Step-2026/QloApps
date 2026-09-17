# QloApps Sidecar Services — Master Architecture & RFC Catalog

> **Documento:** Master Architecture Specification & Service Catalog (v1.0-GA)  
> **Status:** Approved for Implementation (Milestone de 2 Semanas)  
> **Padrão:** Production Engineering RFC / Turnkey Architecture  

---

## 1. Visão Geral da Arquitetura do Sistema

O ecossistema **QloApps Sidecar Services** estabelece uma arquitetura híbrida de alta performance e desacoplamento para a plataforma de gestão hoteleira QloApps.

O núcleo do QloApps (PHP 8.1+, MySQL e Smarty) permanece inalterado (*zero core modification*). Cada funcionalidade especializada é implementada como um **Serviço Local Autônomo (Sidecar)** na linguagem nativa mais adequada para o domínio (C++, Python ou Kotlin), comunicando-se exclusivamente via HTTP/JSON sobre a interface de loopback (`127.0.0.1`).

```text
┌─────────────────────────────────────────────────────────────────────────────┐
│                             NAVEGADOR WEB (CLIENTE)                         │
└──────────────────────────────────────┬──────────────────────────────────────┘
                                       │ HTTPS / HTTP
                                       ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                  QLOAPPS CORE & BACK-OFFICE (PHP 8.1+ / SMARTY)             │
│                                                                             │
│  ┌───────────────────────┐ ┌───────────────────────┐ ┌───────────────────┐ │
│  │ qloreservationassist. │ │  qlovisualinspection  │ │ qlocontacthealth  │ │
│  └───────────┬───────────┘ └───────────┬───────────┘ └─────────┬─────────┘ │
│  ┌───────────┴───────────┐ ┌───────────┴───────────┐ ┌─────────┴─────────┐ │
│  │   qloarrivalsharing   │ │ qloreservationpolicy  │ │qloexternalrequests│ │
│  └───────────┬───────────┘ └───────────┬───────────┘ └─────────┬─────────┘ │
│  ┌───────────┴───────────┐ ┌───────────┴───────────┐                     │
│  │   qloinventoryaudit   │ │  qloactionablesearch  │                     │
│  └───────────┬───────────┘ └───────────┬───────────┘                     │
└──────────────┼─────────────────────────┼─────────────────────────────────┘
               │ HTTP POST (cURL síncrono, timeout 600-800ms)
               │ Interface: 127.0.0.1 (Exclusivo Loopback Local)
               ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                    SERVIÇOS LOCAIS DE ALTA PERFORMANCE                      │
│                                                                             │
│  [Porta 8101] C++17/20    : Inferência e Consulta de Intenções              │
│  [Porta 8102] Python 3.10 : Inspeção Visual e Métricas de Imagem (Pillow)   │
│  [Porta 8103] Kotlin 1.9  : Avaliação de Saúde e Higiene de Contatos        │
│  [Porta 8104] Kotlin/Java : Geofencing e Detecção de Proximidade            │
│  [Porta 8105] Python 3.10 : Motor Determinístico de Políticas de Exceção    │
│  [Porta 8106] Kotlin 1.9  : Conversor Canônico de Solicitações Multicanal   │
│  [Porta 8107] C++17/20    : Auditor de Sobreposição de Intervalos           │
│  [Porta 8108] C++17/20    : Motor Lexical de Busca e Catálogo               │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## 2. Catálogo de Serviços e Matriz de Portas

| RFC | Feature | Engenharia Responsável | Stack do Serviço | Módulo QloApps | Porta Local | Healthcheck |
| :--- | :--- | :--- | :---: | :---: | :---: | :--- |
| [`RFC-001`](./RFC-001-copiloto-reservas-observavel.md) | **Copiloto de Intenções de Reserva** | Engenharia de Backend & Inferência | C++17/20 | `qloreservationassistant` | `8101` | `GET :8101/healthz` |
| [`RFC-002`](./RFC-002-inspecao-visual-quartos.md) | **Inspeção Visual e Avaliação de Imagem** | Engenharia de Visão Computacional | Python 3.10+ | `qlovisualinspection` | `8102` | `GET :8102/healthz` |
| [`RFC-003`](./RFC-003-saude-contatos-recuperacao.md) | **Avaliador de Saúde de Contatos** | Engenharia de Identidade & Acesso | Kotlin 1.9+ | `qlocontacthealth` | `8103` | `GET :8103/healthz` |
| [`RFC-004`](./RFC-004-localizacao-chegada-traslado.md) | **Geofencing e Detecção de Chegada** | Engenharia de Sistemas de Localização | Kotlin/Java | `qloarrivalsharing` | `8104` | `GET :8104/healthz` |
| [`RFC-005`](./RFC-005-motor-politicas-reserva-inventario.md) | **Motor de Políticas de Reserva** | Engenharia de Políticas & Tarifação | Python 3.10+ | `qloreservationpolicy` | `8105` | `GET :8105/healthz` |
| [`RFC-006`](./RFC-006-conversor-solicitacao-reserva.md) | **Conversor Canônico Multicanal** | Engenharia de Integração de Canais | Kotlin 1.9+ | `qloexternalrequests` | `8106` | `GET :8106/healthz` |
| [`RFC-007`](./RFC-007-auditor-inventario-reservas.md) | **Auditor de Sobreposição de Inventário** | Engenharia de Algoritmos & Concorrência | C++17/20 | `qloinventoryaudit` | `8107` | `GET :8107/healthz` |
| [`RFC-008`](./RFC-008-busca-entidades-acionaveis.md) | **Motor Lexical de Busca e Catálogo** | Engenharia de Sistemas de Busca | C++17/20 | `qloactionablesearch` | `8108` | `GET :8108/healthz` |

---

## 3. Padrão de Comunicação e Protocolo IPC

1. **Transporte:** HTTP/1.1 sobre socket TCP local em `127.0.0.1`.
2. **Formato de Carga:** `application/json; charset=utf-8` (exceto RFC-002 que utiliza `multipart/form-data`).
3. **Rastreabilidade Obrigatória:** Toda requisição originada no PHP gera e propaga o header `X-Correlation-ID: <uuid-v4>`, que é retornado no corpo da resposta e registrado nos logs estruturados.
4. **Formato de Erro Padronizado (RFC 7807 - Problem Details):**

   ```json
   {
     "type": "https://hotel.local/errors/<error-code>",
     "title": "<Mensagem Curta>",
     "status": 400,
     "detail": "<Explicação Detalhada do Erro>",
     "instance": "/v1/<endpoint>"
   }
   ```

5. **Timeouts Estritos:**
   - Chamadas PHP cURL configuradas com `CURLOPT_TIMEOUT_MS = 600` (800ms para imagem no RFC-002).
   - O QloApps implementa degradação graciosa com alertas amigáveis caso o serviço local esteja inativo (HTTP 503).

---

## 4. Estrutura Padrão de Integração com o QloApps

Cada funcionalidade é empacotada como um módulo nativo do QloApps em `QloApps/modules/<nome_do_modulo>/` contendo exatamente três arquivos essenciais:

```text
QloApps/modules/<nome_do_modulo>/
├── <nome_do_modulo>.php                # Classe raiz: declaração, install(), uninstall(), installTab()
├── controllers/
│   └── admin/
│       └── Admin<Nome>Controller.php   # Controller: renderView(), postProcess(), cURL cliente
└── views/
    └── templates/
        └── admin/
            └── <view_name>.tpl         # Template Smarty: formulário, badges e tabela de resultados
```

---

## 5. Política de Segurança, Sandboxing e Privacidade

- **Isolamento de Rede:** Nenhum serviço local escuta em interfaces externas (`0.0.0.0` é estritamente proibido). O bind é restrito a `127.0.0.1`.
- **Zero Acesso Direto ao Banco:** Os serviços locais não abrem conexões MySQL. Todo acesso a dados é mediado pelo QloApps e enviado via payload JSON sanitizado.
- **Minimização de PII:** Nomes e telefones são mascarados em todas as saídas de console e logs (`c***a@empresa.com`, `+55 11 *****-4321`).
- **Operação Offline Total:** Nenhuma dependência de APIs externas de nuvem (OpenAI, AWS, Google Cloud) é utilizada no MVP de 2 semanas.

---

## 6. Script de Verificação Automatizada da Suíte

O script abaixo pode ser executado para validar a integridade de todas as especificações e contratos da suíte:

```bash
python3 -c "
from pathlib import Path
root = Path('.')
rfc_files = sorted(root.glob('0*-*.md'))
print(f'Total de especificações RFC homologadas: {len(rfc_files)}')
assert len(rfc_files) == 8, 'Esperava 8 arquivos RFC'
for f in rfc_files:
    lines = len(f.read_text().splitlines())
    print(f'  ✓ {f.name:45s} ({lines:4d} linhas)')
print('Suíte de Engenharia 100% Homologada!')
"
```
