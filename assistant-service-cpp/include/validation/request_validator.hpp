#pragma once

#include <string>
#include <algorithm>
#include <cctype>
#include "../json.hpp"
#include "../date_resolver.hpp"
#include "problem_details.hpp"

namespace assistant::validation {

using json = nlohmann::json;

struct ValidatedRequest {
    bool isValid;
    int httpStatus;
    json problem;
    std::string rawQuery;
    std::string referenceDate;
};

class RequestValidator {
public:
    static bool isValidJsonContentType(const std::string& headerVal) {
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

    static ValidatedRequest validatePayload(const std::string& rawBody) {
        json parsed;
        try {
            parsed = json::parse(rawBody);
        } catch (...) {
            return {
                false,
                400,
                ProblemDetails::create(
                    "https://hotel.local/errors/malformed-json",
                    "Malformed JSON",
                    400,
                    "Request body contains invalid JSON.",
                    "MALFORMED_JSON"
                ),
                "",
                ""
            };
        }

        // 1. Validate 'query' presence and type
        if (!parsed.contains("query")) {
            return {
                false,
                400,
                ProblemDetails::create(
                    "https://hotel.local/errors/invalid-payload",
                    "Invalid Request Payload",
                    400,
                    "Field 'query' is required.",
                    "MISSING_QUERY",
                    "query"
                ),
                "",
                ""
            };
        }

        if (!parsed["query"].is_string()) {
            return {
                false,
                400,
                ProblemDetails::create(
                    "https://hotel.local/errors/invalid-payload",
                    "Invalid Request Payload",
                    400,
                    "Field 'query' must be a valid non-null string.",
                    "INVALID_QUERY_TYPE",
                    "query"
                ),
                "",
                ""
            };
        }

        std::string rawQuery = parsed["query"].get<std::string>();

        // 2. Validate 'query' minLength: 1 (reject empty and whitespace-only)
        bool isOnlyWhitespace = std::all_of(rawQuery.begin(), rawQuery.end(), [](unsigned char c) {
            return std::isspace(c);
        });

        if (rawQuery.empty() || isOnlyWhitespace) {
            return {
                false,
                400,
                ProblemDetails::create(
                    "https://hotel.local/errors/invalid-payload",
                    "Invalid Request Payload",
                    400,
                    "Field 'query' must not be empty or solely whitespace (minLength: 1).",
                    "EMPTY_QUERY",
                    "query"
                ),
                "",
                ""
            };
        }

        // 3. Validate 'query' maxLength: 256
        if (rawQuery.size() > 256) {
            return {
                false,
                400,
                ProblemDetails::create(
                    "https://hotel.local/errors/invalid-payload",
                    "Invalid Request Payload",
                    400,
                    "Field 'query' exceeds maximum length of 256 characters (maxLength: 256).",
                    "QUERY_TOO_LONG",
                    "query"
                ),
                "",
                ""
            };
        }

        // 4. Validate 'reference_date' presence and type
        if (!parsed.contains("reference_date")) {
            return {
                false,
                400,
                ProblemDetails::create(
                    "https://hotel.local/errors/invalid-payload",
                    "Invalid Request Payload",
                    400,
                    "Field 'reference_date' is required and must match format YYYY-MM-DD.",
                    "MISSING_REFERENCE_DATE",
                    "reference_date"
                ),
                "",
                ""
            };
        }

        if (!parsed["reference_date"].is_string()) {
            return {
                false,
                400,
                ProblemDetails::create(
                    "https://hotel.local/errors/invalid-payload",
                    "Invalid Request Payload",
                    400,
                    "Field 'reference_date' must be a string matching format YYYY-MM-DD.",
                    "INVALID_REFERENCE_DATE",
                    "reference_date"
                ),
                "",
                ""
            };
        }

        std::string refDate = parsed["reference_date"].get<std::string>();

        int y, m, d;
        if (!DateResolver::parseDate(refDate, y, m, d)) {
            return {
                false,
                400,
                ProblemDetails::create(
                    "https://hotel.local/errors/invalid-payload",
                    "Invalid Request Payload",
                    400,
                    "Field 'reference_date' is invalid. Expected format: YYYY-MM-DD.",
                    "INVALID_REFERENCE_DATE",
                    "reference_date"
                ),
                "",
                ""
            };
        }

        // 5. Validate 'locale' type if present
        if (parsed.contains("locale") && !parsed["locale"].is_string()) {
            return {
                false,
                400,
                ProblemDetails::create(
                    "https://hotel.local/errors/invalid-payload",
                    "Invalid Request Payload",
                    400,
                    "Field 'locale' must be a valid string.",
                    "INVALID_LOCALE_TYPE",
                    "locale"
                ),
                "",
                ""
            };
        }

        return {true, 200, json::object(), rawQuery, refDate};
    }
};

} // namespace assistant::validation
