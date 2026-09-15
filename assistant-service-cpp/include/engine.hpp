#pragma once

#include <string>
#include <regex>
#include <algorithm>
#include "json.hpp"
#include "text_normalizer.hpp"
#include "date_resolver.hpp"

namespace assistant {

using json = nlohmann::json;

struct InferenceResult {
    int httpStatus;
    json body;
};

class AssistantEngine {
public:
    static InferenceResult interpret(const std::string& rawBody, const std::string& correlationId) {
        json parsed;
        try {
            parsed = json::parse(rawBody);
        } catch (...) {
            return {400, buildProblemDetails(
                "https://hotel.local/errors/malformed-json",
                "Malformed JSON",
                400,
                "Request body contains invalid JSON.",
                "MALFORMED_JSON"
            )};
        }

        // 1. Validate 'query' presence and type
        if (!parsed.contains("query")) {
            return {400, buildProblemDetails(
                "https://hotel.local/errors/invalid-payload",
                "Invalid Request Payload",
                400,
                "Field 'query' is required.",
                "MISSING_QUERY",
                "query"
            )};
        }

        if (!parsed["query"].is_string()) {
            return {400, buildProblemDetails(
                "https://hotel.local/errors/invalid-payload",
                "Invalid Request Payload",
                400,
                "Field 'query' must be a valid non-null string.",
                "INVALID_QUERY_TYPE",
                "query"
            )};
        }

        std::string rawQuery = parsed["query"].get<std::string>();

        // 2. Validate 'query' minLength: 1 (reject empty and whitespace-only)
        bool isOnlyWhitespace = std::all_of(rawQuery.begin(), rawQuery.end(), [](unsigned char c) {
            return std::isspace(c);
        });

        if (rawQuery.empty() || isOnlyWhitespace) {
            return {400, buildProblemDetails(
                "https://hotel.local/errors/invalid-payload",
                "Invalid Request Payload",
                400,
                "Field 'query' must not be empty or solely whitespace (minLength: 1).",
                "EMPTY_QUERY",
                "query"
            )};
        }

        // 3. Validate 'query' maxLength: 256
        if (rawQuery.size() > 256) {
            return {400, buildProblemDetails(
                "https://hotel.local/errors/invalid-payload",
                "Invalid Request Payload",
                400,
                "Field 'query' exceeds maximum length of 256 characters (maxLength: 256).",
                "QUERY_TOO_LONG",
                "query"
            )};
        }

        // 4. Validate 'reference_date' presence and type
        if (!parsed.contains("reference_date")) {
            return {400, buildProblemDetails(
                "https://hotel.local/errors/invalid-payload",
                "Invalid Request Payload",
                400,
                "Field 'reference_date' is required and must match format YYYY-MM-DD.",
                "MISSING_REFERENCE_DATE",
                "reference_date"
            )};
        }

        if (!parsed["reference_date"].is_string()) {
            return {400, buildProblemDetails(
                "https://hotel.local/errors/invalid-payload",
                "Invalid Request Payload",
                400,
                "Field 'reference_date' must be a string matching format YYYY-MM-DD.",
                "INVALID_REFERENCE_DATE",
                "reference_date"
            )};
        }

        std::string refDate = parsed["reference_date"].get<std::string>();

        int y, m, d;
        if (!DateResolver::parseDate(refDate, y, m, d)) {
            return {400, buildProblemDetails(
                "https://hotel.local/errors/invalid-payload",
                "Invalid Request Payload",
                400,
                "Field 'reference_date' is invalid. Expected format: YYYY-MM-DD.",
                "INVALID_REFERENCE_DATE",
                "reference_date"
            )};
        }

        std::string normQuery = normalizeText(rawQuery);

        // Classify intent
        json response;
        response["correlation_id"] = correlationId;

        // 1. Check for RESERVATION_LOOKUP
        std::smatch resMatch;
        std::regex resCodeRegex("res-[0-9a-zA-Z]+");
        bool hasResCode = std::regex_search(normQuery, resMatch, resCodeRegex);
        bool hasResLookupTerms = (normQuery.find("minha reserva") != std::string::npos ||
                                  normQuery.find("status da reserva") != std::string::npos ||
                                  normQuery.find("consultar reserva") != std::string::npos ||
                                  normQuery.find("codigo de reserva") != std::string::npos);

        if (hasResCode || hasResLookupTerms) {
            response["intent"] = "RESERVATION_LOOKUP";
            response["confidence"] = 0.92;
            std::string code = "";
            if (hasResCode) {
                code = resMatch.str();
                // Uppercase the reservation code (e.g. RES-9941)
                std::transform(code.begin(), code.end(), code.begin(), ::toupper);
            }
            json slots = {
                {"reservation_code", code.empty() ? nullptr : json(code)}
            };
            response["slots"] = slots;
            response["explanation"] = "Identificada consulta de status de reserva com localizador.";
            return {200, response};
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
            response["intent"] = "POLICY_QUERY";
            response["confidence"] = 0.90;
            std::string category = hasCheckinRules ? "checkin_rules" : "cancellation";
            response["slots"] = {
                {"policy_category", category}
            };
            response["explanation"] = "Identificada duvida sobre regras de " + category + ".";
            return {200, response};
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
            response["intent"] = "AVAILABILITY_QUERY";
            response["confidence"] = 0.95;

            // Extract room_type
            json roomType = nullptr;
            if (normQuery.find("deluxe") != std::string::npos) {
                roomType = "deluxe";
            } else if (normQuery.find("suite") != std::string::npos) {
                roomType = "suite";
            } else if (normQuery.find("standard") != std::string::npos) {
                roomType = "standard";
            }

            // Extract relative dates
            auto rel = DateResolver::detectRelativeDate(normQuery);
            json checkIn = nullptr;
            json checkOut = nullptr;
            std::string dateExplanation = "";

            if (rel.hasTemporal) {
                std::string inDate = DateResolver::addDays(refDate, rel.dayOffset);
                std::string outDate = DateResolver::addDays(inDate, 1);
                checkIn = inDate;
                checkOut = outDate;
                dateExplanation = " e data relativa '" + rel.label + "' calculada como " + inDate + " baseada em " + refDate;
            }

            // Extract guests count
            json guests = nullptr;
            std::smatch guestMatch;
            std::regex guestRegex("(?:para\\s+)?([0-9]+)\\s*(?:pessoas?|adultos?|hospedes?|hóspedes?)");
            if (std::regex_search(normQuery, guestMatch, guestRegex)) {
                try {
                    int count = std::stoi(guestMatch[1].str());
                    if (count >= 1 && count <= 10) {
                        guests = count;
                    }
                } catch (...) {}
            }

            response["slots"] = {
                {"room_type", roomType},
                {"check_in", checkIn},
                {"check_out", checkOut},
                {"guests", guests},
                {"policy_category", nullptr},
                {"reservation_code", nullptr}
            };

            std::string roomExp = roomType.is_string() ? ("tipo de quarto '" + roomType.get<std::string>() + "'") : "nenhum tipo de quarto especifico";
            std::string guestExp = guests.is_number() ? (", contagem de hospedes (" + std::to_string(guests.get<int>()) + ")") : "";
            response["explanation"] = "Identificado " + roomExp + guestExp + dateExplanation + ".";
            return {200, response};
        }

        // 4. UNKNOWN fallback
        response["intent"] = "UNKNOWN";
        response["confidence"] = 0.35;
        response["slots"] = json::object();
        response["explanation"] = "Consulta fora de dominio hoteleiro.";
        return {200, response};
    }

private:
    static json buildProblemDetails(
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

} // namespace assistant
