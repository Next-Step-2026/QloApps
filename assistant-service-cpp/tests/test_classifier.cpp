#include <iostream>
#include <cassert>
#include "domain/query_classifier.hpp"
#include "domain/slot_extractor.hpp"
#include "text_normalizer.hpp"

using namespace assistant::domain;

int main() {
    std::cout << "=== Running Classifier Unit Tests ===" << std::endl;

    // 1. Test AVAILABILITY_QUERY
    {
        std::string q = assistant::normalizeText("Tem quarto deluxe disponível?");
        auto res = QueryClassifier::classify(q);
        assert(res.intent == Intent::AVAILABILITY_QUERY);
        assert(res.confidence >= 0.90);
        std::cout << "  [PASS] AVAILABILITY_QUERY classification" << std::endl;
    }

    // 2. Test POLICY_QUERY
    {
        std::string q = assistant::normalizeText("Qual a política de cancelamento e reembolso?");
        auto res = QueryClassifier::classify(q);
        assert(res.intent == Intent::POLICY_QUERY);
        assert(res.confidence >= 0.90);
        std::cout << "  [PASS] POLICY_QUERY classification" << std::endl;
    }

    // 3. Test RESERVATION_LOOKUP
    {
        std::string q = assistant::normalizeText("Gostaria de ver o status da reserva RES-9941");
        auto res = QueryClassifier::classify(q);
        assert(res.intent == Intent::RESERVATION_LOOKUP);
        std::string code = SlotExtractor::extractReservationCode(q);
        assert(code == "RES-9941");
        std::cout << "  [PASS] RESERVATION_LOOKUP classification and code extraction" << std::endl;
    }

    // 4. Test UNKNOWN fallback
    {
        std::string q = assistant::normalizeText("Qual a previsão do tempo para a praia?");
        auto res = QueryClassifier::classify(q);
        assert(res.intent == Intent::UNKNOWN);
        assert(res.confidence < 0.60);
        std::cout << "  [PASS] UNKNOWN fallback classification" << std::endl;
    }

    std::cout << "=== All Classifier Unit Tests Passed Successfully! ===" << std::endl;
    return 0;
}
