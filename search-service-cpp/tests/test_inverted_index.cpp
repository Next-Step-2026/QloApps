#include <gtest/gtest.h>

#include <string>
#include <vector>

#include "InvertedIndex.hpp"
#include "Normalizer.hpp"

class InvertedIndexTest : public ::testing::Test {
 protected:
  void SetUp() override {
    CatalogEntity e1;
    e1.id = "room-suite-01";
    e1.type = "ROOM_TYPE";
    e1.title = "Suíte Master Vista Mar";
    e1.capacityAdults = 2;
    e1.amenities = {"vista_mar", "ar_condicionado", "banheira"};
    e1.aliases = {"suite", "suite master", "vista mar"};
    index.addEntity(e1);

    CatalogEntity e2;
    e2.id = "room-std-02";
    e2.type = "ROOM_TYPE";
    e2.title = "Quarto Standard Casal";
    e2.capacityAdults = 2;
    e2.amenities = {"ar_condicionado"};
    e2.aliases = {"standard", "casal"};
    index.addEntity(e2);

    CatalogEntity e3;
    e3.id = "room-sgl-03";
    e3.type = "ROOM_TYPE";
    e3.title = "Quarto Single Individual";
    e3.capacityAdults = 1;
    e3.amenities = {"ventilador"};
    e3.aliases = {"single", "solteiro"};
    index.addEntity(e3);
  }

  InvertedIndex index;
};

// 1. Busca conjuntiva (AND) onde todos os tokens validos devem casar
TEST_F(InvertedIndexTest, SearchConjunctive_AllTokensMatch) {
  auto tokens1 = Normalizer::tokenize("suite vista mar");
  auto res1 = index.searchConjunctive(tokens1);
  ASSERT_EQ(res1.size(), 1u);
  EXPECT_EQ(res1[0], "room-suite-01");

  auto tokens2 = Normalizer::tokenize("ar condicionado");
  auto res2 = index.searchConjunctive(tokens2);
  ASSERT_EQ(res2.size(), 2u);
  EXPECT_EQ(res2[0], "room-suite-01");
  EXPECT_EQ(res2[1], "room-std-02");
}

// 2. Filtro de capacidade minima (RN-003, RN-004)
TEST_F(InvertedIndexTest, SearchConjunctive_CapacityFilterExcludesSmallRooms) {
  // "quarto" com minAdults = 2: room-sgl-03 (capacidade 1) deve ser excluido
  auto tokens = Normalizer::tokenize("quarto");
  auto res = index.searchConjunctive(tokens, 2);
  ASSERT_EQ(res.size(), 1u);
  EXPECT_EQ(res[0], "room-std-02");

  // "quarto" com minAdults = 1: inclui ambos os quartos
  auto resMin1 = index.searchConjunctive(tokens, 1);
  ASSERT_EQ(resMin1.size(), 2u);
  EXPECT_EQ(resMin1[0], "room-std-02");
  EXPECT_EQ(resMin1[1], "room-sgl-03");

  // Capacidade alem do disponivel: retorna vazio
  auto resHigh = index.searchConjunctive(tokens, 10);
  EXPECT_TRUE(resHigh.empty());
}

// 3. Busca sem correspondencia
TEST_F(InvertedIndexTest, SearchConjunctive_NoMatchReturnsEmpty) {
  auto tokens = Normalizer::tokenize("chale na montanha com lareira");
  auto res = index.searchConjunctive(tokens);
  EXPECT_TRUE(res.empty());
}

// 4. Tolerancia a erros ortograficos e termos desconhecidos
TEST_F(InvertedIndexTest, TypoTolerance_IgnoresUnknownTokens) {
  // "vita" e erro de "vista", deve ser ignorado e casar "suite" e "mar" -> room-suite-01
  auto tokens = Normalizer::tokenize("suite vita mar");
  auto res = index.searchConjunctive(tokens);
  ASSERT_EQ(res.size(), 1u);
  EXPECT_EQ(res[0], "room-suite-01");

  // Tokens casados devem conter apenas os termos conhecidos presentes no catalogo
  auto matched = index.getMatchedTokens(tokens);
  ASSERT_EQ(matched.size(), 2u);
  EXPECT_EQ(matched[0], "suite");
  EXPECT_EQ(matched[1], "mar");
}

// 5. Query composta exclusivamente por termos desconhecidos
TEST_F(InvertedIndexTest, TypoTolerance_AllUnknownTokensReturnsEmpty) {
  auto tokens = Normalizer::tokenize("xptovita qwerty");
  auto res = index.searchConjunctive(tokens);
  EXPECT_TRUE(res.empty());

  auto matched = index.getMatchedTokens(tokens);
  EXPECT_TRUE(matched.empty());
}

// 6. Query vazia retorna todas as entidades na ordem de insercao
TEST_F(InvertedIndexTest, EmptyQuery_ReturnsAllEntitiesInInsertionOrder) {
  std::vector<std::string> emptyTokens;
  auto res = index.searchConjunctive(emptyTokens);
  ASSERT_EQ(res.size(), 3u);
  EXPECT_EQ(res[0], "room-suite-01");
  EXPECT_EQ(res[1], "room-std-02");
  EXPECT_EQ(res[2], "room-sgl-03");
}

// 7. Limpeza e reconstrucao do indice (clear)
TEST_F(InvertedIndexTest, ClearAndRebuild) {
  index.clear();
  std::vector<std::string> emptyTokens;
  EXPECT_TRUE(index.searchConjunctive(emptyTokens).empty());

  CatalogEntity novo;
  novo.id = "room-deluxe-99";
  novo.title = "Cobertura Presidencial";
  novo.capacityAdults = 4;
  index.addEntity(novo);

  auto tokens = Normalizer::tokenize("presidencial");
  auto res = index.searchConjunctive(tokens);
  ASSERT_EQ(res.size(), 1u);
  EXPECT_EQ(res[0], "room-deluxe-99");
}

// 8. Consulta direta de entidade por ID (getEntity)
TEST_F(InvertedIndexTest, GetEntity_ReturnsCorrectPointer) {
  const CatalogEntity* e = index.getEntity("room-suite-01");
  ASSERT_NE(e, nullptr);
  EXPECT_EQ(e->title, "Suíte Master Vista Mar");
  EXPECT_EQ(e->capacityAdults, 2);

  const CatalogEntity* nonExistent = index.getEntity("id-inexistente");
  EXPECT_EQ(nonExistent, nullptr);
}
