#include <iostream>
#include <string>
#include <algorithm>
#include "rapidcheck.h"
#include "Normalizer.hpp"
#include "Extractor.hpp"
#include "InvertedIndex.hpp"

int main() {
    std::cout << "=== Executando Property-Based Testing com RapidCheck ===\n";

    // Propriedade 1: Idempotencia da normalizacao
    // Normalizar uma string duas vezes consecutivas deve produzir exatamente o mesmo resultado
    bool p1 = rc::check("Normalizer::toLowerAndStripAccents e idempotente", [](const std::string& input) {
        std::string once = Normalizer::toLowerAndStripAccents(input);
        std::string twice = Normalizer::toLowerAndStripAccents(once);
        RC_ASSERT(once == twice);
    });

    // Propriedade 2: Ausencia de letras maiusculas apos normalizacao
    bool p2 = rc::check("Normalizer nao contem caracteres ASCII maiusculos", [](const std::string& input) {
        std::string norm = Normalizer::toLowerAndStripAccents(input);
        for (char c : norm) {
            RC_ASSERT(c < 'A' || c > 'Z');
        }
    });

    // Propriedade 3: Monotonicidade da contagem de tokens com/sem stopwords
    bool p3 = rc::check("Tokens com stopwords sao sempre >= tokens sem stopwords", [](const std::string& input) {
        auto withStopwords = Normalizer::tokenize(input, false);
        auto withoutStopwords = Normalizer::tokenize(input, true);
        RC_ASSERT(withStopwords.size() >= withoutStopwords.size());
    });

    // Propriedade 4: Invariante do filtro de capacidade minima
    bool p4 = rc::check("searchConjunctive respeita capacidade minima", []() {
        int capacityReq = *rc::gen::inRange(1, 6);
        InvertedIndex index;
        CatalogEntity e1{"room-1", "ROOM_TYPE", "Suite Teste", 2, {}, {}};
        CatalogEntity e2{"room-2", "ROOM_TYPE", "Suite Familia", 4, {}, {}};
        index.addEntity(e1);
        index.addEntity(e2);

        auto results = index.searchConjunctive({"suite"}, capacityReq);
        for (const auto& id : results) {
            const auto* entity = index.getEntity(id);
            RC_ASSERT(entity != nullptr);
            RC_ASSERT(entity->capacityAdults >= capacityReq);
        }
    });

    if (p1 && p2 && p3 && p4) {
        std::cout << "\n>>> TODAS AS PROPRIEDADES INVARIANTES DO RAPIDCHECK PASSARAM! <<<\n";
        return 0;
    }

    return 1;
}
