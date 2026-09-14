#include <algorithm>
#include <cassert>
#include <iostream>
#include <string>
#include <vector>

#include "Extractor.hpp"
#include "InvertedIndex.hpp"
#include "Normalizer.hpp"

// Utilitario simples para assercoes com mensagens claras
#define ASSERT_TRUE(condition, message)                        \
  do {                                                         \
    if (!(condition)) {                                        \
      std::cerr << "FAIL: " << (message) << " (" << #condition \
                << ") na linha " << __LINE__ << '\n';          \
      std::exit(1);                                            \
    }                                                          \
  } while (0)

#define ASSERT_EQ(val1, val2, message)                                 \
  do {                                                                 \
    if ((val1) != (val2)) {                                            \
      std::cerr << "FAIL: " << (message) << " | Esperado: " << (val2)  \
                << " | Obtido: " << (val1) << " na linha " << __LINE__ \
                << '\n';                                               \
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
  std::vector<std::string> expected = {
      "suite", "vista", "mar", "banheira", "piscina", "climatizada",
  };
  ASSERT_EQ(tokens.size(), expected.size(),
            "Quantidade de tokens apos stopwords");
  for (size_t i = 0; i < expected.size(); ++i) {
    ASSERT_EQ(tokens[i], expected[i], "Token correspondente");
  }

  // Garante que 'mar', 'sol', 'spa' nao sejam descartados
  ASSERT_TRUE(!Normalizer::isStopword("mar"), "'mar' nao pode ser stopword");
  ASSERT_TRUE(!Normalizer::isStopword("spa"), "'spa' nao pode ser stopword");
  ASSERT_TRUE(!Normalizer::isStopword("sol"), "'sol' nao pode ser stopword");

  std::cout << "[PASS] testNormalization" << '\n';
}

// 2. Teste de Extracao de Ocupacao e Comodidades (RN-002 e Variabilidade Expandida)
void testExtractor() {
  // 2 adultos
  auto f1 = Extractor::extract("suite para 2 adultos com vista mar");
  ASSERT_TRUE(f1.adults.has_value(), "Deve extrair adultos");
  ASSERT_EQ(f1.adults.value(), 2, "Quantidade de adultos esperada: 2");
  ASSERT_TRUE(!f1.amenities.empty() && f1.amenities[0] == "vista_mar",
              "Deve identificar comodidade vista_mar");

  // 3 pessoas com banheira
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

  // Novo: Numeros por extenso ("duas pessoas", "tres hospedes")
  auto f5 = Extractor::extract("suite para duas pessoas com jacuzzi e wifi");
  ASSERT_TRUE(f5.adults.has_value(), "Deve extrair 'duas pessoas' como 2 adultos");
  ASSERT_EQ(f5.adults.value(), 2, "Quantidade esperada: 2");
  ASSERT_TRUE(std::find(f5.amenities.begin(), f5.amenities.end(), "banheira") != f5.amenities.end(),
              "Jacuzzi deve mapear para banheira");
  ASSERT_TRUE(std::find(f5.amenities.begin(), f5.amenities.end(), "wifi") != f5.amenities.end(),
              "Wifi deve ser identificado");

  // Novo: Preposicao + digitos ("para 4 com ar split")
  auto f6 = Extractor::extract("quarto para 4 com ar split");
  ASSERT_TRUE(f6.adults.has_value(), "Deve extrair 'para 4' como 4 adultos");
  ASSERT_EQ(f6.adults.value(), 4, "Quantidade esperada: 4");
  ASSERT_TRUE(!f6.amenities.empty() && f6.amenities[0] == "ar_condicionado",
              "Ar split deve mapear para ar_condicionado");

  // Novo: Termo semantico casal e comodidade pe na areia (vista_mar)
  auto f7 = Extractor::extract("quarto casal pe na areia climatizado");
  ASSERT_TRUE(f7.adults.has_value(), "Casal deve inferir 2 adultos");
  ASSERT_EQ(f7.adults.value(), 2, "Quantidade esperada: 2");
  ASSERT_TRUE(std::find(f7.amenities.begin(), f7.amenities.end(), "vista_mar") != f7.amenities.end(),
              "Pe na areia deve mapear para vista_mar");
  ASSERT_TRUE(std::find(f7.amenities.begin(), f7.amenities.end(), "ar_condicionado") != f7.amenities.end(),
              "Climatizado deve mapear para ar_condicionado");

  // Novo: stripFilterPhrases com numeros por extenso e preposicoes
  std::string stripped1 = Extractor::stripFilterPhrases("suite para duas pessoas com vista mar");
  ASSERT_TRUE(stripped1.find("duas pessoas") == std::string::npos,
              "'duas pessoas' deve ser removido de strippedQuery");

  std::string stripped2 = Extractor::stripFilterPhrases("quarto para 3 com ar split");
  ASSERT_TRUE(stripped2.find("para 3") == std::string::npos,
              "'para 3' deve ser removido de strippedQuery");

  std::cout << "[PASS] testExtractor (com variabilidade expandida)" << '\n';
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

  std::cout << "[PASS] testConjunctiveSearchAndCapacity" << '\n';
}

// 4. Teste de Tolerancia a Erros Ortograficos e Termos Desconhecidos Ignorados
void testTypoToleranceAndIgnoredUnknownWords() {
  auto index = buildFixtureCatalog();

  // Caso: "suite vita mar" onde "vita" e erro de digitacao de "vista"
  // "vita" deve ser ignorado e a busca deve casar "suite" e "mar" -> room-suite-01
  auto tokens1 = Normalizer::tokenize("suite vita mar");
  auto res1 = index.searchConjunctive(tokens1);
  ASSERT_EQ(res1.size(), 1, "Termo 'vita' com erro deve ser ignorado e retornar a suite");
  ASSERT_EQ(res1[0], "room-suite-01", "ID do quarto suite master retornado");

  // Tokens casados devem ser apenas os termos validos existentes no catalogo
  auto matched1 = index.getMatchedTokens(tokens1);
  ASSERT_EQ(matched1.size(), 2, "Apenas 'suite' e 'mar' devem constar em matched tokens");
  ASSERT_EQ(matched1[0], "suite", "Token 'suite'");
  ASSERT_EQ(matched1[1], "mar", "Token 'mar'");

  // Caso: busca onde APENAS ha termos desconhecidos/errados ("xptovita qwerty") -> retorna 0
  auto tokens2 = Normalizer::tokenize("xptovita qwerty");
  auto res2 = index.searchConjunctive(tokens2);
  ASSERT_EQ(res2.size(), 0, "Apenas termos invalidos deve retornar 0 resultados");

  std::cout << "[PASS] testTypoToleranceAndIgnoredUnknownWords" << '\n';
}

int main() {
  std::cout << "=== Executando Testes Unitarios do Motor de Busca C++ "
               "(QLO-FEAT-008) ==="
            << '\n';

  testNormalization();
  testExtractor();
  testConjunctiveSearchAndCapacity();
  testTypoToleranceAndIgnoredUnknownWords();

  std::cout << ">>> TODOS OS TESTES PASSARAM COM SUCESSO (100% OK)! <<<"
            << '\n';
  return 0;
}
