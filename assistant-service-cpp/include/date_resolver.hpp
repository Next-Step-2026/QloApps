#pragma once

#include <string>
#include <ctime>
#include <cstdio>
#include <regex>

namespace assistant {

class DateResolver {
public:
    static bool parseDate(const std::string& dateStr, int& year, int& month, int& day) {
        if (dateStr.size() != 10 || dateStr[4] != '-' || dateStr[7] != '-') {
            return false;
        }

        try {
            year = std::stoi(dateStr.substr(0, 4));
            month = std::stoi(dateStr.substr(5, 2));
            day = std::stoi(dateStr.substr(8, 2));
        } catch (...) {
            return false;
        }

        if (year < 1970 || year > 2100 || month < 1 || month > 12 || day < 1 || day > 31) {
            return false;
        }

        static const int daysInMonth[] = {0, 31, 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31};
        int maxDays = daysInMonth[month];
        if (month == 2) {
            bool isLeap = (year % 4 == 0 && year % 100 != 0) || (year % 400 == 0);
            if (isLeap) maxDays = 29;
        }

        return day <= maxDays;
    }

    static std::string addDays(const std::string& dateStr, int daysToAdd) {
        int y, m, d;
        if (!parseDate(dateStr, y, m, d)) {
            return "";
        }

        struct tm t = {};
        t.tm_year = y - 1900;
        t.tm_mon = m - 1;
        t.tm_mday = d + daysToAdd;

        time_t sec = timegm(&t);
        struct tm* res = gmtime(&sec);
        if (!res) {
            return "";
        }

        char buf[64];
        std::snprintf(buf, sizeof(buf), "%04d-%02d-%02d", res->tm_year + 1900, res->tm_mon + 1, res->tm_mday);
        return std::string(buf);
    }

    struct RelativeDateResult {
        bool hasTemporal;
        int dayOffset;
        std::string label;
    };

    static RelativeDateResult detectRelativeDate(const std::string& normalizedQuery) {
        // "depois de amanha" must be checked before "amanha"
        if (normalizedQuery.find("depois de amanha") != std::string::npos) {
            return {true, 2, "depois de amanha"};
        }
        if (normalizedQuery.find("amanha") != std::string::npos) {
            return {true, 1, "amanha"};
        }
        if (normalizedQuery.find("hoje") != std::string::npos) {
            return {true, 0, "hoje"};
        }
        return {false, 0, ""};
    }
};

} // namespace assistant
