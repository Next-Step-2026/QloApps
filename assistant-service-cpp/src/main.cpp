#include <iostream>
#include <string>
#include "httplib.h"
#include "json.hpp"
#include "engine.hpp"

int main() {
    httplib::Server svr;

    svr.Get("/healthz", [](const httplib::Request&, httplib::Response& res) {
        res.set_content("{\"status\":\"UP\"}", "application/json");
    });

    svr.Post("/v1/assist/interpret", [](const httplib::Request& req, httplib::Response& res) {
        std::string corrId = req.has_header("X-Correlation-ID") ? req.get_header_value("X-Correlation-ID") : "corr-local-demo";
        auto result = assistant::AssistantEngine::interpret(req.body, corrId);
        res.status = result.httpStatus;
        res.set_content(result.body.dump(), "application/json");
    });

    std::cout << "[QLO-FEAT-001] Assistant C++ service listening on http://127.0.0.1:8101" << std::endl;
    svr.listen("127.0.0.1", 8101);
    return 0;
}
