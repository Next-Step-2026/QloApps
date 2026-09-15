#include "http_server.hpp"
#include "httplib.h"
#include "engine.hpp"
#include "validation/request_validator.hpp"
#include "validation/problem_details.hpp"
#include <iostream>

namespace assistant::http {

HttpServer::HttpServer(const std::string& host, int port)
    : host_(host), port_(port), server_(std::make_unique<httplib::Server>()) {
    registerRoutes();
}

HttpServer::~HttpServer() {
    stop();
}

void HttpServer::registerRoutes() {
    server_->Get("/healthz", [](const httplib::Request&, httplib::Response& res) {
        res.set_content("{\"status\":\"UP\"}", "application/json; charset=utf-8");
    });

    server_->Post("/v1/assist/interpret", [](const httplib::Request& req, httplib::Response& res) {
        // 1. Strict Content-Type validation (reject substring matches like text/application/json)
        if (!req.has_header("Content-Type") || !validation::RequestValidator::isValidJsonContentType(req.get_header_value("Content-Type"))) {
            if (req.has_header("X-Correlation-ID") && !req.get_header_value("X-Correlation-ID").empty()) {
                res.set_header("X-Correlation-ID", req.get_header_value("X-Correlation-ID"));
            }
            res.status = 415;
            res.set_content(
                "{\"code\":\"UNSUPPORTED_MEDIA_TYPE\","
                "\"detail\":\"Header 'Content-Type' must be 'application/json' or 'application/json; charset=utf-8'.\","
                "\"instance\":\"/v1/assist/interpret\","
                "\"invalid_params\":[{\"name\":\"Content-Type\",\"reason\":\"Header 'Content-Type' must be 'application/json' or 'application/json; charset=utf-8'.\"}],"
                "\"status\":415,"
                "\"title\":\"Unsupported Media Type\","
                "\"type\":\"https://hotel.local/errors/unsupported-media-type\"}",
                "application/problem+json; charset=utf-8"
            );
            return;
        }

        // 2. Strict X-Correlation-ID validation per RFC traceability requirements
        if (!req.has_header("X-Correlation-ID") || req.get_header_value("X-Correlation-ID").empty()) {
            res.status = 400;
            res.set_content(
                "{\"code\":\"MISSING_CORRELATION_ID\","
                "\"detail\":\"Header 'X-Correlation-ID' is required for request traceability.\","
                "\"instance\":\"/v1/assist/interpret\","
                "\"invalid_params\":[{\"name\":\"X-Correlation-ID\",\"reason\":\"Header 'X-Correlation-ID' is required for request traceability.\"}],"
                "\"status\":400,"
                "\"title\":\"Missing Header\","
                "\"type\":\"https://hotel.local/errors/missing-header\"}",
                "application/problem+json; charset=utf-8"
            );
            return;
        }

        std::string corrId = req.get_header_value("X-Correlation-ID");
        auto result = assistant::AssistantEngine::interpret(req.body, corrId);

        // 3. Return correlation ID in response header
        res.status = result.httpStatus;
        res.set_header("X-Correlation-ID", corrId);

        // 4. Return appropriate Content-Type with charset=utf-8 based on status
        if (result.httpStatus == 200) {
            res.set_content(result.body.dump(), "application/json; charset=utf-8");
        } else {
            res.set_content(result.body.dump(), "application/problem+json; charset=utf-8");
        }
    });
}

void HttpServer::start() {
    std::cout << "[QLO-FEAT-001] Assistant C++ service listening on http://" << host_ << ":" << port_ << std::endl;
    server_->listen(host_.c_str(), port_);
}

void HttpServer::stop() {
    if (server_ && server_->is_running()) {
        server_->stop();
    }
}

} // namespace assistant::http
