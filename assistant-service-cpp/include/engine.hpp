#pragma once

#include <string>
#include "json.hpp"
#include "api_contract.hpp"
#include "text_normalizer.hpp"
#include "validation/request_validator.hpp"
#include "domain/intent.hpp"
#include "domain/query_classifier.hpp"
#include "domain/slot_extractor.hpp"

namespace assistant {

using json = nlohmann::json;

class AssistantEngine {
public:
    static InferenceResult interpret(const std::string& rawBody, const std::string& correlationId) {
        // 1. Validate payload and extract fields
        auto validationResult = validation::RequestValidator::validatePayload(rawBody);
        if (!validationResult.isValid) {
            return {validationResult.httpStatus, validationResult.problem};
        }

        // 2. Normalize query text
        std::string normQuery = normalizeText(validationResult.rawQuery);

        // 3. Classify intent
        auto classification = domain::QueryClassifier::classify(normQuery);

        json response;
        response["correlation_id"] = correlationId;
        response["intent"] = domain::intentToString(classification.intent);
        response["confidence"] = classification.confidence;

        // 4. Build response according to intent
        switch (classification.intent) {
            case domain::Intent::RESERVATION_LOOKUP: {
                std::string code = domain::SlotExtractor::extractReservationCode(normQuery);
                response["slots"] = {
                    {"reservation_code", code.empty() ? nullptr : json(code)}
                };
                response["explanation"] = "Identificada consulta de status de reserva com localizador.";
                return {200, response};
            }

            case domain::Intent::POLICY_QUERY: {
                bool hasCheckinRules = (normQuery.find("check-in tardio") != std::string::npos ||
                                        normQuery.find("checkin tardio") != std::string::npos ||
                                        normQuery.find("horario de check-in") != std::string::npos ||
                                        normQuery.find("horario de checkin") != std::string::npos ||
                                        normQuery.find("limite de check-in") != std::string::npos ||
                                        normQuery.find("politica de check-in") != std::string::npos);
                std::string category = hasCheckinRules ? "checkin_rules" : "cancellation";
                response["slots"] = {
                    {"policy_category", category}
                };
                response["explanation"] = "Identificada duvida sobre regras de " + category + ".";
                return {200, response};
            }

            case domain::Intent::AVAILABILITY_QUERY: {
                auto slots = domain::SlotExtractor::extractAvailabilitySlots(normQuery, validationResult.referenceDate);
                response["slots"] = {
                    {"room_type", slots.roomType},
                    {"check_in", slots.checkIn},
                    {"check_out", slots.checkOut},
                    {"guests", slots.guests},
                    {"policy_category", nullptr},
                    {"reservation_code", nullptr}
                };

                std::string roomExp = slots.roomType.is_string() ? 
                    ("tipo de quarto '" + slots.roomType.get<std::string>() + "'") : 
                    "nenhum tipo de quarto especifico";
                std::string guestExp = slots.guests.is_number() ? 
                    (", contagem de hospedes (" + std::to_string(slots.guests.get<int>()) + ")") : "";
                
                response["explanation"] = "Identificado " + roomExp + guestExp + slots.dateExplanation + ".";
                return {200, response};
            }

            case domain::Intent::UNKNOWN:
            default: {
                response["slots"] = json::object();
                response["explanation"] = "Consulta fora de dominio hoteleiro.";
                return {200, response};
            }
        }
    }
};

} // namespace assistant
