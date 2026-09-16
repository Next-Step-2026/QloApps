#pragma once

#include <string>
#include "../json.hpp"

namespace assistant::validation {

using json = nlohmann::json;

class ProblemDetails {
public:
    static json create(
        const std::string& type,
        const std::string& title,
        int status,
        const std::string& detail,
        const std::string& code,
        const std::string& paramName = ""
    ) {
        json p;
        p["type"] = type;
        p["title"] = title;
        p["status"] = status;
        p["detail"] = detail;
        p["code"] = code;
        p["instance"] = "/v1/assist/interpret";
        if (!paramName.empty()) {
            p["invalid_params"] = json::array({
                {{"name", paramName}, {"reason", detail}}
            });
        }
        return p;
    }
};

} // namespace assistant::validation
