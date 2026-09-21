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

    // 5. Test Issue 'Confiante != Confiável': Housekeeping service query must be UNKNOWN (< 0.60)
    {
        std::string q = assistant::normalizeText("quantas camareiras estão disponíveis no quarto suite deluxe premium");
        auto res = QueryClassifier::classify(q);
        assert(res.intent == Intent::UNKNOWN);
        assert(res.confidence < 0.60);
        std::cout << "  [PASS] Issue Regression: Housekeeping staff query classified as UNKNOWN (< 0.60)" << std::endl;
    }

    // 6. Test Issue 'Confiante != Confiável': Underspecified amenity query must not have high confidence
    {
        std::string q = assistant::normalizeText("alguma suíte com vista pra praia");
        auto res = QueryClassifier::classify(q);
        assert(res.confidence < 0.60);
        std::cout << "  [PASS] Issue Regression: Underspecified amenity query has calibrated low confidence (< 0.60)" << std::endl;
    }

    // 7. Test Availability with Amenity: Room with TV and view but with explicit booking intent
    {
        std::string q = assistant::normalizeText("Tem suíte com televisão e vista pro mar disponível para amanhã?");
        auto res = QueryClassifier::classify(q);
        assert(res.intent == Intent::AVAILABILITY_QUERY);
        assert(res.confidence >= 0.90);
        std::cout << "  [PASS] Availability query with amenity and booking intent has high confidence (>= 0.90)" << std::endl;
    }

    // 8. Test RN-002: Availability query without specific room type ("Tem vaga para amanhã?")
    {
        std::string q = assistant::normalizeText("Tem vaga para amanhã?");
        auto res = QueryClassifier::classify(q);
        assert(res.intent == Intent::AVAILABILITY_QUERY);
        assert(res.confidence >= 0.85);
        std::cout << "  [PASS] RN-002: Availability query without room type classified as AVAILABILITY_QUERY (>= 0.85)" << std::endl;
    }

    std::cout << "=== All Classifier Unit Tests Passed Successfully! ===" << std::endl;
    return 0;
}
