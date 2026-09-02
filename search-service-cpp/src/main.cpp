#include <iostream>
#include <string>
#include "httplib.h"
#include "json.hpp"

using json = nlohmann::json;

int main() {
    httplib::Server svr;

    // Endpoint de Health Check (DoD Seção 16)
    svr.Get("/healthz", [](const httplib::Request&, httplib::Response& res) {
        json health = {
            {"status", "UP"}
        };
        res.set_content(health.dump(2), "application/json");
    });

    // Endpoint de Busca e Reconhecimento de Entidades (Seção 8)
    svr.Post("/v1/search/parse", [](const httplib::Request& req, httplib::Response& res) {
        std::string correlationId = req.has_header("X-Correlation-ID")
            ? req.get_header_value("X-Correlation-ID")
            : "corr-demo";

        if (req.body.empty()) {
            json errorResp = {
                {"type", "https://hotel.local/errors/invalid-query"},
                {"title", "Consulta de Busca Invalida"},
                {"status", 400},
                {"detail", "Corpo da requisicao vazio."},
                {"instance", "/v1/search/parse"}
            };
            res.status = 400;
            res.set_content(errorResp.dump(2), "application/problem+json");
            return;
        }

        try {
            json body = json::parse(req.body);

            if (!body.contains("query")) {
                json errorResp = {
                    {"type", "https://hotel.local/errors/invalid-query"},
                    {"title", "Campo Obrigatorio Ausente"},
                    {"status", 400},
                    {"detail", "O campo 'query' e obrigatorio."},
                    {"instance", "/v1/search/parse"}
                };
                res.status = 400;
                res.set_content(errorResp.dump(2), "application/problem+json");
                return;
            }

            std::string query = body.value("query", "");

            json response;
            response["correlation_id"] = correlationId;
            response["query"] = query;
            response["tokens_matched"] = json::array();
            response["extracted_filters"] = {{"adults", nullptr}};
            response["matching_entity_ids"] = json::array();
            response["total_matches"] = 0;
            response["message"] = "Servico HTTP ativo e pronto para integracao.";

            res.status = 200;
            res.set_content(response.dump(2), "application/json");
        } catch (const std::exception& e) {
            json errorResp = {
                {"type", "https://hotel.local/errors/invalid-json"},
                {"title", "JSON Malformado"},
                {"status", 400},
                {"detail", e.what()},
                {"instance", "/v1/search/parse"}
            };
            res.status = 400;
            res.set_content(errorResp.dump(2), "application/problem+json");
        }
    });

    const std::string host = "127.0.0.1";
    const int port = 8108;

    std::cout << "[QLO-FEAT-008] Servico de Busca C++ escutando em http://" << host << ":" << port << std::endl;

    if (!svr.listen(host, port)) {
        std::cerr << "Erro ao iniciar o servidor HTTP na porta " << port << std::endl;
        return 1;
    }

    return 0;
}
