#include <chrono>
#include <cstdlib>
#include <iomanip>
#include <iostream>
#include <sstream>
#include <string>
#include <vector>

#include "Extractor.hpp"
#include "InvertedIndex.hpp"
#include "Normalizer.hpp"
#include "httplib.h"
#include "json.hpp"

using nlohmann::json;

// Gera timestamp ISO 8601 UTC
static std::string getIsoTimestamp() {
  auto now = std::chrono::system_clock::now();
  auto in_time_t = std::chrono::system_clock::to_time_t(now);
  auto ms = std::chrono::duration_cast<std::chrono::milliseconds>(
                now.time_since_epoch()) %
            1000;

  std::stringstream ss;
  ss << std::put_time(std::gmtime(&in_time_t), "%Y-%m-%dT%H:%M:%S");
  ss << '.' << std::setfill('0') << std::setw(3) << ms.count() << 'Z';
  return ss.str();
}

int main() {
  httplib::Server svr;

  // Endpoint de Health Check (DoD Secao 16)
  svr.Get("/healthz", [](const httplib::Request&, httplib::Response& res) {
    json health = {{"status", "UP"}};
    res.set_content(health.dump(), "application/json");
  });

  // Endpoint de Busca e Reconhecimento de Entidades (Secao 8)
  svr.Post("/v1/search/parse", [](const httplib::Request& req,
                                  httplib::Response& res) {
    auto startTime = std::chrono::steady_clock::now();

    std::string correlationId =
        req.has_header("X-Correlation-ID")
            ? req.get_header_value("X-Correlation-ID")
            : "corr-" +
                  std::to_string(
                      std::chrono::duration_cast<std::chrono::microseconds>(
                          std::chrono::system_clock::now().time_since_epoch())
                          .count());

    if (req.body.empty()) {
      json errorResp = {
          {"type", "https://hotel.local/errors/invalid-query"},
          {"title", "Consulta de Busca Invalida"},
          {"status", 400},
          {"detail", "O corpo da requisicao nao pode ser vazio."},
          {"instance", "/v1/search/parse"},
      };
      res.status = 400;
      res.set_content(errorResp.dump(), "application/problem+json");
      return;
    }

    try {
      json body = json::parse(req.body);

      // Validacao da Query
      if (!body.contains("query") || !body["query"].is_string()) {
        json errorResp = {
            {"type", "https://hotel.local/errors/invalid-query"},
            {"title", "Consulta de Busca Invalida"},
            {"status", 400},
            {"detail", "O campo 'query' e obrigatorio e deve ser texto."},
            {"instance", "/v1/search/parse"},
        };
        res.status = 400;
        res.set_content(errorResp.dump(), "application/problem+json");
        return;
      }

      std::string query = body["query"].get<std::string>();
      if (query.empty() ||
          query.find_first_not_of(" \t\n\r") == std::string::npos) {
        json errorResp = {
            {"type", "https://hotel.local/errors/invalid-query"},
            {"title", "Consulta de Busca Invalida"},
            {"status", 400},
            {"detail", "O campo 'query' nao pode ser vazio."},
            {"instance", "/v1/search/parse"},
        };
        res.status = 400;
        res.set_content(errorResp.dump(), "application/problem+json");
        return;
      }

      if (query.length() > 256) {
        json errorResp = {
            {"type", "https://hotel.local/errors/invalid-query"},
            {"title", "Consulta de Busca Invalida"},
            {"status", 400},
            {"detail", "O campo 'query' excede o limite de 256 caracteres."},
            {"instance", "/v1/search/parse"},
        };
        res.status = 400;
        res.set_content(errorResp.dump(), "application/problem+json");
        return;
      }

      // Validacao do Catalogo (RN-006: Maximo 100 itens)
      if (!body.contains("catalog") || !body["catalog"].is_array()) {
        json errorResp = {
            {"type", "https://hotel.local/errors/invalid-catalog"},
            {"title", "Catalogo Invalido"},
            {"status", 400},
            {"detail", "O campo 'catalog' e obrigatorio e deve ser uma lista."},
            {"instance", "/v1/search/parse"},
        };
        res.status = 400;
        res.set_content(errorResp.dump(), "application/problem+json");
        return;
      }

      if (body["catalog"].size() > 100) {
        json errorResp = {
            {"type", "https://hotel.local/errors/catalog-limit-exceeded"},
            {"title", "Limite de Catalogo Excedido"},
            {"status", 400},
            {"detail", "O catalogo excede o limite maximo de 100 itens."},
            {"instance", "/v1/search/parse"},
        };
        res.status = 400;
        res.set_content(errorResp.dump(), "application/problem+json");
        return;
      }

      // Pipeline de Processamento
      // 1. Normalizacao da query
      std::string normalizedQuery = Normalizer::toLowerAndStripAccents(query);

      // 2. Extracao de filtros de capacidade e comodidades (RN-002)
      ExtractedFilters filters = Extractor::extract(normalizedQuery);

      // 3. Limpeza de termos de filtro e tokenizacao (RN-001, RN-005)
      std::string strippedQuery =
          Extractor::stripFilterPhrases(normalizedQuery);
      std::vector<std::string> queryTokens =
          Normalizer::tokenize(strippedQuery, true);

      // 4. Construcao do indice invertido a partir do catalogo
      InvertedIndex index;
      for (const auto& item : body["catalog"]) {
        CatalogEntity entity;
        entity.id = item.value("id", "");
        entity.type = item.value("type", "ROOM_TYPE");
        entity.title = item.value("title", "");
        entity.capacityAdults = item.value("capacity_adults", 1);

        if (item.contains("amenities") && item["amenities"].is_array()) {
          for (const auto& am : item["amenities"]) {
            entity.amenities.push_back(am.get<std::string>());
          }
        }
        if (item.contains("aliases") && item["aliases"].is_array()) {
          for (const auto& al : item["aliases"]) {
            entity.aliases.push_back(al.get<std::string>());
          }
        }

        index.addEntity(entity);
      }

      // 5. Identificacao de tokens que casaram no catalogo
      std::vector<std::string> matchedTokens =
          index.getMatchedTokens(queryTokens);

      // 6. Busca conjuntiva (AND search) com filtro de capacidade (RN-003,
      // RN-004)
      std::vector<std::string> matchingIds =
          index.searchConjunctive(queryTokens, filters.adults);

      // Construcao da Resposta RFC-008
      json response;
      response["correlation_id"] = correlationId;
      response["tokens_matched"] = matchedTokens;
      response["extracted_filters"] = {
          {
              "adults",
              filters.adults.has_value() ? json(filters.adults.value())
                                         : json(nullptr),
          },
          {"amenities", filters.amenities},
      };
      response["matching_entity_ids"] = matchingIds;
      response["total_matches"] = matchingIds.size();

      // Calculo de Duracao e Log Estruturado JSON (Secao 12)
      auto endTime = std::chrono::steady_clock::now();
      double durationMs =
          std::chrono::duration<double, std::milli>(endTime - startTime)
              .count();

      json logEntry = {
          {"timestamp", getIsoTimestamp()},
          {"level", "INFO"},
          {"correlation_id", correlationId},
          {"event", "SEARCH_PARSED"},
          {"query", query},
          {"tokens_count", queryTokens.size()},
          {"matches_count", matchingIds.size()},
          {"duration_ms", durationMs},
      };
      std::cout << logEntry.dump() << '\n';

      res.status = 200;
      res.set_content(response.dump(), "application/json");

    } catch (const json::exception& e) {
      json errorResp = {
          {"type", "https://hotel.local/errors/invalid-json"},
          {"title", "JSON Malformado"},
          {"status", 400},
          {"detail", e.what()},
          {"instance", "/v1/search/parse"},
      };
      res.status = 400;
      res.set_content(errorResp.dump(), "application/problem+json");
    } catch (const std::exception& e) {
      json errorResp = {
          {"type", "https://hotel.local/errors/internal-error"},
          {"title", "Erro Interno no Processamento"},
          {"status", 500},
          {"detail", e.what()},
          {"instance", "/v1/search/parse"},
      };
      res.status = 500;
      res.set_content(errorResp.dump(), "application/problem+json");
    }
  });

  // Host e porta de escuta configuraveis (padrao 0.0.0.0 para atender host e
  // Docker)
  const char* envHost = std::getenv("SEARCH_SERVICE_HOST");
  const std::string host = (envHost != nullptr) ? envHost : "0.0.0.0";

  const char* envPort = std::getenv("SEARCH_SERVICE_PORT");
  const int port = (envPort != nullptr)
                       ? static_cast<int>(std::strtol(envPort, nullptr, 10))
                       : 8108;

  std::cout << "[QLO-FEAT-008] Servico de Busca C++ inicializado em http://"
            << host << ":" << port << '\n';

  if (!svr.listen(host, port)) {
    std::cerr << "Erro fatal: nao foi possivel abrir o servidor HTTP em "
              << host << ":" << port << '\n';
    return 1;
  }

  return 0;
}
