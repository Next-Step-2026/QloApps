#include <cassert>
#include <iostream>
#include <string>
#include <vector>

#include "Extractor.hpp"
#include "InvertedIndex.hpp"
#include "Normalizer.hpp"

// Utilitario simples para assercoes com mensagens claras
#define ASSERT_TRUE(condition, message)                                       \
  do {                                                                        \
    if (!(condition)) {                                                       \
      std::cerr << "FAIL: " << message << " (" << #condition << ") na linha " \
                << __LINE__ << std::endl;                                     \
      std::exit(1);                                                           \
    }                                                                         \
  } while (0)

#define ASSERT_EQ(val1, val2, message)                                 \
  do {                                                                 \
    if ((val1) != (val2)) {                                            \
      std::cerr << "FAIL: " << message << " | Esperado: " << (val2)    \
                << " | Obtido: " << (val1) << " na linha " << __LINE__ \
                << std::endl;                                          \
      std::exit(1);                                                    \
    }                                                                  \
  } while (0)

// 1. Teste de Normalizacao e Remocao de Acentos (RN-001)
void testNormalization() {
  std::string text = "SUÍTE VISTA MAR com BANHEIRA & PISCINA CLIMATIZADA!";
  std::string normalized = Normalizer::toLowerAndStripAccents(text);
  ASSERT_EQ(normalized, "suite vista mar com banheira & piscina climatizada!",
            "Normalizacao basica de acentos e caixa");

  auto tokens = Normalizer::tokenize(text, true);
  // Stopwords como "com" devem ser removidas
  std::vector<std::string> expected = {"suite",    "vista",   "mar",
                                       "banheira", "piscina", "climatizada"};
  ASSERT_EQ(tokens.size(), expected.size(),
            "Quantidade de tokens apos stopwords");
  for (size_t i = 0; i < expected.size(); ++i) {
    ASSERT_EQ(tokens[i], expected[i], "Token correspondente");
  }

  // Garante que 'mar', 'sol', 'spa' nao sejam descartados
  ASSERT_TRUE(!Normalizer::isStopword("mar"), "'mar' nao pode ser stopword");
  ASSERT_TRUE(!Normalizer::isStopword("spa"), "'spa' nao pode ser stopword");
  ASSERT_TRUE(!Normalizer::isStopword("sol"), "'sol' nao pode ser stopword");

  std::cout << "[PASS] testNormalization" << std::endl;
}

// 2. Teste de Extracao de Ocupacao e Comodidades (RN-002)
void testExtractor() {
  // 2 adultos
  auto f1 = Extractor::extract("suite para 2 adultos com vista mar");
  ASSERT_TRUE(f1.adults.has_value(), "Deve extrair adultos");
  ASSERT_EQ(f1.adults.value(), 2, "Quantidade de adultos esperada: 2");
  ASSERT_TRUE(!f1.amenities.empty() && f1.amenities[0] == "vista_mar",
              "Deve identificar comodidade vista_mar");

  // 3 pessoas
  auto f2 = Extractor::extract("quarto com banheira para 3 pessoas");
  ASSERT_TRUE(f2.adults.has_value(), "Deve extrair pessoas");
  ASSERT_EQ(f2.adults.value(), 3, "Quantidade de pessoas esperada: 3");
  ASSERT_TRUE(!f2.amenities.empty() && f2.amenities[0] == "banheira",
              "Deve identificar banheira");

  // 1 hospede
  auto f3 = Extractor::extract("quarto single para 1 hospede");
  ASSERT_TRUE(f3.adults.has_value(), "Deve extrair hospede");
  ASSERT_EQ(f3.adults.value(), 1, "Quantidade esperada: 1");

  // Numero solto sem palavra-chave de ocupacao (RN-002: desconsiderar)
  auto f4 = Extractor::extract("quarto numero 104");
  ASSERT_TRUE(!f4.adults.has_value(),
              "Numero solto 104 nao pode ser interpretado como adultos");

  std::cout << "[PASS] testExtractor" << std::endl;
}

// Fixture padrao do catalogo para os testes de busca
InvertedIndex buildFixtureCatalog() {
  InvertedIndex index;

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

  return index;
}

// 3. Teste de Correspondencia Conjuntiva AND e Filtro de Capacidade (RN-003,
// RN-004)
void testConjunctiveSearchAndCapacity() {
  auto index = buildFixtureCatalog();

  // Busca "suite vista mar" -> deve retornar exatamente room-suite-01
  auto tokens1 = Normalizer::tokenize("suite vista mar");
  auto res1 = index.searchConjunctive(tokens1);
  ASSERT_EQ(res1.size(), 1, "Apenas 1 quarto tem suite + vista + mar");
  ASSERT_EQ(res1[0], "room-suite-01", "ID do quarto suite master");

  // Busca "ar condicionado" -> presente na suite e no standard
  auto tokens2 = Normalizer::tokenize("ar condicionado");
  auto res2 = index.searchConjunctive(tokens2);
  ASSERT_EQ(res2.size(), 2, "Dois quartos tem ar condicionado");
  ASSERT_EQ(res2[0], "room-suite-01", "Primeiro quarto retornado");
  ASSERT_EQ(res2[1], "room-std-02", "Segundo quarto retornado");

  // Filtro de capacidade minima (RN-003): 2 adultos
  // Busca "quarto" com minAdults = 2: room-sgl-03 (capacidade 1) deve ser
  // excluido
  auto tokens3 = Normalizer::tokenize("quarto");
  auto res3 = index.searchConjunctive(tokens3, 2);
  ASSERT_EQ(res3.size(), 1,
            "Apenas o quarto standard casal tem capacidade >= 2");
  ASSERT_EQ(res3[0], "room-std-02", "Quarto standard casal retornado");

  // Sem correspondencia (total_matches = 0)
  auto tokens4 = Normalizer::tokenize("chale na montanha com lareira");
  auto res4 = index.searchConjunctive(tokens4);
  ASSERT_EQ(res4.size(), 0, "Termo inexistente deve retornar vazio");

  std::cout << "[PASS] testConjunctiveSearchAndCapacity" << std::endl;
}

int main() {
  std::cout << "=== Executando Testes Unitarios do Motor de Busca C++ "
               "(QLO-FEAT-008) ==="
            << std::endl;

  testNormalization();
  testExtractor();
  testConjunctiveSearchAndCapacity();

  std::cout << ">>> TODOS OS TESTES PASSARAM COM SUCESSO (100% OK)! <<<"
            << std::endl;
  return 0;
}
