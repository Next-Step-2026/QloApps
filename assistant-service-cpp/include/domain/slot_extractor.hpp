#pragma once

#include <string>
#include <regex>
#include <algorithm>
#include "../json.hpp"
#include "relative_date.hpp"

namespace assistant::domain {

using json = nlohmann::json;

struct ExtractedSlots {
    json roomType = nullptr;
    json checkIn = nullptr;
    json checkOut = nullptr;
    json guests = nullptr;
    json policyCategory = nullptr;
    json reservationCode = nullptr;
    std::string dateExplanation = "";
};

class SlotExtractor {
public:
    static std::string extractReservationCode(const std::string& normQuery) {
        std::smatch resMatch;
        std::regex resCodeRegex("res-[0-9a-zA-Z]+");
        if (std::regex_search(normQuery, resMatch, resCodeRegex)) {
            std::string code = resMatch.str();
            std::transform(code.begin(), code.end(), code.begin(), ::toupper);
            return code;
        }
        return "";
    }

    static json extractRoomType(const std::string& normQuery) {
        if (normQuery.find("deluxe") != std::string::npos) {
            return "deluxe";
        } else if (normQuery.find("suite") != std::string::npos) {
            return "suite";
        } else if (normQuery.find("standard") != std::string::npos) {
            return "standard";
        }
        return nullptr;
    }

    static json extractGuests(const std::string& normQuery) {
        std::smatch guestMatch;
        std::regex guestRegex("(?:para\\s+)?([0-9]+)\\s*(?:pessoas?|adultos?|hospedes?|hóspedes?)");
        if (std::regex_search(normQuery, guestMatch, guestRegex)) {
            try {
                int count = std::stoi(guestMatch[1].str());
                if (count >= 1 && count <= 10) {
                    return count;
                }
            } catch (...) {}
        }
        return nullptr;
    }

    static ExtractedSlots extractAvailabilitySlots(const std::string& normQuery, const std::string& refDate) {
        ExtractedSlots slots;
        slots.roomType = extractRoomType(normQuery);
        slots.guests = extractGuests(normQuery);

        auto rel = RelativeDateResolver::detect(normQuery);
        if (rel.hasTemporal) {
            std::string inDate = RelativeDateResolver::calculateDate(refDate, rel.dayOffset);
            std::string outDate = RelativeDateResolver::calculateDate(inDate, 1);
            slots.checkIn = inDate;
            slots.checkOut = outDate;
            slots.dateExplanation = " e data relativa '" + rel.label + "' calculada como " + inDate + " baseada em " + refDate;
        }

        return slots;
    }
};

} // namespace assistant::domain
