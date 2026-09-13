#pragma once

#include <optional>
#include <string>
#include <vector>

struct ExtractedFilters {
  std::optional<int> adults;
  std::vector<std::string> amenities;
};

class Extractor {
 public:
  // Extrai filtros estruturados (adultos/pessoas) de uma query normalizada
  static ExtractedFilters extract(const std::string& normalizedQuery);

  // Remove as frases de filtro da query para que os numeros de ocupacao nao
  // poluam os tokens de busca
  static std::string stripFilterPhrases(const std::string& normalizedQuery);
};
