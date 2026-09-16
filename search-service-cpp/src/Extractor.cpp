#include "Extractor.hpp"

#include <algorithm>
#include <cctype>
#include <regex>
#include <unordered_map>
#include <vector>

namespace {

// Verifica se uma palavra exata existe na string com delimitadores de borda
bool hasExactWord(const std::string& text, const std::string& word) {
  size_t pos = 0;
  while ((pos = text.find(word, pos)) != std::string::npos) {
    bool leftBound =
        (pos == 0 || !std::isalnum(static_cast<unsigned char>(text[pos - 1])));
    bool rightBound =
        (pos + word.size() == text.size() ||
         !std::isalnum(static_cast<unsigned char>(text[pos + word.size()])));
    if (leftBound && rightBound) {
      return true;
    }
    pos += word.size();
  }
  return false;
}

struct AmenityRule {
  std::string id;
  std::vector<std::string> phrases;
  std::vector<std::string> exactWords;
};

// Dicionario ampliado de comodidades com alta variabilidade de termos e sinonimos
const std::vector<AmenityRule> AMENITY_RULES = {
    {"vista_mar",
     {"vista mar", "vista para o mar", "vista pro mar", "vista ao mar",
      "frente mar", "frente ao mar", "frente para o mar", "frente praia",
      "vista praia", "beira mar", "pe na areia", "sea view", "ocean view"},
     {"beachfront"}},

    {"ar_condicionado",
     {"ar condicionado", "ar-condicionado", "arcondicionado", "ar cond",
      "ar split", "ar-split", "air conditioning", "air conditioner"},
     {"climatizado", "climatizada", "climatizacao", "split", "ac"}},

    {"banheira",
     {"banheira de hidro", "banheira de hidromassagem", "hidro massagem",
      "hot tub"},
     {"banheira", "hidro", "hidromassagem", "jacuzzi", "ofuro", "bathtub",
      "tub"}},

    {"piscina",
     {"swimming pool", "piscina privativa", "piscina aquecida"},
     {"piscina", "piscinas", "pool"}},

    {"varanda",
     {"varanda gourmet"},
     {"varanda", "sacada", "balcao", "balcony", "terraco"}},

    {"ventilador",
     {"ventilador de teto"},
     {"ventilador", "ventiladores", "fan"}},

    {"wifi",
     {"wi-fi", "wi fi", "internet sem fio", "rede sem fio"},
     {"wifi", "internet", "wireless"}},

    {"tv",
     {"smart tv", "smart-tv", "tv a cabo", "tv smart"},
     {"televisao", "televisor", "tv"}},

    {"frigobar",
     {"frigobar abastecido"},
     {"frigobar", "minibar", "geladeira", "refrigerador", "fridge"}},

    {"cafe_manha",
     {"cafe da manha", "cafe incluso", "cafe matinal"},
     {"breakfast"}},

    {"estacionamento",
     {"estacionamento gratuito", "estacionamento incluso", "vaga de garagem"},
     {"estacionamento", "garagem", "parking"}},

    {"academia",
     {"sala fitness", "centro fitness"},
     {"academia", "fitness", "gym"}},

    {"pet_friendly",
     {"pet friendly", "pet-friendly", "aceita pet", "aceita pets",
      "aceita animais"},
     {"pet", "pets"}},

    {"cozinha",
     {"cozinha compacta", "cozinha equipada"},
     {"cozinha", "kitchen", "cooktop", "microondas"}},

    {"acessibilidade",
     {"acesso pcd", "quarto acessivel"},
     {"acessivel", "acessibilidade", "pcd", "cadeirante"}},
};

}  // namespace

ExtractedFilters Extractor::extract(const std::string& normalizedQuery) {
  ExtractedFilters filters;

  // 1. Regex para contagem numerica explicita com termos de ocupacao:
  // ex: "2 adultos", "para 3 pessoas", "1 hospede", "4 guests", "2 pax", "2pax"
  std::regex digitsRegex(
      R"(\b(\d+)\s*(adultos?|pessoas?|hospedes?|guests?|pax|viajantes?)\b)",
      std::regex_constants::icase);
  std::smatch match;

  if (std::regex_search(normalizedQuery, match, digitsRegex)) {
    try {
      filters.adults = std::stoi(match[1].str());
    } catch (...) {
      filters.adults = std::nullopt;
    }
  }

  // 2. Preposicao de indicacao + digitos: "para 2", "pra 3", "p/ 2"
  if (!filters.adults.has_value()) {
    std::regex prepDigitsRegex(R"(\b(?:para|pra|p/)\s*(\d+)\b)",
                               std::regex_constants::icase);
    if (std::regex_search(normalizedQuery, match, prepDigitsRegex)) {
      try {
        filters.adults = std::stoi(match[1].str());
      } catch (...) {
        filters.adults = std::nullopt;
      }
    }
  }

  // 3. Numeros por extenso em portugues ou ingles:
  // ex: "dois adultos", "duas pessoas", "tres hospedes", "one guest"
  if (!filters.adults.has_value()) {
    std::regex wordNumbersRegex(
        R"(\b(um|uma|dois|duas|tres|quatro|cinco|seis|one|two|three|four|five|six)\s+(adultos?|pessoas?|hospedes?|guests?|pax|viajantes?)\b)",
        std::regex_constants::icase);
    if (std::regex_search(normalizedQuery, match, wordNumbersRegex)) {
      std::string w = match[1].str();
      static const std::unordered_map<std::string, int> wordsMap = {
          {"um", 1},     {"uma", 1},    {"one", 1},
          {"dois", 2},   {"duas", 2},   {"two", 2},
          {"tres", 3},   {"three", 3},
          {"quatro", 4}, {"four", 4},
          {"cinco", 5},  {"five", 5},
          {"seis", 6},   {"six", 6},
      };
      auto it = wordsMap.find(w);
      if (it != wordsMap.end()) {
        filters.adults = it->second;
      }
    }
  }

  // 4. Termos semanticos hoteleiros (inferencia quando nao ha numero expresso)
  if (!filters.adults.has_value()) {
    if (hasExactWord(normalizedQuery, "casal") ||
        hasExactWord(normalizedQuery, "duplo") ||
        hasExactWord(normalizedQuery, "dupla")) {
      filters.adults = 2;
    } else if (hasExactWord(normalizedQuery, "triplo") ||
               hasExactWord(normalizedQuery, "tripla")) {
      filters.adults = 3;
    } else if (hasExactWord(normalizedQuery, "quadruplo") ||
               hasExactWord(normalizedQuery, "quadrupla")) {
      filters.adults = 4;
    } else if (hasExactWord(normalizedQuery, "solteiro") ||
               hasExactWord(normalizedQuery, "single") ||
               hasExactWord(normalizedQuery, "individual")) {
      filters.adults = 1;
    }
  }

  // 5. Identificacao de comodidades com o dicionario expandido
  for (const auto& rule : AMENITY_RULES) {
    bool matched = false;
    for (const auto& phrase : rule.phrases) {
      if (normalizedQuery.find(phrase) != std::string::npos) {
        matched = true;
        break;
      }
    }
    if (!matched) {
      for (const auto& word : rule.exactWords) {
        if (hasExactWord(normalizedQuery, word)) {
          matched = true;
          break;
        }
      }
    }
    if (matched) {
      if (std::find(filters.amenities.begin(), filters.amenities.end(),
                    rule.id) == filters.amenities.end()) {
        filters.amenities.push_back(rule.id);
      }
    }
  }

  return filters;
}

std::string Extractor::stripFilterPhrases(const std::string& normalizedQuery) {
  std::string cleaned = normalizedQuery;

  // 1. Remove frases quantitativas com digitos: "2 adultos", "para 3 pessoas", "p/ 4 hospedes"
  std::regex filterRegex(
      R"(\b(?:para|pra|p/)?\s*\d+\s*(adultos?|pessoas?|hospedes?|guests?|pax|viajantes?)\b)",
      std::regex_constants::icase);
  cleaned = std::regex_replace(cleaned, filterRegex, " ");

  // 2. Remove frases quantitativas com numeros por extenso: "dois adultos", "duas pessoas", etc.
  std::regex wordFilterRegex(
      R"(\b(?:para|pra|p/)?\s*(?:um|uma|dois|duas|tres|quatro|cinco|seis|one|two|three|four|five|six)\s+(adultos?|pessoas?|hospedes?|guests?|pax|viajantes?)\b)",
      std::regex_constants::icase);
  cleaned = std::regex_replace(cleaned, wordFilterRegex, " ");

  // 3. Remove preposicao com digitos: "para 2", "pra 3", "p/ 2"
  std::regex prepDigitsRegex(R"(\b(?:para|pra|p/)\s*\d+\b)",
                             std::regex_constants::icase);
  cleaned = std::regex_replace(cleaned, prepDigitsRegex, " ");

  return cleaned;
}
