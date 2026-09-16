#include <gtest/gtest.h>

#include <algorithm>
#include <string>
#include <vector>

#include "Extractor.hpp"

// 1. Extração de ocupação por números explícitos acompanhados de palavras-chave
TEST(ExtractorTest, Extract_NumericAdultsWithKeywords) {
  // 2 adultos
  auto f1 = Extractor::extract("suite para 2 adultos com vista mar");
  ASSERT_TRUE(f1.adults.has_value());
  EXPECT_EQ(f1.adults.value(), 2);
  ASSERT_FALSE(f1.amenities.empty());
  EXPECT_EQ(f1.amenities[0], "vista_mar");

  // 3 pessoas
  auto f2 = Extractor::extract("quarto com banheira para 3 pessoas");
  ASSERT_TRUE(f2.adults.has_value());
  EXPECT_EQ(f2.adults.value(), 3);
  ASSERT_FALSE(f2.amenities.empty());
  EXPECT_EQ(f2.amenities[0], "banheira");

  // 1 hospede
  auto f3 = Extractor::extract("quarto single para 1 hospede");
  ASSERT_TRUE(f3.adults.has_value());
  EXPECT_EQ(f3.adults.value(), 1);

  // 4 guests / 2 pax
  auto f4 = Extractor::extract("quarto para 4 guests com wifi");
  ASSERT_TRUE(f4.adults.has_value());
  EXPECT_EQ(f4.adults.value(), 4);

  auto f5 = Extractor::extract("suite executiva 2 pax");
  ASSERT_TRUE(f5.adults.has_value());
  EXPECT_EQ(f5.adults.value(), 2);
}

// 2. Extração de ocupação por preposição + dígitos
TEST(ExtractorTest, Extract_PrepositionWithDigits) {
  auto f1 = Extractor::extract("quarto para 4 com ar split");
  ASSERT_TRUE(f1.adults.has_value());
  EXPECT_EQ(f1.adults.value(), 4);
  ASSERT_FALSE(f1.amenities.empty());
  EXPECT_EQ(f1.amenities[0], "ar_condicionado");

  auto f2 = Extractor::extract("suite pra 2 com banheira");
  ASSERT_TRUE(f2.adults.has_value());
  EXPECT_EQ(f2.adults.value(), 2);
}

// 3. Extração de ocupação por números por extenso
TEST(ExtractorTest, Extract_WordNumbers) {
  auto f1 = Extractor::extract("suite para duas pessoas com jacuzzi e wifi");
  ASSERT_TRUE(f1.adults.has_value());
  EXPECT_EQ(f1.adults.value(), 2);
  EXPECT_NE(std::find(f1.amenities.begin(), f1.amenities.end(), "banheira"),
            f1.amenities.end());
  EXPECT_NE(std::find(f1.amenities.begin(), f1.amenities.end(), "wifi"),
            f1.amenities.end());

  auto f2 = Extractor::extract("quarto para tres hospedes com varanda");
  ASSERT_TRUE(f2.adults.has_value());
  EXPECT_EQ(f2.adults.value(), 3);
  EXPECT_NE(std::find(f2.amenities.begin(), f2.amenities.end(), "varanda"),
            f2.amenities.end());
}

// 4. Inferência de ocupação por termos semânticos hoteleiros
TEST(ExtractorTest, Extract_SemanticTerms) {
  auto fCasal = Extractor::extract("quarto casal pe na areia climatizado");
  ASSERT_TRUE(fCasal.adults.has_value());
  EXPECT_EQ(fCasal.adults.value(), 2);
  EXPECT_NE(std::find(fCasal.amenities.begin(), fCasal.amenities.end(), "vista_mar"),
            fCasal.amenities.end());
  EXPECT_NE(std::find(fCasal.amenities.begin(), fCasal.amenities.end(), "ar_condicionado"),
            fCasal.amenities.end());

  auto fDuplo = Extractor::extract("quarto duplo");
  ASSERT_TRUE(fDuplo.adults.has_value());
  EXPECT_EQ(fDuplo.adults.value(), 2);

  auto fTriplo = Extractor::extract("apartamento triplo");
  ASSERT_TRUE(fTriplo.adults.has_value());
  EXPECT_EQ(fTriplo.adults.value(), 3);

  auto fQuadruplo = Extractor::extract("quarto quadruplo");
  ASSERT_TRUE(fQuadruplo.adults.has_value());
  EXPECT_EQ(fQuadruplo.adults.value(), 4);

  auto fSingle = Extractor::extract("quarto solteiro");
  ASSERT_TRUE(fSingle.adults.has_value());
  EXPECT_EQ(fSingle.adults.value(), 1);
}

// 5. Números soltos sem palavra-chave de ocupação devem ser ignorados (RN-002)
TEST(ExtractorTest, Extract_LooseNumbersIgnoredWithoutKeywords) {
  auto f = Extractor::extract("quarto numero 104");
  EXPECT_FALSE(f.adults.has_value());
}

// 6. Mapeamento de sinonimos de comodidades
TEST(ExtractorTest, Extract_AmenitiesSynonyms) {
  auto f1 = Extractor::extract("chale com hidro e piscina aquecida e arcondicionado");
  EXPECT_NE(std::find(f1.amenities.begin(), f1.amenities.end(), "banheira"),
            f1.amenities.end());
  EXPECT_NE(std::find(f1.amenities.begin(), f1.amenities.end(), "piscina"),
            f1.amenities.end());
  EXPECT_NE(std::find(f1.amenities.begin(), f1.amenities.end(), "ar_condicionado"),
            f1.amenities.end());

  auto f2 = Extractor::extract("quarto com smart tv, frigobar e cafe da manha incluso");
  EXPECT_NE(std::find(f2.amenities.begin(), f2.amenities.end(), "tv"),
            f2.amenities.end());
  EXPECT_NE(std::find(f2.amenities.begin(), f2.amenities.end(), "frigobar"),
            f2.amenities.end());
  EXPECT_NE(std::find(f2.amenities.begin(), f2.amenities.end(), "cafe_manha"),
            f2.amenities.end());

  auto f3 = Extractor::extract("hotel pet friendly com academia, estacionamento e acessibilidade");
  EXPECT_NE(std::find(f3.amenities.begin(), f3.amenities.end(), "pet_friendly"),
            f3.amenities.end());
  EXPECT_NE(std::find(f3.amenities.begin(), f3.amenities.end(), "academia"),
            f3.amenities.end());
  EXPECT_NE(std::find(f3.amenities.begin(), f3.amenities.end(), "estacionamento"),
            f3.amenities.end());
  EXPECT_NE(std::find(f3.amenities.begin(), f3.amenities.end(), "acessibilidade"),
            f3.amenities.end());
}

// 7. Remoção de expressões de filtro da query (stripFilterPhrases)
TEST(ExtractorTest, StripFilterPhrases_RemovesOccupancyPatterns) {
  std::string s1 = Extractor::stripFilterPhrases("suite para duas pessoas com vista mar");
  EXPECT_EQ(s1.find("duas pessoas"), std::string::npos);

  std::string s2 = Extractor::stripFilterPhrases("quarto para 3 com ar split");
  EXPECT_EQ(s2.find("para 3"), std::string::npos);

  std::string s3 = Extractor::stripFilterPhrases("reserva 2 adultos em frente ao mar");
  EXPECT_EQ(s3.find("2 adultos"), std::string::npos);
}
