#include <gtest/gtest.h>

#include "Normalizer.hpp"

// 1. Testes de conversao de caixa e remocao de acentos (RN-001)
TEST(NormalizerTest, ToLowerAndStripAccents_BasicAndDiacritics) {
  std::string text = "SUÍTE VISTA MAR com BANHEIRA & PISCINA CLIMATIZADA!";
  std::string normalized = Normalizer::toLowerAndStripAccents(text);
  EXPECT_EQ(normalized, "suite vista mar com banheira & piscina climatizada!");
}

TEST(NormalizerTest, ToLowerAndStripAccents_AllPortugueseDiacritics) {
  // Testa conjunto de diacriticos e acentuacoes suportados em UTF-8
  std::string input = "ÀÁÂÃÄ ÈÉÊË ÌÍÎÏ ÒÓÔÕÖ ÙÚÛÜ Ç Ñ àáâãä èéêë ìíîï òóôõö ùúûü ç ñ";
  std::string expected = "aaaaa eeee iiii ooooo uuuu c n aaaaa eeee iiii ooooo uuuu c n";
  EXPECT_EQ(Normalizer::toLowerAndStripAccents(input), expected);
}

// 2. Testes de tokenizacao e remocao de pontuacao
TEST(NormalizerTest, Tokenize_RemovesPunctuationAndStopwords) {
  std::string text = "SUÍTE VISTA MAR com BANHEIRA & PISCINA CLIMATIZADA!";
  auto tokens = Normalizer::tokenize(text, true);

  std::vector<std::string> expected = {
      "suite", "vista", "mar", "banheira", "piscina", "climatizada",
  };
  EXPECT_EQ(tokens, expected);
}

TEST(NormalizerTest, Tokenize_KeepsStopwordsWhenFlagIsFalse) {
  std::string text = "quarto com vista para o mar";
  auto tokens = Normalizer::tokenize(text, false);

  std::vector<std::string> expected = {
      "quarto", "com", "vista", "para", "o", "mar",
  };
  EXPECT_EQ(tokens, expected);
}

TEST(NormalizerTest, Tokenize_HandlesEmptyAndWhitespace) {
  auto emptyTokens = Normalizer::tokenize("");
  EXPECT_TRUE(emptyTokens.empty());

  auto wsTokens = Normalizer::tokenize("   \t  \n  ");
  EXPECT_TRUE(wsTokens.empty());
}

// 3. Testes de Stopwords (preservacao de vocabulario de dominio hoteleiro)
TEST(NormalizerTest, IsStopword_PreservesDomainSpecificTerms) {
  // Termos como 'mar', 'sol', 'spa', 'pet', 'suite' nao podem ser descartados
  EXPECT_FALSE(Normalizer::isStopword("mar"));
  EXPECT_FALSE(Normalizer::isStopword("spa"));
  EXPECT_FALSE(Normalizer::isStopword("sol"));
  EXPECT_FALSE(Normalizer::isStopword("pet"));
  EXPECT_FALSE(Normalizer::isStopword("suite"));
  EXPECT_FALSE(Normalizer::isStopword("quarto"));
}

TEST(NormalizerTest, IsStopword_IdentifiesCommonStopwords) {
  EXPECT_TRUE(Normalizer::isStopword("de"));
  EXPECT_TRUE(Normalizer::isStopword("com"));
  EXPECT_TRUE(Normalizer::isStopword("para"));
  EXPECT_TRUE(Normalizer::isStopword("em"));
  EXPECT_TRUE(Normalizer::isStopword("no"));
  EXPECT_TRUE(Normalizer::isStopword("na"));
  EXPECT_TRUE(Normalizer::isStopword("the"));
  EXPECT_TRUE(Normalizer::isStopword("with"));
}
