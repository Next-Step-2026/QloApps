#pragma once

#include <string>

namespace assistant::domain {

enum class Intent {
    AVAILABILITY_QUERY,
    POLICY_QUERY,
    RESERVATION_LOOKUP,
    UNKNOWN
};

inline std::string intentToString(Intent intent) {
    switch (intent) {
        case Intent::AVAILABILITY_QUERY:
            return "AVAILABILITY_QUERY";
        case Intent::POLICY_QUERY:
            return "POLICY_QUERY";
        case Intent::RESERVATION_LOOKUP:
            return "RESERVATION_LOOKUP";
        case Intent::UNKNOWN:
        default:
            return "UNKNOWN";
    }
}

} // namespace assistant::domain
