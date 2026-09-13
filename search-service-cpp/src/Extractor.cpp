#include "Extractor.hpp"

#include <regex>

ExtractedFilters Extractor::extract(const std::string& normalizedQuery) {
  ExtractedFilters filters;

  // Regex para adultos/pessoas/hospedes: "2 adultos", "para 3 pessoas", "1
  // hospede"
  std::regex adultsRegex(R"((\d+)\s*(adultos?|pessoas?|hospedes?|guests?))",
                         std::regex_constants::icase);
  std::smatch match;

  if (std::regex_search(normalizedQuery, match, adultsRegex)) {
    try {
      filters.adults = std::stoi(match[1].str());
    } catch (...) {
      filters.adults = std::nullopt;
    }
  }

  // Identificacao de comodidades conhecidas
  if (normalizedQuery.find("vista mar") != std::string::npos ||
      normalizedQuery.find("vista para o mar") != std::string::npos ||
      normalizedQuery.find("frente mar") != std::string::npos) {
    filters.amenities.push_back("vista_mar");
  }
  if (normalizedQuery.find("ar condicionado") != std::string::npos ||
      normalizedQuery.find("ar-condicionado") != std::string::npos ||
      normalizedQuery.find("climatizado") != std::string::npos) {
    filters.amenities.push_back("ar_condicionado");
  }
  if (normalizedQuery.find("banheira") != std::string::npos ||
      normalizedQuery.find("hidro") != std::string::npos ||
      normalizedQuery.find("hidromassagem") != std::string::npos) {
    filters.amenities.push_back("banheira");
  }
  if (normalizedQuery.find("piscina") != std::string::npos) {
    filters.amenities.push_back("piscina");
  }
  if (normalizedQuery.find("varanda") != std::string::npos ||
      normalizedQuery.find("sacada") != std::string::npos) {
    filters.amenities.push_back("varanda");
  }

  return filters;
}

std::string Extractor::stripFilterPhrases(const std::string& normalizedQuery) {
  // Remove as frases de contagem de pessoas para nao buscarem "2" ou "adultos"
  // no indice
  std::regex filterRegex(
      R"((?:para|pra)?\s*\b\d+\s*(adultos?|pessoas?|hospedes?|guests?)\b)",
      std::regex_constants::icase);
  return std::regex_replace(normalizedQuery, filterRegex, " ");
}
