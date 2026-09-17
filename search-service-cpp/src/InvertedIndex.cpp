#include "InvertedIndex.hpp"

#include <algorithm>

#include "Normalizer.hpp"

void InvertedIndex::clear() {
  index_.clear();
  entities_.clear();
  insertionOrder_.clear();
}

void InvertedIndex::addEntity(const CatalogEntity& entity) {
  entities_[entity.id] = entity;
  insertionOrder_.push_back(entity.id);

  // Indexa tokens do titulo
  std::vector<std::string> titleTokens = Normalizer::tokenize(entity.title);
  for (const auto& tok : titleTokens) {
    index_[tok].insert(entity.id);
  }

  // Indexa tokens de cada alias
  for (const auto& alias : entity.aliases) {
    std::vector<std::string> aliasTokens = Normalizer::tokenize(alias);
    for (const auto& tok : aliasTokens) {
      index_[tok].insert(entity.id);
    }
  }

  // Indexa tokens de cada comodidade (ex: vista_mar -> vista, mar)
  for (const auto& amenity : entity.amenities) {
    std::vector<std::string> amenityTokens = Normalizer::tokenize(amenity);
    for (const auto& tok : amenityTokens) {
      index_[tok].insert(entity.id);
    }
  }
}

std::vector<std::string> InvertedIndex::getMatchedTokens(
    const std::vector<std::string>& queryTokens) const {
  std::vector<std::string> matched;
  std::unordered_set<std::string> seen;

  for (const auto& tok : queryTokens) {
    if (index_.find(tok) != index_.end() && seen.find(tok) == seen.end()) {
      matched.push_back(tok);
      seen.insert(tok);
    }
  }
  return matched;
}

std::vector<std::string> InvertedIndex::searchConjunctive(
    const std::vector<std::string>& queryTokens,
    std::optional<int> minAdults) const {
  if (entities_.empty()) {
    return {};
  }

  std::unordered_set<std::string> candidateIds;

  if (queryTokens.empty()) {
    // Sem tokens textuais: todos os itens do catalogo sao candidatos
    for (const auto& id : insertionOrder_) {
      candidateIds.insert(id);
    }
  } else {
    // Para cada token da busca conhecido, deve haver correspondencia (AND search).
    // Tokens desconhecidos ou com erros ortograficos (ex: "vita" em vez de "vista")
    // sao ignorados para retornar o resultado mais proximo pelos termos validos.
    bool firstToken = true;
    size_t knownTokensCount = 0;

    for (const auto& tok : queryTokens) {
      auto it = index_.find(tok);
      if (it == index_.end()) {
        // Palavra ausente no catalogo / erro ortografico: ignora o termo
        continue;
      }

      knownTokensCount++;
      if (firstToken) {
        candidateIds = it->second;
        firstToken = false;
      } else {
        std::unordered_set<std::string> intersection;
        for (const auto& id : candidateIds) {
          if (it->second.find(id) != it->second.end()) {
            intersection.insert(id);
          }
        }
        candidateIds = std::move(intersection);
        if (candidateIds.empty()) {
          return {};
        }
      }
    }

    // Se nenhum dos termos pesquisados foi reconhecido no catalogo, retorna vazio
    if (knownTokensCount == 0) {
      return {};
    }
  }

  // Filtra por capacidade minima e mantem a ordem original de insercao
  std::vector<std::string> result;
  result.reserve(candidateIds.size());

  for (const auto& id : insertionOrder_) {
    if (candidateIds.find(id) != candidateIds.end()) {
      auto entIt = entities_.find(id);
      if (entIt != entities_.end()) {
        if (minAdults.has_value() &&
            entIt->second.capacityAdults < minAdults.value()) {
          continue;  // Exclui entidades com capacidade insuficiente (RN-003)
        }
        result.push_back(id);
      }
    }
  }

  return result;
}

const CatalogEntity* InvertedIndex::getEntity(const std::string& id) const {
  auto it = entities_.find(id);
  if (it != entities_.end()) {
    return &it->second;
  }
  return nullptr;
}
