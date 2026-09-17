#pragma once

#include <string>
#include "../date_resolver.hpp"

namespace assistant::domain {

using RelativeDateResult = assistant::DateResolver::RelativeDateResult;

class RelativeDateResolver {
public:
    static RelativeDateResult detect(const std::string& text) {
        return assistant::DateResolver::detectRelativeDate(text);
    }

    static std::string calculateDate(const std::string& baseDate, int dayOffset) {
        return assistant::DateResolver::addDays(baseDate, dayOffset);
    }
};

} // namespace assistant::domain
