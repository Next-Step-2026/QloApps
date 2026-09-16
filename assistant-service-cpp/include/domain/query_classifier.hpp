#pragma once

#include <string>
#include <regex>
#include "intent.hpp"

namespace assistant::domain {

struct ClassificationResult {
    Intent intent;
    double confidence;
};

class QueryClassifier {
public:
    static ClassificationResult classify(const std::string& normQuery) {
        // 1. Check for RESERVATION_LOOKUP
        std::regex resCodeRegex("res-[0-9a-zA-Z]+");
        bool hasResCode = std::regex_search(normQuery, resCodeRegex);
        bool hasResLookupTerms = (normQuery.find("minha reserva") != std::string::npos ||
                                  normQuery.find("status da reserva") != std::string::npos ||
                                  normQuery.find("consultar reserva") != std::string::npos ||
                                  normQuery.find("codigo de reserva") != std::string::npos);

        if (hasResCode || hasResLookupTerms) {
            return {Intent::RESERVATION_LOOKUP, 0.92};
        }

        // 2. Check for POLICY_QUERY
        bool hasCancellation = (normQuery.find("cancelamento") != std::string::npos ||
                                normQuery.find("reembolso") != std::string::npos ||
                                normQuery.find("multa") != std::string::npos ||
                                normQuery.find("no-show") != std::string::npos ||
                                normQuery.find("noshow") != std::string::npos);

        bool hasCheckinRules = (normQuery.find("check-in tardio") != std::string::npos ||
                                normQuery.find("checkin tardio") != std::string::npos ||
                                normQuery.find("horario de check-in") != std::string::npos ||
                                normQuery.find("horario de checkin") != std::string::npos ||
                                normQuery.find("limite de check-in") != std::string::npos ||
                                normQuery.find("politica de check-in") != std::string::npos);

        if (hasCancellation || hasCheckinRules || (normQuery.find("politica") != std::string::npos && normQuery.find("quarto") == std::string::npos)) {
            return {Intent::POLICY_QUERY, 0.90};
        }

        // 3. Check for AVAILABILITY_QUERY
        bool hasAvailTerms = (normQuery.find("quarto") != std::string::npos ||
                              normQuery.find("suite") != std::string::npos ||
                              normQuery.find("deluxe") != std::string::npos ||
                              normQuery.find("standard") != std::string::npos ||
                              normQuery.find("vaga") != std::string::npos ||
                              normQuery.find("disponivel") != std::string::npos ||
                              normQuery.find("disponibilidade") != std::string::npos ||
                              normQuery.find("reservar") != std::string::npos ||
                              normQuery.find("hospedar") != std::string::npos ||
                              normQuery.find("acomodacao") != std::string::npos);

        if (hasAvailTerms) {
            return {Intent::AVAILABILITY_QUERY, 0.95};
        }

        // 4. UNKNOWN fallback
        return {Intent::UNKNOWN, 0.35};
    }
};

} // namespace assistant::domain
