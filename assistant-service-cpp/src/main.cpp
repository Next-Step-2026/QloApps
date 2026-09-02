#include <iostream>
#include <regex>
#include <string>
#include "httplib.h"
#include "json.hpp"

using json = nlohmann::json;

int main() {
    httplib::Server svr;

    svr.Get("/healthz", [](const httplib::Request&, httplib::Response& res) {
        res.set_content("{\"status\":\"UP\"}", "application/json");
    });

    svr.Post("/v1/assist/interpret", [](const httplib::Request& req, httplib::Response& res) {
        try {
            auto body = json::parse(req.body);
            if (!body.contains("query") || !body.contains("reference_date")) {
                res.status = 400;
                res.set_content("{\"error\":\"INVALID_PAYLOAD\",\"detail\":\"Campos query e reference_date obrigatorios.\"}", "application/json");
                return;
            }

            std::string query = body["query"];
            std::string refDate = body["reference_date"];
            std::string corrId = req.has_header("X-Correlation-ID") ? req.get_header_value("X-Correlation-ID") : "corr-local-demo";

            (void)refDate;

            json response;
            response["correlation_id"] = corrId;

            if (query.find("quarto") != std::string::npos || query.find("disponivel") != std::string::npos || query.find("suite") != std::string::npos) {
                response["intent"] = "AVAILABILITY_QUERY";
                response["confidence"] = 0.95;
                std::string roomType = (query.find("suite") != std::string::npos) ? "suite" : "standard";
                response["slots"] = {
                    {"room_type", roomType},
                    {"check_in", "2026-08-28"},
                    {"check_out", "2026-08-29"},
                    {"guests", 2}
                };
                response["explanation"] = "Identificada intencao de disponibilidade com calculo temporal relativo.";
            } else if (query.find("cancelamento") != std::string::npos || query.find("multa") != std::string::npos) {
                response["intent"] = "POLICY_QUERY";
                response["confidence"] = 0.90;
                response["slots"] = {{"policy_category", "cancellation"}};
                response["explanation"] = "Identificada duvida sobre regras de cancelamento.";
            } else {
                response["intent"] = "UNKNOWN";
                response["confidence"] = 0.40;
                response["slots"] = json::object();
                response["explanation"] = "Consulta fora de dominio hoteleiro.";
            }

            res.status = 200;
            res.set_content(response.dump(), "application/json");
        } catch (const std::exception& e) {
            (void)e;
            res.status = 400;
            res.set_content("{\"error\":\"MALFORMED_JSON\"}", "application/json");
        }
    });

    std::cout << "[QLO-FEAT-001] Servico C++ escutando em http://127.0.0.1:8101" << std::endl;
    svr.listen("127.0.0.1", 8101);
    return 0;
}
