#pragma once

#include <optional>
#include <string>
#include <unordered_map>
#include <unordered_set>
#include <vector>

struct CatalogEntity {
  std::string id;
  std::string type;
  std::string title;
  int capacityAdults = 1;
  std::vector<std::string> amenities;
  std::vector<std::string> aliases;
};

class InvertedIndex {
 public:
  void clear();
  void addEntity(const CatalogEntity& entity);

  // Busca conjuntiva (AND): a entidade precisa casar com todos os tokens
  // pesquisados e ter capacidade suficiente (capacity_adults >= minAdults)
  std::vector<std::string> searchConjunctive(
      const std::vector<std::string>& queryTokens,
      std::optional<int> minAdults = std::nullopt) const;

  const CatalogEntity* getEntity(const std::string& id) const;

  // Retorna quais dos tokens da busca estao presentes no catalogo
  std::vector<std::string> getMatchedTokens(
      const std::vector<std::string>& queryTokens) const;

 private:
  // Mapeamento: token -> lista de IDs de entidades que contem o token
  std::unordered_map<std::string, std::unordered_set<std::string>> index_;
  // Mapeamento: ID -> entidade completa
  std::unordered_map<std::string, CatalogEntity> entities_;
  // Lista ordenada de insercao dos IDs para manter determinismo
  std::vector<std::string> insertionOrder_;
};
