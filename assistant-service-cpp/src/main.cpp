#include <iostream>
#include <string>
#include <algorithm>
#include "httplib.h"
#include "json.hpp"
#include "engine.hpp"

namespace {

bool isValidJsonContentType(const std::string& headerVal) {
    size_t semi = headerVal.find(';');
    std::string mediaType = (semi == std::string::npos) ? headerVal : headerVal.substr(0, semi);

    auto trim = [](std::string& s) {
        s.erase(0, s.find_first_not_of(" \t\r\n"));
        s.erase(s.find_last_not_of(" \t\r\n") + 1);
    };
    trim(mediaType);
    std::transform(mediaType.begin(), mediaType.end(), mediaType.begin(), ::tolower);

    // Must be exact "application/json"
    if (mediaType != "application/json") {
        return false;
    }

    if (semi == std::string::npos) {
        return true;
    }

    std::string params = headerVal.substr(semi + 1);
    trim(params);
    std::transform(params.begin(), params.end(), params.begin(), ::tolower);

    const size_t charsetStart = params.find("charset=");
    if (charsetStart == std::string::npos || charsetStart != 0) {
        return false;
    }

    std::string charset = params.substr(8);
    trim(charset);
    if (charset.size() >= 2 && (charset.front() == '"' || charset.front() == '\'')) {
        if (charset.back() != charset.front()) {
            return false;
        }
        charset = charset.substr(1, charset.size() - 2);
    }

    return charset == "utf-8";
}

} // namespace

int main() {
    httplib::Server svr;

    svr.Get("/healthz", [](const httplib::Request&, httplib::Response& res) {
        res.set_content("{\"status\":\"UP\"}", "application/json; charset=utf-8");
    });

    svr.Post("/v1/assist/interpret", [](const httplib::Request& req, httplib::Response& res) {
        // 1. Strict Content-Type validation (reject substring matches like text/application/json)
        if (!req.has_header("Content-Type") || !isValidJsonContentType(req.get_header_value("Content-Type"))) {
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

    std::cout << "[QLO-FEAT-001] Assistant C++ service listening on http://127.0.0.1:8101" << std::endl;
    svr.listen("127.0.0.1", 8101);
    return 0;
}
