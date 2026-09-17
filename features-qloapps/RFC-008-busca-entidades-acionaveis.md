# RFC-008 — Busca e Reconhecimento de Entidades Acionáveis

## 1. Identificação e Informações do Projeto

- **Código da Feature:** `QLO-FEAT-008`
- **Nome da Feature:** Motor Lexical de Busca Rápida e Reconhecimento de Entidades de Catálogo
- **Engenharia Responsável:** Engenharia de Sistemas de Busca, Indexação & NLU
- **Stack do Desenvolvedor:** C++ (C++17/20, backend/algoritmos de indexação e tokenização)
- **Stack de Apresentação:** Módulo QloApps (`qloactionablesearch`, PHP 8.1+ / Smarty 3.x / HTML / CSS)
- **Porta de Execução Local:** `http://127.0.0.1:8108`
- **Prazo de Execução:** Milestone de 2 Semanas (10 dias úteis de sprint focado)

---

## 2. Contexto de Negócio e Motivação Operacional

Em balcões de atendimento e centrais de reservas, atendentes e clientes buscam opções de acomodação digitando frases livres combinando múltiplos critérios: tipo de quarto (*"suíte master"*), comodidades desejadas (*"vista mar"*, *"banheira"*, *"ar condicionado"*) e quantidade de ocupantes (*"2 adultos"*).

O mecanismo de busca nativo de sistemas tradicionais baseia-se em correspondência SQL exata ou exige preencher múltiplos campos de formulário separados. Quando o atendente comete erros de acentuação, usa sinônimos ou altera a ordem das palavras, a busca retorna zero resultados, causando perda de vendas e lentidão no atendimento.

O **Motor de Busca de Entidades Acionáveis** resolve essa dor executando um pipeline local em C++ de alta performance: ele normaliza o texto (eliminando acentos e padronizando para minúsculas), extrai filtros estruturados de capacidade (ex.: *"2 adultos"* $\rightarrow$ `adults = 2`), consulta um índice invertido em memória para recuperar entidades correspondentes e devolve tags visuais (*chips*) e IDs de quartos compatíveis para preenchimento imediato no QloApps.

---

## 3. Histórias de Usuário e Personas

### Personas

- **Carla (Atendente de Reservas):** Precisa localizar rapidamente opções de quartos enquanto conversa com o hóspede, sem precisar clicar em filtros avançados.
- **Tiago (Gerente de Marketing e Vendas):** Deseja que os termos pesquisados frequentemente sejam mapeados para comodidades e categorias de quarto reais do hotel.

### Histórias de Usuário

1. **Busca Tolerante a Acentuação e Caixa:**
   - *Como* atendente de recepção,
   - *Quero* digitar `"SUITE VISTA MAR"` ou `"suite vista mar"`,
   - *Para que* o sistema reconheça a categoria `Suíte Master Vista Mar` independentemente de acentos ou maiúsculas.
2. **Extração Automática de Ocupação:**
   - *Como* atendente,
   - *Quero* incluir `"para 3 adultos"` na mesma frase da busca,
   - *Para que* o sistema filtre automaticamente apenas quartos com capacidade mínima de 3 pessoas.
3. **Identificação de Comodidades Específicas:**
   - *Como* atendente,
   - *Quero* pesquisar por comodidades (ex.: `"com banheira e varanda"`),
   - *Para que* o sistema me retorne as opções de quarto que possuem essas características cadastradas.

---

## 4. Escopo Operacional e Limites da Entrega

### No Escopo (In-Scope para o MVP)

- Módulo QloApps (`qloactionablesearch`) com barra de pesquisa rápida e painel de resultados no back-office.
- Envio da consulta textual e do catálogo de teste (JSON com até 100 entidades) via HTTP POST para o serviço C++.
- Serviço local em C++ escutando em `127.0.0.1:8108` com endpoint `POST /v1/search/parse`.
- Algoritmo de normalização Unicode em C++ (remoção de acentos/diacríticos e conversão para minúsculas).
- Extrator de filtros de ocupação via expressões regulares (ex.: `"X adultos"`, `"X pessoas"`).
- Índice invertido em memória (`std::unordered_map<std::string, std::vector<std::string>>`) mapeando tokens para IDs de entidades.
- Interseção de tokens para correspondência conjuntiva (AND search) e retorno de lista ordenada de IDs.
- Renderização visual no QloApps com tags (*chips*) das entidades identificadas e cards dos quartos encontrados.
- Resiliência com timeout de 600ms e tratamento no PHP caso o executável C++ esteja desligado.

### Fora do Escopo (Out-of-Scope para Versão 2)

- Motores distribuídos externos de busca (Elasticsearch, Solr, OpenSearch).
- Modelos neurais de busca vetorial (Embeddings, Cross-Encoders).
- Ranking ponderado por histórico de cliques ou personalização por perfil de hóspede.

---

## 5. Mapa de Entidades, Ciclo de Vida e Estados

### 5.1. Mapeamento de Tabelas Relacionais do QloApps (MySQL)
A busca lexical indexa os termos contidos no catálogo de produtos e comodidades:
- `ps_product_lang`: Nomes e descrições dos tipos de quarto (`name`, `description_short`).
- `ps_feature_value_lang`: Nomes das comodidades cadastradas (ex.: Ar Condicionado, Vista Mar).
- `ps_htl_room_type`: Capacidade máxima de adultos e crianças por categoria de quarto.

### Diagrama de Estados do Pipeline de Busca

```text
  [Consulta de Busca Digitada]
                 │
                 ▼
  [Normalização Textual (NFD sem Acentos / Lowercase)]
                 │
                 ▼
  [Extração de Filtros Estruturados (Regex: adults, capacity)]
                 │
                 ▼
  [Consulta ao Índice Invertido por Tokens]
        ├── Correspondência Encontrada ──► [Lista de IDs de Quartos Compatíveis (200 OK)]
        │
        └── Sem Correspondência        ──► [Resultado Vazio: total_matches = 0 (200 OK)]
```

---

## 6. Topologia de Comunicação e Fluxo de Dados Ponta a Ponta

```text
[Navegador / Atendente]
         │
         │ 1. Digita: "suite com vista para o mar 2 adultos"
         ▼
[QloApps Back-Office: Módulo qloactionablesearch (PHP/Smarty)]
         │
         │ 2. Empacota texto da query e catálogo de teste em JSON
         ▼ (HTTP POST síncrono em loopback, timeout 600ms)
[Serviço Local C++: http://127.0.0.1:8108/v1/search/parse]
   ├── 3.1. Normalizador de Texto (ASCII/NFD sem acentos)
   ├── 3.2. Extrator de Filtros Estruturados (Capacidade)
   ├── 3.3. Tokenizador e Stopword Remover
   └── 3.4. Buscador por Interseção de Índice Invertido
         │
         │ 3.5. Retorna JSON com Tokens, Filtros e IDs Encontrados
         ▼
[QloApps Módulo PHP]
         │
         │ 4. Renderiza chips de comodidades e cards dos quartos compatíveis
         ▼
[Navegador / Atendente]
```

### Estrutura de Diretórios Recomendada

```text
projeto/
├── qloactionablesearch/               # Módulo PHP para QloApps
│   ├── qloactionablesearch.php        # Registro do módulo e menus
│   ├── config.xml                     # Metadados
│   ├── controllers/
│   │   └── admin/
│   │       └── AdminActionableSearchController.php # Controller administrativo
│   └── views/
│       └── templates/
│           └── admin/
│               └── search_dashboard.tpl # View Smarty com barra e cards
│
└── search-service-cpp/                # Serviço Local C++
    ├── CMakeLists.txt (ou Makefile)
    ├── include/
    │   ├── httplib.h                  # cpp-httplib (header-only)
    │   ├── json.hpp                   # nlohmann/json (header-only)
    │   ├── Normalizer.hpp             # Normalização de strings e acentos
    │   └── InvertedIndex.hpp          # Estrutura do índice em memória
    ├── src/
    │   ├── main.cpp                   # Servidor HTTP e rotas
    │   ├── Normalizer.cpp             # Implementação de remoção de acentos
    │   └── InvertedIndex.cpp          # Lógica de indexação e busca
    └── tests/
        └── test_search.cpp            # Testes unitários com casos de busca
```

---

## 7. Regras de Negócio Detalhadas e Tabela de Casos de Borda

| ID | Regra de Negócio | Condição de Entrada | Comportamento Esperado | Caso de Borda / Tratamento |
| --- | --- | --- | --- | --- |
| **RN-001** | **Insensibilidade a Acentos e Caixa** | Termos com acentos (ex.: *"suíte"*, *"não"*, *"piscina climatizada"*) | Normalizar para *"suite"*, *"nao"*, *"piscina climatizada"* antes de consultar o índice. | Garantir que caracteres Unicode sejam normalizados sem corromper bytes. |
| **RN-002** | **Extração de Filtro de Adultos** | Padrões como `"2 adultos"`, `"3 pessoas"`, `"1 hospede"` | Extrair inteiro correspondente em `extracted_filters.adults`. | Desconsiderar números soltos que não acompanhem palavras-chave de ocupação. |
| **RN-003** | **Filtragem por Capacidade Mínima** | `extracted_filters.adults` presente | Excluir dos resultados entidades com `capacity_adults < adults`. | Quarto com capacidade 3 deve aparecer em busca de 2 adultos. |
| **RN-004** | **Correspondência Conjuntiva (AND)** | Múltiplos termos informados (ex.: `"suite"` e `"mar"`) | Retornar apenas entidades que possuam **todos** os tokens pesquisados. | Se nenhum quarto contiver todos os termos, retornar lista vazia `total_matches = 0`. |
| **RN-005** | **Remoção de Stopwords** | Palavras vazias como *"com"*, *"para"*, *"de"*, *"o"*, *"a"* | Filtrar stopwords antes de consultar o índice para evitar ruído. | Não descartar termos curtos que sejam significativos (ex.: *"mar"*). |
| **RN-006** | **Limite de Entidades de Catálogo** | Catálogo no payload com mais de 100 itens | Rejeitar com HTTP `400` para proteger o tempo de resposta no sprint. | Catálogo deve conter IDs únicos. |

---

## 8. Especificação Completa do Contrato de API (OpenAPI / RFC 7807)

### Endpoint Local

- **URL:** `http://127.0.0.1:8108/v1/search/parse`
- **Método:** `POST`
- **Headers Obrigatórios:**
  - `Content-Type: application/json`
  - `X-Correlation-ID: <uuid-v4>`

### Schema de Entrada (Request Body)

```json
{
  "$schema": "http://json-schema.org/draft-07/schema#",
  "type": "object",
  "required": ["query", "catalog"],
  "properties": {
    "query": { "type": "string", "minLength": 1, "maxLength": 256 },
    "catalog_version": { "type": "string" },
    "catalog": {
      "type": "array",
      "maxItems": 100,
      "items": {
        "type": "object",
        "required": ["id", "type", "title", "capacity_adults", "amenities", "aliases"],
        "properties": {
          "id": { "type": "string" },
          "type": { "type": "string", "enum": ["ROOM_TYPE", "AMENITY", "PROPERTY"] },
          "title": { "type": "string" },
          "capacity_adults": { "type": "integer", "minimum": 1 },
          "amenities": { "type": "array", "items": { "type": "string" } },
          "aliases": { "type": "array", "items": { "type": "string" } }
        }
      }
    }
  }
}
```

### Exemplo de Requisição (Request)

```json
{
  "query": "suite com vista para o mar 2 adultos",
  "catalog_version": "v1-demo",
  "catalog": [
    {
      "id": "room-suite-01",
      "type": "ROOM_TYPE",
      "title": "Suíte Master Vista Mar",
      "capacity_adults": 2,
      "amenities": ["vista_mar", "ar_condicionado", "banheira"],
      "aliases": ["suite", "suite master", "vista mar"]
    },
    {
      "id": "room-std-02",
      "type": "ROOM_TYPE",
      "title": "Quarto Standard Casal",
      "capacity_adults": 2,
      "amenities": ["ar_condicionado"],
      "aliases": ["standard", "casal"]
    },
    {
      "id": "room-sgl-03",
      "type": "ROOM_TYPE",
      "title": "Quarto Single Individual",
      "capacity_adults": 1,
      "amenities": ["ventilador"],
      "aliases": ["single", "solteiro"]
    }
  ]
}
```

### Exemplo de Resposta (Response: 200 OK)

```json
{
  "correlation_id": "9b1deb4d-3b7d-4bad-9bdd-2b0d7b3dcb6d",
  "tokens_matched": ["suite", "vista", "mar"],
  "extracted_filters": {
    "adults": 2,
    "amenities": ["vista_mar"]
  },
  "matching_entity_ids": [
    "room-suite-01"
  ],
  "total_matches": 1
}
```

### Schema de Erro Estruturado (RFC 7807 — 400 Bad Request)

```json
{
  "type": "https://hotel.local/errors/invalid-query",
  "title": "Consulta de Busca Inválida",
  "status": 400,
  "detail": "O campo 'query' não pode ser vazio.",
  "instance": "/v1/search/parse"
}
```

### Matriz de Códigos HTTP

| Código | Nome | Condição | Ação do QloApps |
| --- | --- | --- | --- |
| `200` | OK | Consulta processada e entidades localizadas. | Renderiza chips de tags e cards dos quartos. |
| `400` | Bad Request | Query vazia ou catálogo $> 100$ itens. | Exibe mensagem de validação de busca. |
| `503` | Service Unavailable | Serviço C++ offline na porta 8108. | Renderiza aviso de contingência no painel. |

---

## 9. Massa de Dados de Teste e Cenários de Fixture

```json
[
  {
    "description": "Busca por suíte com comodidade vista mar",
    "input": {
      "query": "SUÍTE VISTA MAR",
      "catalog_version": "v1"
    },
    "expected_output": {
      "tokens_matched": ["suite", "vista", "mar"],
      "matching_entity_ids": ["room-suite-01"],
      "total_matches": 1
    }
  },
  {
    "description": "Busca com filtro de 2 adultos (exclui single)",
    "input": {
      "query": "quarto para 2 adultos",
      "catalog_version": "v1"
    },
    "expected_output": {
      "extracted_filters": { "adults": 2 },
      "matching_entity_ids": ["room-suite-01", "room-std-02"],
      "total_matches": 2
    }
  },
  {
    "description": "Termo sem correspondência no catálogo",
    "input": {
      "query": "chalé na montanha com lareira",
      "catalog_version": "v1"
    },
    "expected_output": {
      "matching_entity_ids": [],
      "total_matches": 0
    }
  }
]
```

---

## 10. Critérios de Aceitação em BDD (Gherkin: Given-When-Then)

```gherkin
Feature: Busca e Reconhecimento de Entidades Acionáveis de Catálogo

  Scenario: Busca com acentuação e caixa alta localiza entidade correta
    Given que o serviço C++ de busca está ativo em "http://127.0.0.1:8108"
    When o atendente pesquisa pelo termo "SUÍTE VISTA MAR"
    Then o status HTTP da resposta deve ser 200
    And o array "matching_entity_ids" deve conter "room-suite-01"
    And o campo "total_matches" deve ser 1

  Scenario: Filtro de capacidade exclui acomodações insuficientes
    Given que o serviço C++ está ativo
    When o atendente pesquisa por "quarto para 2 adultos"
    Then o status HTTP deve ser 200
    And o campo "extracted_filters.adults" deve ser 2
    And a lista de resultados não deve conter a entidade "room-sgl-03"

  Scenario: Busca de termos inexistentes retorna lista vazia
    Given que o serviço C++ está ativo
    When o atendente pesquisa por "apartamento presidencial"
    Then o status HTTP deve ser 200
    And o campo "total_matches" deve ser 0
    And o array "matching_entity_ids" deve ser vazio

  Scenario: Queda do serviço de busca C++
    Given que o binário C++ na porta 8108 está desligado
    When o atendente realiza uma busca no QloApps
    Then o módulo PHP deve capturar o timeout em no máximo 600ms
    And a página deve renderizar mensagem informativa de contingência
```

---

## 11. Guia de Integração com o QloApps (Módulo PHP / Smarty)

### Classe Principal do Módulo (`qloactionablesearch.php`)
```php
<?php
// modules/qloactionablesearch/qloactionablesearch.php

if (!defined('_PS_VERSION_')) {
    exit;
}

class QloActionableSearch extends Module
{
    public function __construct()
    {
        $this->name = 'qloactionablesearch';
        $this->tab = 'front_office_features';
        $this->version = '1.0.0';
        $this->author = 'QloApps Engineering';
        $this->need_instance = 0;
        $this->bootstrap = true;

        parent::__construct();

        $this->displayName = $this->l('Busca de Entidades Acionáveis');
        $this->description = $this->l('Motor lexical rápido para pesquisa de quartos e comodidades de catálogo.');
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
        $tab->class_name = 'AdminActionableSearch';
        $tab->name = array();
        foreach (Language::getLanguages(true) as $lang) {
            $tab->name[$lang['id_lang']] = 'Busca de Entidades';
        }
        $tab->id_parent = (int) Tab::getIdFromClassName('AdminParentOrders');
        $tab->module = $this->name;
        return $tab->add();
    }

    private function uninstallTab()
    {
        $idTab = (int) Tab::getIdFromClassName('AdminActionableSearch');
        if ($idTab) {
            $tab = new Tab($idTab);
            return $tab->delete();
        }
        return true;
    }
}
```

### Controller PHP (`AdminActionableSearchController.php`)

```php
<?php
// modules/qloactionablesearch/controllers/admin/AdminActionableSearchController.php

class AdminActionableSearchController extends ModuleAdminController
{
    public function __construct()
    {
        $this->bootstrap = true;
        parent::__construct();
    }

    public function initContent()
    {
        parent::initContent();

        $searchResult = null;
        $errorMessage = null;

        if (Tools::isSubmit('submitSearchQuery')) {
            $userQuery = trim(Tools::getValue('search_query'));
            $corrId    = Tools::passwdGen(16, 'ALPHANUMERIC');

            $mockCatalog = [
                [
                    'id'              => 'room-suite-01',
                    'type'            => 'ROOM_TYPE',
                    'title'           => 'Suíte Master Vista Mar',
                    'capacity_adults' => 2,
                    'amenities'       => ['vista_mar', 'ar_condicionado', 'banheira'],
                    'aliases'         => ['suite', 'suite master', 'vista mar']
                ],
                [
                    'id'              => 'room-std-02',
                    'type'            => 'ROOM_TYPE',
                    'title'           => 'Quarto Standard Casal',
                    'capacity_adults' => 2,
                    'amenities'       => ['ar_condicionado'],
                    'aliases'         => ['standard', 'casal']
                ],
                [
                    'id'              => 'room-sgl-03',
                    'type'            => 'ROOM_TYPE',
                    'title'           => 'Quarto Single Individual',
                    'capacity_adults' => 1,
                    'amenities'       => ['ventilador'],
                    'aliases'         => ['single', 'solteiro']
                ]
            ];

            $payload = json_encode([
                'query'           => $userQuery,
                'catalog_version' => 'v1-demo',
                'catalog'         => $mockCatalog
            ]);

            $ch = curl_init('http://127.0.0.1:8108/v1/search/parse');
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
                $searchResult = json_decode($response, true);
            } else {
                $errorMessage = 'Motor de busca C++ offline (HTTP ' . $httpCode . ').';
            }
        }

        $this->context->smarty->assign([
            'searchResult' => $searchResult,
            'searchError'  => $errorMessage
        ]);

        $this->setTemplate('search_dashboard.tpl');
    }
}
```

### Template Smarty (`search_dashboard.tpl`)

```html
<div class="panel">
    <div class="panel-heading">
        <i class="icon-search"></i> Busca Rápida de Quartos e Entidades de Catálogo
    </div>

    {if $searchError}
        <div class="alert alert-warning">
            <i class="icon-warning-sign"></i> {$searchError}
        </div>
    {/if}

    <form method="post" action="" class="form-horizontal">
        <div class="form-group">
            <label class="control-label col-lg-3">Busca Livre (ex: suite mar 2 pessoas):</label>
            <div class="col-lg-7">
                <input type="text" name="search_query" placeholder="Digite os termos da acomodação desejada..." class="form-control" autofocus required />
            </div>
            <div class="col-lg-2">
                <button type="submit" name="submitSearchQuery" class="btn btn-primary btn-block">
                    <i class="icon-search"></i> Buscar
                </button>
            </div>
        </div>
    </form>

    {if $searchResult}
        <hr />
        <div class="well">
            <h4><i class="icon-tags"></i> Entidades e Filtros Identificados:</h4>
            <p>
                <strong>Termos Casados:</strong> 
                {foreach from=$searchResult.tokens_matched item=tok}
                    <span class="badge badge-info">{$tok}</span>
                {/foreach}
            </p>

            {if $searchResult.extracted_filters.adults}
                <p><strong>Filtro de Ocupação:</strong> <span class="label label-primary">{$searchResult.extracted_filters.adults} Adulto(s)</span></p>
            {/if}

            <h4><i class="icon-building"></i> Quartos Compatíveis ({$searchResult.total_matches}):</h4>
            {if $searchResult.total_matches > 0}
                <ul class="list-group">
                    {foreach from=$searchResult.matching_entity_ids item=entityId}
                        <li class="list-group-item">
                            <i class="icon-ok text-success"></i> Código do Quarto: <strong>{$entityId}</strong>
                        </li>
                    {/foreach}
                </ul>
            {else}
                <p class="text-muted">Nenhum quarto atendeu a todos os critérios da busca.</p>
            {/if}
        </div>
    {/if}
</div>
```

---

### 11.4. Código Inicial Standalone do Serviço C++ (`main.cpp`)
```cpp
// search-service-cpp/src/main.cpp
#include <iostream>
#include <vector>
#include <unordered_map>
#include <string>
#include <regex>
#include <algorithm>
#include "../include/httplib.h"
#include "../include/json.hpp"

using json = nlohmann::json;

std::string normalizeString(std::string str) {
    std::transform(str.begin(), str.end(), str.begin(), ::tolower);
    std::unordered_map<std::string, std::string> diacritics = {
        {"á","a"},{"à","a"},{"ã","a"},{"â","a"},{"é","e"},{"ê","e"},{"í","i"},{"ó","o"},{"ô","o"},{"õ","o"},{"ú","u"},{"ç","c"}
    };
    for (const auto& pair : diacritics) {
        size_t pos = 0;
        while ((pos = str.find(pair.first, pos)) != std::string::npos) {
            str.replace(pos, pair.first.length(), pair.second);
            pos += pair.second.length();
        }
    }
    return str;
}

int main() {
    httplib::Server svr;

    svr.Get("/healthz", [](const httplib::Request&, httplib::Response& res) {
        res.set_content("{\"status\":\"UP\"}", "application/json");
    });

    svr.Post("/v1/search/parse", [](const httplib::Request& req, httplib::Response& res) {
        try {
            auto body = json::parse(req.body);
            std::string queryNorm = normalizeString(body.value("query", ""));

            int reqAdults = 0;
            std::smatch match;
            std::regex adultsRegex(R"((\d+)\s*(adulto|adultos|pessoas|hospedes))");
            if (std::regex_search(queryNorm, match, adultsRegex)) {
                reqAdults = std::stoi(match[1].str());
            }

            json matchingIds = json::array();
            for (const auto& item : body["catalog"]) {
                std::string titleNorm = normalizeString(item.value("title", ""));
                int capacity = item.value("capacity_adults", 1);
                if (reqAdults > 0 && capacity < reqAdults) continue;

                if (queryNorm.find("suite") != std::string::npos && titleNorm.find("suite") != std::string::npos) {
                    matchingIds.push_back(item["id"]);
                } else if (queryNorm.find("standard") != std::string::npos && titleNorm.find("standard") != std::string::npos) {
                    matchingIds.push_back(item["id"]);
                }
            }

            json response;
            response["correlation_id"] = req.has_header("X-Correlation-ID") ? req.get_header_value("X-Correlation-ID") : "corr-demo";
            response["tokens_matched"] = json::array({"suite", "mar"});
            response["extracted_filters"] = {{"adults", reqAdults > 0 ? json(reqAdults) : json(nullptr)}};
            response["matching_entity_ids"] = matchingIds;
            response["total_matches"] = matchingIds.size();

            res.status = 200;
            res.set_content(response.dump(), "application/json");
        } catch (const std::exception& e) {
            res.status = 400;
            res.set_content("{\"error\":\"MALFORMED_JSON\"}", "application/json");
        }
    });

    std::cout << "[QLO-FEAT-008] Servico de Busca C++ escutando em http://127.0.0.1:8108" << std::endl;
    svr.listen("127.0.0.1", 8108);
    return 0;
}
```

## 12. Observabilidade, Logs Estruturados e SLAs Operacionais

### Níveis de Serviço (SLAs / SLOs)

- **Latência P95:** $< 5\text{ ms}$ para tokenização e busca em índice invertido em C++.
- **Latência Total Ponta a Ponta (PHP + cURL + C++):** $< 50\text{ ms}$.
- **Timeout Máximo do Cliente:** $600\text{ ms}$.

### Formato de Log Estruturado (Stdout do Serviço C++)

```json
{
  "timestamp": "2026-08-27T10:50:00.312Z",
  "level": "INFO",
  "correlation_id": "9b1deb4d-3b7d-4bad-9bdd-2b0d7b3dcb6d",
  "event": "SEARCH_PARSED",
  "query": "suite com vista para o mar 2 adultos",
  "tokens_count": 3,
  "matches_count": 1,
  "duration_ms": 0.95
}
```

---

## 13. Matriz de Riscos, Segurança e Privacidade

| Ameaça Identificada | Impacto | Nível | Controle Técnico Aplicado |
| --- | --- | --- | --- |
| **Injeção de Caracteres de Controle em C++** | Falha de parser ou crash | Alto | Normalizador rejeita bytes nulos e caracteres não imprimíveis. |
| **Exaustão de Memória por Catálogo Excessivo** | Queda do processo | Médio | Validação rígida no PHP limitando o payload a no máximo 100 itens. |
| **Exposição de Porta em Interface Aberta** | Acesso não autenticado | Alto | Servidor HTTP C++ configurado estritamente em `127.0.0.1:8108`. |

---

## 14. Guia de Diagnóstico e Resolução de Problemas (Troubleshooting FAQ)

### FAQ Técnico

1. **Erro: `cURL error 7: Failed to connect to 127.0.0.1 port 8108`**
   - *Causa:* O executável C++ de busca não foi iniciado.
   - *Solução:* Execute `./search_service` no terminal dentro do diretório do serviço.
2. **Termos com acento retornando zero resultados**
   - *Causa:* Falha na etapa de normalização NFD de caracteres acentuados.
   - *Solução:* Verifique se a função `normalizeString()` decompõe e remove os bytes de diacríticos corretamente.
3. **Filtro de adultos não extraído de frases como "2 pessoas"**
   - *Causa:* Expressão regular cobrindo apenas a palavra "adultos".
   - *Solução:* Ampliar a regex para incluir `(adultos|pessoas|hospedes)`.

---

## 15. Plano de Execução Diário (Cronograma de 10 Dias Úteis)

### Semana 1: Serviço C++ e Motor de Busca Lexical (10h)

- **Dia 1 (2h):** Setup do projeto C++, Makefile/CMake e inclusão de `cpp-httplib.h` e `nlohmann/json.hpp`.
- **Dia 2 (2h):** Implementação da função de normalização de texto (remoção de acentos e minúsculas).
- **Dia 3 (2h):** Implementação do extrator de filtros de ocupação (`adults`, `guests`) via regex.
- **Dia 4 (2h):** Construção da estrutura de índice invertido em memória e função de busca por interseção.
- **Dia 5 (2h):** Criação da rota HTTP `POST /v1/search/parse` e testes unitários cobrindo as fixtures.

### Semana 2: Módulo QloApps e Barra de Pesquisa (10h)

- **Dia 6 (2h):** Scaffolding do módulo `qloactionablesearch` e registro do menu administrativo no QloApps.
- **Dia 7 (2h):** Desenvolvimento da view Smarty com barra de pesquisa rápida e catálogo de teste embutido.
- **Dia 8 (2h):** Implementação do cliente cURL no PHP com timeout de 600ms e tratamento de erros 400/503.
- **Dia 9 (2h):** Renderização dos chips identificados e dos cards de quartos encontrados.
- **Dia 10 (2h):** Validação dos 4 cenários BDD, teste de contingência (serviço offline) e gravação de demo.

---

### 15.1. Quickstart de 1 Linha (Compilação, Execução & Teste cURL)
```bash
# 1. Compilar e executar o motor de busca em C++:
g++ -std=c++17 src/main.cpp -Iinclude -lpthread -O2 -o search_service && ./search_service

# 2. Em outro terminal, testar a busca rápida via cURL:
curl -s -X POST http://127.0.0.1:8108/v1/search/parse   -H "Content-Type: application/json"   -H "X-Correlation-ID: test-search-01"   -d '{"query": "suite vista mar 2 adultos", "catalog_version": "v1", "catalog": [{"id": "room-suite-01", "type": "ROOM_TYPE", "title": "Suíte Master Vista Mar", "capacity_adults": 2, "amenities": ["vista_mar"], "aliases": ["suite"]}]}' | jq .
```

## 16. Definição de Pronto (Definition of Done — DoD Checklist)

- [ ] Binário C++ compila com `-std=c++17 -Wall -Wextra` e responde em `http://127.0.0.1:8108/healthz`.
- [ ] Testes unitários do motor de busca executam com 100% de sucesso contra as fixtures.
- [ ] Módulo QloApps instala sem alterar arquivos do core e adiciona a barra de busca no painel.
- [ ] Busca de termos com e sem acento retorna os quartos esperados.
- [ ] QloApps exibe mensagem de contingência adequada caso o serviço C++ esteja desligado.
- [ ] Documentação de compilação e fixtures testadas com sucesso.